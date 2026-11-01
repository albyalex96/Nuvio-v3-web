package com.nuvio.app.features.tvremote

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.MediaMetadata
import android.media.VolumeProvider
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import co.touchlab.kermit.Logger
import com.nuvio.app.R
import com.nuvio.app.core.network.nuvioSharedOkHttpClient
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.tv_remote_episode_label
import nuvio.composeapp.generated.resources.tv_remote_next_episode
import nuvio.composeapp.generated.resources.tv_remote_nothing_playing
import nuvio.composeapp.generated.resources.tv_remote_notification_channel
import nuvio.composeapp.generated.resources.tv_remote_notification_channel_description
import nuvio.composeapp.generated.resources.tv_remote_notification_stop
import nuvio.composeapp.generated.resources.tv_remote_now_playing_on
import nuvio.composeapp.generated.resources.tv_remote_pause
import nuvio.composeapp.generated.resources.tv_remote_play
import nuvio.composeapp.generated.resources.tv_remote_seek_back
import nuvio.composeapp.generated.resources.tv_remote_seek_forward
import nuvio.composeapp.generated.resources.tv_remote_status_connecting
import nuvio.composeapp.generated.resources.tv_remote_status_unreachable
import okhttp3.Request
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString

/**
 * Keeps the TV remote alive when the phone app is in background (Android full): it observes the
 * TV, shows a media notification (also on the lock screen) and routes the phone volume keys to
 * the TV volume. It stops by itself when nothing plays on the TV for a while, when the TV is
 * forgotten or when the user closes it from the notification.
 */
class PhoneTvRemoteService : Service() {
    private val log = Logger.withTag("PhoneTvRemote")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var client: PhoneTvRemoteClient
    private lateinit var session: MediaSession
    private var releaseObservation: (() -> Unit)? = null
    private var volumeProvider: VolumeProvider? = null
    private var volumeMax = -1

    private var latest = PhoneTvRemoteUiState()
    private var idleSinceMs: Long? = null
    private var artworkUrl: String? = null
    private var artwork: Bitmap? = null
    private var artworkJob: Job? = null
    private var foreground = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val active = PhoneTvRemote.activeClient
        if (active == null) {
            stopSelf()
            return
        }
        client = active
        running = true
        createChannel()
        session = MediaSession(this, "NuvioTvRemote").apply {
            setCallback(SessionCallback())
            launchIntent()?.let(::setSessionActivity)
            isActive = true
        }
        releaseObservation = client.observe()
        scope.launch {
            client.state.collect { state ->
                latest = state
                render(state)
            }
        }
        scope.launch {
            // Ends the service once nothing plays on the TV (or it is unreachable) for a while.
            while (true) {
                delay(30_000L)
                val since = idleSinceMs ?: continue
                if (SystemClock.elapsedRealtime() - since >= IDLE_TIMEOUT_MS) {
                    log.i { "Nothing playing on the TV: background controls closed" }
                    stopNow()
                    break
                }
            }
        }
        log.i { "Background controls started" }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!::client.isInitialized) {
            stopSelf()
            return START_NOT_STICKY
        }
        // The foreground notification must be posted right away, before the first TV state.
        if (!foreground) render(latest.takeIf { it.pairedTv != null } ?: client.state.value)
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> command(TvRemoteCommandType.TogglePlayback)
            ACTION_REWIND -> latest.playback?.let { command(TvRemoteCommandType.SeekStep, forward = false) }
            ACTION_FORWARD -> latest.playback?.let { command(TvRemoteCommandType.SeekStep, forward = true) }
            ACTION_SKIP -> command(TvRemoteCommandType.SkipSegment)
            ACTION_NEXT -> command(TvRemoteCommandType.NextEpisode)
            ACTION_STOP -> {
                dismissedSessionKey = latest.playback?.sessionKey
                log.i { "Background controls closed from the notification" }
                stopNow()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        running = false
        releaseObservation?.invoke()
        releaseObservation = null
        artworkJob?.cancel()
        if (::session.isInitialized) {
            session.isActive = false
            session.release()
        }
        scope.cancel()
        super.onDestroy()
    }

    private fun command(type: TvRemoteCommandType, forward: Boolean? = null, position: Long? = null, level: Int? = null) {
        log.d { "Notification command $type" }
        client.send(type, positionMs = position, forward = forward, level = level)
    }

    private fun stopNow() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        foreground = false
        stopSelf()
    }

    // region Rendering

    private fun render(state: PhoneTvRemoteUiState) {
        val tv = state.pairedTv
        if (tv == null || state.connection == PhoneTvRemoteConnection.Revoked || !state.backgroundControls) {
            log.i { "Background controls not needed any more" }
            if (!foreground) postForeground(placeholderNotification())
            stopNow()
            return
        }
        val playback = state.playback
        idleSinceMs = if (playback == null) idleSinceMs ?: SystemClock.elapsedRealtime() else null
        updateVolume(state.volume)
        updateSession(playback)
        if (playback?.artworkUrl != artworkUrl) loadArtwork(playback?.artworkUrl)
        postForeground(buildNotification(state, tv, playback))
    }

    private fun updateSession(playback: TvRemotePlayback?) {
        if (playback == null) {
            session.setPlaybackState(PlaybackState.Builder().setState(PlaybackState.STATE_NONE, 0L, 0f).build())
            return
        }
        val metadata = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, playback.title)
            .putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, playback.title)
            .apply {
                subtitle(playback)?.let {
                    putString(MediaMetadata.METADATA_KEY_ARTIST, it)
                    putString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, it)
                }
                if (!playback.isLive && playback.durationMs > 0L) {
                    putLong(MediaMetadata.METADATA_KEY_DURATION, playback.durationMs)
                }
                artwork?.takeIf { !it.isRecycled }?.let { putBitmap(MediaMetadata.METADATA_KEY_ART, it) }
            }
            .build()
        runCatching { session.setMetadata(metadata) }

        val stateCode = when {
            playback.isEnded -> PlaybackState.STATE_STOPPED
            playback.isBuffering -> PlaybackState.STATE_BUFFERING
            playback.isPlaying -> PlaybackState.STATE_PLAYING
            else -> PlaybackState.STATE_PAUSED
        }
        var actions = PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE
        if (playback.canSeek && !playback.isLive) {
            actions = actions or PlaybackState.ACTION_SEEK_TO or
                PlaybackState.ACTION_FAST_FORWARD or PlaybackState.ACTION_REWIND
        }
        if (playback.nextEpisode != null) actions = actions or PlaybackState.ACTION_SKIP_TO_NEXT
        session.setPlaybackState(
            PlaybackState.Builder()
                .setActions(actions)
                .setState(
                    stateCode,
                    playback.positionMs.coerceAtLeast(0L),
                    if (playback.isPlaying && !playback.isBuffering) playback.playbackSpeed else 0f,
                    SystemClock.elapsedRealtime(),
                )
                .build(),
        )
    }

    /** The phone volume keys change the TV volume while the session is active. */
    private fun updateVolume(volume: TvRemoteVolume?) {
        if (volume == null || volume.max <= 0) {
            if (volumeProvider != null) {
                volumeProvider = null
                volumeMax = -1
                session.setPlaybackToLocal(
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build(),
                )
            }
            return
        }
        val current = if (volume.muted) 0 else volume.level.coerceIn(0, volume.max)
        val provider = volumeProvider
        if (provider != null && volumeMax == volume.max) {
            if (provider.currentVolume != current) provider.currentVolume = current
            return
        }
        volumeMax = volume.max
        volumeProvider = object : VolumeProvider(VOLUME_CONTROL_ABSOLUTE, volume.max, current) {
            override fun onSetVolumeTo(volume: Int) {
                currentVolume = volume
                command(TvRemoteCommandType.SetVolume, level = volume)
            }

            override fun onAdjustVolume(direction: Int) {
                when {
                    direction > 0 -> command(TvRemoteCommandType.VolumeUp)
                    direction < 0 -> command(TvRemoteCommandType.VolumeDown)
                }
            }
        }.also(session::setPlaybackToRemote)
    }

    private fun buildNotification(
        state: PhoneTvRemoteUiState,
        tv: PhoneTvRemotePairedTv,
        playback: TvRemotePlayback?,
    ): Notification {
        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_small)
            .setContentIntent(launchIntent())
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setOngoing(true)
            .setSubText(text(Res.string.tv_remote_now_playing_on, tv.name))

        if (playback == null) {
            val status = when (state.connection) {
                PhoneTvRemoteConnection.Unreachable -> text(Res.string.tv_remote_status_unreachable)
                PhoneTvRemoteConnection.Connected -> text(Res.string.tv_remote_nothing_playing)
                else -> text(Res.string.tv_remote_status_connecting)
            }
            return builder
                .setContentTitle(tv.name)
                .setContentText(status)
                .addAction(action(android.R.drawable.ic_menu_close_clear_cancel, text(Res.string.tv_remote_notification_stop), ACTION_STOP))
                .setStyle(Notification.MediaStyle().setMediaSession(session.sessionToken).setShowActionsInCompactView(0))
                .build()
        }

        val seek = playback.seekIntervalSeconds.coerceAtLeast(1)
        val canSeek = playback.canSeek && !playback.isLive
        val actions = mutableListOf<Notification.Action>()
        val compact = mutableListOf<Int>()
        if (canSeek) {
            compact += actions.size
            actions += action(android.R.drawable.ic_media_rew, text(Res.string.tv_remote_seek_back, seek), ACTION_REWIND)
        }
        compact += actions.size
        actions += if (playback.isPlaying) {
            action(android.R.drawable.ic_media_pause, text(Res.string.tv_remote_pause), ACTION_PLAY_PAUSE)
        } else {
            action(android.R.drawable.ic_media_play, text(Res.string.tv_remote_play), ACTION_PLAY_PAUSE)
        }
        if (canSeek) {
            compact += actions.size
            actions += action(android.R.drawable.ic_media_ff, text(Res.string.tv_remote_seek_forward, seek), ACTION_FORWARD)
        }
        // Contextual buttons, as on the TV: skip only while a segment is on screen.
        if (playback.skipSegmentType != null) {
            actions += action(
                android.R.drawable.ic_media_next,
                skipLabel(playback.skipSegmentType, playback.skipToPostCredits),
                ACTION_SKIP,
            )
        } else if (playback.nextEpisode != null) {
            actions += action(android.R.drawable.ic_media_next, text(Res.string.tv_remote_next_episode), ACTION_NEXT)
        }
        actions += action(android.R.drawable.ic_menu_close_clear_cancel, text(Res.string.tv_remote_notification_stop), ACTION_STOP)

        actions.forEach(builder::addAction)
        return builder
            .setContentTitle(playback.title)
            .setContentText(subtitle(playback))
            .setLargeIcon(artwork?.takeIf { !it.isRecycled })
            .setStyle(
                Notification.MediaStyle()
                    .setMediaSession(session.sessionToken)
                    .setShowActionsInCompactView(*compact.take(3).toIntArray()),
            )
            .build()
    }

    private fun placeholderNotification(): Notification =
        Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_small)
            .setContentTitle(text(Res.string.tv_remote_status_connecting))
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .build()

    private fun postForeground(notification: Notification) {
        if (!foreground) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                } else {
                    0
                },
            )
            foreground = true
        } else {
            getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun subtitle(playback: TvRemotePlayback): String? {
        val season = playback.seasonNumber
        val episode = playback.episodeNumber
        val code = if (season != null && episode != null) text(Res.string.tv_remote_episode_label, season, episode) else null
        return listOfNotNull(code, playback.episodeTitle?.takeIf(String::isNotBlank))
            .joinToString(" · ")
            .ifBlank { null }
    }

    private fun skipLabel(type: String?, postCredits: Boolean): String =
        text(tvRemoteSkipLabelRes(type, postCredits))

    private fun action(icon: Int, title: String, action: String): Notification.Action =
        Notification.Action.Builder(
            android.graphics.drawable.Icon.createWithResource(this, icon),
            title,
            PendingIntent.getService(
                this,
                action.hashCode(),
                Intent(this, PhoneTvRemoteService::class.java).setAction(action),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        ).build()

    private fun launchIntent(): PendingIntent? {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            ?: return null
        return PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(CHANNEL_ID, text(Res.string.tv_remote_notification_channel), NotificationManager.IMPORTANCE_LOW).apply {
            description = text(Res.string.tv_remote_notification_channel_description)
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    // endregion

    // region Artwork

    private fun loadArtwork(url: String?) {
        artworkUrl = url
        artwork = null
        artworkJob?.cancel()
        if (url.isNullOrBlank() || !url.startsWith("https://")) return
        artworkJob = scope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                runCatching { downloadArtwork(url) }
                    .onFailure { log.d { "TV artwork not loaded: ${it.message}" } }
                    .getOrNull()
            }
            if (artworkUrl == url && bitmap != null) {
                artwork = bitmap
                render(latest)
            }
        }
    }

    private fun downloadArtwork(url: String): Bitmap? {
        val client = nuvioSharedOkHttpClient.newBuilder().callTimeout(15, TimeUnit.SECONDS).build()
        return client.newCall(Request.Builder().url(url).header("Accept", "image/*").build()).execute().use { response ->
            if (!response.isSuccessful) return null
            val bytes = response.body.byteStream().use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > MAX_ARTWORK_BYTES) return null
                    output.write(buffer, 0, read)
                }
                output.toByteArray()
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (bounds.outWidth / sample > MAX_ARTWORK_EDGE || bounds.outHeight / sample > MAX_ARTWORK_EDGE) sample *= 2
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    }

    // endregion

    private inner class SessionCallback : MediaSession.Callback() {
        override fun onPlay() = command(TvRemoteCommandType.Play)
        override fun onPause() = command(TvRemoteCommandType.Pause)
        override fun onStop() = command(TvRemoteCommandType.Pause)
        override fun onSeekTo(pos: Long) = command(TvRemoteCommandType.SeekTo, position = pos.coerceAtLeast(0L))
        override fun onFastForward() = command(TvRemoteCommandType.SeekStep, forward = true)
        override fun onRewind() = command(TvRemoteCommandType.SeekStep, forward = false)
        override fun onSkipToNext() {
            if (latest.playback?.skipSegmentType != null) {
                command(TvRemoteCommandType.SkipSegment)
            } else {
                command(TvRemoteCommandType.NextEpisode)
            }
        }
    }

    /** Compose resource string in the app language (cached by the resource reader). */
    private fun text(resource: StringResource, vararg args: Any): String =
        runBlocking { getString(resource, *args) }

    companion object {
        private const val CHANNEL_ID = "nuvio_tv_remote"
        private const val NOTIFICATION_ID = 0x7E1E
        private const val IDLE_TIMEOUT_MS = 10 * 60_000L
        private const val MAX_ARTWORK_BYTES = 8 * 1024 * 1024
        private const val MAX_ARTWORK_EDGE = 512
        private const val ACTION_PLAY_PAUSE = "com.nuvio.app.tvremote.PLAY_PAUSE"
        private const val ACTION_REWIND = "com.nuvio.app.tvremote.REWIND"
        private const val ACTION_FORWARD = "com.nuvio.app.tvremote.FORWARD"
        private const val ACTION_SKIP = "com.nuvio.app.tvremote.SKIP"
        private const val ACTION_NEXT = "com.nuvio.app.tvremote.NEXT"
        private const val ACTION_STOP = "com.nuvio.app.tvremote.STOP"

        @Volatile
        private var running = false

        /** Playback closed by the user from the notification: not reopened until something else plays. */
        @Volatile
        private var dismissedSessionKey: String? = null

        internal fun shouldStartFor(sessionKey: String): Boolean {
            if (running) return false
            if (sessionKey == dismissedSessionKey) return false
            dismissedSessionKey = null
            return true
        }

        internal fun start(context: Context) {
            running = true
            try {
                ContextCompat.startForegroundService(context, Intent(context, PhoneTvRemoteService::class.java))
            } catch (error: Exception) {
                // Android 12+ refuses foreground services started from background: retried
                // the next time the app is opened.
                running = false
                Logger.withTag("PhoneTvRemote").w(error) { "Background controls not started" }
            }
        }
    }
}
