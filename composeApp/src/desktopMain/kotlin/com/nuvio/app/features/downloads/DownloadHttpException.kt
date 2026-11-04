package com.nuvio.app.features.downloads

import java.io.IOException

/** HTTP error of a download request (same as the Android transfer). */
internal class DownloadHttpException(val statusCode: Int) : IOException("Download failed: HTTP $statusCode")
