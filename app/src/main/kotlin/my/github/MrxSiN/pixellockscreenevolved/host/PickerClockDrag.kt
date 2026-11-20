package my.github.MrxSiN.pixellockscreenevolved.host

import android.annotation.SuppressLint
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo

import kotlin.math.abs

import my.github.MrxSiN.pixellockscreenevolved.clock.ClockFrame

/**
 * Drags the large clock itself in Wallpaper & style's preview while the clock
 * sheet's Position tab is open ([PickerClockState.isEditingPosition]): the
 * real face follows the finger one to one ([ClockLayout.dragTo]), within the
 * band the lock screen allows, stretching a little and no further past its
 * ends. Near an anchor ([ClockPosition.ANCHORS]: the top, the quarters, the
 * bottom) it snaps to it, with the system's segment tick and a guide across
 * the preview where the clock's middle lands. Let go, it settles on the
 * spatial spring where it was left, and the position is chosen
 * ([PickerClockState.choosePosition]) until Apply or Back.
 *
 * A face a person can frame by hand ([ClockFaceAdapter.isFramed]) also moves
 * across, snapping to the middle with a guide down the preview, and shows a
 * frame around its time with a handle at each corner ([FrameOverlay]): a corner
 * dragged stretches the time wider or narrower, taller or shorter, following
 * the finger one to one while the opposite corner stays where it is, as a
 * picture is cropped; near its natural proportions it snaps to them with a tick
 * ([ClockFrame]). What was framed is chosen ([PickerClockState.chooseFrame])
 * as the finger lifts.
 *
 * Nothing can be moved down while the position is adaptive, though a framed
 * face can still be moved across and stretched. TalkBack and switch access move
 * the clock from anchor to anchor with Move up and Move down.
 */
internal class PickerClockDrag(
    private val face: ClockFaceAdapter,
    private val state: PickerClockState,
) {

    private val view = face.view
    private val layout = face.clockLayout
    private val guide = Guide()
    private val frameOverlay = FrameOverlay()

    private var startX = 0f
    private var startY = 0f
    private var startTop = 0f
    private var startFrame = ClockFrame.NATURAL
    private var scale = 1f
    private var snapped = -1
    private var centred = false
    private var naturalX = false
    private var naturalY = false
    private var dragging = false
    private var movesDown = false

    /** The corner a stretch is dragging, as -1 or 1 across and down, or 0 while the whole clock is moved. */
    private var cornerX = 0
    private var cornerY = 0

    // Scratch for the preview's drawn size and the time's box, read as a drag starts and before each draw.
    private val parentBounds = RectF()
    private val timeBounds = RectF()
    private val startBounds = RectF()

    private val editing: Boolean get() = state.isEditingPosition && layout.isMovable && layout.faceHeight > 0f

    private val canMoveDown: Boolean get() = editing && !state.shownPosition().adaptive

    private val canFrame: Boolean get() = editing && face.isFramed

    @SuppressLint("ClickableViewAccessibility")
    fun install() {
        view.setOnTouchListener { _, event -> onTouch(event) }
        view.accessibilityDelegate = Accessibility()
        BeforeEachDraw.follow(view) { frameOverlay.follow() }
    }

    private fun onTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!canMoveDown && !canFrame) return false
                val parent = view.parent as? View ?: return false
                parent.parent?.requestDisallowInterceptTouchEvent(true)
                ViewBounds.inWindow(parent, parentBounds)
                scale = if (parent.height > 0) (parentBounds.height() / parent.height).takeIf { it > 0f } ?: 1f else 1f
                startX = event.rawX
                startY = event.rawY
                startTop = view.top + view.translationY
                startFrame = face.frame
                ViewBounds.inAncestor(view, face.timeView, startBounds)
                pickCorner(event.x, event.y)
                movesDown = canMoveDown
                snapped = -1
                centred = false
                naturalX = startFrame.scaleX == 1f
                naturalY = startFrame.scaleY == 1f
                dragging = true
                return true
            }
            MotionEvent.ACTION_MOVE -> if (dragging) {
                val dx = (event.rawX - startX) / scale
                val dy = (event.rawY - startY) / scale
                when {
                    cornerX != 0 -> stretch(dx, dy)
                    else -> {
                        if (movesDown) layout.dragTo(follow(startTop + dy))
                        if (canFrame) face.setFrame(movedAcross(dx))
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (dragging) {
                dragging = false
                guide.hide()
                if (canFrame && face.frame != startFrame) state.chooseFrame(face.frame)
                if (movesDown) {
                    val share = if (snapped >= 0) ClockPosition.ANCHORS[snapped] else shareOf(view.top + view.translationY).coerceIn(ClockPosition.HIGHEST, 1f)
                    state.choosePosition(ClockPosition(share))
                    layout.endDrag()
                    view.announceForAccessibility(ClockPosition.nameOf(share))
                }
                cornerX = 0
                cornerY = 0
            }
        }
        return dragging
    }

    /** Takes the corner of the time's frame under [x], [y], in the face's own pixels, or none. */
    private fun pickCorner(x: Float, y: Float) {
        cornerX = 0
        cornerY = 0
        if (!canFrame || startBounds.isEmpty) return
        val reach = HANDLE_REACH_DP * view.resources.displayMetrics.density / scale
        val nearLeft = abs(x - startBounds.left) < reach
        val nearRight = abs(x - startBounds.right) < reach
        val nearTop = abs(y - startBounds.top) < reach
        val nearBottom = abs(y - startBounds.bottom) < reach
        if ((nearLeft || nearRight) && (nearTop || nearBottom)) {
            cornerX = if (nearRight) 1 else -1
            cornerY = if (nearBottom) 1 else -1
            view.performHapticFeedback(HapticFeedbackConstants.GESTURE_START)
        }
    }

    /**
     * Stretches the time for a corner dragged [dx] across and [dy] down, the
     * opposite corner held where it stood: the time is kept centred in its
     * column, so it is moved across by half what it grew; it hangs from its
     * top, so a top corner moves the whole clock down by what it shrank.
     */
    private fun stretch(dx: Float, dy: Float) {
        val width = startBounds.width().coerceAtLeast(1f)
        val height = startBounds.height().coerceAtLeast(1f)
        val tolerance = NATURAL_SNAP_DP * view.resources.displayMetrics.density / scale
        var scaleX = (startFrame.scaleX * (width + cornerX * dx) / width).coerceIn(ClockFrame.MIN_SCALE, ClockFrame.MAX_SCALE)
        var scaleY = (startFrame.scaleY * (height + cornerY * dy) / height).coerceIn(ClockFrame.MIN_SCALE, ClockFrame.MAX_SCALE)
        // Near its natural proportions, a side snaps to them, with a tick as it does.
        val nearX = abs(scaleX - 1f) * width / startFrame.scaleX < tolerance
        val nearY = abs(scaleY - 1f) * height / startFrame.scaleY < tolerance
        if ((nearX && !naturalX) || (nearY && !naturalY)) view.performHapticFeedback(HapticFeedbackConstants.SEGMENT_TICK)
        naturalX = nearX
        naturalY = nearY
        if (nearX) scaleX = 1f
        if (nearY) scaleY = 1f
        val grownX = width * (scaleX / startFrame.scaleX - 1f)
        val grownY = height * (scaleY / startFrame.scaleY - 1f)
        val screen = view.rootView.width.takeIf { it > 0 }?.toFloat() ?: 1f
        face.setFrame(
            startFrame.copy(scaleX = scaleX, scaleY = scaleY, shiftX = startFrame.shiftX + cornerX * grownX / 2f / screen).clamped(),
        )
        if (movesDown) layout.dragTo(if (cornerY < 0) startTop - grownY else startTop)
    }

    /** The frame for the clock moved [dx] across, snapped to the middle near it. */
    private fun movedAcross(dx: Float): ClockFrame {
        val screen = view.rootView.width.takeIf { it > 0 }?.toFloat() ?: return startFrame
        val wanted = startFrame.shiftX + dx / screen
        val near = abs(wanted) * screen < SNAP_DP * view.resources.displayMetrics.density / scale
        if (near != centred) {
            centred = near
            if (near) view.performHapticFeedback(HapticFeedbackConstants.SEGMENT_TICK)
        }
        if (near) guide.showDown((view.parent as? View)?.width?.div(2f) ?: 0f) else guide.hideDown()
        return startFrame.copy(shiftX = if (near) 0f else wanted).clamped()
    }

    /** Where the face's top goes for a finger asking for [wanted]: snapped to an anchor near it, held back past the ends. */
    private fun follow(wanted: Float): Float {
        // Above the band the clock goes on to the top of the screen, as far as a person drags it.
        val top = topOf(ClockPosition.HIGHEST)
        val bottom = topOf(1f)
        val snapDistance = SNAP_DP * view.resources.displayMetrics.density
        var nearest = -1
        for (i in ClockPosition.ANCHORS.indices) {
            if (abs(topOf(ClockPosition.ANCHORS[i]) - wanted) < snapDistance) nearest = i
        }
        if (nearest != snapped) {
            snapped = nearest
            if (nearest >= 0) view.performHapticFeedback(HapticFeedbackConstants.SEGMENT_TICK)
        }
        if (nearest >= 0) {
            val anchorTop = topOf(ClockPosition.ANCHORS[nearest])
            guide.showAcross(anchorTop + layout.faceHeight / 2f)
            return anchorTop
        }
        guide.hideAcross()
        return when {
            wanted < top -> top - (top - wanted) * OVERSTRETCH
            wanted > bottom -> bottom + (wanted - bottom) * OVERSTRETCH
            else -> wanted
        }
    }

    private fun topOf(share: Float): Float = ClockPosition.topAt(share, layout.bandTop, layout.bandBottom, layout.faceHeight)

    private fun shareOf(top: Float): Float = ClockPosition.shareAt(top, layout.bandTop, layout.bandBottom, layout.faceHeight)

    /** Moves the clock one anchor up or down, for those who cannot drag. */
    private fun step(by: Int): Boolean {
        if (!canMoveDown) return false
        val share = state.shownPosition().share
        val anchors = ClockPosition.ANCHORS
        var index = anchors.indices.minByOrNull { abs(anchors[it] - share) } ?: return false
        index = (index + by).coerceIn(0, anchors.lastIndex)
        state.choosePosition(ClockPosition(anchors[index]))
        view.announceForAccessibility(ClockPosition.nameOf(anchors[index]))
        return true
    }

    private inner class Accessibility : View.AccessibilityDelegate() {
        override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
            super.onInitializeAccessibilityNodeInfo(host, info)
            if (!canMoveDown) return
            info.addAction(AccessibilityNodeInfo.AccessibilityAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.id, MOVE_UP))
            info.addAction(AccessibilityNodeInfo.AccessibilityAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.id, MOVE_DOWN))
        }

        override fun performAccessibilityAction(host: View, action: Int, args: Bundle?): Boolean = when (action) {
            AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.id -> step(-1)
            AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.id -> step(1)
            else -> super.performAccessibilityAction(host, action, args)
        }
    }

    /**
     * Lines over the preview where the clock snaps: across it where the clock's
     * middle stands on an anchor, and down it where the clock is centred, in
     * the system's accent at the strength Material gives a divider, drawn in
     * the preview's overlay only while the clock is snapped.
     */
    private inner class Guide : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = view.context.getColor(android.R.color.system_accent1_300)
            alpha = GUIDE_ALPHA
            strokeWidth = GUIDE_DP * view.resources.displayMetrics.density
        }
        private var y = Float.NaN
        private var x = Float.NaN
        private var shown = false

        fun showAcross(at: Float) {
            y = at
            update()
        }

        fun hideAcross() {
            y = Float.NaN
            update()
        }

        fun showDown(at: Float) {
            x = at
            update()
        }

        fun hideDown() {
            x = Float.NaN
            update()
        }

        fun hide() {
            x = Float.NaN
            y = Float.NaN
            update()
        }

        private fun update() {
            val parent = view.parent as? View ?: return
            val wanted = !x.isNaN() || !y.isNaN()
            if (wanted && !shown) {
                shown = true
                setBounds(0, 0, parent.width, parent.height)
                parent.overlay.add(this)
            } else if (!wanted && shown) {
                shown = false
                parent.overlay.remove(this)
            }
            invalidateSelf()
        }

        override fun draw(canvas: Canvas) {
            if (!y.isNaN()) {
                val inset = bounds.width() * GUIDE_INSET
                canvas.drawLine(bounds.left + inset, y, bounds.right - inset, y, paint)
            }
            if (!x.isNaN()) {
                val inset = bounds.height() * GUIDE_INSET
                canvas.drawLine(x, bounds.top + inset, x, bounds.bottom - inset, paint)
            }
        }

        override fun setAlpha(alpha: Int) = Unit

        override fun setColorFilter(colorFilter: ColorFilter?) = Unit

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    /**
     * The frame around the time of a face a person can frame by hand, while the
     * Position tab is open: a faint line around it and a bracket at each
     * corner, as other systems mark a lock screen clock that can be stretched.
     * Drawn in the preview's overlay, and followed before each draw.
     */
    private inner class FrameOverlay : Drawable() {
        private val density = view.resources.displayMetrics.density
        private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = android.graphics.Color.WHITE
            alpha = FRAME_ALPHA
            strokeWidth = FRAME_DP * density
        }
        private val bracket = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = android.graphics.Color.WHITE
            strokeWidth = BRACKET_DP * density
            strokeCap = Paint.Cap.ROUND
        }
        private val shown = RectF()

        /** The view whose overlay holds the frame, while it is shown. */
        private var addedTo: View? = null

        /** Shows the frame where the time stands now, or takes it away while it cannot be framed. */
        fun follow() {
            val parent = view.parent as? View
            if (parent == null || !canFrame || !view.isShown) {
                addedTo?.overlay?.remove(this)
                addedTo = null
                return
            }
            if (addedTo !== parent) {
                addedTo?.overlay?.remove(this)
                parent.overlay.add(this)
                addedTo = parent
            }
            if (bounds.width() != parent.width || bounds.height() != parent.height) setBounds(0, 0, parent.width, parent.height)
            ViewBounds.inAncestor(parent, face.timeView, timeBounds, beforeDraw = true)
            val pad = FRAME_PAD_DP * density
            timeBounds.inset(-pad, -pad)
            if (timeBounds != shown) {
                shown.set(timeBounds)
                invalidateSelf()
            }
        }

        override fun draw(canvas: Canvas) {
            if (shown.isEmpty) return
            val radius = BRACKET_RADIUS_DP * density
            canvas.drawRoundRect(shown, radius, radius, line)
            val arm = minOf(BRACKET_ARM_DP * density, shown.width() / 3f, shown.height() / 3f)
            for (right in listOf(false, true)) {
                for (bottom in listOf(false, true)) {
                    val x = if (right) shown.right else shown.left
                    val y = if (bottom) shown.bottom else shown.top
                    val sx = if (right) -1f else 1f
                    val sy = if (bottom) -1f else 1f
                    canvas.drawLine(x, y + sy * radius, x, y + sy * arm, bracket)
                    canvas.drawLine(x + sx * radius, y, x + sx * arm, y, bracket)
                    canvas.drawArc(
                        minOf(x, x + sx * 2 * radius), minOf(y, y + sy * 2 * radius),
                        maxOf(x, x + sx * 2 * radius), maxOf(y, y + sy * 2 * radius),
                        when { !right && !bottom -> 180f; right && !bottom -> 270f; right && bottom -> 0f; else -> 90f },
                        90f, false, bracket,
                    )
                }
            }
        }

        override fun setAlpha(alpha: Int) = Unit

        override fun setColorFilter(colorFilter: ColorFilter?) = Unit

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    private companion object {
        /** How near an anchor, in dp of the preview, the clock snaps to it. */
        const val SNAP_DP = 12f

        /** How far the clock follows a finger past the band's ends: a rubber band, not a wall. */
        const val OVERSTRETCH = 0.25f

        /** How near a corner of the time's frame, in dp on screen, a finger takes it to stretch the time. */
        const val HANDLE_REACH_DP = 36f

        /** How near its natural width or height, in dp on screen, a stretched side snaps to it. */
        const val NATURAL_SNAP_DP = 8f

        const val FRAME_DP = 1f
        const val FRAME_ALPHA = 110
        const val FRAME_PAD_DP = 6f
        const val BRACKET_DP = 3.5f
        const val BRACKET_ARM_DP = 22f
        const val BRACKET_RADIUS_DP = 10f

        const val GUIDE_DP = 1.5f
        const val GUIDE_ALPHA = 160
        const val GUIDE_INSET = 0.06f
        const val MOVE_UP = "Move clock up"
        const val MOVE_DOWN = "Move clock down"
    }
}
