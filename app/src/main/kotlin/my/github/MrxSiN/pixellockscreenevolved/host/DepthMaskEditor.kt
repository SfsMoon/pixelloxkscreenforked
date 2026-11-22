package my.github.MrxSiN.pixellockscreenevolved.host

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView

import my.github.MrxSiN.pixellockscreenevolved.core.Logger

/**
 * Refines the depth effect's subject by hand: the lock screen photo, its
 * subject lit and the rest dimmed, painted onto with a finger to add to the
 * subject or erase from it, in a window of SystemUI's own over whatever is
 * open, as Wallpaper & style's Depth effect page asks ([DepthRefineRequest]).
 *
 * One finger paints, with a soft brush of the size chosen; two fingers zoom and
 * move the photo, and a second finger landing mid-stroke takes the stroke back.
 * Undo steps back a stroke at a time, Reset goes back to the [original] mask,
 * as the model found it or as last saved. Save hands the mask to [onSave] and
 * closes; Cancel or Back closes without it.
 */
internal class DepthMaskEditor(
    context: Context,
    private val photo: Bitmap,
    private val original: Bitmap,
    private val logger: Logger,
    private val onSave: (Bitmap) -> Unit,
) {

    /**
     * A context of its own for the editor's window type: the one handed over may be a window context
     * of another type, as the wallpaper's is, which the window manager refuses to add a dialog through.
     */
    @Suppress("DEPRECATION")
    private val windowContext: Context = (context.applicationContext ?: context).let { app ->
        val display = context.display ?: app.getSystemService(android.hardware.display.DisplayManager::class.java)
            .getDisplay(android.view.Display.DEFAULT_DISPLAY)
        app.createWindowContext(display, WINDOW_TYPE, null)
    }
    private val context = ContextThemeWrapper(windowContext, android.R.style.Theme_DeviceDefault)
    private val windowManager = windowContext.getSystemService(WindowManager::class.java)
    private val density = context.resources.displayMetrics.density
    private val accent = context.getColor(android.R.color.system_accent1_200)

    /** The mask as it is being painted: alpha only, as large as the stored one. */
    private val mask: Bitmap = original.copy(Bitmap.Config.ALPHA_8, true)
    private val maskCanvas = Canvas(mask)
    private val undo = ArrayDeque<Bitmap>()

    private var adding = true
    private var brushDp = DEFAULT_BRUSH_DP
    private var root: View? = null

    private val sheet = MaskView()
    private lateinit var addButton: TextView
    private lateinit var eraseButton: TextView
    private lateinit var undoButton: TextView

    fun show() {
        if (root != null) return
        val view = layout()
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WINDOW_TYPE,
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.OPAQUE,
        ).apply {
            title = TITLE
            fitInsetsTypes = 0
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        runCatching { windowManager.addView(view, params) }
            .onSuccess { root = view }
            .onFailure { logger.warn("The subject editor could not be shown", it) }
    }

    private fun close() {
        val view = root ?: return
        root = null
        runCatching { windowManager.removeView(view) }
    }

    private fun save() {
        val saved = mask.copy(Bitmap.Config.ALPHA_8, false)
        close()
        onSave(saved)
    }

    @SuppressLint("SetTextI18n")
    private fun layout(): View {
        val frame = FrameLayout(context).apply {
            setBackgroundColor(Color.BLACK)
            isFocusableInTouchMode = true
            setOnKeyListener { _, code, event ->
                if (code == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) close()
                code == KeyEvent.KEYCODE_BACK
            }
        }
        frame.addView(sheet, FrameLayout.LayoutParams(MATCH, MATCH))

        val top = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(button("Cancel") { close() })
            addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
            addView(TextView(context).apply {
                text = TITLE
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            })
            addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
            addView(button("Save", filled = true) { save() })
        }
        frame.addView(top, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.TOP))

        addButton = button("Add") { choose(adding = true) }
        eraseButton = button("Erase") { choose(adding = false) }
        undoButton = button("Undo") { stepBack() }
        val tools = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(addButton)
            addView(eraseButton)
            addView(undoButton)
            addView(button("Reset") { reset() })
        }
        val size = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(context).apply {
                text = "Brush"
                setTextColor(Color.WHITE)
            })
            addView(SeekBar(context).apply {
                max = (MAX_BRUSH_DP - MIN_BRUSH_DP).toInt()
                progress = (brushDp - MIN_BRUSH_DP).toInt()
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(bar: SeekBar, value: Int, fromUser: Boolean) {
                        brushDp = MIN_BRUSH_DP + value
                        sheet.showBrush()
                    }

                    override fun onStartTrackingTouch(bar: SeekBar) = Unit

                    override fun onStopTrackingTouch(bar: SeekBar) = sheet.hideBrush()
                })
            }, LinearLayout.LayoutParams(0, WRAP, 1f))
        }
        val bottom = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(PANEL)
                cornerRadius = dp(28f)
            }
            val pad = dp(16f).toInt()
            setPadding(pad, pad, pad, pad)
            addView(tools)
            addView(size, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(8f).toInt() })
            addView(TextView(context).apply {
                text = "One finger paints · two fingers zoom and move"
                setTextColor(HINT)
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            }, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(4f).toInt() })
        }
        frame.addView(bottom, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM).apply {
            val side = dp(12f).toInt()
            setMargins(side, 0, side, side)
        })

        // Clear of the status and navigation bars, which this window draws under.
        frame.setOnApplyWindowInsetsListener { _, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            val pad = dp(12f).toInt()
            top.setPadding(pad, bars.top + pad, pad, pad)
            (bottom.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin = bars.bottom + pad
            bottom.requestLayout()
            sheet.setInsets(bars.top + dp(64f).toInt(), bars.bottom + dp(190f).toInt())
            insets
        }
        frame.post { frame.requestFocus() }
        choose(adding = true)
        return frame
    }

    private fun button(label: String, filled: Boolean = false, onClick: () -> Unit): TextView = TextView(context).apply {
        text = label
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        gravity = Gravity.CENTER
        val horizontal = dp(18f).toInt()
        val vertical = dp(10f).toInt()
        setPadding(horizontal, vertical, horizontal, vertical)
        layoutParams = LinearLayout.LayoutParams(WRAP, WRAP).apply { setMargins(dp(4f).toInt(), 0, dp(4f).toInt(), 0) }
        paintButton(this, filled)
        setOnClickListener { onClick() }
    }

    private fun paintButton(button: TextView, selected: Boolean) {
        button.background = GradientDrawable().apply {
            setColor(if (selected) accent else BUTTON)
            cornerRadius = dp(20f)
        }
        button.setTextColor(if (selected) Color.BLACK else Color.WHITE)
    }

    private fun choose(adding: Boolean) {
        this.adding = adding
        paintButton(addButton, adding)
        paintButton(eraseButton, !adding)
        refreshUndo()
    }

    private fun refreshUndo() {
        undoButton.alpha = if (undo.isEmpty()) DISABLED else 1f
    }

    /** Keeps the mask as it is now, to step back to. */
    private fun remember() {
        undo.addLast(mask.copy(Bitmap.Config.ALPHA_8, false))
        while (undo.size > MAX_UNDO) undo.removeFirst()
        refreshUndo()
    }

    private fun stepBack() {
        val previous = undo.removeLastOrNull() ?: return
        replaceMask(previous)
        refreshUndo()
    }

    private fun reset() {
        remember()
        replaceMask(original)
    }

    private fun replaceMask(with: Bitmap) {
        maskCanvas.drawBitmap(with, null, RectF(0f, 0f, mask.width.toFloat(), mask.height.toFloat()), REPLACE)
        sheet.invalidate()
    }

    private fun dp(value: Float): Float = value * density

    /**
     * The photo with its subject, and the finger painting on it. The photo is
     * fitted between the bars, then zoomed and moved as two fingers ask.
     */
    @SuppressLint("ViewConstructor")
    private inner class MaskView : View(context) {

        private val fit = Matrix()
        private val zoom = Matrix()
        private val photoToView = Matrix()
        private val viewToMask = Matrix()
        private val maskToPhoto = Matrix().apply { setScale(photo.width.toFloat() / mask.width, photo.height.toFloat() / mask.height) }
        private val photoBounds = RectF(0f, 0f, photo.width.toFloat(), photo.height.toFloat())
        private var insetTop = 0
        private var insetBottom = 0

        private val dimmed = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setScale(DIM, DIM, DIM, 1f) })
        }
        private val coverage = Paint(Paint.FILTER_BITMAP_FLAG).apply { color = Color.BLACK }
        private val litPhoto = Paint(Paint.FILTER_BITMAP_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN) }
        private val tint = Paint(Paint.FILTER_BITMAP_FLAG).apply { color = accent; alpha = TINT_ALPHA }
        private val brush = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = Color.BLACK
        }
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.WHITE
            strokeWidth = dp(1.5f)
        }

        private var painting = false
        private var transforming = false
        private val last = FloatArray(2)
        private val point = FloatArray(2)
        private var ringX = Float.NaN
        private var ringY = Float.NaN
        private var focusX = 0f
        private var focusY = 0f

        private val scaler = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                zoom.postScale(detector.scaleFactor, detector.scaleFactor, detector.focusX, detector.focusY)
                keepZoomInRange()
                invalidate()
                return true
            }
        })

        fun setInsets(top: Int, bottom: Int) {
            insetTop = top
            insetBottom = bottom
            refit()
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) = refit()

        private fun refit() {
            val room = RectF(0f, insetTop.toFloat(), width.toFloat(), (height - insetBottom).toFloat())
            if (room.width() <= 0f || room.height() <= 0f) return
            fit.setRectToRect(photoBounds, room, Matrix.ScaleToFit.CENTER)
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            photoToView.set(fit)
            photoToView.postConcat(zoom)
            canvas.save()
            canvas.concat(photoToView)
            canvas.drawBitmap(photo, 0f, 0f, dimmed)
            // The subject at full brightness: the photo kept only where the mask covers it.
            val layer = canvas.saveLayer(photoBounds, null)
            canvas.drawBitmap(mask, maskToPhoto, coverage)
            canvas.drawBitmap(photo, 0f, 0f, litPhoto)
            canvas.restoreToCount(layer)
            canvas.drawBitmap(mask, maskToPhoto, tint)
            canvas.restore()
            if (!ringX.isNaN()) canvas.drawCircle(ringX, ringY, dp(brushDp) / 2f, ring)
        }

        fun showBrush() {
            ringX = width / 2f
            ringY = (insetTop + height - insetBottom) / 2f
            invalidate()
        }

        fun hideBrush() {
            ringX = Float.NaN
            invalidate()
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            scaler.onTouchEvent(event)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    painting = true
                    transforming = false
                    remember()
                    toMask(event.x, event.y, last)
                    paintTo(last[0], last[1])
                    ring(event.x, event.y)
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    // A second finger means zooming or moving, not painting: the stroke just begun is taken back.
                    if (painting) {
                        painting = false
                        stepBack()
                    }
                    transforming = true
                    focus(event)
                    hideBrush()
                }
                MotionEvent.ACTION_MOVE -> when {
                    transforming && event.pointerCount >= 2 -> {
                        val previousX = focusX
                        val previousY = focusY
                        focus(event)
                        zoom.postTranslate(focusX - previousX, focusY - previousY)
                        invalidate()
                    }
                    painting -> {
                        for (i in 0 until event.historySize) stroke(event.getHistoricalX(i), event.getHistoricalY(i))
                        stroke(event.x, event.y)
                        ring(event.x, event.y)
                    }
                }
                MotionEvent.ACTION_POINTER_UP -> focus(event, leaving = event.actionIndex)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    painting = false
                    transforming = false
                    hideBrush()
                }
            }
            return true
        }

        private fun stroke(x: Float, y: Float) {
            toMask(x, y, point)
            paintLine(last[0], last[1], point[0], point[1])
            last[0] = point[0]
            last[1] = point[1]
        }

        private fun paintTo(x: Float, y: Float) = paintLine(x, y, x, y)

        /** Paints a soft round stroke from one point of the mask to another, adding or erasing. */
        private fun paintLine(fromX: Float, fromY: Float, toX: Float, toY: Float) {
            val width = dp(brushDp) / viewToMaskScale()
            brush.strokeWidth = width
            brush.maskFilter = BlurMaskFilter((width * SOFTNESS).coerceAtLeast(1f), BlurMaskFilter.Blur.NORMAL)
            brush.xfermode = if (adding) null else ERASE
            maskCanvas.drawLine(fromX, fromY, toX, toY, brush)
            invalidate()
        }

        /** How many view pixels one mask pixel covers now. */
        private fun viewToMaskScale(): Float {
            photoToView.set(fit)
            photoToView.postConcat(zoom)
            val toView = Matrix(maskToPhoto).apply { postConcat(photoToView) }
            return toView.mapRadius(1f).coerceAtLeast(0.0001f)
        }

        private fun toMask(x: Float, y: Float, out: FloatArray) {
            photoToView.set(fit)
            photoToView.postConcat(zoom)
            val toView = Matrix(maskToPhoto).apply { postConcat(photoToView) }
            toView.invert(viewToMask)
            out[0] = x
            out[1] = y
            viewToMask.mapPoints(out)
        }

        private fun ring(x: Float, y: Float) {
            ringX = x
            ringY = y
        }

        /** The middle of the fingers down, leaving out the one at [leaving], which is lifting. */
        private fun focus(event: MotionEvent, leaving: Int = -1) {
            var sumX = 0f
            var sumY = 0f
            var count = 0
            for (i in 0 until event.pointerCount) {
                if (i == leaving) continue
                sumX += event.getX(i)
                sumY += event.getY(i)
                count++
            }
            if (count == 0) return
            focusX = sumX / count
            focusY = sumY / count
        }

        /** Keeps the zoom from the photo fitted to eight times that. */
        private fun keepZoomInRange() {
            val scale = zoom.mapRadius(1f)
            if (scale < 1f) zoom.postScale(1f / scale, 1f / scale, width / 2f, height / 2f)
            if (scale > MAX_ZOOM) zoom.postScale(MAX_ZOOM / scale, MAX_ZOOM / scale, focusX, focusY)
        }
    }

    private companion object {
        const val TITLE = "Refine subject"

        /** A system dialog window stands over whatever app is open, Wallpaper & style here; SystemUI may add one. */
        @Suppress("DEPRECATION")
        const val WINDOW_TYPE = WindowManager.LayoutParams.TYPE_SYSTEM_DIALOG
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

        const val MIN_BRUSH_DP = 8f
        const val MAX_BRUSH_DP = 160f
        const val DEFAULT_BRUSH_DP = 40f

        /** How soft the brush's edge is, against its width. */
        const val SOFTNESS = 0.12f

        const val MAX_UNDO = 20
        const val MAX_ZOOM = 8f

        /** How dark what is not the subject is drawn, and how strongly the subject is tinted. */
        const val DIM = 0.38f
        const val TINT_ALPHA = 70

        const val DISABLED = 0.4f
        const val PANEL = 0xE6202124.toInt()
        const val BUTTON = 0x33FFFFFF
        const val HINT = 0x99FFFFFF.toInt()

        val ERASE = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
        val REPLACE = Paint(Paint.FILTER_BITMAP_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC) }
    }
}
