package my.github.MrxSiN.pixellockscreenevolved.clock.poster

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PosterOptionsTest {

    @Test
    fun eachFontStartsInItsOwnLooksAndFullyOpaque() {
        PosterFont.entries.forEach { font ->
            val (hour, minute, opacity) = PosterOptions.of(font)
            assertEquals(font.hourLook, PosterOptions.lookOf(hour.defaultStep))
            assertEquals(font.minuteLook, PosterOptions.lookOf(minute.defaultStep))
            assertEquals(1f, PosterOptions.opacityOf(opacity.defaultStep), 0f)
        }
    }

    @Test
    fun bothFontsShareTheirKeysSoMovingBetweenThemKeepsTheChoices() {
        assertEquals(PosterOptions.of(PosterFont.TALL).map { it.key }, PosterOptions.of(PosterFont.BOLD).map { it.key })
    }

    @Test
    fun lookStepsAreStoredInDeclarationOrder() {
        assertEquals(GlyphLook.FILLED, PosterOptions.lookOf(0))
        assertEquals(GlyphLook.OUTLINE, PosterOptions.lookOf(1))
        assertEquals(GlyphLook.GLASS, PosterOptions.lookOf(2))
        assertEquals(GlyphLook.GLASS, PosterOptions.lookOf(9))
    }

    @Test
    fun opacityRunsFromATenthToFull() {
        assertEquals(0.1f, PosterOptions.opacityOf(0), 1e-6f)
        assertEquals(1f, PosterOptions.opacityOf(99), 1e-6f)
    }

    @Test
    fun dozeThinsTheWeightAndKeepsTheHeight() {
        val awake = PosterFont.TALL.settings(0f)
        val dozing = PosterFont.TALL.settings(1f)
        assertEquals("'HGHT' 1000.0, 'wght' 1000.0", awake)
        assertEquals("'HGHT' 1000.0, 'wght' 220.0", dozing)
        assertNotEquals(PosterFont.TALL.id, PosterFont.BOLD.id)
    }
}
