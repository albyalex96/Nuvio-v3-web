package com.nuvio.app.core.network.dns

import co.touchlab.kermit.Logger
import com.nuvio.app.core.network.DohResolver
import com.nuvio.app.core.network.NuvioDns
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal actual object DnsOverHttpsPlatform {
    private val log = Logger.withTag("DnsOverHttps")

    actual val isSupported: Boolean = true

    actual suspend fun test(settings: DnsOverHttpsSettings): DnsOverHttpsTestResult = withContext(Dispatchers.IO) {
        val endpoint = resolveDnsOverHttpsEndpoint(settings)
            ?: return@withContext DnsOverHttpsTestResult.Failure("No DNS over HTTPS server selected")
        val started = System.currentTimeMillis()
        try {
            val addresses = DohResolver(endpoint, fallbackToSystem = false, bootstrapClient = NuvioDns.bootstrapClient)
                .lookup(DNS_OVER_HTTPS_TEST_HOST)
            val elapsed = System.currentTimeMillis() - started
            log.i { "Test of ${settings.providerType.id} succeeded in ${elapsed}ms (${addresses.size} address(es))" }
            DnsOverHttpsTestResult.Success(addresses.mapNotNull { it.hostAddress }, elapsed)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            log.w(error) { "Test of ${settings.providerType.id} failed" }
            val cause = generateSequence<Throwable>(error) { it.cause }.last()
            DnsOverHttpsTestResult.Failure(cause.message ?: error.message ?: cause.javaClass.simpleName)
        }
    }
}
