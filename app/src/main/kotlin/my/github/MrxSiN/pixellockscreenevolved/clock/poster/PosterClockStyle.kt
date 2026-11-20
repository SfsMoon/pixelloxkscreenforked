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

import kotlin.math.abs

import java.util.Calendar
import java.util.Locale

import my.github.MrxSiN.pixellockscreenevolved.clock.Backdrop
import my.github.MrxSiN.pixellockscreenevolved.clock.BackdropClockFace
import my.github.MrxSiN.pixellockscreenevolved.clock.ClockFace
import my.github.MrxSiN.pixellockscreenevolved.clock.ClockFrame
import my.github.MrxSiN.pixellockscreenevolved.clock.ClockOption
import my.github.MrxSiN.pixellockscreenevolved.clock.ClockStyle
import my.github.MrxSiN.pixellockscreenevolved.clock.ClockWeather
import my.github.MrxSiN.pixellockscreenevolved.clock.ConfigurableClockFace
import my.github.MrxSiN.pixellockscreenevolved.clock.DozingClockFace
import my.github.MrxSiN.pixellockscreenevolved.clock.EdgeToEdgeClockFace
import my.github.MrxSiN.pixellockscreenevolved.clock.FaceSize
import my.github.MrxSiN.pixellockscreenevolved.clock.FontSource
import my.github.MrxSiN.pixellockscreenevolved.clock.FramedClockFace
import my.github.MrxSiN.pixellockscreenevolved.clock.ResizableClockFace
import my.github.MrxSiN.pixellockscreenevolved.clock.WeatherClockFace
import my.github.MrxSiN.pixellockscreenevolved.clock.parts.ClockThumbnail
import my.github.MrxSiN.pixellockscreenevolved.clock.parts.DateLine
import my.github.MrxSiN.pixellockscreenevolved.clock.parts.MarginColumn
import my.github.MrxSiN.pixellockscreenevolved.clock.parts.TimeParts
import my.github.MrxSiN.pixellockscreenevolved.clock.parts.WideColumn

/**
 * Poster: the time as one line of giant figures, the hours then the minutes,
 * in a font the module ships ([PosterFont]). The hours and the minutes are each
 * drawn solid, outlined or as glass that shows the photo behind it
 * ([BackdropClockFace]), the time as opaque as chosen, and the figures as tall,
 * heavy and wide as chosen ([PosterOptions]); the large face's size and
 * position are chosen as for the other styles. The line may run nearly from
 * edge to edge, past the box the lock screen lays the clock out in
 * ([EdgeToEdgeClockFace]), and stays centred on screen.
 */
class PosterClockStyle(private val font: PosterFont, private val fonts: FontSource) : ClockStyle {

    override val id: String = font.id
    override val name: String = font.title
    override val description: String = font.description
    override val isResizable: Boolean = true

    /** Placed and stretched by hand, the clock stays where it was put as the display dozes. */
    override val keepsPlaceOnAod: Boolean = true
    override val options: List<ClockOption> = PosterOptions.of(font)

    override fun createFace(context: Context, size: FaceSize): ClockFace = Face(context, size, typeface(context))

    /** "0941", the hours as this face draws them and the minutes in their default look. */
    override fun createThumbnail(context: Context): Drawable {
        val typeface = typeface(context)
        return ClockThumbnail(context) { canvas, box, paint ->
            val hour = ClockThumbnail.SAMPLE_HOUR.padStart(2, '0')
            val minute = ClockThumbnail.SAMPLE_MINUTE
            paint.typeface = typeface
            paint.fontVariationSettings = font.settings(font.heights.initialShare, font.weights.initialShare, doze = 0f)
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
        ClockFace, ResizableClockFace, DozingClockFace, WeatherClockFace, ConfigurableClockFace, EdgeToEdgeClockFace, BackdropClockFace, FramedClockFace {

        private val context = context
        private val shortSide = context.resources.displayMetrics.let { minOf(it.widthPixels, it.heightPixels) }.toFloat()
        private val time = PosterTimeView(context, font, typeface)
        private val date = DateLine(context, DATE).takeIf { size == FaceSize.LARGE }

        private val density = context.resources.displayMetrics.density

        private var hourLook = font.hourLook
        private var minuteLook = font.minuteLook
        private var heightShare = font.heights.initialShare
        private var weightShare = font.weights.initialShare
        private var frame = ClockFrame.NATURAL
        private var glass = GlassLook()
        private var datePlace = DatePlace.ABOVE
        private var shownSize = 0f

        /** How much smaller than laid out the host draws the face ([setDrawnScale]). */
        private var drawnScale = 1f

        /** The large face's column, which a person may move across; the small face keeps to the lock screen's margin. */
        private val column = WideColumn(context).takeIf { size == FaceSize.LARGE }

        override val view: View = (column ?: MarginColumn(context)).apply {
            if (column != null) gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = ViewGroup.LayoutParams(WRAP, WRAP)
            val across = if (column != null) Gravity.CENTER_HORIZONTAL else Gravity.START
            date?.let { addView(it.view, LinearLayout.LayoutParams(WRAP, WRAP).apply { gravity = across }) }
            addView(time, LinearLayout.LayoutParams(WRAP, WRAP).apply { gravity = across })
        }
        override val timeView: View get() = time
        override val drawsDate: Boolean get() = date != null

        init {
            time.setLooks(hourLook, minuteLook)
            setSize(0f)
            refresh()
            // In a preview the window is not the device's screen, so the line is fitted again once it is shown in one.
            time.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(attached: View) = setSize(shownSize)

                override fun onViewDetachedFromWindow(detached: View) = Unit
            })
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
            shownSize = size
            if (this.size == FaceSize.SMALL) {
                time.fit(shortSide * SMALL_TO_SHORT_SIDE, maxWidth = shortSide * SMALL_MAX_WIDTH)
                return
            }
            val sized = shortSide * (font.smallest + (font.largest - font.smallest) * size.coerceIn(0f, 1f))
            time.fit(sized, maxWidth = columnWidth())
            time.setScale(frame.scaleX, frame.scaleY)
            column?.shift = frame.shiftX * windowWidth() / drawnScale
            placeDate(datePlace)
        }

        override fun setDoze(fraction: Float) = time.setDoze(fraction)

        override fun setDrawnScale(scale: Float) {
            if (scale <= 0f || abs(drawnScale - scale) < SCALE_STEP) return
            drawnScale = scale
            setSize(shownSize)
        }

        override fun setBackdrop(backdrop: Backdrop?) = time.setBackdrop(backdrop)

        /** Writes the date above the time, below it, or not at all. */
        private fun placeDate(place: DatePlace) {
            val line = date?.view ?: return
            val column = view as ViewGroup
            line.visibility = if (place == DatePlace.HIDDEN) View.GONE else View.VISIBLE
            val wanted = if (place == DatePlace.BELOW) column.childCount - 1 else 0
            if (column.indexOfChild(line) != wanted) {
                column.removeView(line)
                column.addView(line, wanted)
            }
            // The gap stands between the two, on whichever is lower.
            val gap = if (place == DatePlace.HIDDEN) 0 else (shortSide * DATE_GAP).toInt()
            (time.layoutParams as? ViewGroup.MarginLayoutParams)?.let {
                it.topMargin = if (place == DatePlace.ABOVE) gap else 0
                time.layoutParams = it
            }
            (line.layoutParams as? ViewGroup.MarginLayoutParams)?.let {
                it.topMargin = if (place == DatePlace.BELOW) gap else 0
                line.layoutParams = it
            }
            datePlace = place
        }

        /** Stretches and moves the large face as a person framed it by hand; the small face keeps its place beside notifications. */
        override fun setFrame(frame: ClockFrame) {
            if (size == FaceSize.SMALL || frame == this.frame) return
            this.frame = frame
            setSize(shownSize)
        }

        /**
         * The width the line may span, in the face's own pixels: its window's
         * width less the lock screen's text margin at either edge, as drawn. The
         * lock screen lays the clock out narrower than that; the column lets
         * the line run past its box ([WideColumn]).
         */
        private fun columnWidth(): Float = (windowWidth() - 2 * EdgeToEdgeClockFace.EDGE_DP * density) / drawnScale

        /** The width of the window the face is shown in: in a preview, not the device's. */
        private fun windowWidth(): Float = (time.rootView.width.takeIf { it > 0 && time.isAttachedToWindow }
            ?: context.resources.displayMetrics.widthPixels).toFloat()

        override fun setWeather(weather: ClockWeather?) {
            date?.setWeather(weather)
        }

        override fun setOption(key: String, step: Int) {
            when (key) {
                PosterOptions.HOUR_KEY -> hourLook = PosterOptions.lookOf(step)
                PosterOptions.MINUTE_KEY -> minuteLook = PosterOptions.lookOf(step)
                PosterOptions.OPACITY_KEY -> return time.setOpacity(PosterOptions.opacityOf(step))
                PosterOptions.HEIGHT_KEY -> heightShare = PosterOptions.axisShareOf(step)
                PosterOptions.WEIGHT_KEY -> weightShare = PosterOptions.axisShareOf(step)
                PosterOptions.DATE_KEY -> return placeDate(PosterOptions.datePlaceOf(step))
                else -> {
                    glass = glass.with(key, step) ?: return
                    return time.setGlass(glass)
                }
            }
            time.setLooks(hourLook, minuteLook)
            time.setCut(heightShare, weightShare)
        }
    }

    private companion object {
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

        val DATE = DateLine.Look(skeleton = "EEEMMMd", sizeToShortSide = 0.045f, weight = 600)

        /** The least change of the scene's scale worth fitting the line again for. */
        const val SCALE_STEP = 0.005f

        /**
         * The small face, above notifications: taller than a Pixel small clock, as its figures are narrow, and
         * starting where SystemUI's date line under it starts ([MarginColumn]).
         */
        const val SMALL_TO_SHORT_SIDE = 0.22f
        const val SMALL_MAX_WIDTH = 0.8f

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
