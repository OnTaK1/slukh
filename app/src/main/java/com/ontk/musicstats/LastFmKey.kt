package com.ontk.musicstats

import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

enum class KeyCheck { OK, INVALID, SUSPENDED, LIMIT, NETWORK, OTHER }

object LastFmKey {
    private val KEY_REGEX = Regex("\\b[0-9a-fA-F]{32}\\b")
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    /** Ищет в тексте строку из 32 шестнадцатеричных символов. */
    fun find(text: String?): String? =
        text?.let { KEY_REGEX.find(it)?.value?.lowercase() }

    /** Читает буфер обмена. Вызывать только когда приложение на переднем плане. */
    fun readClipboard(context: Context): String? {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = cm.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        return clip.getItemAt(0).coerceToText(context)?.toString()
    }

    /** Проверяет ключ тестовым запросом; результат придёт в главном потоке. */
    fun validate(key: String, onResult: (KeyCheck) -> Unit) {
        executor.execute {
            val result = try {
                checkKey(key)
            } catch (e: IOException) {
                KeyCheck.NETWORK
            } catch (e: Exception) {
                KeyCheck.OTHER
            }
            main.post { onResult(result) }
        }
    }

    private fun checkKey(key: String): KeyCheck {
        val url = "https://ws.audioscrobbler.com/2.0/?method=artist.gettoptags" +
                "&artist=Radiohead&api_key=$key&format=json"
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (body.isEmpty()) return if (code >= 500) KeyCheck.NETWORK else KeyCheck.OTHER
            val json = JSONObject(body)
            if (!json.has("error")) return KeyCheck.OK
            return when (json.optInt("error")) {
                10 -> KeyCheck.INVALID
                26 -> KeyCheck.SUSPENDED
                29 -> KeyCheck.LIMIT
                else -> KeyCheck.OTHER
            }
        } finally {
            conn.disconnect()
        }
    }
}

/** Состояние мастера настройки ключа. */
object KeySetup {
    /** Пользователь ушёл на сайт Last.fm: после возврата проверим буфер обмена. */
    var waiting by mutableStateOf(false)

    /** Ключ, найденный в буфере обмена. */
    var candidate by mutableStateOf("")

    fun checkClipboard(context: Context) {
        if (!waiting) return
        val found = LastFmKey.find(LastFmKey.readClipboard(context))
        if (found != null) {
            candidate = found
            waiting = false
        }
    }
}