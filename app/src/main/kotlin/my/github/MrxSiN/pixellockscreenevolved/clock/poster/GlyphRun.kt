package my.github.MrxSiN.pixellockscreenevolved.clock.poster

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader

import my.github.MrxSiN.pixellockscreenevolved.clock.parts.DozeInk

/**
 * One run of figures (the hours, or the minutes) as outlines, and the paint
 * that draws them in their [GlyphLook].
 *
 * Glass shows the photo behind it where there is one ([LiquidGlass]), with a
 * crisp rim and a soft shadow under it, so the figures lift off the photo.
 * Where there is none (a live wallpaper, a preview of another photo, the
 * picker's own list) it is drawn from the figures' outline alone, as frosted
 * glass reads on a photo: a faint tinted body, brighter at the top, and a soft
 * glow inside the edges, lit from the top left and catching light again at the
 * bottom right. On the always-on display all but the rim fades with the doze,
 * so few pixels stay lit.
 *
 * Gradients depend on where the figures stand and their colour, so they are
 * built when either changes ([prepare]), not on every draw.
 */
internal class GlyphRun {

    /** The figures' outline, in the time view's own coordinates before its insets. */
    val path = Path()
    val bounds = RectF()
    var look = GlyphLook.FILLED

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
    }
    private val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
    }
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
    }
    private val sheen = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val shadowPath = Path()
    private val liquid = LiquidGlass()

    private var shadowDrop = 0f
    private var sheenBottom = 0f

    /**
     * Settles every paint for the figures as they stand now, drawn in [color],
     * [numeralHeight] pixels tall, at least [hairline] pixels wide for a line.
     * The glass's height field is built again only with [reshaped] figures.
     */
    fun prepare(color: Int, numeralHeight: Float, hairline: Float, reshaped: Boolean) {
        path.computeBounds(bounds, true)
        val h = numeralHeight.coerceAtLeast(1f)
        fill.color = color
        line.color = color
        line.strokeWidth = maxOf(hairline, h * OUTLINE_TO_HEIGHT)

        body.shader = LinearGradient(
            0f, bounds.top, 0f, bounds.bottom,
            DozeInk.withAlpha(color, BODY_TOP), DozeInk.withAlpha(color, BODY_BOTTOM), Shader.TileMode.CLAMP,
        )
        glow.strokeWidth = h * GLOW_TO_HEIGHT
        glow.maskFilter = BlurMaskFilter((h * GLOW_BLUR_TO_HEIGHT).coerceAtLeast(1f), BlurMaskFilter.Blur.NORMAL)
        glow.shader = diagonal(color, GLOW_LIT, GLOW_MID, GLOW_FAR)
        rim.strokeWidth = maxOf(hairline, h * RIM_TO_HEIGHT)
        rim.shader = diagonal(color, RIM_LIT, RIM_MID, RIM_FAR)
        sheenBottom = bounds.top + bounds.height() * SHEEN_SHARE
        sheen.shader = LinearGradient(
            0f, bounds.top, 0f, sheenBottom,
            DozeInk.withAlpha(Color.WHITE, SHEEN), Color.TRANSPARENT, Shader.TileMode.CLAMP,
        )
        shadow.color = Color.BLACK
        shadow.maskFilter = BlurMaskFilter((h * SHADOW_BLUR_TO_HEIGHT).coerceAtLeast(1f), BlurMaskFilter.Blur.NORMAL)
        shadowDrop = h * SHADOW_DROP_TO_HEIGHT
        shadowPath.set(path)
        shadowPath.offset(0f, shadowDrop)
        if (look != GlyphLook.GLASS) liquid.clear() else if (reshaped || !liquid.isReady) liquid.prepare(path, bounds, h, color)
    }

    /**
     * Draws the figures, [doze] of the way into the always-on display, glass
     * through [backdrop], the photo behind placed in the canvas's units, where there is one.
     */
    fun draw(canvas: Canvas, doze: Float, backdrop: Shader?) {
        when (look) {
            GlyphLook.FILLED -> canvas.drawPath(path, fill)
            GlyphLook.OUTLINE -> canvas.drawPath(path, line)
            GlyphLook.GLASS -> drawGlass(canvas, 1f - doze.coerceIn(0f, 1f), backdrop)
        }
    }

    /** Glass, all but its rim at [presence] of their strength. */
    private fun drawGlass(canvas: Canvas, presence: Float, backdrop: Shader?) {
        if (presence > 0f) {
            shadow.alpha = (SHADOW * presence * 255).toInt()
            canvas.drawPath(shadowPath, shadow)
        }
        if (presence > 0f && backdrop != null && liquid.isReady) {
            liquid.draw(canvas, path, backdrop, presence)
        } else if (presence > 0f) {
            body.alpha = (presence * 255).toInt()
            canvas.drawPath(path, body)
            canvas.save()
            canvas.clipPath(path)
            glow.alpha = body.alpha
            canvas.drawPath(path, glow)
            sheen.alpha = body.alpha
            canvas.drawRect(bounds.left, bounds.top, bounds.right, sheenBottom, sheen)
            canvas.restore()
        }
        rim.alpha = if (backdrop != null && liquid.isReady) ((LIQUID_RIM + (1f - LIQUID_RIM) * (1f - presence)) * 255).toInt() else 255
        canvas.drawPath(path, rim)
    }

    /** Light across the figures from their top left to their bottom right. */
    private fun diagonal(color: Int, lit: Float, mid: Float, far: Float): Shader {
        val bright = blend(color, Color.WHITE, LIGHT_TO_WHITE)
        return LinearGradient(
            bounds.left, bounds.top, bounds.right, bounds.bottom,
            intArrayOf(DozeInk.withAlpha(bright, lit), DozeInk.withAlpha(bright, mid), DozeInk.withAlpha(bright, far)),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP,
        )
    }

    private companion object {
        const val OUTLINE_TO_HEIGHT = 0.006f

        /** The body's opacity at the figures' top and bottom. */
        const val BODY_TOP = 0.34f
        const val BODY_BOTTOM = 0.12f

        /** The glow inside the edges: half its stroke falls inside the figures, softened by the blur. */
        const val GLOW_TO_HEIGHT = 0.05f
        const val GLOW_BLUR_TO_HEIGHT = 0.018f
        const val GLOW_LIT = 0.75f
        const val GLOW_MID = 0.12f
        const val GLOW_FAR = 0.45f

        const val RIM_TO_HEIGHT = 0.0045f
        const val RIM_LIT = 0.95f
        const val RIM_MID = 0.35f
        const val RIM_FAR = 0.75f

        /** A gloss over the top of the figures. */
        const val SHEEN_SHARE = 0.35f
        const val SHEEN = 0.16f

        const val SHADOW = 0.22f

        /** Over the photo the bevel already catches the light, so the rim is fainter until the doze leaves it alone. */
        const val LIQUID_RIM = 0.55f
        const val SHADOW_BLUR_TO_HEIGHT = 0.02f
        const val SHADOW_DROP_TO_HEIGHT = 0.012f

        /** How far toward white the light on glass is from the face's colour. */
        const val LIGHT_TO_WHITE = 0.6f

        fun blend(from: Int, to: Int, t: Float): Int = Color.argb(
            Color.alpha(from) + ((Color.alpha(to) - Color.alpha(from)) * t).toInt(),
            Color.red(from) + ((Color.red(to) - Color.red(from)) * t).toInt(),
            Color.green(from) + ((Color.green(to) - Color.green(from)) * t).toInt(),
            Color.blue(from) + ((Color.blue(to) - Color.blue(from)) * t).toInt(),
        )
    }
}
