package my.github.MrxSiN.pixellockscreenevolved.clock.parts

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
 * Glass that bends and catches the light of the photo behind it, for one run of
 * figures, all of it in one shader ([SHADER]).
 *
 * The figures' outline, blurred, is a height field: flat and high inside a
 * stroke, falling away over its edges ([prepare]); how wide that bevel is comes
 * from the [GlassLook]. Its slope gives every point of the glass a surface
 * normal, and from the normal the shader works out what glass does with light:
 *
 * - it bends the photo behind it, more over the bevel than in the middle, and
 *   each colour a little differently (dispersion);
 * - it shows the photo nearly sharp or frosted, mixed as chosen;
 * - it reflects the photo around it at grazing angles, as glass does by
 *   Fresnel's law, so its edges pick up the photo's own light;
 * - it catches a light from the chosen direction in a sharp specular glint and
 *   a broad sheen (Blinn-Phong), and a softer glint from the opposite side, as
 *   light passing through a rounded stroke catches its far edge;
 * - its side away from the light is a little darker.
 */
internal class LiquidGlass {

    private val shader = RuntimeShader(SHADER)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val blur = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
    private val heightToPath = Matrix()
    private var heights: Bitmap? = null
    private var heightShader: Shader? = null

    /** Whether there is a height field to draw with. */
    val isReady: Boolean get() = heightShader != null

    /**
     * Builds the height field for [path], within [bounds], for figures
     * [numeralHeight] pixels tall, and sets the glass's [look], tinted toward [tint].
     */
    fun prepare(path: Path, bounds: RectF, numeralHeight: Float, tint: Int, look: GlassLook) {
        val sigma = (numeralHeight * (THINNEST_BEVEL + (WIDEST_BEVEL - THINNEST_BEVEL) * look.bevel)).coerceAtLeast(MIN_BEVEL_PX)
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
        blur.maskFilter = BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL)
        canvas.drawPath(path, blur)
        heightToPath.setScale(1f / HEIGHT_SCALE, 1f / HEIGHT_SCALE)
        heightToPath.postTranslate(bounds.left - margin, bounds.top - margin)
        heightShader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply { setLocalMatrix(heightToPath) }
        shader.setFloatUniform("texel", 1f / HEIGHT_SCALE)
        shader.setFloatUniform("bevel", sigma * SLOPE_TO_UNIT)
        shader.setFloatUniform("refraction", numeralHeight * MOST_REFRACTION * look.refraction)
        shader.setFloatUniform("frost", look.frost)
        shader.setFloatUniform("dispersion", look.dispersion * MOST_DISPERSION)
        shader.setFloatUniform("highlights", look.highlights)
        shader.setFloatUniform("light", look.lightX, look.lightY)
        shader.setFloatUniform("tintAmount", look.tint)
        shader.setColorUniform("tint", tint)
    }

    /**
     * Draws [path] as glass over the photo behind it, [sharp] and [frosted],
     * each placed in the canvas's units, [alpha] opaque.
     */
    fun draw(canvas: Canvas, path: Path, sharp: Shader, frosted: Shader, alpha: Float) {
        val heightField = heightShader ?: return
        shader.setInputShader("sharp", sharp)
        shader.setInputShader("frosted", frosted)
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

        /** How wide the bevel is, against the figures' height, from the thinnest Edge to the widest. */
        const val THINNEST_BEVEL = 0.004f
        const val WIDEST_BEVEL = 0.024f
        const val MIN_BEVEL_PX = 1.5f

        /** A Gaussian edge's steepest slope is 0.4 / sigma; this brings it to about 1. */
        const val SLOPE_TO_UNIT = 2.5f

        /** How far the bevel bends the photo at full Refraction, against the figures' height. */
        const val MOST_REFRACTION = 0.07f

        /** How far apart full Dispersion bends the red and the blue, against the green. */
        const val MOST_DISPERSION = 0.25f

        val SHADER = """
            uniform shader sharp;
            uniform shader frosted;
            uniform shader height;
            uniform float texel;
            uniform float bevel;
            uniform float refraction;
            uniform float frost;
            uniform float dispersion;
            uniform float highlights;
            uniform float2 light;
            uniform float tintAmount;
            layout(color) uniform half4 tint;

            float h(float2 p) { return height.eval(p).a; }

            half3 behind(float2 p) {
                return mix(sharp.eval(p).rgb, frosted.eval(p).rgb, half(frost));
            }

            half4 main(float2 p) {
                float2 slope = float2(h(p + float2(texel, 0.0)) - h(p - float2(texel, 0.0)),
                                      h(p + float2(0.0, texel)) - h(p - float2(0.0, texel))) / (2.0 * texel) * bevel;
                // A rounded stroke: steeper toward the edge than the slope of the blur alone.
                float steep = clamp(length(slope), 0.0, 0.999);
                float2 across = steep > 0.0001 ? slope / length(slope) : float2(0.0);
                float tilt = steep / sqrt(1.0 - steep * steep);
                float3 n = normalize(float3(-across * tilt * 1.4, 1.0));

                // Through the glass: bent over the bevel, each colour a little differently.
                float2 bend = n.xy * refraction;
                half3 through = half3(behind(p + bend * (1.0 + dispersion)).r,
                                      behind(p + bend).g,
                                      behind(p + bend * (1.0 - dispersion)).b);

                // Off the glass: the photo around it, reflected more the more grazing the surface.
                float fresnel = 0.04 + 0.96 * pow(1.0 - n.z, 3.0);
                half3 around = frosted.eval(p - n.xy * refraction * 4.0).rgb;
                half3 glass = mix(through, around * 1.35 + 0.08, half(clamp(fresnel * (0.4 + highlights), 0.0, 1.0)));

                // The light: a sharp glint and a broad sheen toward it, a softer glint from the far side.
                float3 toLight = normalize(float3(light, 0.85));
                float3 halfway = normalize(toLight + float3(0.0, 0.0, 1.0));
                float facing = max(dot(n, halfway), 0.0);
                float glint = pow(facing, 90.0) * 1.8 + pow(facing, 12.0) * 0.18;
                float3 farLight = normalize(float3(-light, 0.85));
                float farFacing = max(dot(n, normalize(farLight + float3(0.0, 0.0, 1.0))), 0.0);
                glint += pow(farFacing, 60.0) * 0.6;

                // The side away from the light, a little in shadow.
                float away = max(dot(n.xy, -light), 0.0);
                glass *= half(1.0 - away * 0.35 * highlights);

                glass = mix(glass, tint.rgb, half(tintAmount));
                glass = glass * 1.03 + 0.02 + half(glint * highlights);
                return half4(clamp(glass, 0.0, 1.0), 1.0);
            }
        """.trimIndent()
    }
}
