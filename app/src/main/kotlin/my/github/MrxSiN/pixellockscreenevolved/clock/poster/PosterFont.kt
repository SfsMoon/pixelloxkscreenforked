package my.github.MrxSiN.pixellockscreenevolved.clock.poster

/**
 * The two fonts the Poster clocks are drawn in, both shipped in the module's
 * assets, and how each is cut and sized.
 *
 * Each is a variable font with a weight (`wght`) and a figure height (`HGHT`)
 * axis. The height stays at the cut each design was drawn for; the weight is
 * lowered on the always-on display, so few pixels stay lit, as the Roboto Flex
 * clocks thin theirs. Kept free of Android so the cuts can be unit tested.
 */
enum class PosterFont(
    /** Stable identifier written to the system clock setting; renaming one resets every device that picked it. */
    val id: String,
    val title: String,
    val description: String,
    /** Path of the font in the module's assets. */
    val asset: String,
    private val figureHeight: Float,
    private val awakeWeight: Float,
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
        figureHeight = 1000f,
        awakeWeight = 1000f,
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
        figureHeight = 500f,
        awakeWeight = 850f,
        dozeWeight = 160f,
        smallest = 0.34f,
        largest = 0.72f,
        hourLook = GlyphLook.FILLED,
        minuteLook = GlyphLook.OUTLINE,
    ),
    ;

    /** The font's variation settings [doze] of the way into the always-on display, from 0 awake to 1 dozing. */
    fun settings(doze: Float): String {
        val t = doze.coerceIn(0f, 1f)
        return "'HGHT' $figureHeight, 'wght' ${awakeWeight + (dozeWeight - awakeWeight) * t}"
    }
}
