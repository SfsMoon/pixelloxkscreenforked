package my.github.MrxSiN.pixellockscreenevolved.host

import android.content.res.Configuration
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import android.view.ViewTreeObserver

import my.github.MrxSiN.pixellockscreenevolved.clock.Backdrop
import my.github.MrxSiN.pixellockscreenevolved.clock.parts.GlassLook
import my.github.MrxSiN.pixellockscreenevolved.clock.parts.LiquidGlass
import my.github.MrxSiN.pixellockscreenevolved.clock.parts.SurfaceGlass

/**
 * Draws one of the lock screen's surfaces, as a notification card's
 * background or the disc behind the fingerprint's mark, as a [SurfaceGlass]
 * says: glass through the photo behind ([LiquidGlass]), or clear, with the
 * line around it chosen. The surface is a rounded rectangle in the units it is
 * drawn in; its glass's height field is built again only as its shape or look
 * changes, and [view], which draws it, is drawn again before any frame it has
 * moved in, so the photo through the glass stays where the photo is.
 */
internal class GlassSurfacePainter(private val view: View) {

    private val liquid = LiquidGlass()
    private val path = Path()
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
    }
    private val shaped = RectF()
    private var shapedRadii: FloatArray? = null
    private var shapedLook: GlassLook? = null
    private var shapedTint = 0
    private var shapeKnown = false
    private var placedFor: Backdrop? = null
    private var sharp: BitmapShader? = null
    private var frosted: BitmapShader? = null
    private val sharpToCanvas = Matrix()
    private val frostedToCanvas = Matrix()
    private val drawnAt = Matrix()
    private val standsAt = Matrix()
    private var followsPhoto = false

    init {
        view.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (followsPhoto) {
                    standsAt.reset()
                    view.transformMatrixToGlobal(standsAt)
                    if (standsAt != drawnAt) view.invalidate()
                }
                return true
            }
        })
    }

    /**
     * Draws the surface, [bounds] with corners [radii] in [canvas]'s units, as
     * [glass] says, through [backdrop]; [toCanvas] maps [view]'s own pixels to
     * the canvas's units where they differ. [referencePx] is the size the glass
     * is shaped for: its bevel and how far it bends what is behind. False where
     * it cannot be drawn and SystemUI's own should be.
     */
    fun draw(
        canvas: Canvas,
        bounds: RectF,
        radii: FloatArray?,
        glass: SurfaceGlass,
        backdrop: Backdrop?,
        referencePx: Float,
        toCanvas: Matrix? = null,
    ): Boolean {
        val look = glass.look
        val tint = surfaceColour()
        if (!shapeKnown || bounds != shaped || !radii.contentEquals(shapedRadii)) {
            shaped.set(bounds)
            shapedRadii = radii?.copyOf()
            path.reset()
            path.addRoundRect(bounds, radii ?: FloatArray(CORNERS), Path.Direction.CW)
            shapeKnown = true
            shapedLook = null
        }
        if (look != null) {
            val behind = backdrop ?: return false
            if (look != shapedLook || tint != shapedTint || !liquid.isReady) {
                shapedLook = look
                shapedTint = tint
                liquid.prepare(path, bounds, referencePx, tint, look)
            }
            if (!place(behind, toCanvas)) return false
            liquid.draw(canvas, path, requireNotNull(sharp), requireNotNull(frosted), 1f)
        } else {
            followsPhoto = false
        }
        if (glass.outline > 0f) {
            rim.alpha = (glass.outline * OPAQUE).toInt()
            rim.strokeWidth = glass.outlineWidthDp * view.resources.displayMetrics.density / (toCanvas?.mapRadius(1f) ?: 1f)
            canvas.drawPath(path, rim)
        }
        return true
    }

    /** Places [backdrop] in the canvas's units, as it stands behind [view] now. */
    private fun place(backdrop: Backdrop, toCanvas: Matrix?): Boolean {
        if (backdrop !== placedFor) {
            placedFor = backdrop
            sharp = BitmapShader(backdrop.picture, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            frosted = BitmapShader(backdrop.frosted, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        drawnAt.reset()
        view.transformMatrixToGlobal(drawnAt)
        if (!backdrop.place(view, sharpToCanvas)) return false
        toCanvas?.let(sharpToCanvas::postConcat)
        requireNotNull(sharp).setLocalMatrix(sharpToCanvas)
        frostedToCanvas.setScale(backdrop.picture.width.toFloat() / backdrop.frosted.width, backdrop.picture.height.toFloat() / backdrop.frosted.height)
        frostedToCanvas.postConcat(sharpToCanvas)
        requireNotNull(frosted).setLocalMatrix(frostedToCanvas)
        followsPhoto = true
        return true
    }

    /** The colour a surface is tinted toward: a dark one's in the dark theme, a light one's otherwise. */
    private fun surfaceColour(): Int {
        val night = view.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        return if (night) DARK else LIGHT
    }

    private companion object {
        const val CORNERS = 8
        const val OPAQUE = 255
        val DARK = Color.rgb(24, 24, 28)
        val LIGHT = Color.rgb(246, 246, 250)
    }
}
