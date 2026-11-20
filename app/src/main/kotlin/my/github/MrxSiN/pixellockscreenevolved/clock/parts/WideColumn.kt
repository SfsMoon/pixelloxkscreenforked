package my.github.MrxSiN.pixellockscreenevolved.clock.parts

import android.content.Context
import android.view.View
import android.widget.LinearLayout

/**
 * A face's column, as wide and as tall as what it holds even where that is
 * wider than the room its host lays it out in
 * ([my.github.MrxSiN.pixellockscreenevolved.clock.EdgeToEdgeClockFace]): the
 * lock screen's clock element is narrower than the screen. Drawn whole rather
 * than clipped to that room (the depth effect draws the face as one layer,
 * which ends at the face's bounds), and kept centred on it: a host that puts
 * it at its start, as the scene's view holder does, has it moved back by half
 * the overflow.
 */
class WideColumn(context: Context) : LinearLayout(context) {

    /** How far, in pixels, the column is moved right of where it is kept centred, as a person moved it. */
    var shift: Float = 0f
        set(value) {
            if (field == value) return
            field = value
            place()
        }

    init {
        orientation = VERTICAL
        // A rolling digit, or glass's glow, may pass a little beyond the time's own box.
        clipChildren = false
        clipToPadding = false
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // As tall as the time asks too: the scene's clock box is shorter than the largest sizes, and the
        // depth effect's layer, which ends at the face's bounds, would cut the numerals off at its edge.
        val unbounded = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        super.onMeasure(unbounded, unbounded)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        place()
    }

    private fun place() {
        val room = (parent as? View)?.width ?: return
        val centring = if (left == 0 && width > room) (room - width) / 2f else 0f
        if (translationX != centring + shift) translationX = centring + shift
    }
}
