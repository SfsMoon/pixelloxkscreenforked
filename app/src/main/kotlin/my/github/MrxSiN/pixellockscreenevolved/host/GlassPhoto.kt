package my.github.MrxSiN.pixellockscreenevolved.host

import android.graphics.Bitmap

import kotlin.math.max
import kotlin.math.roundToInt

import my.github.MrxSiN.pixellockscreenevolved.core.BoxBlur

/** The wallpaper as glass shows it: small, and blurred as frosted glass softens what is behind it. */
internal object GlassPhoto {

    /** The longest side the blurred photo is kept at: it is blurred anyway, and drawn through small figures. */
    private const val LONGEST_SIDE = 360f

    /** The blur at that size, which comes to several dozen pixels on screen. */
    private const val BLUR_RADIUS = 4

    /** [photo] at [LONGEST_SIDE] or smaller, in memory the CPU can read, or null where it cannot be read. */
    fun shrink(photo: Bitmap): Bitmap? = runCatching {
        if (photo.isRecycled) return null
        val scale = minOf(1f, LONGEST_SIDE / max(photo.width, photo.height))
        val small = Bitmap.createScaledBitmap(
            photo,
            (photo.width * scale).roundToInt().coerceAtLeast(1),
            (photo.height * scale).roundToInt().coerceAtLeast(1),
            true,
        )
        if (small.config == Bitmap.Config.HARDWARE) small.copy(Bitmap.Config.ARGB_8888, false) else small
    }.getOrNull()

    /** A blurred copy of [picture], which must be readable by the CPU. Slow: off the main thread. */
    fun blur(picture: Bitmap): Bitmap {
        val pixels = IntArray(picture.width * picture.height)
        picture.getPixels(pixels, 0, picture.width, 0, 0, picture.width, picture.height)
        BoxBlur.blur(pixels, picture.width, picture.height, BLUR_RADIUS)
        return Bitmap.createBitmap(pixels, picture.width, picture.height, Bitmap.Config.ARGB_8888)
    }
}
