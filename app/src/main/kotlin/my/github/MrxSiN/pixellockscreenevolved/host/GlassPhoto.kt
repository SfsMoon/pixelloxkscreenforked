package my.github.MrxSiN.pixellockscreenevolved.host

import android.graphics.Bitmap

import kotlin.math.max
import kotlin.math.roundToInt

import my.github.MrxSiN.pixellockscreenevolved.core.BoxBlur

/**
 * The wallpaper as glass shows it ([my.github.MrxSiN.pixellockscreenevolved.clock.Backdrop]):
 * small and nearly sharp, and smaller still and blurred, as frosted glass
 * softens what is behind it. Glass mixes the two by how frosted it is.
 */
internal object GlassPhoto {

    class Pictures(val sharp: Bitmap, val frosted: Bitmap)

    /** The longest side of each picture: both are drawn through figures, and bent, so neither needs the photo's own size. */
    private const val SHARP_SIDE = 720f
    private const val FROSTED_SIDE = 300f

    /** The blur of each at its size; the frosted one comes to several dozen pixels on screen. */
    private const val SHARP_BLUR = 1
    private const val FROSTED_BLUR = 5

    /** [photo] at the sharp picture's size or smaller, in memory the CPU can read, or null where it cannot be read. */
    fun shrink(photo: Bitmap): Bitmap? = scaled(photo, SHARP_SIDE)

    /** Both pictures from [small], as [shrink] made it. Slow: off the main thread. */
    fun pictures(small: Bitmap): Pictures {
        val sharp = blurred(small, SHARP_BLUR)
        val frosted = blurred(scaled(small, FROSTED_SIDE) ?: small, FROSTED_BLUR)
        return Pictures(sharp, frosted)
    }

    private fun scaled(photo: Bitmap, side: Float): Bitmap? = runCatching {
        if (photo.isRecycled) return null
        val scale = minOf(1f, side / max(photo.width, photo.height))
        val small = Bitmap.createScaledBitmap(
            photo,
            (photo.width * scale).roundToInt().coerceAtLeast(1),
            (photo.height * scale).roundToInt().coerceAtLeast(1),
            true,
        )
        if (small.config == Bitmap.Config.HARDWARE) small.copy(Bitmap.Config.ARGB_8888, false) else small
    }.getOrNull()

    private fun blurred(picture: Bitmap, radius: Int): Bitmap {
        val pixels = IntArray(picture.width * picture.height)
        picture.getPixels(pixels, 0, picture.width, 0, 0, picture.width, picture.height)
        BoxBlur.blur(pixels, picture.width, picture.height, radius)
        return Bitmap.createBitmap(pixels, picture.width, picture.height, Bitmap.Config.ARGB_8888)
    }
}
