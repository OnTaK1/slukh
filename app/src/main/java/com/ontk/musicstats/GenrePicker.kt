package com.ontk.musicstats

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private fun splitSubs(raw: String): List<String> =
    raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }

@Composable
private fun PickChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .clip(PillShape)
            .background(if (selected) cs.primary else Color.Transparent)
            .border(1.dp, if (selected) cs.primary else cs.outline, PillShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) cs.onPrimary else cs.onSurface,
            maxLines = 1
        )
    }
}

/**
 * Выбор жанра и поджанров исполнителя: из готовых вариантов или вручную.
 * Пустой жанр в [onSave] означает «определить автоматически».
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GenrePickerDialog(
    artistName: String,
    initialGenre: String,
    initialSubgenres: String,
    genres: List<String>,
    subgenres: List<String>,
    onSave: (genre: String, subgenres: String) -> Unit,
    onDismiss: () -> Unit
) {
    var genre by remember { mutableStateOf(initialGenre) }
    var subs by remember { mutableStateOf(splitSubs(initialSubgenres)) }
    var subInput by remember { mutableStateOf("") }

    // порядок вариантов не прыгает при выборе: готовые, потом добавленные вручную
    val baseSubs = (GenreMapper.subgenresFor(genre.trim()) + subgenres).distinct()
    val subOptions = baseSubs + subs.filter { it !in baseSubs }

    fun addSub() {
        val t = subInput.trim()
        if (t.isNotEmpty() && t !in subs) subs = subs + t
        subInput = ""
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(artistName, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                MonoLabel("жанр")
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PickChip("Авто", genre.isBlank()) { genre = "" }
                    genres.forEach { g ->
                        PickChip(g, genre.trim().equals(g, ignoreCase = true)) { genre = g }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = genre,
                    onValueChange = { genre = it },
                    label = { Text("Или впиши свой жанр") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(18.dp))
                MonoLabel("поджанры")
                Spacer(modifier = Modifier.height(8.dp))
                if (subOptions.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        subOptions.forEach { s ->
                            PickChip(s, s in subs) {
                                subs = if (s in subs) subs - s else subs + s
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = subInput,
                        onValueChange = { subInput = it },
                        label = { Text("Свой поджанр") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { addSub() }, enabled = subInput.isNotBlank()) {
                        Text("Добавить")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "«Авто» — жанр и поджанры определятся сами по тегам Last.fm.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val pending = subInput.trim()
                val finalSubs = if (pending.isNotEmpty() && pending !in subs) subs + pending else subs
                onSave(genre.trim(), finalSubs.joinToString(", "))
            }) { Text("Сохранить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}