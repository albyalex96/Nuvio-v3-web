package com.nuvio.app.features.tvremote

import android.app.Application
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class TvRemoteProtocolTest {

    private val pairing = TvRemotePairingInfo(
        tvId = "3f6c2d0e-tv",
        tvName = "Salotto & Cucina TV",
        host = "192.168.1.42",
        port = 47_380,
        secret = TvRemoteCrypto.encode(TvRemoteCrypto.randomKey()),
    )

    @Test
    fun pairingCodeSurvivesTheQrRoundTrip() {
        assertEquals(pairing, TvRemotePairingInfo.parse(pairing.toUri()))
    }

    @Test
    fun foreignCodesAreRejected() {
        assertNull(TvRemotePairingInfo.parse("https://nuvio.tv/party/abc"))
        assertNull(TvRemotePairingInfo.parse("nuvio-remote://pair?v=1&id=x&h=1.2.3.4&p=99999&s=" + "a".repeat(43)))
        assertNull(TvRemotePairingInfo.parse("nuvio-remote://pair?v=1&id=x&h=1.2.3.4&p=8080&s=short"))
        assertNull(TvRemotePairingInfo.parse("nuvio-remote://pair?v=1&h=1.2.3.4&p=8080&s=" + "a".repeat(43)))
    }

    @Test
    fun sealedRequestOpensOnlyWithTheSameKeyAndPath() {
        val key = TvRemoteCrypto.randomKey()
        val request = TvRemoteCommandRequest(
            timestampMs = 1_000L,
            requestId = "req-1",
            bootId = "boot",
            command = TvRemoteCommandType.SeekStep,
            sessionKey = "tt123|tt123:1:2|1|2",
            forward = true,
        )
        val envelope = TvRemoteCrypto.seal(key, "client", TV_REMOTE_PATH_COMMAND, TvRemoteCommandRequest.serializer(), request)

        assertEquals(request, TvRemoteCrypto.open(key, TV_REMOTE_PATH_COMMAND, TvRemoteCommandRequest.serializer(), envelope))
        // Another key, another endpoint or another client id must not open it.
        assertFailsWith<Exception> {
            TvRemoteCrypto.open(TvRemoteCrypto.randomKey(), TV_REMOTE_PATH_COMMAND, TvRemoteCommandRequest.serializer(), envelope)
        }
        assertFailsWith<Exception> {
            TvRemoteCrypto.open(key, TV_REMOTE_PATH_STATE, TvRemoteCommandRequest.serializer(), envelope)
        }
        assertFailsWith<Exception> {
            TvRemoteCrypto.open(key, TV_REMOTE_PATH_COMMAND, TvRemoteCommandRequest.serializer(), envelope.copy(clientId = "other"))
        }
    }
}
