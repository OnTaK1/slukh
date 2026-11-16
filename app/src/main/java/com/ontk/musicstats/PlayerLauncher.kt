package com.ontk.musicstats

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.util.Log

/**
 * Открывает трек в музыкальном приложении, где его слушали.
 * Единого способа «включить конкретный трек» у Android нет, поэтому пробуем несколько
 * вариантов по очереди, пока один не сработает.
 *
 * Плееры определяются по имени пакета, а не по точному совпадению: у модифицированных
 * сборок (ReVanced и т.п.) имя пакета отличается и может быть любым.
 */
object PlayerLauncher {
    private const val TAG = "Slukh"

    const val YT_MUSIC = "com.google.android.apps.youtube.music"
    const val SPOTIFY = "com.spotify.music"
    const val YANDEX = "ru.yandex.music"

    private fun isYtMusic(pkg: String) = pkg.contains("youtube.music")
    private fun isSpotify(pkg: String) = pkg == SPOTIFY
    private fun isYandex(pkg: String) = pkg.contains("yandex.music")
    private fun isVk(pkg: String) =
        pkg == "com.vkontakte.android" || pkg == "com.uma.musicvk" || pkg.contains("musicvk")

    private fun isKnown(pkg: String) = isYtMusic(pkg) || isSpotify(pkg) || isYandex(pkg) || isVk(pkg)

    private fun firstArtist(artists: String): String = artists.substringBefore(',').trim()

    private fun view(uri: String, pkg: String?): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply { if (pkg != null) setPackage(pkg) }

    private fun playFromSearch(pkg: String?, query: String, artist: String, title: String): Intent =
        Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
            if (pkg != null) setPackage(pkg)
            putExtra(MediaStore.EXTRA_MEDIA_FOCUS, MediaStore.Audio.Media.ENTRY_CONTENT_TYPE)
            putExtra(SearchManager.QUERY, query)
            putExtra(MediaStore.EXTRA_MEDIA_ARTIST, artist)
            putExtra(MediaStore.EXTRA_MEDIA_TITLE, title)
        }

    private fun attempts(
        context: Context,
        query: String,
        artist: String,
        title: String,
        pkg: String
    ): List<Intent> {
        val enc = Uri.encode(query)
        val list = mutableListOf<Intent>()
        when {
            isYtMusic(pkg) -> {
                val url = "https://music.youtube.com/search?q=$enc"
                list += view(url, pkg)   // поиск внутри приложения, если оно понимает эту ссылку
                list += view(url, null)  // иначе любое приложение или браузер: поиск откроется хотя бы в вебе
                list += playFromSearch(pkg, query, artist, title)
            }
            isSpotify(pkg) -> {
                // Spotify открывает поиск по запросу, но сам трек не включает
                list += view("https://open.spotify.com/search/$enc", pkg)
                list += view("spotify:search:$enc", pkg)
            }
            isYandex(pkg) -> {
                list += view("https://music.yandex.ru/search?text=$enc", pkg)
                list += playFromSearch(pkg, query, artist, title)
            }
            isVk(pkg) -> {
                list += view("https://vk.com/audio?q=$enc", pkg)
                list += playFromSearch(pkg, query, artist, title)
            }
        }
        if (isKnown(pkg)) {
            // последняя надежда для известного плеера: просто открыть приложение
            context.packageManager.getLaunchIntentForPackage(pkg)?.let { list += it }
        }
        // система сама предложит приложения, умеющие играть по поиску
        list += playFromSearch(null, query, artist, title)
        return list
    }

    /**
     * true, если запрос ушёл в плеер или какое-то приложение открылось.
     * Сначала пробуем медиасессию живого плеера: она включает трек сама.
     * Если сессии нет (плеер закрыт), открываем поиск.
     */
    fun play(context: Context, artists: String, title: String, sourcePackage: String): Boolean {
        val artist = firstArtist(artists)
        val query = "$artist $title".replace('/', ' ').replace(Regex("\\s+"), " ").trim()

        if (sourcePackage.isNotEmpty() &&
            MusicListenerService.playFromSearch(sourcePackage, query, artist, title)
        ) return true

        for (intent in attempts(context, query, artist, title, sourcePackage)) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                Log.d(TAG, "Открыто: ${intent.action} ${intent.data} pkg=${intent.`package`}")
                return true
            } catch (e: ActivityNotFoundException) {
                Log.d(TAG, "Не открылось: ${intent.action} ${intent.data} pkg=${intent.`package`}")
            } catch (e: SecurityException) {
                Log.d(TAG, "Нет доступа: ${intent.action} ${intent.data}: ${e.message}")
            }
        }
        return false
    }
}