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
import my.github.MrxSiN.pixellockscreenevolved.clock.NotificationGlassFace
import my.github.MrxSiN.pixellockscreenevolved.clock.parts.NotificationGlass
import my.github.MrxSiN.pixellockscreenevolved.clock.parts.NotificationLook
import my.github.MrxSiN.pixellockscreenevolved.clock.ResizableClockFace
import my.github.MrxSiN.pixellockscreenevolved.clock.SmartspaceClockFace
import my.github.MrxSiN.pixellockscreenevolved.clock.WeatherClockFace
import my.github.MrxSiN.pixellockscreenevolved.clock.parts.ClockThumbnail
import my.github.MrxSiN.pixellockscreenevolved.clock.parts.DateLine
import my.github.MrxSiN.pixellockscreenevolved.clock.parts.TimeParts
import my.github.MrxSiN.pixellockscreenevolved.clock.parts.WideColumn
import my.github.MrxSiN.pixellockscreenevolved.clock.parts.GlassLook

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

    /** With notifications the clock stays itself, smaller, rather than swapping for a small one. */
    override val smallFaceFollowsLarge: Boolean = true
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
        ClockFace, ResizableClockFace, DozingClockFace, WeatherClockFace, ConfigurableClockFace, EdgeToEdgeClockFace, BackdropClockFace, FramedClockFace, SmartspaceClockFace, NotificationGlassFace {

        private val context = context
        private val shortSide = context.resources.displayMetrics.let { minOf(it.widthPixels, it.heightPixels) }.toFloat()
        private val time = PosterTimeView(context, font, typeface)
        private val date = DateLine(context, DATE)

        private val density = context.resources.displayMetrics.density
        private val screenHeight = context.resources.displayMetrics.let { maxOf(it.widthPixels, it.heightPixels) }.toFloat()

        /** How much height the date and the gap to it take, as laid out, or as they will before they are. */
        private fun dateRoom(): Float = date.view.height.takeIf { it > 0 }?.toFloat()?.plus(shortSide * DATE_GAP)
            ?: shortSide * (DATE.sizeToShortSide * DATE_LINE_HEIGHT + DATE_GAP)

        private var hourLook = font.hourLook
        private var minuteLook = font.minuteLook
        private var heightShare = font.heights.initialShare
        private var weightShare = font.weights.initialShare
        private var frame = ClockFrame.NATURAL
        private var glass = GlassLook()
        private var notifications = NotificationLook()
        private var shownBackdrop: Backdrop? = null

        override val notificationGlass: NotificationGlass? get() = notifications.glassBeside(glass)

        override val backdrop: Backdrop? get() = shownBackdrop
        private var datePlace = DatePlace.ABOVE
        private var shownSize = 0f

        /** How much smaller than laid out the host draws the face ([setDrawnScale]). */
        private var drawnScale = 1f

        /**
         * The face's column, which a person may move across. The small face, shown above notifications,
         * is the large one made smaller, as other systems keep their big clock with notifications rather
         * than swapping it for a small one: its date, look, frame and place across are the large face's.
         */
        private val column = WideColumn(context)

        override val view: View = column.apply {
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = ViewGroup.LayoutParams(WRAP, WRAP)
            addView(date.view, LinearLayout.LayoutParams(WRAP, WRAP).apply { gravity = Gravity.CENTER_HORIZONTAL })
            addView(time, LinearLayout.LayoutParams(WRAP, WRAP).apply { gravity = Gravity.CENTER_HORIZONTAL })
        }
        override val timeView: View get() = time
        override val drawsDate: Boolean = true

        override var showsSmartspace: Boolean = false
            private set

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
            date.refresh()
        }

        override fun setColor(color: Int) {
            time.setColor(color)
            date.setColor(color)
        }

        override fun setSize(size: Float) {
            shownSize = size
            val sized = shortSide * (font.smallest + (font.largest - font.smallest) * size.coerceIn(0f, 1f))
            time.fit(sized, maxWidth = columnWidth())
            time.setScale(frame.scaleX, frame.scaleY)
            // Above notifications the face is the large one, its height alone pressed to half the screen where it
            // stands taller, so the notifications have room below it.
            if (this.size == FaceSize.SMALL) {
                val room = screenHeight * SMALL_SCREEN_SHARE - if (datePlace == DatePlace.HIDDEN) 0f else dateRoom()
                val tall = time.figuresTall
                if (tall > room && room > 0f) time.setScale(frame.scaleX, frame.scaleY * room / tall)
            }
            column.shift = frame.shiftX * windowWidth() / drawnScale
            placeDate(datePlace)
        }

        override fun setDoze(fraction: Float) = time.setDoze(fraction)

        override fun setDrawnScale(scale: Float) {
            if (scale <= 0f || abs(drawnScale - scale) < SCALE_STEP) return
            drawnScale = scale
            setSize(shownSize)
        }

        override fun setBackdrop(backdrop: Backdrop?) {
            shownBackdrop = backdrop
            time.setBackdrop(backdrop)
        }

        /** Writes the date above the time, below it, or not at all. */
        private fun placeDate(place: DatePlace) {
            val line = date.view
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

        /** Stretches and moves the face as a person framed it by hand; the small face follows the large one. */
        override fun setFrame(frame: ClockFrame) {
            if (frame == this.frame) return
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
            date.setWeather(weather)
        }

        override fun setOption(key: String, step: Int) {
            when (key) {
                PosterOptions.HOUR_KEY -> hourLook = PosterOptions.lookOf(step)
                PosterOptions.MINUTE_KEY -> minuteLook = PosterOptions.lookOf(step)
                PosterOptions.OPACITY_KEY -> return time.setOpacity(PosterOptions.opacityOf(step))
                PosterOptions.HEIGHT_KEY -> heightShare = PosterOptions.axisShareOf(step)
                PosterOptions.WEIGHT_KEY -> weightShare = PosterOptions.axisShareOf(step)
                PosterOptions.DATE_KEY -> return placeDate(PosterOptions.datePlaceOf(step))
                PosterOptions.SMARTSPACE_KEY -> {
                    showsSmartspace = PosterOptions.showsSmartspaceAt(step)
                    // The lock screen places the card before its next draw, which this asks for.
                    return view.invalidate()
                }
                else -> {
                    notifications.with(key, step)?.let {
                        notifications = it
                        // The notifications beside the face are drawn again before the next frame.
                        return view.invalidate()
                    }
                    glass = glass.with(key, step) ?: return
                    view.invalidate()
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

        /** A line of text is about this much taller than its text size. */
        const val DATE_LINE_HEIGHT = 1.3f

        /** The most of the screen's height the small face, above notifications, may stand. */
        const val SMALL_SCREEN_SHARE = 0.5f

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
