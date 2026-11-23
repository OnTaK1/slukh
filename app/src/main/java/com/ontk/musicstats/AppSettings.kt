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

    /** Папка (SAF) для автоматической резервной копии. Пусто: автокопия выключена. */
    var autoBackupUri by mutableStateOf("")
        private set

    var lastBackupAt by mutableStateOf(0L)
        private set

    /** Один раз удалили из истории рекламные вставки. */
    var adsPurged by mutableStateOf(false)
        private set

    var checkUpdates by mutableStateOf(true)
        private set

    var skippedVersion by mutableStateOf("")
        private set

    var lastUpdateCheck by mutableStateOf(0L)
        private set

    var suggestionsEnabled by mutableStateOf(true)
        private set

    /** После скольких прослушиваний за две недели предлагать действие с треком. */
    var suggestMinPlays by mutableStateOf(3)
        private set

    /** Папки с музыкой, выбранные через системный выбор (SAF). */
    var libraryTrees by mutableStateOf(listOf<String>())
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
        autoBackupUri = p.getString("auto_backup_uri", "").orEmpty()
        lastBackupAt = p.getLong("last_backup_at", 0L)
        adsPurged = p.getBoolean("ads_purged", false)
        checkUpdates = p.getBoolean("check_updates", true)
        skippedVersion = p.getString("skipped_version", "").orEmpty()
        lastUpdateCheck = p.getLong("last_update_check", 0L)
        suggestionsEnabled = p.getBoolean("suggestions", true)
        suggestMinPlays = p.getInt("suggest_min", 3)
        libraryTrees = p.getString("library_trees", "").orEmpty().split('\n').filter { it.isNotBlank() }
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

    fun setProfileId(context: Context, id: String) {
        profileId = id
        prefs(context).edit().putString("profile_id", id).apply()
    }

    fun setAutoBackupUri(context: Context, uri: String) {
        autoBackupUri = uri
        prefs(context).edit().putString("auto_backup_uri", uri).apply()
    }

    fun setLastBackupAt(context: Context, time: Long) {
        lastBackupAt = time
        prefs(context).edit().putLong("last_backup_at", time).apply()
    }

    fun setAdsPurged(context: Context, value: Boolean) {
        adsPurged = value
        prefs(context).edit().putBoolean("ads_purged", value).apply()
    }

    fun setCheckUpdates(context: Context, value: Boolean) {
        checkUpdates = value
        prefs(context).edit().putBoolean("check_updates", value).apply()
    }

    fun setSkippedVersion(context: Context, tag: String) {
        skippedVersion = tag
        prefs(context).edit().putString("skipped_version", tag).apply()
    }

    fun setLastUpdateCheck(context: Context, time: Long) {
        lastUpdateCheck = time
        prefs(context).edit().putLong("last_update_check", time).apply()
    }

    fun setSuggestionsEnabled(context: Context, value: Boolean) {
        suggestionsEnabled = value
        prefs(context).edit().putBoolean("suggestions", value).apply()
    }

    fun setSuggestMinPlays(context: Context, value: Int) {
        suggestMinPlays = value
        prefs(context).edit().putInt("suggest_min", value).apply()
    }

    private fun saveTrees(context: Context) {
        prefs(context).edit().putString("library_trees", libraryTrees.joinToString("\n")).apply()
    }

    fun addLibraryTree(context: Context, uri: String) {
        if (uri in libraryTrees) return
        libraryTrees = libraryTrees + uri
        saveTrees(context)
    }

    fun removeLibraryTree(context: Context, uri: String) {
        libraryTrees = libraryTrees - uri
        saveTrees(context)
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