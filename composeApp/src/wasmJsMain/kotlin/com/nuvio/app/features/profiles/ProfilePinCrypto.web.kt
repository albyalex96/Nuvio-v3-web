package com.nuvio.app.features.profiles

actual object ProfilePinCrypto {
    actual fun sha256Hex(value: String): String =
        com.nuvio.app.features.cloudstream.sha256Hex(value.encodeToByteArray())
}
