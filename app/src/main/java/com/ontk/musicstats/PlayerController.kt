package com.ontk.musicstats

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken

/**
 * Пульт управления встроенным плеером. Подключается к MusicPlaybackService
 * и отдаёт интерфейсу состояние в виде Compose-состояний.
 */
object PlayerController {
    private const val TAG = "Slukh"

    private var controller: MediaController? = null
    private var connecting = false
    private val waiting = mutableListOf<() -> Unit>()

    var isPlaying by mutableStateOf(false)
        private set
    var hasMedia by mutableStateOf(false)
        private set
    var title by mutableStateOf("")
        private set
    var artist by mutableStateOf("")
        private set
    var durationMs by mutableStateOf(0L)
        private set

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) = refresh()
        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) = refresh()
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = refresh()
        override fun onPlaybackStateChanged(playbackState: Int) = refresh()
    }

    private fun refresh() {
        val c = controller ?: return
        title = c.mediaMetadata.title?.toString().orEmpty()
        artist = c.mediaMetadata.artist?.toString().orEmpty()
        durationMs = c.duration.takeIf { it > 0 } ?: 0L
        hasMedia = c.mediaItemCount > 0
        isPlaying = c.isPlaying
    }

    /** Подключается к службе (если ещё не подключён) и затем вызывает [onReady]. */
    fun connect(context: Context, onReady: (() -> Unit)? = null) {
        if (controller != null) {
            onReady?.invoke()
            return
        }
        if (onReady != null) waiting.add(onReady)
        if (connecting) return
        connecting = true
        val app = context.applicationContext
        val token = SessionToken(app, ComponentName(app, MusicPlaybackService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        future.addListener({
            connecting = false
            try {
                controller = future.get().also { it.addListener(listener) }
                refresh()
                val callbacks = waiting.toList()
                waiting.clear()
                callbacks.forEach { it() }
            } catch (e: Exception) {
                Log.d(TAG, "Не удалось подключиться к плееру: $e")
                waiting.clear()
            }
        }, ContextCompat.getMainExecutor(app))
    }

    private fun toItem(t: LibraryTrack): MediaItem =
        MediaItem.Builder()
            .setMediaId(t.uri)
            .setUri(t.uri)
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(Uri.parse(t.uri)).build())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(t.title)
                    .setArtist(t.artists)
                    .setAlbumTitle(t.album.ifEmpty { null })
                    .build()
            )
            .build()

    /** Играет список начиная с [startIndex]. */
    fun playQueue(context: Context, tracks: List<LibraryTrack>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return
        connect(context) {
            val c = controller ?: return@connect
            c.setMediaItems(tracks.map { toItem(it) }, startIndex.coerceIn(0, tracks.lastIndex), 0L)
            c.prepare()
            c.play()
        }
    }

    fun toggle() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun next() {
        controller?.seekToNextMediaItem()
    }

    fun previous() {
        controller?.seekToPreviousMediaItem()
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
    }

    fun positionMs(): Long = controller?.currentPosition ?: 0L
}