package my.github.MrxSiN.pixellockscreenevolved.clock.poster

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.Typeface
import android.view.View

import kotlin.math.ceil

/**
 * The time on one line, the hours then the minutes with no separator, each
 * run drawn in its own [GlyphLook], in a [PosterFont].
 *
 * The figures are drawn from their outlines rather than as text, so an outline
 * keeps one width around every stroke at any size and glass can light the
 * figures' own edges. The view is exactly as tall as the figures plus room for
 * the glass's glow and shadow, so a face can stack it tightly under its date.
 * Every digit is sized as the widest, so the line keeps its size whichever
 * minute it shows.
 */
internal class PosterTimeView(context: Context, private val font: PosterFont, typeface: Typeface) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.typeface = typeface }
    private val hours = GlyphRun()
    private val minutes = GlyphRun()
    private val figures = Rect()
    private val hairline = context.resources.displayMetrics.density * HAIRLINE_DP

    private var hourText = ""
    private var minuteText = ""
    private var numeralHeight = 0f
    private var maxWidth = Float.MAX_VALUE
    private var color = Color.WHITE
    private var doze = 0f

    /** How opaque the figures are drawn, from 0 to 1. */
    private var opacity = 1f

    /** The figures' top against the baseline, and their height, at the text size in use. */
    private var figuresTop = 0f
    private var figuresHeight = 0f

    /** How wide the line is drawn, and the room kept around it for glow and shadow. */
    private var lineWidth = 0f
    private var inset = 0

    fun setTime(hour: String, minute: String) {
        if (hour == hourText && minute == minuteText) return
        val widthBefore = lineWidth
        hourText = hour
        minuteText = minute
        resize()
        if (lineWidth != widthBefore) requestLayout()
    }

    /** Sets the figures [numeralHeight] pixels tall, or smaller where the line would be wider than [maxWidth]. */
    fun fit(numeralHeight: Float, maxWidth: Float) {
        this.numeralHeight = numeralHeight
        this.maxWidth = maxWidth
        resize()
        requestLayout()
    }

    fun setLooks(hour: GlyphLook, minute: GlyphLook) {
        if (hours.look == hour && minutes.look == minute) return
        hours.look = hour
        minutes.look = minute
        invalidate()
    }

    fun setOpacity(opacity: Float) {
        val next = opacity.coerceIn(0f, 1f)
        if (next == this.opacity) return
        this.opacity = next
        invalidate()
    }

    fun setColor(color: Int) {
        if (color == this.color) return
        this.color = color
        prepare()
        invalidate()
    }

    /** Thins the figures toward the always-on display's light cut, from 0 awake to 1 dozing. */
    fun setDoze(fraction: Float) {
        val next = fraction.coerceIn(0f, 1f)
        if (next == doze) return
        doze = next
        outline()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = ceil(lineWidth).toInt() + 2 * inset
        val height = ceil(figuresHeight).toInt() + 2 * inset
        setMeasuredDimension(resolveSize(width, widthMeasureSpec), resolveSize(height, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        if (opacity <= 0f) return
        val layer = if (opacity < 1f) canvas.saveLayerAlpha(0f, 0f, width.toFloat(), height.toFloat(), (opacity * 255).toInt()) else canvas.save()
        canvas.translate((width - lineWidth) / 2, inset - figuresTop)
        hours.draw(canvas, doze)
        minutes.draw(canvas, doze)
        canvas.restoreToCount(layer)
    }

    /** Sets the text size for [numeralHeight] and [maxWidth] in the awake cut, and outlines the figures again. */
    private fun resize() {
        paint.fontVariationSettings = font.settings(0f)
        paint.textSize = PROBE_SIZE
        paint.getTextBounds(DIGITS, 0, DIGITS.length, figures)
        val heightPerSize = figures.height() / PROBE_SIZE
        val widthPerSize = (measureWidest(hourText) + gapPerSize() + measureWidest(minuteText)) / PROBE_SIZE
        val size = minOf(numeralHeight / heightPerSize, if (widthPerSize > 0f) maxWidth / widthPerSize else Float.MAX_VALUE)
        paint.textSize = size.coerceAtLeast(1f)
        paint.getTextBounds(DIGITS, 0, DIGITS.length, figures)
        figuresTop = figures.top.toFloat()
        figuresHeight = figures.height().toFloat()
        inset = ceil(figuresHeight * ROOM_TO_HEIGHT).toInt()
        outline()
    }

    /** Outlines both runs in the cut shown now, placed by the awake cut's advances so nothing shifts as it thins. */
    private fun outline() {
        paint.fontVariationSettings = font.settings(0f)
        val hourWidth = paint.measureText(hourText)
        val gap = gapPerSize() * paint.textSize / PROBE_SIZE
        lineWidth = hourWidth + gap + paint.measureText(minuteText)
        val hourAdvances = advances(hourText)
        val minuteAdvances = advances(minuteText)
        paint.fontVariationSettings = font.settings(doze)
        outline(hours, hourText, hourAdvances, 0f)
        outline(minutes, minuteText, minuteAdvances, hourWidth + gap)
        paint.fontVariationSettings = font.settings(0f)
        prepare()
    }

    /** Each glyph of [text] centred in the awake cut's advance, from [start]. */
    private fun outline(run: GlyphRun, text: String, advances: FloatArray, start: Float) {
        run.path.reset()
        var left = start
        text.indices.forEach { index ->
            val offset = (advances[index] - paint.measureText(text, index, index + 1)) / 2
            val glyph = Path()
            paint.getTextPath(text, index, index + 1, left + offset, 0f, glyph)
            run.path.addPath(glyph)
            left += advances[index]
        }
    }

    private fun advances(text: String): FloatArray = FloatArray(text.length) { paint.measureText(text, it, it + 1) }

    private fun prepare() {
        hours.prepare(color, figuresHeight, hairline)
        minutes.prepare(color, figuresHeight, hairline)
    }

    /** [text] measured with every digit as the widest, in the awake cut at [PROBE_SIZE]. */
    private fun measureWidest(text: String): Float {
        val size = paint.textSize
        paint.textSize = PROBE_SIZE
        val width = paint.measureText(text.map { if (it.isDigit()) WIDE_DIGIT else it }.joinToString(""))
        paint.textSize = size
        return width
    }

    /** The space between the hours and the minutes, at [PROBE_SIZE]. */
    private fun gapPerSize(): Float {
        val size = paint.textSize
        paint.textSize = PROBE_SIZE
        val gap = paint.measureText(WIDE_DIGIT.toString()) * GAP_TO_DIGIT
        paint.textSize = size
        return gap
    }

    private companion object {
        const val DIGITS = "0123456789"
        const val WIDE_DIGIT = '0'
        const val PROBE_SIZE = 200f

        /** Space between the hours and the minutes, against one digit's advance. */
        const val GAP_TO_DIGIT = 0.08f

        /** Room around the figures for glass's glow and shadow, against their height. */
        const val ROOM_TO_HEIGHT = 0.04f

        /** The thinnest an outline or rim is drawn, so it never breaks up on a small face. */
        const val HAIRLINE_DP = 1.25f
    }
}
