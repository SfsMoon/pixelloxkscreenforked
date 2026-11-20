package my.github.MrxSiN.pixellockscreenevolved.clock.poster

/**
 * The two fonts the Poster clocks are drawn in, both shipped in the module's
 * assets, and how each is cut and sized.
 *
 * Each is a variable font with a figure height (`HGHT`) and a weight (`wght`)
 * axis, both chosen in Wallpaper & style ([PosterOptions]); the figures are
 * then fitted to the size chosen, so a lower height draws them wider. On the
 * always-on display the weight thins toward [dozeWeight], so few pixels stay
 * lit, as the Roboto Flex clocks thin theirs. Kept free of Android so the cuts
 * can be unit tested.
 */
enum class PosterFont(
    /** Stable identifier written to the system clock setting; renaming one resets every device that picked it. */
    val id: String,
    val title: String,
    val description: String,
    /** Path of the font in the module's assets. */
    val asset: String,
    /** The `HGHT` axis's range as this design uses it, and where it starts. */
    val heights: AxisRange,
    /** The `wght` axis's range as this design uses it, and where it starts. */
    val weights: AxisRange,
    private val dozeWeight: Float,
    /** The large face's numeral height against the screen's short side, at the smallest and the largest size. */
    val smallest: Float,
    val largest: Float,
    val hourLook: GlyphLook,
    val minuteLook: GlyphLook,
) {
    /** Tall condensed figures, glass by default. */
    TALL(
        id = "PIXEL_LOCK_SCREEN_EVOLVED_POSTER_TALL",
        title = "Poster Tall",
        description = "Tall condensed figures in glass, outline or solid",
        asset = "fonts/poster_tall.ttf",
        heights = AxisRange(from = 100f, to = 1000f, initial = 1000f),
        weights = AxisRange(from = 100f, to = 1000f, initial = 1000f),
        dozeWeight = 220f,
        smallest = 0.62f,
        largest = 1.2f,
        hourLook = GlyphLook.GLASS,
        minuteLook = GlyphLook.GLASS,
    ),

    /** Short heavy figures, solid hours and outlined minutes by default. */
    BOLD(
        id = "PIXEL_LOCK_SCREEN_EVOLVED_POSTER_BOLD",
        title = "Poster Bold",
        description = "Heavy figures, the hours and the minutes each solid, outline or glass",
        asset = "fonts/poster_bold.ttf",
        heights = AxisRange(from = 0f, to = 1000f, initial = 500f),
        weights = AxisRange(from = 100f, to = 1000f, initial = 800f),
        dozeWeight = 160f,
        smallest = 0.34f,
        largest = 0.72f,
        hourLook = GlyphLook.FILLED,
        minuteLook = GlyphLook.OUTLINE,
    ),
    ;

    /**
     * The variation settings for the figure [height] and [weight] chosen, each
     * from 0 to 1 across its range, [doze] of the way into the always-on
     * display, from 0 awake to 1 dozing.
     */
    fun settings(height: Float, weight: Float, doze: Float): String {
        val awake = weights.at(weight)
        val thin = minOf(awake, dozeWeight)
        return "'HGHT' ${heights.at(height)}, 'wght' ${awake + (thin - awake) * doze.coerceIn(0f, 1f)}"
    }
}

/** One axis's values from [from] to [to], as a 0 to 1 share chooses them, starting at [initial]. */
data class AxisRange(val from: Float, val to: Float, val initial: Float) {

    /** The value [share] of the way from [from] to [to]. */
    fun at(share: Float): Float = from + (to - from) * share.coerceIn(0f, 1f)

    /** How far [initial] is along the range, from 0 to 1. */
    val initialShare: Float get() = if (to == from) 0f else (initial - from) / (to - from)
}
