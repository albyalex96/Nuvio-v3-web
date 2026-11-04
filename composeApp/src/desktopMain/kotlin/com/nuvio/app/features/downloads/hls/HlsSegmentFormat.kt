package com.nuvio.app.features.downloads.hls

import kotlinx.serialization.Serializable

/** Container of the media segments of one HLS track, as saved by the downloader (same as Android). */
@Serializable
internal enum class HlsSegmentFormat { Ts, Fmp4, PackedAac, PackedAc3, PackedMp3, WebVtt, Unsupported }
