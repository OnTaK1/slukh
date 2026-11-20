package com.ontk.musicstats

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/** Фоновые дела при запуске приложения: чистка рекламы, автокопия, проверка обновлений. */
object Maintenance {
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    fun onAppStart(context: Context) {
        val app = context.applicationContext
        if (!AppSettings.adsPurged) {
            executor.execute {
                val removed = try {
                    StatsDb.get(app).purgeAds()
                } catch (e: Exception) {
                    Log.d("Slukh", "Не удалось почистить рекламу: $e")
                    -1
                }
                main.post {
                    if (removed >= 0) {
                        AppSettings.setAdsPurged(app, true)
                        if (removed > 0) NowPlaying.version++
                    }
                    Backup.autoBackupIfNeeded(app)
                }
            }
        } else {
            Backup.autoBackupIfNeeded(app)
        }
        UpdateChecker.check(app)
    }
}

private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: Exception) {
        Log.d("Slukh", "Не удалось открыть ссылку: $e")
    }
}

/** Плашка на главном экране, когда вышла новая версия. */
@Composable
fun UpdateBanner() {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val release = UpdateChecker.visible() ?: return
    val notes = release.notes
        .replace(Regex("[*#`>]"), "")
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("SHA", ignoreCase = true) }
        .take(3)
        .joinToString("\n")
        .take(220)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        color = cs.primary,
        contentColor = cs.onPrimary
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            MonoLabel("обновление", color = cs.onPrimary.copy(alpha = 0.8f))
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Доступна версия ${release.tag.trimStart('v', 'V')}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black
            )
            if (notes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onPrimary.copy(alpha = 0.9f),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { openUrl(context, release.apkUrl ?: release.pageUrl) },
                    shape = PillShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = cs.onPrimary,
                        contentColor = cs.primary
                    )
                ) { Text("Скачать") }
                TextButton(onClick = { AppSettings.setSkippedVersion(context, release.tag) }) {
                    Text("Пропустить", color = cs.onPrimary)
                }
            }
        }
    }
}

/** Панель настроек: проверка обновлений. */
@Composable
fun UpdatePanel() {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val release = UpdateChecker.available
    Panel {
        MonoLabel("обновления")
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            "Версия ${UpdateChecker.currentVersion(context)}",
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = AppSettings.checkUpdates,
                onCheckedChange = { AppSettings.setCheckUpdates(context, it) }
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text("Проверять при запуске", modifier = Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            "Приложение запрашивает у GitHub список релизов. Никакие данные о тебе " +
                    "и о твоей музыке не передаются.",
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { UpdateChecker.check(context, manual = true) },
                enabled = !UpdateChecker.checking,
                shape = PillShape
            ) { Text("Проверить сейчас") }
            if (release != null) {
                Button(
                    onClick = { openUrl(context, release.apkUrl ?: release.pageUrl) },
                    shape = PillShape
                ) { Text("Скачать") }
            }
        }
        if (UpdateChecker.status.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(UpdateChecker.status, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Панель настроек: резервная копия истории. */
@Composable
fun BackupPanel() {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val fmt = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            busy = true
            Backup.exportToUri(context, uri) { ok ->
                busy = false
                message = if (ok) "Копия сохранена." else "Не удалось сохранить копию."
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            busy = true
            Backup.restoreFromUri(context, uri) { result ->
                busy = false
                message = result.fold(
                    onSuccess = {
                        "Восстановлено: ${it.plays} прослушиваний" +
                                (if (it.skipped > 0) " (уже были: ${it.skipped})" else "") +
                                ", жанров ${it.genres}, друзей ${it.friends}."
                    },
                    onFailure = {
                        "Не удалось восстановить: " +
                                ((it as? IllegalArgumentException)?.message
                                    ?: "файл повреждён или не от Slukh.")
                    }
                )
            }
        }
    }

    val treeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                AppSettings.setAutoBackupUri(context, uri.toString())
                busy = true
                Backup.backupNow(context) { ok ->
                    busy = false
                    message = if (ok) "Автокопия включена, первая копия сохранена."
                    else "Папка выбрана, но записать в неё не удалось. Выбери другую."
                }
            } catch (e: SecurityException) {
                message = "Не удалось получить доступ к этой папке."
            }
        }
    }

    val autoUri = AppSettings.autoBackupUri
    val stamp = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }

    Panel {
        MonoLabel("резервная копия")
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            "Копия хранит историю, жанры, друзей и профиль. Ключ Last.fm в неё не попадает. " +
                    "Файл переживёт обновление и переустановку приложения: положи его в облако " +
                    "или на флешку.",
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(14.dp))

        Text("Автокопия", fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(2.dp))
        if (autoUri.isEmpty()) {
            Text(
                "Выключена. Выбери папку, и Slukh раз в сутки будет обновлять в ней файл копии.",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))
            Button(onClick = { treeLauncher.launch(null) }, shape = PillShape, enabled = !busy) {
                Text("Выбрать папку")
            }
        } else {
            val folder = Uri.parse(autoUri).lastPathSegment.orEmpty()
            val last = AppSettings.lastBackupAt
            Text(
                "Папка: $folder\nПоследняя копия: " +
                        if (last > 0) fmt.format(Date(last)) else "ещё не сохранялась",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        busy = true
                        Backup.backupNow(context) { ok ->
                            busy = false
                            message = if (ok) "Копия сохранена." else "Не удалось сохранить копию."
                        }
                    },
                    shape = PillShape,
                    enabled = !busy
                ) { Text("Сохранить сейчас") }
                OutlinedButton(onClick = { treeLauncher.launch(null) }, shape = PillShape, enabled = !busy) {
                    Text("Сменить папку")
                }
            }
            TextButton(onClick = {
                AppSettings.setAutoBackupUri(context, "")
                message = "Автокопия выключена."
            }) { Text("Отключить автокопию") }
        }

        Spacer(modifier = Modifier.height(14.dp))
        Text("Вручную", fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { exportLauncher.launch("slukh-backup-$stamp.slukh") },
                shape = PillShape,
                enabled = !busy
            ) { Text("Сохранить в файл") }
            OutlinedButton(
                onClick = { importLauncher.launch(arrayOf("*/*")) },
                shape = PillShape,
                enabled = !busy
            ) { Text("Восстановить") }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            "Восстановление объединяет файл с текущей историей и не создаёт дубликатов, " +
                    "его можно выполнять повторно.",
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant
        )
        if (busy) {
            Spacer(modifier = Modifier.height(8.dp))
            Text("Работаю…", style = MaterialTheme.typography.bodySmall)
        }
        message?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodySmall)
        }
    }
}
