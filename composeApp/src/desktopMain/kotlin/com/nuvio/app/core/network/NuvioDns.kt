package com.nuvio.app.core.network

import co.touchlab.kermit.Logger
import com.nuvio.app.core.mediaproxy.isValidIpv4
import com.nuvio.app.core.network.dns.DnsOverHttpsEndpoint
import com.nuvio.app.core.network.dns.DnsOverHttpsRepository
import com.nuvio.app.core.network.dns.DnsOverHttpsSettings
import com.nuvio.app.core.network.dns.isValidIpv6
import com.nuvio.app.core.network.dns.resolveDnsOverHttpsEndpoint
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.io.IOException
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

private val log = Logger.withTag("DnsOverHttps")

/**
 * Resolver shared by every OkHttp client of the app. It follows the DNS over HTTPS option of
 * Settings → Advanced → Network: the device DNS while it is off, the chosen DoH server otherwise.
 * A change of the option takes effect on the next lookup (open connections keep their address).
 */
object NuvioDns : Dns {
    private val lock = Any()

    @Volatile
    private var active: Pair<DnsOverHttpsSettings, Dns>? = null

    /** Client used only to reach the DoH servers: it always resolves with the device DNS. */
    internal val bootstrapClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(Dns.SYSTEM)
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    override fun lookup(hostname: String): List<InetAddress> = currentResolver().lookup(hostname)

    private fun currentResolver(): Dns {
        val settings = DnsOverHttpsRepository.settings.value
        active?.let { (activeSettings, resolver) -> if (activeSettings == settings) return resolver }
        return synchronized(lock) {
            active?.takeIf { it.first == settings }?.second ?: run {
                val endpoint = resolveDnsOverHttpsEndpoint(settings)
                val resolver = if (endpoint == null) {
                    Dns.SYSTEM
                } else {
                    DohResolver(endpoint, settings.fallbackToSystem, bootstrapClient)
                }
                log.i {
                    if (endpoint == null) {
                        "Resolver: device DNS"
                    } else {
                        "Resolver: DoH ${endpoint.url.toHttpUrl().host} " +
                            "(bootstrap=${endpoint.bootstrapAddresses.size}, fallback=${settings.fallbackToSystem})"
                    }
                }
                active = settings to resolver
                resolver
            }
        }
    }
}

/** Plain client for small one-off requests (artwork, probes, proxy upstream); derive it with newBuilder(). */
internal val nuvioSharedOkHttpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .dns(NuvioDns)
        .build()
}

/**
 * Resolves through a DoH server, with a short in-memory cache (the DoH client has no HTTP cache).
 * Local names (`localhost`, single labels, `.local`, `.lan`, `.home.arpa`...) always go to the
 * device DNS, since public resolvers cannot know them. When [fallbackToSystem] is on, failures of
 * the DoH server are retried with [systemDns].
 */
internal class DohResolver(
    endpoint: DnsOverHttpsEndpoint,
    private val fallbackToSystem: Boolean,
    bootstrapClient: OkHttpClient,
    private val systemDns: Dns = Dns.SYSTEM,
    private val nowMs: () -> Long = System::currentTimeMillis,
) : Dns {
    private val doh: Dns = DnsOverHttps.Builder()
        .client(bootstrapClient)
        .url(endpoint.url.toHttpUrl())
        .apply {
            val bootstrap = endpoint.bootstrapAddresses.mapNotNull { address ->
                runCatching { InetAddress.getByName(address) }.getOrNull()
            }
            if (bootstrap.isNotEmpty()) bootstrapDnsHosts(bootstrap)
        }
        .includeIPv6(true)
        // Private answers (e.g. a LAN server published on a public name) are kept.
        .resolvePrivateAddresses(true)
        .build()

    private class CacheEntry(val addresses: List<InetAddress>, val expiresAtMs: Long)

    private val cache = ConcurrentHashMap<String, CacheEntry>()

    @Volatile
    private var lastFallbackLogMs = 0L

    override fun lookup(hostname: String): List<InetAddress> {
        val host = hostname.lowercase().removeSuffix(".")
        if (isLocalHostname(host)) return systemDns.lookup(hostname)
        val now = nowMs()
        cache[host]?.let { entry -> if (entry.expiresAtMs > now) return entry.addresses else cache.remove(host) }
        val addresses = try {
            doh.lookup(host)
        } catch (error: IOException) {
            if (!fallbackToSystem) {
                log.w { "DoH lookup failed (${error.javaClass.simpleName}: ${error.message}), no fallback" }
                throw error as? UnknownHostException
                    ?: UnknownHostException("DNS over HTTPS lookup failed for $hostname").apply { initCause(error) }
            }
            if (now - lastFallbackLogMs > FALLBACK_LOG_INTERVAL_MS) {
                lastFallbackLogMs = now
                log.w { "DoH lookup failed (${error.javaClass.simpleName}: ${error.message}), using the device DNS" }
            }
            log.d { "Device DNS fallback for $host" }
            return systemDns.lookup(hostname)
        }
        if (cache.size >= MAX_CACHE_ENTRIES) cache.clear()
        cache[host] = CacheEntry(addresses, now + CACHE_TTL_MS)
        log.d { "DoH resolved $host -> ${addresses.size} address(es)" }
        return addresses
    }

    companion object {
        private const val CACHE_TTL_MS = 5 * 60_000L
        private const val MAX_CACHE_ENTRIES = 512
        private const val FALLBACK_LOG_INTERVAL_MS = 60_000L
        private val LOCAL_SUFFIXES = listOf(
            ".local", ".lan", ".home", ".home.arpa", ".internal", ".localdomain", ".localhost", ".intranet", ".corp",
        )

        /** Also true for IP literals, which the device resolver returns as they are. */
        fun isLocalHostname(host: String): Boolean =
            host == "localhost" || '.' !in host || LOCAL_SUFFIXES.any(host::endsWith) ||
                isValidIpv4(host) || isValidIpv6(host.removePrefix("[").removeSuffix("]"))
    }
}
