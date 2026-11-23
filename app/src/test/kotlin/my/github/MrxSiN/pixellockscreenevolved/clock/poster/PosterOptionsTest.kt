package my.github.MrxSiN.pixellockscreenevolved.clock.poster

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

import my.github.MrxSiN.pixellockscreenevolved.clock.parts.GlassLook

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

class GlassLookTest {

    @Test
    fun everyGlassSliderStartsAtTheDefaultLook() {
        val start = GlassLook.options.fold(GlassLook()) { look, option -> requireNotNull(look.with(option.key, option.defaultStep)) }
        assertEquals(GlassLook(), start)
    }

    @Test
    fun glassSlidersAreShownInTheGlassTab() {
        assert(GlassLook.options.all { it.tab == my.github.MrxSiN.pixellockscreenevolved.clock.ClockOption.Tab.GLASS })
    }

    @Test
    fun theLightTurnsFromTheRightClockwise() {
        val fromBelow = requireNotNull(GlassLook().with(GlassLook.LIGHT_KEY, 3))
        assertEquals(90f, fromBelow.lightDegrees, 0f)
        assertEquals(0f, fromBelow.lightX, 1e-6f)
        assertEquals(1f, fromBelow.lightY, 1e-6f)
    }

    @Test
    fun aKeyNotOfGlassChangesNothing() {
        assertEquals(null, GlassLook().with(PosterOptions.HOUR_KEY, 1))
    }
}

class SurfaceLookTest {

    private val clock = GlassLook()
    private val surfaces = my.github.MrxSiN.pixellockscreenevolved.clock.parts.SurfaceLook.Surface.entries

    @Test
    fun everySurfaceSliderStartsAtTheDefaultLook() {
        for (surface in surfaces) {
            val start = surface.options.fold(my.github.MrxSiN.pixellockscreenevolved.clock.parts.SurfaceLook(surface)) { look, option ->
                requireNotNull(look.with(option.key, option.defaultStep))
            }
            assertEquals(my.github.MrxSiN.pixellockscreenevolved.clock.parts.SurfaceLook(surface), start)
        }
    }

    @Test
    fun notificationKeysAreTheOnesStoredBefore() {
        val keys = my.github.MrxSiN.pixellockscreenevolved.clock.parts.SurfaceLook.Surface.NOTIFICATIONS.options.map { it.key }
        assert("PIXEL_LOCK_SCREEN_EVOLVED_NOTIFICATION_GLASS" in keys)
        assert("PIXEL_LOCK_SCREEN_EVOLVED_NOTIFICATION_OUTLINE_WIDTH" in keys)
    }

    @Test
    fun onlyTheFingerprintMayBeClear() {
        val notifications = my.github.MrxSiN.pixellockscreenevolved.clock.parts.SurfaceLook.Surface.NOTIFICATIONS
        val fingerprint = my.github.MrxSiN.pixellockscreenevolved.clock.parts.SurfaceLook.Surface.FINGERPRINT
        assert(my.github.MrxSiN.pixellockscreenevolved.clock.parts.SurfaceLook.Mode.CLEAR !in notifications.modes)
        val pastTheLast = requireNotNull(my.github.MrxSiN.pixellockscreenevolved.clock.parts.SurfaceLook(notifications).with(notifications.modeKey, 2)).beside(clock)
        assert(requireNotNull(pastTheLast).look != null)
        val clear = requireNotNull(my.github.MrxSiN.pixellockscreenevolved.clock.parts.SurfaceLook(fingerprint).with(fingerprint.modeKey, 2)).beside(clock)
        assertEquals(null, requireNotNull(clear).look)
    }

    @Test
    fun stockSurfacesHaveNoGlass() {
        val stock = my.github.MrxSiN.pixellockscreenevolved.clock.parts.SurfaceLook(
            my.github.MrxSiN.pixellockscreenevolved.clock.parts.SurfaceLook.Surface.NOTIFICATIONS,
            mode = my.github.MrxSiN.pixellockscreenevolved.clock.parts.SurfaceLook.Mode.STOCK,
        )
        assertEquals(null, stock.beside(clock))
    }

    @Test
    fun glassAlwaysKeepsSomeOfTheSurfacesOwnColour() {
        val glass = requireNotNull(
            my.github.MrxSiN.pixellockscreenevolved.clock.parts.SurfaceLook(my.github.MrxSiN.pixellockscreenevolved.clock.parts.SurfaceLook.Surface.FINGERPRINT)
                .beside(clock.copy(tint = 0f)),
        )
        assert(requireNotNull(glass.look).tint >= 0.3f)
    }
}
