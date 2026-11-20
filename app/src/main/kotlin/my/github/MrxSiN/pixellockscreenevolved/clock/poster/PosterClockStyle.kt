package my.github.MrxSiN.pixellockscreenevolved.clock.poster

import android.content.Context
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout

import java.util.Calendar
import java.util.Locale

import my.github.MrxSiN.pixellockscreenevolved.clock.ClockFace
import my.github.MrxSiN.pixellockscreenevolved.clock.ClockOption
import my.github.MrxSiN.pixellockscreenevolved.clock.ClockStyle
import my.github.MrxSiN.pixellockscreenevolved.clock.ClockWeather
import my.github.MrxSiN.pixellockscreenevolved.clock.ConfigurableClockFace
import my.github.MrxSiN.pixellockscreenevolved.clock.DozingClockFace
import my.github.MrxSiN.pixellockscreenevolved.clock.FaceSize
import my.github.MrxSiN.pixellockscreenevolved.clock.FontSource
import my.github.MrxSiN.pixellockscreenevolved.clock.ResizableClockFace
import my.github.MrxSiN.pixellockscreenevolved.clock.WeatherClockFace
import my.github.MrxSiN.pixellockscreenevolved.clock.parts.ClockThumbnail
import my.github.MrxSiN.pixellockscreenevolved.clock.parts.DateLine
import my.github.MrxSiN.pixellockscreenevolved.clock.parts.TimeParts

/**
 * Poster: the time as one line of giant figures, the hours then the minutes,
 * in a font the module ships ([PosterFont]). The hours and the minutes are each
 * drawn solid, outlined or as glass, and the time as opaque as chosen
 * ([PosterOptions]); the large face's size and position are chosen as for the
 * other styles.
 */
class PosterClockStyle(private val font: PosterFont, private val fonts: FontSource) : ClockStyle {

    override val id: String = font.id
    override val name: String = font.title
    override val description: String = font.description
    override val isResizable: Boolean = true
    override val options: List<ClockOption> = PosterOptions.of(font)

    override fun createFace(context: Context, size: FaceSize): ClockFace = Face(context, size, typeface(context))

    /** "0941", the hours as this face draws them and the minutes in their default look. */
    override fun createThumbnail(context: Context): Drawable {
        val typeface = typeface(context)
        return ClockThumbnail(context) { canvas, box, paint ->
            val hour = ClockThumbnail.SAMPLE_HOUR.padStart(2, '0')
            val minute = ClockThumbnail.SAMPLE_MINUTE
            paint.typeface = typeface
            paint.fontVariationSettings = font.settings(0f)
            paint.textSize = PROBE
            val figures = Rect().also { paint.getTextBounds(DIGITS, 0, DIGITS.length, it) }
            val widthPerSize = paint.measureText(hour + minute) / PROBE
            paint.textSize = minOf(box.height() * THUMBNAIL_HEIGHT * PROBE / figures.height(), box.width() * THUMBNAIL_WIDTH / widthPerSize)
            paint.getTextBounds(DIGITS, 0, DIGITS.length, figures)
            val left = box.exactCenterX() - paint.measureText(hour + minute) / 2
            val baseline = box.exactCenterY() - figures.exactCenterY()
            sketch(paint, font.hourLook)
            canvas.drawText(hour, left, baseline, paint)
            sketch(paint, font.minuteLook)
            canvas.drawText(minute, left + paint.measureText(hour), baseline, paint)
        }
    }

    /** The font, or a condensed system font where the module's cannot be read, so the clock still shows the time. */
    private fun typeface(context: Context): Typeface =
        fonts.typeface(context, font.asset) ?: Typeface.create("sans-serif-condensed", Typeface.BOLD)

    private inner class Face(context: Context, private val size: FaceSize, typeface: Typeface) :
        ClockFace, ResizableClockFace, DozingClockFace, WeatherClockFace, ConfigurableClockFace {

        private val context = context
        private val shortSide = context.resources.displayMetrics.let { minOf(it.widthPixels, it.heightPixels) }.toFloat()
        private val time = PosterTimeView(context, font, typeface)
        private val date = DateLine(context, DATE).takeIf { size == FaceSize.LARGE }

        private var hourLook = font.hourLook
        private var minuteLook = font.minuteLook

        override val view: View = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = ViewGroup.LayoutParams(WRAP, WRAP)
            date?.let { addView(it.view, LinearLayout.LayoutParams(WRAP, WRAP).apply { gravity = Gravity.CENTER_HORIZONTAL }) }
            addView(time, LinearLayout.LayoutParams(WRAP, WRAP).apply { gravity = Gravity.CENTER_HORIZONTAL })
        }
        override val timeView: View get() = time
        override val drawsDate: Boolean get() = date != null

        init {
            time.setLooks(hourLook, minuteLook)
            setSize(0f)
            refresh()
        }

        override fun refresh() {
            val now = Calendar.getInstance()
            val parts = TimeParts.of(now[Calendar.HOUR_OF_DAY], now[Calendar.MINUTE], DateFormat.is24HourFormat(context), Locale.getDefault())
            time.setTime(parts.hour.padStart(2, '0'), parts.minute)
            date?.refresh()
        }

        override fun setColor(color: Int) {
            time.setColor(color)
            date?.setColor(color)
        }

        override fun setSize(size: Float) {
            if (this.size == FaceSize.SMALL) {
                time.fit(shortSide * SMALL_TO_SHORT_SIDE, maxWidth = shortSide * SMALL_MAX_WIDTH)
                return
            }
            time.fit(shortSide * (font.smallest + (font.largest - font.smallest) * size.coerceIn(0f, 1f)), maxWidth = shortSide * MAX_WIDTH)
            (time.layoutParams as? ViewGroup.MarginLayoutParams)?.let {
                it.topMargin = (shortSide * DATE_GAP).toInt()
                time.layoutParams = it
            }
        }

        override fun setDoze(fraction: Float) = time.setDoze(fraction)

        override fun setWeather(weather: ClockWeather?) {
            date?.setWeather(weather)
        }

        override fun setOption(key: String, step: Int) {
            when (key) {
                PosterOptions.HOUR_KEY -> hourLook = PosterOptions.lookOf(step)
                PosterOptions.MINUTE_KEY -> minuteLook = PosterOptions.lookOf(step)
                PosterOptions.OPACITY_KEY -> return time.setOpacity(PosterOptions.opacityOf(step))
                else -> return
            }
            time.setLooks(hourLook, minuteLook)
        }
    }

    private companion object {
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

        val DATE = DateLine.Look(skeleton = "EEEMMMd", sizeToShortSide = 0.045f, weight = 600)

        /** Widest the large face's line may be, against the screen's short side. */
        const val MAX_WIDTH = 0.94f

        /** The small face, beside notifications: a little taller than a Pixel small clock, as its figures are narrow. */
        const val SMALL_TO_SHORT_SIDE = 0.16f
        const val SMALL_MAX_WIDTH = 0.6f

        /** Space between the date and the figures, against the short side. */
        const val DATE_GAP = 0.015f

        const val DIGITS = "0123456789"
        const val PROBE = 100f
        const val THUMBNAIL_HEIGHT = 0.7f
        const val THUMBNAIL_WIDTH = 0.9f

        /** Sets [paint] to draw a thumbnail's figures in [look]: glass reads as an outline at that size. */
        fun sketch(paint: Paint, look: GlyphLook) {
            paint.style = if (look == GlyphLook.FILLED) Paint.Style.FILL else Paint.Style.STROKE
            paint.strokeWidth = paint.textSize * THUMBNAIL_LINE
        }

        const val THUMBNAIL_LINE = 0.025f
    }
}
