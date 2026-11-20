package my.github.MrxSiN.pixellockscreenevolved.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoxBlurTest {

    @Test
    fun aFlatPictureStaysFlat() {
        val pixels = IntArray(12 * 7) { 0xFF336699.toInt() }
        BoxBlur.blur(pixels, 12, 7, radius = 3)
        assertArrayEquals(IntArray(12 * 7) { 0xFF336699.toInt() }, pixels)
    }

    @Test
    fun aSharpEdgeIsSoftenedSymmetrically() {
        val width = 20
        val pixels = IntArray(width) { if (it < width / 2) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
        BoxBlur.blur(pixels, width, 1, radius = 2)
        val blue = pixels.map { it and 0xFF }
        assertEquals(0, blue.first())
        assertEquals(255, blue.last())
        assertTrue(blue[width / 2 - 1] in 1..254)
        assertTrue(blue.zipWithNext().all { (a, b) -> a <= b })
        assertTrue(pixels.all { it ushr 24 == 0xFF })
    }

    @Test
    fun aRadiusBelowOneLeavesThePictureAlone() {
        val pixels = intArrayOf(1, 2, 3, 4)
        BoxBlur.blur(pixels, 2, 2, radius = 0)
        assertArrayEquals(intArrayOf(1, 2, 3, 4), pixels)
    }
}
