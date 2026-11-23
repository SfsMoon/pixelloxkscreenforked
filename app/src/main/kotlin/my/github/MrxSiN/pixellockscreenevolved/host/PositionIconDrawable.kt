package my.github.MrxSiN.pixellockscreenevolved.host

import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable

/**
 * A small icon of the Position tab's own, drawn here: a drawable of this
 * module's own resources cannot be loaded in Wallpaper & style, which may not
 * see this module's package. It takes the tint it is given, as a vector
 * drawable would.
 *
 * - [Glyph.TAB]: Material Symbols "height" (a double arrow between two bars),
 *   the tab's icon.
 * - [Glyph.GLASS]: Material Symbols "water_drop", the Glass tab's icon.
 * - [Glyph.BELL]: Material Symbols "notifications", the Notifications tab's icon.
 * - [Glyph.DRAG]: a fingertip with an arrow above and below, beside the hint
 *   that the clock is dragged up and down the preview ([PickerDragHint]).
 */
internal class PositionIconDrawable(private val sizePx: Int, glyph: Glyph = Glyph.TAB) : Drawable() {

    enum class Glyph { TAB, DRAG, GLASS, BELL }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val path = Path().apply {
        when (glyph) {
            Glyph.TAB -> {
                // M13,6.99h3L12,3L8,6.99h3v10.02H8L12,21l4,-3.99h-3z, then the two bars.
                moveTo(13f, 6.99f)
                lineTo(16f, 6.99f)
                lineTo(12f, 3f)
                lineTo(8f, 6.99f)
                lineTo(11f, 6.99f)
                lineTo(11f, 17.01f)
                lineTo(8f, 17.01f)
                lineTo(12f, 21f)
                lineTo(16f, 17.01f)
                lineTo(13f, 17.01f)
                close()
                addRect(4f, 0.5f, 20f, 2f, Path.Direction.CW)
                addRect(4f, 22f, 20f, 23.5f, Path.Direction.CW)
            }
            Glyph.GLASS -> {
                // M12,2c-5.33,4.55 -8,8.48 -8,11.8c0,4.98 3.8,8.2 8,8.2s8,-3.22 8,-8.2c0,-3.32 -2.67,-7.25 -8,-11.8z
                moveTo(12f, 2f)
                rCubicTo(-5.33f, 4.55f, -8f, 8.48f, -8f, 11.8f)
                rCubicTo(0f, 4.98f, 3.8f, 8.2f, 8f, 8.2f)
                rCubicTo(4.2f, 0f, 8f, -3.22f, 8f, -8.2f)
                rCubicTo(0f, -3.32f, -2.67f, -7.25f, -8f, -11.8f)
                close()
            }
            Glyph.BELL -> {
                // The bell, after Material Symbols "notifications".
                moveTo(4f, 19f)
                lineTo(4f, 17f)
                lineTo(6f, 17f)
                lineTo(6f, 10f)
                cubicTo(6f, 7.2f, 7.8f, 4.8f, 10.5f, 4.2f)
                lineTo(10.5f, 3.5f)
                cubicTo(10.5f, 2.67f, 11.17f, 2f, 12f, 2f)
                cubicTo(12.83f, 2f, 13.5f, 2.67f, 13.5f, 3.5f)
                lineTo(13.5f, 4.2f)
                cubicTo(16.2f, 4.8f, 18f, 7.2f, 18f, 10f)
                lineTo(18f, 17f)
                lineTo(20f, 17f)
                lineTo(20f, 19f)
                close()
                // The clapper.
                moveTo(12f, 22f)
                cubicTo(10.9f, 22f, 10f, 21.1f, 10f, 20f)
                lineTo(14f, 20f)
                cubicTo(14f, 21.1f, 13.1f, 22f, 12f, 22f)
                close()
            }
            Glyph.DRAG -> {
                fillType = Path.FillType.EVEN_ODD
                // The fingertip, and a ring around it.
                addCircle(12f, 12f, 3f, Path.Direction.CW)
                addCircle(12f, 12f, 6f, Path.Direction.CW)
                addCircle(12f, 12f, 4.5f, Path.Direction.CW)
                // Up and down.
                moveTo(12f, 0.5f)
                lineTo(16f, 4.5f)
                lineTo(8f, 4.5f)
                close()
                moveTo(12f, 23.5f)
                lineTo(8f, 19.5f)
                lineTo(16f, 19.5f)
                close()
            }
        }
    }
    private var tint: ColorStateList? = null

    override fun getIntrinsicWidth(): Int = sizePx

    override fun getIntrinsicHeight(): Int = sizePx

    override fun draw(canvas: Canvas) {
        val unit = bounds.width() / VIEWPORT
        canvas.save()
        canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
        canvas.scale(unit, unit)
        canvas.drawPath(path, paint)
        canvas.restore()
    }

    override fun setTintList(tint: ColorStateList?) {
        this.tint = tint
        applyTint()
    }

    override fun isStateful(): Boolean = tint?.isStateful == true

    override fun onStateChange(state: IntArray): Boolean = applyTint()

    private fun applyTint(): Boolean {
        val color = tint?.getColorForState(state, Color.WHITE) ?: Color.WHITE
        if (paint.color == color) return false
        paint.color = color
        invalidateSelf()
        return true
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    private companion object {
        const val VIEWPORT = 24f
    }
}
