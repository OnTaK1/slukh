package com.ontk.musicstats

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

object GenreResolver {
    private const val TAG = "MusicStats"
    private const val RETRY_AFTER_MS = 7L * 24 * 60 * 60 * 1000
    private const val PAUSE_MS = 1000L
    private const val API = "https://ws.audioscrobbler.com/2.0/"

    private val executor = Executors.newSingleThreadExecutor()
    private val running = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Ищет жанры для исполнителей, у которых их ещё нет. Безопасно вызывать много раз. */
    fun requestMissing(context: Context) {
        val apiKey = AppSettings.lastfmKey
        if (apiKey.isBlank()) return
        if (!running.compareAndSet(false, true)) return
        val db = StatsDb.get(context)
        executor.execute {
            try {
                while (true) {
                    val batch = db.artistsNeedingGenre(
                        System.currentTimeMillis() - RETRY_AFTER_MS, 20
                    )
                    if (batch.isEmpty()) break
                    for ((key, name) in batch) {
                        val guess = GenreMapper.guess(fetchTags(name, apiKey))
                        db.saveGenre(
                            key, name, guess?.genre,
                            guess?.subgenres?.joinToString(", "),
                            if (guess != null) "lastfm" else "none"
                        )
                        Log.d(TAG, "Жанр: $name → ${guess?.genre ?: "не найден"}")
                        mainHandler.post { NowPlaying.version++ }
                        Thread.sleep(PAUSE_MS)
                    }
                }
            } catch (e: IOException) {
                Log.d(TAG, "Нет доступа к Last.fm: ${e.message}")
            } catch (e: Exception) {
                Log.d(TAG, "Ошибка поиска жанра: $e")
            } finally {
                running.set(false)
            }
        }
    }

    /** Возвращает пары (тег, вес). Пустой список — исполнитель не найден или без тегов. */
    private fun fetchTags(artist: String, apiKey: String): List<Pair<String, Int>> {
        val q = URLEncoder.encode(artist, "UTF-8")
        val url = "$API?method=artist.gettoptags&artist=$q&autocorrect=1" +
                "&api_key=$apiKey&format=json"
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.requestMethod = "GET"
            val code = conn.responseCode
            if (code == 429 || code >= 500) throw IOException("HTTP $code")
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = JSONObject(body)
            if (json.has("error")) {
                // 6 = исполнитель не найден; всё остальное (ключ, лимиты) — ошибка сервиса
                if (json.optInt("error") == 6) return emptyList()
                throw IOException("Last.fm error ${json.optInt("error")}: ${json.optString("message")}")
            }
            val node = json.optJSONObject("toptags")?.opt("tag") ?: return emptyList()
            val result = mutableListOf<Pair<String, Int>>()
            when (node) {
                is JSONArray -> {
                    for (i in 0 until node.length()) {
                        val o = node.getJSONObject(i)
                        result.add(o.optString("name") to o.optInt("count"))
                    }
                }
                is JSONObject -> result.add(node.optString("name") to node.optInt("count"))
            }
            return result
        } finally {
            conn.disconnect()
        }
    }
}