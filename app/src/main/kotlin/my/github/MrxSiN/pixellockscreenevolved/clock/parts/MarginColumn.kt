package my.github.MrxSiN.pixellockscreenevolved.clock.parts

import android.content.Context
import android.view.Gravity
import android.view.ViewTreeObserver
import android.widget.LinearLayout

import my.github.MrxSiN.pixellockscreenevolved.clock.EdgeToEdgeClockFace

/**
 * A small face's column, as wide as the room it is given and its content
 * starting at the lock screen's text margin
 * ([EdgeToEdgeClockFace.EDGE_DP] from the window's edge), where SystemUI starts
 * the date and weather line it writes under a small clock, so the two read as
 * one block, as a Pixel's own small clock does. The lock screen centres a face
 * in its room, and its room may itself start anywhere, so before each draw the
 * column is moved across until its start lands on the margin.
 */
class MarginColumn(context: Context) : LinearLayout(context) {

    private val margin = EdgeToEdgeClockFace.EDGE_DP * context.resources.displayMetrics.density
    private val location = IntArray(2)

    private val align = ViewTreeObserver.OnPreDrawListener {
        getLocationInWindow(location)
        val laidOutAt = location[0] - translationX
        val wanted = margin - laidOutAt
        if (kotlin.math.abs(translationX - wanted) >= 0.5f) translationX = wanted
        true
    }

    init {
        orientation = VERTICAL
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
        clipChildren = false
        clipToPadding = false
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnPreDrawListener(align)
    }

    override fun onDetachedFromWindow() {
        viewTreeObserver.removeOnPreDrawListener(align)
        super.onDetachedFromWindow()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val unbounded = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        super.onMeasure(unbounded, unbounded)
        // As wide as offered, so the lock screen has nothing to centre; never narrower than the time.
        val offered = if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED) 0 else MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(maxOf(offered, measuredWidth), measuredHeight)
    }
}
