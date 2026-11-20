package com.ontk.musicstats

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

data class ReleaseInfo(
    val tag: String,
    val notes: String,
    val pageUrl: String,
    val apkUrl: String?
)

/**
 * Узнаёт у GitHub, вышла ли новая версия. Запрос не содержит никаких данных о тебе или музыке.
 * Работает, пока репозиторий публичный. Предрелизы тоже учитываются.
 */
object UpdateChecker {
    private const val TAG = "Slukh"
    private const val REPO = "OnTaK1/slukh"
    private const val API = "https://api.github.com/repos/$REPO/releases?per_page=10"
    private const val PAGE_FALLBACK = "https://github.com/$REPO/releases"
    private const val CHECK_INTERVAL_MS = 12L * 60 * 60 * 1000

    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    /** Найденная новая версия или null. */
    var available by mutableStateOf<ReleaseInfo?>(null)
        private set

    /** Текст результата последней ручной проверки. */
    var status by mutableStateOf("")
        private set

    var checking by mutableStateOf(false)
        private set

    /** Новая версия, о которой нужно напомнить (кроме пропущенной). */
    fun visible(): ReleaseInfo? = available?.takeIf { it.tag != AppSettings.skippedVersion }

    fun currentVersion(context: Context): String =
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
        } catch (e: Exception) {
            ""
        }

    private fun parse(version: String): List<Int> =
        version.trim().trimStart('v', 'V').substringBefore('-').substringBefore('+')
            .split('.')
            .map { part -> part.filter { it.isDigit() }.toIntOrNull() ?: 0 }

    /** true, если версия [remote] новее [local]. */
    fun isNewer(remote: String, local: String): Boolean {
        val a = parse(remote)
        val b = parse(local)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** Самая новая версия из последних релизов. null: релизов нет. Ошибка сети: IOException. */
    private fun fetchLatest(): ReleaseInfo? {
        val conn = URL(API).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "Slukh-Android")
            val code = conn.responseCode
            if (code == 404) return null
            if (code !in 200..299) throw IOException("HTTP $code")
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val arr = JSONArray(body)
            var best: ReleaseInfo? = null
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                if (o.optBoolean("draft")) continue
                val tag = o.optString("tag_name").trim()
                if (tag.isEmpty()) continue

                var page = o.optString("html_url")
                if (!page.startsWith("https://github.com/$REPO/")) page = PAGE_FALLBACK

                var apk: String? = null
                val assets = o.optJSONArray("assets")
                if (assets != null) {
                    for (j in 0 until assets.length()) {
                        val a = assets.getJSONObject(j)
                        val url = a.optString("browser_download_url")
                        if (a.optString("name").endsWith(".apk", ignoreCase = true) &&
                            url.startsWith("https://github.com/$REPO/releases/download/")
                        ) {
                            apk = url
                            break
                        }
                    }
                }
                val info = ReleaseInfo(tag, o.optString("body").trim(), page, apk)
                if (best == null || isNewer(info.tag, best.tag)) best = info
            }
            return best
        } finally {
            conn.disconnect()
        }
    }

    /** Проверка по расписанию (раз в 12 часов) или вручную из настроек. */
    fun check(context: Context, manual: Boolean = false) {
        if (checking) return
        if (!manual) {
            if (!AppSettings.checkUpdates) return
            if (System.currentTimeMillis() - AppSettings.lastUpdateCheck < CHECK_INTERVAL_MS) return
        }
        val app = context.applicationContext
        val current = currentVersion(app)
        checking = true
        if (manual) status = "Проверяю…"
        executor.execute {
            val result = try {
                Result.success(fetchLatest())
            } catch (e: Exception) {
                Log.d(TAG, "Не удалось проверить обновления: $e")
                Result.failure(e)
            }
            main.post {
                checking = false
                if (result.isFailure) {
                    if (manual) status = "Не удалось связаться с GitHub. Проверь интернет."
                } else {
                    AppSettings.setLastUpdateCheck(app, System.currentTimeMillis())
                    val release = result.getOrNull()
                    if (release != null && isNewer(release.tag, current)) {
                        if (manual) AppSettings.setSkippedVersion(app, "")
                        available = release
                        if (manual) status = "Доступна версия ${release.tag.trimStart('v', 'V')}."
                    } else {
                        available = null
                        if (manual) {
                            status = if (release == null) {
                                "Релизов на GitHub пока нет."
                            } else {
                                "У тебя последняя версия ($current)."
                            }
                        }
                    }
                }
            }
        }
    }
}
