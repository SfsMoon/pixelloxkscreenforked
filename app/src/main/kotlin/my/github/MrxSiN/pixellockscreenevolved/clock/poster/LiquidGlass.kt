package my.github.MrxSiN.pixellockscreenevolved.clock.poster

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Shader

import kotlin.math.ceil

/**
 * Glass that bends the photo behind it, for one run of figures.
 *
 * The figures' outline, blurred, is a height field: flat and high inside a
 * stroke, falling away over its edges ([prepare]). A shader reads its slope to
 * bend the blurred photo behind the figures as a lens bevel would, more at the
 * edges than in the middle and each colour a little differently, lifts and
 * tints it as frosted glass, and lights the bevel from the top left with a
 * softer glint at the bottom right ([SHADER]).
 */
internal class LiquidGlass {

    private val shader = RuntimeShader(SHADER)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val heightToPath = Matrix()
    private var heights: Bitmap? = null
    private var heightShader: Shader? = null

    /** Whether there is a height field to draw with. */
    val isReady: Boolean get() = heightShader != null

    /**
     * Builds the height field for [path], within [bounds], for figures
     * [numeralHeight] pixels tall, and tints the glass toward [tint].
     */
    fun prepare(path: Path, bounds: RectF, numeralHeight: Float, tint: Int) {
        val sigma = (numeralHeight * BEVEL_TO_HEIGHT).coerceAtLeast(MIN_BEVEL_PX)
        // BlurMaskFilter takes a radius; its sigma is about 0.577 of it, plus a half.
        val radius = ((sigma - 0.5f) / 0.57735f).coerceAtLeast(1f)
        val margin = ceil(sigma * 3f)
        val width = ceil((bounds.width() + 2 * margin) * HEIGHT_SCALE).toInt()
        val height = ceil((bounds.height() + 2 * margin) * HEIGHT_SCALE).toInt()
        if (width <= 0 || height <= 0 || bounds.isEmpty) {
            clear()
            return
        }
        val bitmap = heights?.takeIf { it.width == width && it.height == height }
            ?: Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8).also { heights = it }
        bitmap.eraseColor(Color.TRANSPARENT)
        val canvas = Canvas(bitmap)
        canvas.scale(HEIGHT_SCALE, HEIGHT_SCALE)
        canvas.translate(margin - bounds.left, margin - bounds.top)
        canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            maskFilter = BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL)
        })
        heightToPath.setScale(1f / HEIGHT_SCALE, 1f / HEIGHT_SCALE)
        heightToPath.postTranslate(bounds.left - margin, bounds.top - margin)
        heightShader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply { setLocalMatrix(heightToPath) }
        shader.setFloatUniform("texel", 1f / HEIGHT_SCALE)
        shader.setFloatUniform("bevel", sigma * SLOPE_TO_UNIT)
        shader.setFloatUniform("refraction", numeralHeight * REFRACTION_TO_HEIGHT)
        shader.setColorUniform("tint", tint)
    }

    /** Draws [path] as glass over [backdrop], the photo placed in the canvas's units, [alpha] opaque. */
    fun draw(canvas: Canvas, path: Path, backdrop: Shader, alpha: Float) {
        val heightField = heightShader ?: return
        shader.setInputShader("backdrop", backdrop)
        shader.setInputShader("height", heightField)
        paint.shader = shader
        paint.alpha = (alpha.coerceIn(0f, 1f) * 255).toInt()
        canvas.drawPath(path, paint)
    }

    fun clear() {
        heightShader = null
    }

    private companion object {
        /** The height field is built at half the figures' resolution: it is blurred anyway. */
        const val HEIGHT_SCALE = 0.5f

        /** How wide the bevel is, against the figures' height, and never narrower than a few pixels. */
        const val BEVEL_TO_HEIGHT = 0.011f
        const val MIN_BEVEL_PX = 2f

        /** A Gaussian edge's steepest slope is 0.4 / sigma; this brings it to about 1. */
        const val SLOPE_TO_UNIT = 2.5f

        /** How far the bevel bends the photo, at its steepest, against the figures' height. */
        const val REFRACTION_TO_HEIGHT = 0.03f

        val SHADER = """
            uniform shader backdrop;
            uniform shader height;
            uniform float texel;
            uniform float bevel;
            uniform float refraction;
            layout(color) uniform half4 tint;

            float h(float2 p) { return height.eval(p).a; }

            half4 main(float2 p) {
                float2 slope = float2(h(p + float2(texel, 0.0)) - h(p - float2(texel, 0.0)),
                                      h(p + float2(0.0, texel)) - h(p - float2(0.0, texel))) / (2.0 * texel) * bevel;
                float edge = clamp(length(slope), 0.0, 1.0);
                // Bent outward over the bevel, as the rounded edge of a lens bends what is behind it.
                float2 bend = -slope * refraction;
                half3 photo = half3(backdrop.eval(p + bend * 1.12).r,
                                    backdrop.eval(p + bend).g,
                                    backdrop.eval(p + bend * 0.88).b);
                // Frosted: lifted, a little toward the clock's colour, a little brighter over the bevel.
                half3 glass = mix(photo, tint.rgb, 0.16) * 1.06 + 0.035 + half(edge) * 0.05;
                float3 normal = normalize(float3(-slope * 1.6, 1.0));
                float3 eye = float3(0.0, 0.0, 1.0);
                float key = pow(max(dot(reflect(-normalize(float3(-0.55, -0.8, 0.9)), normal), eye), 0.0), 22.0);
                float fill = pow(max(dot(reflect(-normalize(float3(0.6, 0.8, 0.9)), normal), eye), 0.0), 34.0);
                glass += half3(key * 0.85 + fill * 0.35);
                return half4(clamp(glass, 0.0, 1.0), 1.0);
            }
        """.trimIndent()
    }
}
