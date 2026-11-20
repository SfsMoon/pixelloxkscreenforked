package my.github.MrxSiN.pixellockscreenevolved.clock.poster

import kotlin.math.roundToInt

import my.github.MrxSiN.pixellockscreenevolved.clock.ClockOption

/**
 * What Wallpaper & style lets a person choose for a Poster clock: how its hours
 * and its minutes are drawn ([GlyphLook]), how opaque the time is, and how its
 * figures are stretched, through the font's height and weight axes and across.
 * Both Poster styles store them under the same keys, so moving between the two
 * keeps them. Kept free of Android so the steps can be unit tested.
 */
object PosterOptions {

    /** Axis keys. Renaming one silently resets that choice on every device. */
    const val HOUR_KEY = "PIXEL_LOCK_SCREEN_EVOLVED_HOUR_LOOK"
    const val MINUTE_KEY = "PIXEL_LOCK_SCREEN_EVOLVED_MINUTE_LOOK"
    const val OPACITY_KEY = "PIXEL_LOCK_SCREEN_EVOLVED_OPACITY"
    const val HEIGHT_KEY = "PIXEL_LOCK_SCREEN_EVOLVED_FIGURE_HEIGHT"
    const val WEIGHT_KEY = "PIXEL_LOCK_SCREEN_EVOLVED_FIGURE_WEIGHT"
    const val STRETCH_KEY = "PIXEL_LOCK_SCREEN_EVOLVED_FIGURE_STRETCH"

    /** Opacity at each step, in percent, the faintest first. */
    private val OPACITY_PERCENT = (1..10).map { it * 10 }

    /** Stops on the height and weight sliders: eleven, from the axis's lowest to its highest. */
    private const val AXIS_STEPS = 11

    /** How much wider than designed the figures are drawn at each stop, the narrowest first. */
    private val STRETCHES = (0 until 11).map { 0.6f + it * 0.1f }
    private val NATURAL_STRETCH = STRETCHES.indexOfFirst { it >= 1f - 1e-4f }

    /** The options of a style in [font], each starting where that font's design does. */
    fun of(font: PosterFont): List<ClockOption> = listOf(
        look(HOUR_KEY, "Hours", font.hourLook),
        look(MINUTE_KEY, "Minutes", font.minuteLook),
        ClockOption(OPACITY_KEY, "Opacity", OPACITY_PERCENT.map { "$it%" }, OPACITY_PERCENT.lastIndex),
        axis(HEIGHT_KEY, "Height", font.heights),
        axis(WEIGHT_KEY, "Weight", font.weights),
        ClockOption(STRETCH_KEY, "Width", STRETCHES.map { "${(it * 100).roundToInt()}%" }, NATURAL_STRETCH),
    )

    /** The look stored at [step]; a step past the last is the last. */
    fun lookOf(step: Int): GlyphLook = GlyphLook.entries[step.coerceIn(0, GlyphLook.entries.lastIndex)]

    /** The opacity, from 0 to 1, stored at [step]. */
    fun opacityOf(step: Int): Float = OPACITY_PERCENT[step.coerceIn(0, OPACITY_PERCENT.lastIndex)] / 100f

    /** How far along its axis, from 0 to 1, a height or weight [step] is. */
    fun axisShareOf(step: Int): Float = step.coerceIn(0, AXIS_STEPS - 1) / (AXIS_STEPS - 1f)

    /** How much wider than designed the figures are drawn at [step]. */
    fun stretchOf(step: Int): Float = STRETCHES[step.coerceIn(0, STRETCHES.lastIndex)]

    private fun look(key: String, label: String, default: GlyphLook) =
        ClockOption(key, label, GlyphLook.entries.map { it.title }, default.ordinal)

    private fun axis(key: String, label: String, range: AxisRange) = ClockOption(
        key, label,
        List(AXIS_STEPS) { "${(range.at(axisShareOf(it))).roundToInt()}" },
        (range.initialShare * (AXIS_STEPS - 1)).roundToInt(),
    )
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

    /** Glass: the photo behind, blurred and bent at the figures' edges, which catch the light. */
    GLASS("Glass"),
}
