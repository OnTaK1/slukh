package com.ontk.musicstats

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.FileProvider
import androidx.core.graphics.ColorUtils
import java.io.File
import java.io.FileOutputStream

/** Варианты оформления карточки для соцсетей. */
enum class CardStyle(val label: String) {
    POSTER("Постер"),
    MINIMAL("Минимал"),
    CHART("Чарт"),
    VIBE("Вайб")
}

object ShareCard {
    private const val W = 1080
    private const val H = 1920
    private const val MARGIN = 80f
    private const val CONTENT_W = W - 2 * MARGIN

    private val INK = Color.parseColor("#0A0A0A")
    private val PAPER = Color.parseColor("#F7F5F2")

    // ---------- общие помощники ----------

    private fun paint(
        size: Float,
        bold: Boolean = false,
        color: Int = Color.WHITE,
        alpha: Int = 255,
        mono: Boolean = false
    ): TextPaint {
        val p = TextPaint(Paint.ANTI_ALIAS_FLAG)
        p.textSize = size
        p.typeface = when {
            mono -> Typeface.MONOSPACE
            bold -> Typeface.DEFAULT_BOLD
            else -> Typeface.DEFAULT
        }
        p.color = color
        p.alpha = alpha
        return p
    }

    /** Рисует строку, уменьшая шрифт и обрезая её, если она не помещается. */
    private fun drawFit(
        c: Canvas,
        s: String,
        x: Float,
        y: Float,
        p: TextPaint,
        maxWidth: Float = CONTENT_W
    ) {
        while (p.measureText(s) > maxWidth && p.textSize > 28f) {
            p.textSize = p.textSize - 4f
        }
        val t = TextUtils.ellipsize(s, p, maxWidth, TextUtils.TruncateAt.END).toString()
        c.drawText(t, x, y, p)
    }

    private fun drawRight(c: Canvas, s: String, y: Float, p: TextPaint, rightX: Float = W - MARGIN) {
        p.textAlign = Paint.Align.RIGHT
        c.drawText(s, rightX, y, p)
    }

    private fun rect(c: Canvas, l: Float, t: Float, r: Float, b: Float, color: Int, alpha: Int = 255) {
        val p = Paint()
        p.color = color
        p.alpha = alpha
        c.drawRect(l, t, r, b, p)
    }

    private fun wrapped(c: Canvas, text: String, x: Float, y: Float, p: TextPaint, maxLines: Int) {
        val layout = StaticLayout.Builder
            .obtain(text, 0, text.length, p, CONTENT_W.toInt())
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()
        c.save()
        c.translate(x, y)
        layout.draw(c)
        c.restore()
    }

    private fun onAccentOf(accent: Int): Int =
        if (ColorUtils.calculateLuminance(accent) > 0.22) INK else Color.WHITE

    private fun header(c: Canvas, recap: Recap, color: Int) {
        drawFit(c, "SLUKH /", MARGIN, 130f, paint(34f, color = color, alpha = 150, mono = true))
        drawRight(
            c, recap.period.inPhrase.uppercase(), 130f,
            paint(34f, color = color, alpha = 150, mono = true)
        )
    }

    private fun footer(c: Canvas, color: Int) {
        drawFit(c, "slukh · жанры: last.fm", MARGIN, 1880f, paint(30f, color = color, alpha = 130, mono = true))
    }

    private fun topTrackBlock(c: Canvas, recap: Recap, color: Int, labelY: Float) {
        val (artists, title, count) = recap.topTracks.firstOrNull() ?: return
        drawFit(c, "ГЛАВНЫЙ ТРЕК", MARGIN, labelY, paint(32f, color = color, alpha = 150, mono = true))
        drawFit(c, title, MARGIN, labelY + 80f, paint(56f, bold = true, color = color))
        drawFit(c, "$artists · ×$count", MARGIN, labelY + 135f, paint(38f, color = color, alpha = 190))
    }

    // ---------- отрисовка ----------

    fun render(recap: Recap, accent: Int, style: CardStyle = CardStyle.POSTER): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        when (style) {
            CardStyle.POSTER -> poster(c, recap, accent)
            CardStyle.MINIMAL -> minimal(c, recap, accent)
            CardStyle.CHART -> chart(c, recap, accent)
            CardStyle.VIBE -> vibe(c, recap, accent)
        }
        return bmp
    }

    /** Тёмный фон и большой блок акцентного цвета. */
    private fun poster(c: Canvas, recap: Recap, accent: Int) {
        c.drawColor(INK)
        val onAccent = onAccentOf(accent)
        val t = recap.totals

        header(c, recap, Color.WHITE)
        drawFit(c, recap.period.cardTitle, MARGIN, 270f, paint(130f, bold = true))

        rect(c, 0f, 340f, W.toFloat(), 800f, accent)
        drawFit(c, "Я СЛУШАЛ МУЗЫКУ", MARGIN, 420f, paint(34f, color = onAccent, alpha = 200, mono = true))
        drawFit(c, formatDuration(t.totalMs), MARGIN, 620f, paint(190f, bold = true, color = onAccent))
        drawFit(
            c, "${t.plays} прослушиваний · ${t.uniqueTracks} треков",
            MARGIN, 710f, paint(44f, color = onAccent, alpha = 230)
        )
        drawFit(c, "${t.uniqueArtists} исполнителей", MARGIN, 765f, paint(44f, color = onAccent, alpha = 230))

        drawFit(c, "ТОП ИСПОЛНИТЕЛЕЙ", MARGIN, 900f, paint(32f, alpha = 150, mono = true))
        recap.topArtists.take(5).forEachIndexed { i, a ->
            val y = 990f + i * 92f
            drawFit(c, "${i + 1}", MARGIN, y, paint(34f, alpha = 130, mono = true), 60f)
            drawFit(c, a.name, MARGIN + 80f, y, paint(58f, bold = i == 0), CONTENT_W - 80f - 150f)
            drawRight(c, a.count.toString(), y, paint(52f, alpha = 200))
            rect(c, MARGIN, y + 28f, W - MARGIN, y + 30f, Color.WHITE, 40)
        }

        val genre = recap.topGenres.firstOrNull { it.first != "Без жанра" }
        if (genre != null) {
            drawFit(c, "ГЛАВНЫЙ ЖАНР", MARGIN, 1470f, paint(32f, alpha = 150, mono = true))
            GenreIcons.draw(c, genre.first, MARGIN, 1498f, 72f, accent)
            drawFit(c, genre.first, MARGIN + 96f, 1555f, paint(70f, bold = true, color = accent), CONTENT_W - 96f)
            if (recap.topSubgenres.isNotEmpty()) {
                val subs = recap.topSubgenres.take(3).joinToString(" · ") { it.first }
                drawFit(c, subs, MARGIN, 1620f, paint(42f, alpha = 210))
            }
        }

        val phrase = recap.facts.filterIsInstance<Fact.Line>().firstOrNull()?.text
        if (phrase != null) wrapped(c, phrase, MARGIN, 1690f, paint(44f, alpha = 225), 3)

        footer(c, Color.WHITE)
    }

    /** Светлая бумага, много воздуха, акцент только тонкой чертой. */
    private fun minimal(c: Canvas, recap: Recap, accent: Int) {
        c.drawColor(PAPER)
        val t = recap.totals

        header(c, recap, INK)
        drawFit(c, recap.period.cardTitle, MARGIN, 270f, paint(130f, bold = true, color = INK))
        rect(c, MARGIN, 330f, MARGIN + 160f, 342f, accent)

        drawFit(c, "Я СЛУШАЛ МУЗЫКУ", MARGIN, 520f, paint(34f, color = INK, alpha = 150, mono = true))
        drawFit(c, formatDuration(t.totalMs), MARGIN, 720f, paint(200f, bold = true, color = INK))
        drawFit(
            c, "${t.plays} прослушиваний · ${t.uniqueTracks} треков",
            MARGIN, 800f, paint(42f, color = INK, alpha = 170)
        )
        drawFit(c, "${t.uniqueArtists} исполнителей", MARGIN, 855f, paint(42f, color = INK, alpha = 170))

        drawFit(c, "ТОП ИСПОЛНИТЕЛЕЙ", MARGIN, 1030f, paint(32f, color = INK, alpha = 150, mono = true))
        recap.topArtists.take(3).forEachIndexed { i, a ->
            val y = 1135f + i * 140f
            drawFit(c, a.name, MARGIN, y, paint(76f, bold = true, color = INK))
            drawFit(c, "${a.count} прослушиваний", MARGIN, y + 52f, paint(36f, color = INK, alpha = 150))
        }

        topTrackBlock(c, recap, INK, 1600f)
        footer(c, INK)
    }

    /** Топ-5 исполнителей в виде горизонтальных столбиков. */
    private fun chart(c: Canvas, recap: Recap, accent: Int) {
        c.drawColor(INK)
        val t = recap.totals

        header(c, recap, Color.WHITE)
        drawFit(c, "Топ-5", MARGIN, 270f, paint(130f, bold = true))
        drawFit(
            c, "${formatDuration(t.totalMs)} музыки · ${t.plays} прослушиваний",
            MARGIN, 350f, paint(44f, alpha = 200)
        )

        val list = recap.topArtists.take(5)
        val max = (list.maxOfOrNull { it.count } ?: 1).coerceAtLeast(1)
        val barLeft = MARGIN + 80f
        val barWidth = W - MARGIN - barLeft
        list.forEachIndexed { i, a ->
            val y = 520f + i * 230f
            drawFit(c, "${i + 1}", MARGIN, y, paint(34f, alpha = 130, mono = true), 60f)
            drawFit(c, a.name, barLeft, y, paint(62f, bold = i == 0), barWidth - 170f)
            drawRight(c, a.count.toString(), y, paint(52f, alpha = 200))
            val top = y + 30f
            rect(c, barLeft, top, W - MARGIN, top + 26f, Color.WHITE, 30)
            val frac = (a.count.toFloat() / max).coerceAtLeast(0.04f)
            rect(c, barLeft, top, barLeft + barWidth * frac, top + 26f, accent)
        }

        topTrackBlock(c, recap, Color.WHITE, 1650f)
        footer(c, Color.WHITE)
    }

    /** Весь фон в цвет акцента, крупный жанр и тип слушателя. */
    private fun vibe(c: Canvas, recap: Recap, accent: Int) {
        c.drawColor(accent)
        val on = onAccentOf(accent)
        val t = recap.totals
        val genre = recap.topGenres.firstOrNull { it.first != "Без жанра" }
        val v = Vibes.forGenre(genre?.first)

        header(c, recap, on)

        // огромная иконка жанра как водяной знак
        GenreIcons.draw(c, genre?.first, W - MARGIN - 600f, 250f, 600f, on, 70)

        drawFit(c, "ГЛАВНЫЙ ЖАНР", MARGIN, 1010f, paint(32f, color = on, alpha = 170, mono = true))
        drawFit(c, genre?.first ?: "Свой путь", MARGIN, 1130f, paint(120f, bold = true, color = on))
        drawFit(c, v.tagline, MARGIN, 1205f, paint(50f, color = on, alpha = 225))
        if (recap.topSubgenres.isNotEmpty()) {
            val subs = recap.topSubgenres.take(3).joinToString(" · ") { it.first }
            drawFit(c, subs, MARGIN, 1275f, paint(42f, color = on, alpha = 200))
        }

        recap.archetype?.let { a ->
            drawFit(c, "ТВОЙ ТИП СЛУШАТЕЛЯ", MARGIN, 1430f, paint(32f, color = on, alpha = 170, mono = true))
            drawFit(c, a.title, MARGIN, 1515f, paint(68f, bold = true, color = on))
            wrapped(c, a.description, MARGIN, 1560f, paint(38f, color = on, alpha = 215), 3)
        }

        drawFit(
            c, "${formatDuration(t.totalMs)} · ${t.plays} прослушиваний",
            MARGIN, 1810f, paint(44f, bold = true, color = on)
        )
        footer(c, on)
    }

    // ---------- шеринг ----------

    fun share(context: Context, recap: Recap, accent: Int, style: CardStyle = CardStyle.POSTER) {
        val bmp = render(recap, accent, style)
        val dir = File(context.cacheDir, "shared")
        dir.mkdirs()
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "slukh_${style.name.lowercase()}_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Поделиться итогами"))
    }
}