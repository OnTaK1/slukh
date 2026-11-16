package com.ontk.musicstats

import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import android.service.notification.NotificationListenerService
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object NowPlaying {
    var artist by mutableStateOf("")
    var title by mutableStateOf("")
    var pkg by mutableStateOf("")
    var playing by mutableStateOf(false)
    var version by mutableIntStateOf(0)
    var resumeTick by mutableIntStateOf(0)
}

private const val MIN_TRACK_SEC = 30L
private const val MAX_TRACK_SEC = 15 * 60L
private const val MAX_THRESHOLD_SEC = 240L

// Приложения, которые по умолчанию не считаются музыкой (видео и галереи)
private val VIDEO_APPS = setOf(
    "com.google.android.youtube",
    "app.revanced.android.youtube",
    "com.vanced.android.youtube",
    "com.sec.android.gallery3d",
    "com.google.android.apps.photos"
)

class MusicListenerService : NotificationListenerService() {

    companion object {
        @Volatile
        private var instance: MusicListenerService? = null

        /**
         * Просит плеер включить трек через его медиасессию (как это делает голосовой помощник).
         * false: активной сессии нет или плеер не заявил, что умеет искать и играть.
         */
        fun playFromSearch(pkg: String, query: String, artist: String, title: String): Boolean =
            instance?.playViaSession(pkg, query, artist, title) ?: false
    }

    private lateinit var sessionManager: MediaSessionManager
    private lateinit var db: StatsDb
    private val handler = Handler(Looper.getMainLooper())
    private val trackers = mutableMapOf<MediaSession.Token, Tracker>()

    private val sessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { list ->
            updateControllers(list.orEmpty())
        }

    override fun onListenerConnected() {
        instance = this
        db = StatsDb.get(this)
        sessionManager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
        val component = ComponentName(this, MusicListenerService::class.java)
        sessionManager.addOnActiveSessionsChangedListener(sessionsListener, component)
        updateControllers(sessionManager.getActiveSessions(component))
    }

    override fun onListenerDisconnected() {
        instance = null
        if (::sessionManager.isInitialized) {
            sessionManager.removeOnActiveSessionsChangedListener(sessionsListener)
        }
        stopAll()
    }

    override fun onDestroy() {
        instance = null
        stopAll()
        super.onDestroy()
    }

    private fun stopAll() {
        trackers.values.forEach { it.stop() }
        trackers.clear()
    }

    private fun updateControllers(list: List<MediaController>) {
        val aliveTokens = list.map { it.sessionToken }.toSet()
        trackers.keys.filter { it !in aliveTokens }.forEach { token ->
            trackers.remove(token)?.stop()
        }
        list.forEach { controller ->
            if (controller.sessionToken !in trackers) {
                trackers[controller.sessionToken] = Tracker(controller).also { it.start() }
            }
        }
    }

    private fun playViaSession(pkg: String, query: String, artist: String, title: String): Boolean {
        if (!::sessionManager.isInitialized) return false
        return try {
            val component = ComponentName(this, MusicListenerService::class.java)
            val controller = sessionManager.getActiveSessions(component)
                .firstOrNull { it.packageName == pkg }
            if (controller == null) {
                Log.d("Slukh", "Нет активной сессии у $pkg")
                return false
            }
            val actions = controller.playbackState?.actions ?: 0L
            Log.d("Slukh", "Сессия $pkg: actions=$actions")
            if (actions and PlaybackState.ACTION_PLAY_FROM_SEARCH == 0L) return false
            val extras = Bundle().apply {
                putString(MediaStore.EXTRA_MEDIA_FOCUS, MediaStore.Audio.Media.ENTRY_CONTENT_TYPE)
                putString(MediaStore.EXTRA_MEDIA_ARTIST, artist)
                putString(MediaStore.EXTRA_MEDIA_TITLE, title)
            }
            controller.transportControls.playFromSearch(query, extras)
            true
        } catch (e: SecurityException) {
            Log.d("Slukh", "Нет доступа к сессиям: ${e.message}")
            false
        }
    }

    private fun isPlaying(state: PlaybackState?) = state?.state == PlaybackState.STATE_PLAYING

    private inner class Tracker(private val controller: MediaController) {
        private var artist = ""
        private var title = ""
        private var album = ""
        private var durationSec = 0L
        private var startedAt = 0L
        private var playedMs = 0L
        private var resumedAt = 0L
        private var counted = false
        private var playId = -1L
        private val checkRunnable = Runnable { checkCount() }

        private val callback = object : MediaController.Callback() {
            override fun onMetadataChanged(metadata: MediaMetadata?) {
                onMetadata(metadata)
            }

            override fun onPlaybackStateChanged(state: PlaybackState?) {
                onState(state)
            }
        }

        fun start() {
            controller.registerCallback(callback)
            onMetadata(controller.metadata)
            onState(controller.playbackState)
        }

        fun stop() {
            finishTrack()
            if (NowPlaying.pkg == controller.packageName) NowPlaying.playing = false
            controller.unregisterCallback(callback)
        }

        /** Сообщает интерфейсу, что сейчас играет (или стоит на паузе). */
        private fun publish(playing: Boolean) {
            if (title.isEmpty()) return
            val pkg = controller.packageName
            if (playing || NowPlaying.pkg == pkg) {
                NowPlaying.artist = artist
                NowPlaying.title = title
                NowPlaying.pkg = pkg
                NowPlaying.playing = playing
            }
        }

        private fun onMetadata(md: MediaMetadata?) {
            val newArtist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST)?.trim().orEmpty()
            val newTitle = md?.getString(MediaMetadata.METADATA_KEY_TITLE)?.trim().orEmpty()
            val newAlbum = md?.getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty()
            val newDuration = (md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L) / 1000

            if (newArtist == artist && newTitle == title) {
                // тот же трек: длительность могла прийти позже
                album = newAlbum
                durationSec = newDuration
                scheduleCheck()
                if (resumedAt != 0L) publish(true)
                return
            }
            finishTrack()
            artist = newArtist
            title = newTitle
            album = newAlbum
            durationSec = newDuration
            if (isPlaying(controller.playbackState)) {
                beginSegment()
                publish(true)
            }
        }

        private fun onState(state: PlaybackState?) {
            val playing = isPlaying(state)
            if (playing && resumedAt == 0L) {
                beginSegment()
            } else if (!playing && resumedAt != 0L) {
                playedMs += SystemClock.elapsedRealtime() - resumedAt
                resumedAt = 0L
                handler.removeCallbacks(checkRunnable)
                checkCount()
                flushPlayed()
            }
            publish(playing)
        }

        private fun beginSegment() {
            if (playedMs == 0L) startedAt = System.currentTimeMillis()
            resumedAt = SystemClock.elapsedRealtime()
            val pkg = controller.packageName
            if (db.registerApp(pkg, pkg !in VIDEO_APPS)) NowPlaying.version++
            scheduleCheck()
        }

        private fun currentPlayedMs(): Long =
            playedMs + if (resumedAt != 0L) SystemClock.elapsedRealtime() - resumedAt else 0L

        private fun thresholdSec(): Long =
            if (durationSec in MIN_TRACK_SEC..MAX_TRACK_SEC) {
                minOf(durationSec / 2, MAX_THRESHOLD_SEC)
            } else {
                -1L
            }

        private fun scheduleCheck() {
            handler.removeCallbacks(checkRunnable)
            val threshold = thresholdSec()
            if (counted || resumedAt == 0L || threshold < 0) return
            val needMs = threshold * 1000 - currentPlayedMs()
            handler.postDelayed(checkRunnable, maxOf(needMs, 0L) + 300)
        }

        private fun checkCount() {
            if (counted || title.isEmpty()) return
            val threshold = thresholdSec()
            if (threshold < 0 || currentPlayedMs() < threshold * 1000) return
            if (!db.isAppEnabled(controller.packageName)) return
            counted = true
            val parsed = TrackParser.parse(artist, title, controller.packageName)
            if (parsed == null) {
                Log.d("MusicStats", "Пропущено (не музыка): $artist — $title")
                return
            }
            playId = db.insertPlay(
                parsed.artists, parsed.title, album, durationSec,
                controller.packageName, startedAt, currentPlayedMs()
            )
            NowPlaying.version++
            GenreResolver.requestMissing(applicationContext)
            Log.d(
                "MusicStats",
                "Засчитано: ${parsed.artists.joinToString(", ")} — ${parsed.title}"
            )
        }

        /** Уточняет в базе реально проигранное время уже засчитанного трека. */
        private fun flushPlayed() {
            if (playId != -1L) db.updatePlayedMs(playId, currentPlayedMs())
        }

        private fun finishTrack() {
            handler.removeCallbacks(checkRunnable)
            if (resumedAt != 0L) {
                playedMs += SystemClock.elapsedRealtime() - resumedAt
                resumedAt = 0L
            }
            checkCount()
            flushPlayed()
            artist = ""
            title = ""
            album = ""
            durationSec = 0L
            startedAt = 0L
            playedMs = 0L
            counted = false
            playId = -1L
        }
    }
}