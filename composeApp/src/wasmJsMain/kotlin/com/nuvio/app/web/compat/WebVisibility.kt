package com.nuvio.app.web.compat

internal fun jsIsDocumentVisible(): Boolean = js("document.visibilityState === 'visible'")

private fun jsAddVisibilityListener(callback: (Boolean) -> Unit): JsAny =
    js("(function(){ var h = function(){ callback(document.visibilityState === 'visible'); }; document.addEventListener('visibilitychange', h); return h; })()")

private fun jsRemoveVisibilityListener(handler: JsAny) {
    js("document.removeEventListener('visibilitychange', handler)")
}

/** Registers a page visibility listener; returns a function that removes it. */
internal fun addVisibilityListener(callback: (Boolean) -> Unit): () -> Unit {
    val handler = jsAddVisibilityListener(callback)
    return { jsRemoveVisibilityListener(handler) }
}
