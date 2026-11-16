package com.ontk.musicstats

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar

const val DAY_MS = 24L * 60 * 60 * 1000

val CardShape = RoundedCornerShape(10.dp)
val PillShape = RoundedCornerShape(8.dp)

fun formatDuration(ms: Long): String {
    val totalMin = ms / 60_000
    val h = totalMin / 60
    val m = totalMin % 60
    return if (h > 0) "$h ч $m мин" else "$m мин"
}

fun startOfToday(): Long =
    Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

/** Плоская карточка с тонкой рамкой. Цвет текста задан явно. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    padding: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = CardShape,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(padding), content = content)
    }
}

/** Мелкая подпись заглавными, моноширинным шрифтом. */
@Composable
fun MonoLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Text(
        text.uppercase(),
        modifier = modifier,
        color = color,
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        letterSpacing = 1.2.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

/** Переключатель из нескольких вариантов. Если не помещается, прокручивается вбок. */
@Composable
fun Pills(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEachIndexed { i, label ->
            val isSelected = i == selected
            Box(
                modifier = Modifier
                    .clip(PillShape)
                    .background(if (isSelected) cs.primary else Color.Transparent)
                    .border(1.dp, if (isSelected) cs.primary else cs.outline, PillShape)
                    .clickable { onSelect(i) }
                    .padding(horizontal = 16.dp, vertical = 9.dp)
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSelected) cs.onPrimary else cs.onSurface,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
fun GenreChip(genre: String) {
    Box(
        modifier = Modifier
            .clip(PillShape)
            .border(1.dp, MaterialTheme.colorScheme.outline, PillShape)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GenreIcon(genre, iconSize = 14.dp, tint = MaterialTheme.colorScheme.primary)
            Text(
                genre,
                modifier = Modifier.padding(start = 6.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/** Значок исполнителя: форма и заливка зависят от имени. */
@Composable
fun ArtistMark(name: String, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val h = TrackParser.key(name).hashCode() and Int.MAX_VALUE
    val shape = if (h % 2 == 0) CircleShape else RoundedCornerShape(8.dp)
    val style = (h / 2) % 3
    val bg = when (style) {
        0 -> cs.primary
        1 -> cs.onBackground
        else -> Color.Transparent
    }
    val fg = when (style) {
        0 -> cs.onPrimary
        1 -> cs.background
        else -> cs.onBackground
    }
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(shape)
            .background(bg)
            .border(1.5.dp, if (style == 2) cs.onBackground else Color.Transparent, shape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            name.firstOrNull()?.uppercase() ?: "?",
            color = fg,
            fontWeight = FontWeight.Black
        )
    }
}