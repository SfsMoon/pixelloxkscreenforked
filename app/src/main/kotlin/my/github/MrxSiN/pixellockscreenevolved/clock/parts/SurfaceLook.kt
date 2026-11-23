package my.github.MrxSiN.pixellockscreenevolved.clock.parts

import my.github.MrxSiN.pixellockscreenevolved.clock.ClockOption

/**
 * How one of the lock screen's surfaces beside a clock drawn in glass is drawn,
 * as chosen in a tab of the clock sheet of its own ([Surface]): SystemUI's own,
 * glass (the clock's or a look of its own), or, where the surface allows it,
 * clear. Kept free of Android so it can be unit tested.
 */
data class SurfaceLook(
    val surface: Surface,
    val mode: Mode = Mode.GLASS,
    val likeClock: Boolean = true,
    /** Its own look, where it is not the clock's: what matters for a surface, the rest taken from the clock. */
    val own: GlassLook = OWN,
    /** How opaque the line around it is, from 0 for none to 1. */
    val outline: Float = 0.2f,
    /** How wide that line is, from 0 for the thinnest to 1 for the widest ([outlineWidthDp]). */
    val outlineWidth: Float = 0.2f,
) {

    enum class Mode(val title: String) {
        STOCK("Stock"),
        GLASS("Glass"),

        /** Nothing drawn: what stands on it, as the fingerprint's own mark, stands on the photo alone. */
        CLEAR("Clear"),
    }

    /** One surface this can describe: where its choices are stored, what its tab is called, and whether it may be clear. */
    enum class Surface(val keyPrefix: String, val tab: ClockOption.Tab, val clearable: Boolean) {
        /** Each notification card's background. Clear would leave text on the bare photo, so it is not offered. */
        NOTIFICATIONS("PIXEL_LOCK_SCREEN_EVOLVED_NOTIFICATION", ClockOption.Tab.NOTIFICATIONS, clearable = false),

        /** The disc behind the fingerprint's mark. */
        FINGERPRINT("PIXEL_LOCK_SCREEN_EVOLVED_FINGERPRINT", ClockOption.Tab.FINGERPRINT, clearable = true),
        ;

        /** Axis keys. Renaming one silently resets that choice on every device. */
        val modeKey = "${keyPrefix}_GLASS"
        val styleKey = "${keyPrefix}_STYLE"
        val frostKey = "${keyPrefix}_FROST"
        val refractionKey = "${keyPrefix}_REFRACTION"
        val highlightsKey = "${keyPrefix}_HIGHLIGHTS"
        val tintKey = "${keyPrefix}_TINT"
        val outlineKey = "${keyPrefix}_OUTLINE"
        val outlineWidthKey = "${keyPrefix}_OUTLINE_WIDTH"

        val modes: List<Mode> get() = if (clearable) Mode.entries else listOf(Mode.STOCK, Mode.GLASS)

        /** The sliders of this surface's tab, each starting where [SurfaceLook] does. */
        val options: List<ClockOption>
            get() {
                val start = SurfaceLook(this)
                return listOf(
                    ClockOption(modeKey, title(), modes.map { it.title }, modes.indexOf(start.mode), tab),
                    ClockOption(styleKey, "Look", listOf("Like clock", "Own"), 0, tab),
                    GlassLook.share(frostKey, "Frost", start.own.frost, tab),
                    GlassLook.share(refractionKey, "Refraction", start.own.refraction, tab),
                    GlassLook.share(highlightsKey, "Highlights", start.own.highlights, tab),
                    GlassLook.share(tintKey, "Tint", start.own.tint, tab),
                    GlassLook.share(outlineKey, "Outline", start.outline, tab),
                    GlassLook.share(outlineWidthKey, "Outline width", start.outlineWidth, tab),
                )
            }

        private fun title(): String = when (this) {
            NOTIFICATIONS -> "Notifications"
            FINGERPRINT -> "Fingerprint"
        }
    }

    /** The line's width in dp. */
    val outlineWidthDp: Float get() = THINNEST_OUTLINE_DP + (WIDEST_OUTLINE_DP - THINNEST_OUTLINE_DP) * outlineWidth

    /**
     * How the surface is drawn beside a clock in [clock], or null for SystemUI's
     * own. Glass always keeps some of the surface's own colour, so what stands
     * on it reads over any photo.
     */
    fun beside(clock: GlassLook): SurfaceGlass? = when (mode) {
        Mode.STOCK -> null
        Mode.CLEAR -> SurfaceGlass(look = null, outline = outline, outlineWidthDp = outlineWidthDp)
        Mode.GLASS -> {
            val look = if (likeClock) clock else own.copy(bevel = clock.bevel, dispersion = clock.dispersion, lightDegrees = clock.lightDegrees)
            SurfaceGlass(look.copy(tint = maxOf(look.tint, LEAST_TINT)), outline, outlineWidthDp)
        }
    }

    /** This look with the option stored under [key] at [step], or null where [key] is not one of this surface's. */
    fun with(key: String, step: Int): SurfaceLook? = when (key) {
        surface.modeKey -> copy(mode = surface.modes[step.coerceIn(0, surface.modes.lastIndex)])
        surface.styleKey -> copy(likeClock = step <= 0)
        surface.frostKey -> copy(own = own.copy(frost = GlassLook.share(step)))
        surface.refractionKey -> copy(own = own.copy(refraction = GlassLook.share(step)))
        surface.highlightsKey -> copy(own = own.copy(highlights = GlassLook.share(step)))
        surface.tintKey -> copy(own = own.copy(tint = GlassLook.share(step)))
        surface.outlineKey -> copy(outline = GlassLook.share(step))
        surface.outlineWidthKey -> copy(outlineWidth = GlassLook.share(step))
        else -> null
    }

    companion object {
        private const val THINNEST_OUTLINE_DP = 0.5f
        private const val WIDEST_OUTLINE_DP = 3.5f

        /** Frosted and darkened more than the clock's, so what stands on a surface reads over any photo. */
        private val OWN = GlassLook(frost = 0.8f, refraction = 0.3f, highlights = 0.5f, tint = 0.5f)

        /** The least of its own colour a glass surface keeps. */
        private const val LEAST_TINT = 0.3f
    }
}

/**
 * How a surface is drawn: in [look], or clear where it is null, with a line
 * around it [outline] opaque, from 0 to 1, and [outlineWidthDp] wide.
 */
data class SurfaceGlass(val look: GlassLook?, val outline: Float, val outlineWidthDp: Float)
