package my.github.MrxSiN.pixellockscreenevolved.clock.poster

import my.github.MrxSiN.pixellockscreenevolved.clock.ClockOption

/**
 * How a Poster clock draws its hours ([GlyphLook]), its minutes, and how
 * opaque its time is: the choices Wallpaper & style shows for it. Both Poster
 * styles store them under the same keys, so moving between the two keeps them.
 * Kept free of Android so the steps can be unit tested.
 */
object PosterOptions {

    /** Axis keys. Renaming one silently resets that choice on every device. */
    const val HOUR_KEY = "PIXEL_LOCK_SCREEN_EVOLVED_HOUR_LOOK"
    const val MINUTE_KEY = "PIXEL_LOCK_SCREEN_EVOLVED_MINUTE_LOOK"
    const val OPACITY_KEY = "PIXEL_LOCK_SCREEN_EVOLVED_OPACITY"

    /** Opacity at each step, in percent, the faintest first. */
    private val OPACITY_PERCENT = (1..10).map { it * 10 }

    /** The options of a style in [font], each starting where that font's design does. */
    fun of(font: PosterFont): List<ClockOption> = listOf(
        look(HOUR_KEY, "Hours", font.hourLook),
        look(MINUTE_KEY, "Minutes", font.minuteLook),
        ClockOption(OPACITY_KEY, "Opacity", OPACITY_PERCENT.map { "$it%" }, OPACITY_PERCENT.lastIndex),
    )

    /** The look stored at [step]; a step past the last is the last. */
    fun lookOf(step: Int): GlyphLook = GlyphLook.entries[step.coerceIn(0, GlyphLook.entries.lastIndex)]

    /** The opacity, from 0 to 1, stored at [step]. */
    fun opacityOf(step: Int): Float = OPACITY_PERCENT[step.coerceIn(0, OPACITY_PERCENT.lastIndex)] / 100f

    private fun look(key: String, label: String, default: GlyphLook) =
        ClockOption(key, label, GlyphLook.entries.map { it.title }, default.ordinal)
}

/**
 * How a run of figures is drawn. Stored by position in this list, so a new
 * look goes at the end.
 */
enum class GlyphLook(val title: String) {
    /** Solid ink. */
    FILLED("Solid"),

    /** The figures' edges alone, a thin line of ink. */
    OUTLINE("Outline"),

    /** Frosted, translucent figures with lit edges, as glass catches the light. */
    GLASS("Glass"),
}
