package my.github.MrxSiN.pixellockscreenevolved.clock.poster

import android.content.Context
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View

import kotlin.math.ceil

import my.github.MrxSiN.pixellockscreenevolved.clock.Backdrop

/**
 * The time on one line, the hours then the minutes with no separator, each
 * run drawn in its own [GlyphLook], in a [PosterFont] at the figure height and
 * weight chosen, and stretched across as chosen.
 *
 * The figures are drawn from their outlines rather than as text, so an outline
 * keeps one width around every stroke at any size and glass can light and bend
 * through the figures' own edges. The view is exactly as tall as the figures
 * plus room for the glass's shadow, so a face can stack it tightly under its
 * date. Every digit is sized as the widest, so the line keeps its size whichever
 * minute it shows.
 */
internal class PosterTimeView(context: Context, private val font: PosterFont, typeface: Typeface) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.typeface = typeface }
    private val hours = GlyphRun()
    private val minutes = GlyphRun()
    private val figures = Rect()
    private val hairline = context.resources.displayMetrics.density * HAIRLINE_DP
    private val stretching = Matrix()

    private var hourText = ""
    private var minuteText = ""
    private var numeralHeight = 0f
    private var maxWidth = Float.MAX_VALUE
    private var color = Color.WHITE
    private var doze = 0f

    /** The figure height and weight, each from 0 to 1 across the font's range, and how much wider than designed they are drawn. */
    private var heightShare = font.heights.initialShare
    private var weightShare = font.weights.initialShare
    private var stretch = 1f

    /** How opaque the figures are drawn, from 0 to 1. */
    private var opacity = 1f

    /** The photo behind, and its shader, made once per photo and placed again on every draw. */
    private var backdrop: Backdrop? = null
    private var backdropShader: BitmapShader? = null
    private val backdropToView = Matrix()

    /** The figures' top against the baseline, and their height, at the text size in use. */
    private var figuresTop = 0f
    private var figuresHeight = 0f

    /** How wide the line is drawn, and the room kept around it for glass's shadow. */
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
        if (numeralHeight == this.numeralHeight && maxWidth == this.maxWidth) return
        this.numeralHeight = numeralHeight
        this.maxWidth = maxWidth
        resize()
        requestLayout()
    }

    /** Sets the font's figure [height] and [weight], each from 0 to 1 across its range, and how much wider than designed it is drawn. */
    fun setCut(height: Float, weight: Float, stretch: Float) {
        if (height == heightShare && weight == weightShare && stretch == this.stretch) return
        heightShare = height
        weightShare = weight
        this.stretch = stretch
        resize()
        requestLayout()
    }

    fun setLooks(hour: GlyphLook, minute: GlyphLook) {
        if (hours.look == hour && minutes.look == minute) return
        hours.look = hour
        minutes.look = minute
        prepare(reshaped = false)
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
        prepare(reshaped = true)
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

    fun setBackdrop(backdrop: Backdrop?) {
        if (backdrop === this.backdrop) return
        this.backdrop = backdrop
        backdropShader = backdrop?.let { BitmapShader(it.picture, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
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
        val left = (width - lineWidth) / 2
        val top = inset - figuresTop
        canvas.translate(left, top)
        val behind = placedBackdrop(left, top)
        hours.draw(canvas, doze, behind)
        minutes.draw(canvas, doze, behind)
        canvas.restoreToCount(layer)
    }

    /** The photo behind, placed in the units the figures are drawn in, shifted by [left] and [top] from the view's own; or null. */
    private fun placedBackdrop(left: Float, top: Float): Shader? {
        val shader = backdropShader ?: return null
        if (backdrop?.place(this, backdropToView) != true) return null
        backdropToView.postTranslate(-left, -top)
        shader.setLocalMatrix(backdropToView)
        return shader
    }

    /** Sets the text size for [numeralHeight] and [maxWidth] in the awake cut, and outlines the figures again. */
    private fun resize() {
        paint.fontVariationSettings = awakeCut()
        paint.textSize = PROBE_SIZE
        paint.getTextBounds(DIGITS, 0, DIGITS.length, figures)
        val heightPerSize = figures.height() / PROBE_SIZE
        val widthPerSize = (measureWidest(hourText) + gapPerSize() + measureWidest(minuteText)) * stretch / PROBE_SIZE
        val size = if (heightPerSize > 0f) {
            minOf(numeralHeight / heightPerSize, if (widthPerSize > 0f) maxWidth / widthPerSize else Float.MAX_VALUE)
        } else {
            1f
        }
        paint.textSize = size.coerceAtLeast(1f)
        paint.getTextBounds(DIGITS, 0, DIGITS.length, figures)
        figuresTop = figures.top.toFloat()
        figuresHeight = figures.height().toFloat()
        inset = ceil(figuresHeight * ROOM_TO_HEIGHT).toInt()
        outline()
    }

    /** Outlines both runs in the cut shown now, placed by the awake cut's advances so nothing shifts as it thins. */
    private fun outline() {
        paint.fontVariationSettings = awakeCut()
        val hourWidth = paint.measureText(hourText)
        val gap = gapPerSize() * paint.textSize / PROBE_SIZE
        lineWidth = (hourWidth + gap + paint.measureText(minuteText)) * stretch
        val hourAdvances = advances(hourText)
        val minuteAdvances = advances(minuteText)
        paint.fontVariationSettings = font.settings(heightShare, weightShare, doze)
        stretching.setScale(stretch, 1f)
        outline(hours, hourText, hourAdvances, 0f)
        outline(minutes, minuteText, minuteAdvances, hourWidth + gap)
        paint.fontVariationSettings = awakeCut()
        // The glass's height field follows the awake figures; while dozing only its rim is drawn.
        prepare(reshaped = doze == 0f)
    }

    /** Each glyph of [text] centred in the awake cut's advance, from [start], stretched across. */
    private fun outline(run: GlyphRun, text: String, advances: FloatArray, start: Float) {
        run.path.reset()
        var left = start
        val glyph = Path()
        text.indices.forEach { index ->
            val offset = (advances[index] - paint.measureText(text, index, index + 1)) / 2
            glyph.reset()
            paint.getTextPath(text, index, index + 1, left + offset, 0f, glyph)
            run.path.addPath(glyph)
            left += advances[index]
        }
        if (stretch != 1f) run.path.transform(stretching)
    }

    private fun advances(text: String): FloatArray = FloatArray(text.length) { paint.measureText(text, it, it + 1) }

    private fun prepare(reshaped: Boolean) {
        hours.prepare(color, figuresHeight, hairline, reshaped)
        minutes.prepare(color, figuresHeight, hairline, reshaped)
    }

    private fun awakeCut(): String = font.settings(heightShare, weightShare, doze = 0f)

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

        /** Room around the figures for glass's shadow, against their height. */
        const val ROOM_TO_HEIGHT = 0.04f

        /** The thinnest an outline or rim is drawn, so it never breaks up on a small face. */
        const val HAIRLINE_DP = 1.25f
    }
}
