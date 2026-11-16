package com.ontk.musicstats

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object AppSettings {
    private const val PREFS = "settings"

    /** 0 = как на устройстве, 1 = светлая, 2 = тёмная */
    var themeMode by mutableIntStateOf(0)
        private set

    var fontId by mutableIntStateOf(0)
        private set

    /** 0..3 — готовые палитры, 4 — свой цвет */
    var paletteId by mutableIntStateOf(0)
        private set

    /** Свой цвет акцента (ARGB) */
    var customColor by mutableIntStateOf(0xFFE5262B.toInt())
        private set

    /** Личный ключ Last.fm пользователя. Пусто — жанры выключены. */
    var lastfmKey by mutableStateOf("")
        private set

    /** Пакет плеера для кнопки «включить» в плейлисте. Пусто — там, где трек слушали. */
    var playlistPlayer by mutableStateOf("")
        private set

    /** Ник, под которым ты отправляешь свой профиль друзьям. */
    var profileName by mutableStateOf("")
        private set

    /** Случайный постоянный идентификатор профиля: по нему друг заменяет старый снимок новым. */
    var profileId by mutableStateOf("")
        private set

    /** Прошёл ли пользователь приветствие */
    var onboarded by mutableStateOf(false)
        private set

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context) {
        val p = prefs(context)
        themeMode = p.getInt("theme", 0)
        fontId = p.getInt("font", 0)
        paletteId = p.getInt("palette", 0)
        customColor = p.getInt("custom_color", 0xFFE5262B.toInt())
        lastfmKey = p.getString("lastfm_key", "").orEmpty()
        playlistPlayer = p.getString("playlist_player", "").orEmpty()
        profileName = p.getString("profile_name", "").orEmpty()
        var pid = p.getString("profile_id", null)
        if (pid.isNullOrEmpty()) {
            pid = java.util.UUID.randomUUID().toString().replace("-", "").take(12)
            p.edit().putString("profile_id", pid).apply()
        }
        profileId = pid
        onboarded = p.getBoolean("onboarded", false)
    }

    fun setTheme(context: Context, value: Int) {
        themeMode = value
        prefs(context).edit().putInt("theme", value).apply()
    }

    fun setFont(context: Context, value: Int) {
        fontId = value
        prefs(context).edit().putInt("font", value).apply()
    }

    fun setPalette(context: Context, value: Int) {
        paletteId = value
        prefs(context).edit().putInt("palette", value).apply()
    }

    /** Выбор своего цвета сразу включает палитру «свой цвет». */
    fun setCustomColor(context: Context, argb: Int) {
        customColor = argb
        paletteId = 4
        prefs(context).edit()
            .putInt("custom_color", argb)
            .putInt("palette", 4)
            .apply()
    }

    fun setLastfmKey(context: Context, key: String) {
        lastfmKey = key.trim()
        prefs(context).edit().putString("lastfm_key", lastfmKey).apply()
    }

    fun setProfileName(context: Context, name: String) {
        profileName = name.take(24)
        prefs(context).edit().putString("profile_name", profileName).apply()
    }

    fun setPlaylistPlayer(context: Context, pkg: String) {
        playlistPlayer = pkg
        prefs(context).edit().putString("playlist_player", pkg).apply()
    }

    fun setOnboarded(context: Context, value: Boolean) {
        onboarded = value
        prefs(context).edit().putBoolean("onboarded", value).apply()
    }
}