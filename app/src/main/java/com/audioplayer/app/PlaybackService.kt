package com.audioplayer.app

import android.app.PendingIntent
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * Фоновое воспроизведение: работает при выключенном экране,
 * показывает уведомление с кнопками и управление на экране блокировки.
 */
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private lateinit var player: ExoPlayer
    private lateinit var store: Store
    private val handler = Handler(Looper.getMainLooper())
    private var tick = 0
    private var currentId: String? = null

    override fun onCreate() {
        super.onCreate()
        store = Store(this)

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true) // пауза при отключении наушников
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
        player.setPlaybackSpeed(store.speed)

        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                // Предыдущий трек доигран до конца — сбрасываем его позицию
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                    currentId?.let { store.clearPosition(it) }
                }
                PlaybackExtras.clearAb()
                currentId = mediaItem?.mediaId
                // Продолжить с места остановки
                if (mediaItem != null && reason != Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) {
                    val saved = store.getPosition(mediaItem.mediaId)
                    if (saved > 3_000) player.seekTo(saved)
                }
                store.lastIndex = player.currentMediaItemIndex
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) {
                    // Сработал таймер «до конца трека»
                    PlaybackExtras.cancelSleep()
                    player.pauseAtEndOfMediaItems = false
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying) savePosition()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    player.currentMediaItem?.let { store.clearPosition(it.mediaId) }
                    if (PlaybackExtras.sleepAtTrackEnd.value) PlaybackExtras.cancelSleep()
                }
            }
        })

        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        session = MediaSession.Builder(this, player)
            .setSessionActivity(openApp)
            .setCallback(object : MediaSession.Callback {
                // Восстанавливаем адрес файла (mediaId = uri) для элементов от экрана
                override fun onAddMediaItems(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    mediaItems: MutableList<MediaItem>
                ): ListenableFuture<MutableList<MediaItem>> {
                    val resolved = mediaItems
                        .map { it.buildUpon().setUri(it.mediaId).build() }
                        .toMutableList()
                    return Futures.immediateFuture(resolved)
                }
            })
            .build()

        handler.post(ticker)
    }

    /** Каждые 250 мс: повтор A–B, таймер сна (с плавным затуханием), сохранение позиции. */
    private val ticker = object : Runnable {
        override fun run() {
            val pos = player.currentPosition

            val a = PlaybackExtras.abStart.value
            val b = PlaybackExtras.abEnd.value
            if (a >= 0 && b > a && pos >= b) player.seekTo(a)

            val end = PlaybackExtras.sleepEndAt.value
            if (end > 0) {
                val left = end - SystemClock.elapsedRealtime()
                when {
                    left <= 0 -> {
                        player.pause()
                        player.volume = 1f
                        PlaybackExtras.cancelSleep()
                    }
                    left < 15_000 -> player.volume = left / 15_000f
                    player.volume != 1f -> player.volume = 1f
                }
            } else if (player.volume != 1f) {
                player.volume = 1f
            }

            val atEnd = PlaybackExtras.sleepAtTrackEnd.value
            if (player.pauseAtEndOfMediaItems != atEnd) player.pauseAtEndOfMediaItems = atEnd

            if (++tick % 8 == 0 && player.isPlaying) savePosition()
            handler.postDelayed(this, 250)
        }
    }

    private fun savePosition() {
        val item = player.currentMediaItem ?: return
        val pos = player.currentPosition
        val dur = player.duration
        if (dur > 0 && dur - pos < 5_000) store.clearPosition(item.mediaId)
        else if (pos > 0) store.setPosition(item.mediaId, pos)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        savePosition()
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}
