package my.github.MrxSiN.pixellockscreenevolved.clock.poster

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PosterOptionsTest {

    @Test
    fun eachFontStartsInItsOwnLooksAndFullyOpaque() {
        PosterFont.entries.forEach { font ->
            val (hour, minute, opacity) = PosterOptions.of(font).take(3)
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
        assertEquals("'HGHT' 1000.0, 'wght' 1000.0", PosterFont.TALL.settings(height = 1f, weight = 1f, doze = 0f))
        assertEquals("'HGHT' 1000.0, 'wght' 220.0", PosterFont.TALL.settings(height = 1f, weight = 1f, doze = 1f))
        assertNotEquals(PosterFont.TALL.id, PosterFont.BOLD.id)
    }

    @Test
    fun aWeightAlreadyLighterThanTheDozeCutIsNotMadeHeavierWhileDozing() {
        assertEquals("'HGHT' 100.0, 'wght' 100.0", PosterFont.TALL.settings(height = 0f, weight = 0f, doze = 1f))
    }

    @Test
    fun axisSlidersStartWhereEachFontIsDesignedAndSpanItsRange() {
        val bold = PosterOptions.of(PosterFont.BOLD).associateBy { it.key }
        assertEquals(5, bold.getValue(PosterOptions.HEIGHT_KEY).defaultStep)
        assertEquals("0", bold.getValue(PosterOptions.HEIGHT_KEY).values.first())
        assertEquals("1000", bold.getValue(PosterOptions.HEIGHT_KEY).values.last())
        assertEquals(0.5f, PosterOptions.axisShareOf(5), 1e-6f)
    }
}
