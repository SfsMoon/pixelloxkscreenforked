package my.github.MrxSiN.pixellockscreenevolved.core

/**
 * Blurs ARGB pixels in place: three box passes across and three down, which
 * together come close to a Gaussian of about [radius] pixels. Each pass keeps a
 * running sum, so its cost does not grow with the radius. Edges repeat their
 * last pixel. Kept free of Android so it can be unit tested.
 */
object BoxBlur {

    private const val PASSES = 3

    fun blur(pixels: IntArray, width: Int, height: Int, radius: Int) {
        require(pixels.size == width * height) { "${pixels.size} pixels for $width x $height" }
        if (radius < 1 || width < 1 || height < 1) return
        val line = IntArray(maxOf(width, height))
        repeat(PASSES) {
            for (y in 0 until height) pass(pixels, y * width, 1, width, radius, line)
            for (x in 0 until width) pass(pixels, x, width, height, radius, line)
        }
    }

    /** One box pass over the [count] pixels starting at [start], [stride] apart. */
    private fun pass(pixels: IntArray, start: Int, stride: Int, count: Int, radius: Int, line: IntArray) {
        for (i in 0 until count) line[i] = pixels[start + i * stride]
        val window = 2 * radius + 1
        var a = 0
        var r = 0
        var g = 0
        var b = 0
        for (k in -radius..radius) {
            val p = line[k.coerceIn(0, count - 1)]
            a += p ushr 24
            r += (p shr 16) and 0xFF
            g += (p shr 8) and 0xFF
            b += p and 0xFF
        }
        for (i in 0 until count) {
            pixels[start + i * stride] = ((a / window) shl 24) or ((r / window) shl 16) or ((g / window) shl 8) or (b / window)
            val leaving = line[(i - radius).coerceIn(0, count - 1)]
            val entering = line[(i + radius + 1).coerceIn(0, count - 1)]
            a += (entering ushr 24) - (leaving ushr 24)
            r += ((entering shr 16) and 0xFF) - ((leaving shr 16) and 0xFF)
            g += ((entering shr 8) and 0xFF) - ((leaving shr 8) and 0xFF)
            b += (entering and 0xFF) - (leaving and 0xFF)
        }
    }
}
