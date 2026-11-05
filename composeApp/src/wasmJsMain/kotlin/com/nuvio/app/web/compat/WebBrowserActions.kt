package com.nuvio.app.web.compat

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

private fun jsSetWakeLock(enabled: Boolean) {
    js("""(function(){
        try {
            if (enabled) {
                if (!navigator.wakeLock || window.__nuvioWakeLock) return;
                navigator.wakeLock.request('screen').then(function(lock){ window.__nuvioWakeLock = lock; }).catch(function(){});
            } else if (window.__nuvioWakeLock) {
                window.__nuvioWakeLock.release().catch(function(){});
                window.__nuvioWakeLock = null;
            }
        } catch (e) {}
    })()""")
}

private fun jsDownloadText(fileName: String, text: String, mimeType: String) {
    js("""(function(){
        var blob = new Blob([text], { type: mimeType });
        var url = URL.createObjectURL(blob);
        var a = document.createElement('a');
        a.href = url; a.download = fileName; document.body.appendChild(a); a.click(); a.remove();
        setTimeout(function(){ URL.revokeObjectURL(url); }, 10000);
    })()""")
}

private fun jsDownloadBase64(fileName: String, base64: String, mimeType: String) {
    js("""(function(){
        var bin = atob(base64); var bytes = new Uint8Array(bin.length);
        for (var i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
        var blob = new Blob([bytes], { type: mimeType });
        var url = URL.createObjectURL(blob);
        var a = document.createElement('a');
        a.href = url; a.download = fileName; document.body.appendChild(a); a.click(); a.remove();
        setTimeout(function(){ URL.revokeObjectURL(url); }, 10000);
    })()""")
}

private fun jsPickTextFile(accept: String, onLoaded: (String, String) -> Unit, onError: (String) -> Unit) {
    js("""(function(){
        var input = document.createElement('input');
        input.type = 'file'; input.accept = accept;
        input.onchange = function(){
            var file = input.files && input.files[0];
            if (!file) return;
            file.text().then(function(t){ onLoaded(file.name, t); }).catch(function(e){ onError(String(e)); });
        };
        input.click();
    })()""")
}

private fun jsOpenInNewTab(url: String): Boolean =
    js("(function(){ try { window.open(url, '_blank', 'noopener'); return true; } catch (e) { return false; } })()")

/** Keeps the screen on while a video plays (Screen Wake Lock API, where supported). */
internal fun setScreenWakeLock(enabled: Boolean) = runCatching { jsSetWakeLock(enabled) }.let { }

/** Saves a text file through the browser's download flow. */
internal fun downloadTextFile(fileName: String, text: String, mimeType: String = "text/plain"): Boolean =
    runCatching { jsDownloadText(fileName, text, mimeType) }.isSuccess

/** Saves a binary file through the browser's download flow. */
@OptIn(ExperimentalEncodingApi::class)
internal fun downloadBinaryFile(fileName: String, bytes: ByteArray, mimeType: String): Boolean =
    runCatching { jsDownloadBase64(fileName, Base64.encode(bytes), mimeType) }.isSuccess

/** Opens the browser file picker and reads the chosen file as text. */
internal fun pickTextFile(accept: String, onLoaded: (fileName: String, content: String) -> Unit, onError: (String) -> Unit) {
    runCatching { jsPickTextFile(accept, onLoaded, onError) }.onFailure { onError(it.message ?: "File picker unavailable") }
}

internal fun openInNewTab(url: String): Boolean = runCatching { jsOpenInNewTab(url) }.getOrDefault(false)
