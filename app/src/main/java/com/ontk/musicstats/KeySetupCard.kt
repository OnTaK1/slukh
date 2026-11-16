package com.ontk.musicstats

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private const val APP_NAME = "Slukh"
private const val APP_DESCRIPTION = "Slukh: personal music listening statistics, non-commercial, for my own use."
private const val CREATE_URL = "https://www.last.fm/api/account/create"

private fun statusText(s: KeyCheck): String = when (s) {
    KeyCheck.OK -> "Готово! Жанры подключены."
    KeyCheck.INVALID -> "Last.fm не принял ключ. Проверь, что скопировал все 32 символа."
    KeyCheck.SUSPENDED -> "Last.fm приостановил этот ключ."
    KeyCheck.LIMIT -> "Слишком много запросов. Попробуй через минуту."
    KeyCheck.NETWORK -> "Нет связи с Last.fm. Проверь интернет."
    KeyCheck.OTHER -> "Что-то пошло не так. Попробуй ещё раз."
}

private fun copyText(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("Slukh", text))
}

@Composable
fun KeySetupCard() {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val saved = AppSettings.lastfmKey
    val candidate = KeySetup.candidate
    var input by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var messageOk by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }

    fun show(text: String, ok: Boolean) {
        message = text
        messageOk = ok
    }

    fun submit(raw: String) {
        val key = raw.trim().lowercase()
        if (key.length != 32 || LastFmKey.find(key) == null) {
            show("Это не похоже на ключ: нужно 32 символа (цифры и буквы a–f).", false)
            return
        }
        checking = true
        message = null
        LastFmKey.validate(key) { result ->
            checking = false
            show(statusText(result), result == KeyCheck.OK)
            if (result == KeyCheck.OK) {
                AppSettings.setLastfmKey(context, key)
                KeySetup.candidate = ""
                input = ""
                GenreResolver.requestMissing(context)
                NowPlaying.version++
            }
        }
    }

    Panel {
        if (saved.isNotBlank()) {
            MonoLabel("жанры")
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Last.fm подключён ✓",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Жанры определяются автоматически. Ключ хранится только на этом телефоне.",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { submit(saved) },
                    shape = PillShape,
                    enabled = !checking
                ) { Text("Проверить ключ") }
                TextButton(onClick = {
                    AppSettings.setLastfmKey(context, "")
                    message = null
                }) { Text("Удалить ключ") }
            }
        } else {
            MonoLabel("жанры · по желанию")
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Подключи Last.fm — это бесплатно",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Без ключа приложение работает, но жанры не определяются. " +
                        "Ключ нужен один раз и хранится только на этом телефоне.",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "1. Нажми «Открыть Last.fm», войди или зарегистрируйся (бесплатно).\n" +
                        "2. В форме создания приложения вставь название и описание " +
                        "(кнопки ниже копируют их). Остальное можно пропустить. " +
                        "Тип использования — некоммерческий.\n" +
                        "3. Скопируй показанный API key и вернись сюда: я найду его в буфере обмена.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = {
                    KeySetup.waiting = true
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(CREATE_URL)))
                },
                shape = PillShape
            ) { Text("Открыть Last.fm") }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { copyText(context, APP_NAME) }, shape = PillShape) {
                    Text("Копировать название")
                }
                OutlinedButton(onClick = { copyText(context, APP_DESCRIPTION) }, shape = PillShape) {
                    Text("Копировать описание")
                }
            }

            if (candidate.isNotBlank()) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    "Нашёл ключ в буфере: ${candidate.take(6)}…${candidate.takeLast(4)}",
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { submit(candidate) },
                    shape = PillShape,
                    enabled = !checking
                ) { Text("Использовать этот ключ") }
            }

            Spacer(modifier = Modifier.height(14.dp))
            OutlinedTextField(
                value = input,
                onValueChange = {
                    input = it
                    message = null
                },
                label = { Text("API key") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val found = LastFmKey.find(LastFmKey.readClipboard(context))
                    if (found != null) {
                        input = found
                        message = null
                    } else {
                        show("В буфере обмена нет ключа.", false)
                    }
                }, shape = PillShape) { Text("Вставить") }
                Button(
                    onClick = { submit(input) },
                    shape = PillShape,
                    enabled = !checking && input.isNotBlank()
                ) { Text("Сохранить") }
            }
        }

        if (checking) {
            Spacer(modifier = Modifier.height(10.dp))
            Text("Проверяю…", style = MaterialTheme.typography.bodySmall)
        }
        message?.let {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = if (messageOk) cs.onSurface else cs.error
            )
        }
    }
}