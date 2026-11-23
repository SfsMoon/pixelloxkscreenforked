package my.github.MrxSiN.pixellockscreenevolved.clock.parts

import my.github.MrxSiN.pixellockscreenevolved.clock.ClockOption

/**
 * How the lock screen's notifications are drawn beside a clock drawn in glass,
 * as chosen in the clock sheet's Notifications tab: on SystemUI's own backgrounds, or
 * as glass, the clock's own or a look of their own. Kept free of Android so it
 * can be unit tested.
 */
data class NotificationLook(
    val glass: Boolean = true,
    val likeClock: Boolean = true,
    /** Their own look, where it is not the clock's: what matters for a card, the rest taken from the clock. */
    val own: GlassLook = OWN,
    /** How opaque the line around each card is, from 0 for none to 1. */
    val outline: Float = 0.2f,
    /** How wide that line is, from 0 for the thinnest to 1 for the widest ([outlineWidthDp]). */
    val outlineWidth: Float = 0.2f,
) {

    /** The line's width in dp. */
    val outlineWidthDp: Float get() = THINNEST_OUTLINE_DP + (WIDEST_OUTLINE_DP - THINNEST_OUTLINE_DP) * outlineWidth

    /**
     * The glass the notifications are drawn in beside a clock in [clock], or
     * null for SystemUI's own backgrounds. A card always keeps some of its own
     * colour, so its text reads over any photo.
     */
    fun glassBeside(clock: GlassLook): NotificationGlass? {
        if (!glass) return null
        val look = if (likeClock) clock else own.copy(bevel = clock.bevel, dispersion = clock.dispersion, lightDegrees = clock.lightDegrees)
        return NotificationGlass(look.copy(tint = maxOf(look.tint, LEAST_TINT)), outline, outlineWidthDp)
    }

    /** This look with the option stored under [key] at [step], or null where [key] is not one of the notifications'. */
    fun with(key: String, step: Int): NotificationLook? = when (key) {
        GLASS_KEY -> copy(glass = step >= 1)
        STYLE_KEY -> copy(likeClock = step <= 0)
        FROST_KEY -> copy(own = own.copy(frost = GlassLook.share(step)))
        REFRACTION_KEY -> copy(own = own.copy(refraction = GlassLook.share(step)))
        HIGHLIGHTS_KEY -> copy(own = own.copy(highlights = GlassLook.share(step)))
        TINT_KEY -> copy(own = own.copy(tint = GlassLook.share(step)))
        OUTLINE_KEY -> copy(outline = GlassLook.share(step))
        OUTLINE_WIDTH_KEY -> copy(outlineWidth = GlassLook.share(step))
        else -> null
    }

    companion object {
        /** Axis keys. Renaming one silently resets that choice on every device. */
        const val GLASS_KEY = "PIXEL_LOCK_SCREEN_EVOLVED_NOTIFICATION_GLASS"
        const val STYLE_KEY = "PIXEL_LOCK_SCREEN_EVOLVED_NOTIFICATION_STYLE"
        const val FROST_KEY = "PIXEL_LOCK_SCREEN_EVOLVED_NOTIFICATION_FROST"
        const val REFRACTION_KEY = "PIXEL_LOCK_SCREEN_EVOLVED_NOTIFICATION_REFRACTION"
        const val HIGHLIGHTS_KEY = "PIXEL_LOCK_SCREEN_EVOLVED_NOTIFICATION_HIGHLIGHTS"
        const val TINT_KEY = "PIXEL_LOCK_SCREEN_EVOLVED_NOTIFICATION_TINT"
        const val OUTLINE_KEY = "PIXEL_LOCK_SCREEN_EVOLVED_NOTIFICATION_OUTLINE"
        const val OUTLINE_WIDTH_KEY = "PIXEL_LOCK_SCREEN_EVOLVED_NOTIFICATION_OUTLINE_WIDTH"

        private const val THINNEST_OUTLINE_DP = 0.5f
        private const val WIDEST_OUTLINE_DP = 3.5f

        /** Frosted and darkened more than the clock's, so a card's text reads over any photo. */
        private val OWN = GlassLook(frost = 0.8f, refraction = 0.3f, highlights = 0.5f, tint = 0.5f)

        /** What a clock that never stored a choice draws its notifications with; made after [OWN], which it holds. */
        private val DEFAULT = NotificationLook()

        /** The least of its own colour a card keeps. */
        private const val LEAST_TINT = 0.3f

        /** The Notifications tab's sliders. */
        val options: List<ClockOption> = listOf(
            ClockOption(GLASS_KEY, "Notifications", listOf("Stock", "Glass"), 1, ClockOption.Tab.NOTIFICATIONS),
            ClockOption(STYLE_KEY, "Notification look", listOf("Like clock", "Own"), 0, ClockOption.Tab.NOTIFICATIONS),
            GlassLook.share(FROST_KEY, "Frost", OWN.frost, ClockOption.Tab.NOTIFICATIONS),
            GlassLook.share(REFRACTION_KEY, "Refraction", OWN.refraction, ClockOption.Tab.NOTIFICATIONS),
            GlassLook.share(HIGHLIGHTS_KEY, "Highlights", OWN.highlights, ClockOption.Tab.NOTIFICATIONS),
            GlassLook.share(TINT_KEY, "Tint", OWN.tint, ClockOption.Tab.NOTIFICATIONS),
            GlassLook.share(OUTLINE_KEY, "Outline", DEFAULT.outline, ClockOption.Tab.NOTIFICATIONS),
            GlassLook.share(OUTLINE_WIDTH_KEY, "Outline width", DEFAULT.outlineWidth, ClockOption.Tab.NOTIFICATIONS),
        )
    }
}

/** The glass a card is drawn in, and the line around it: how opaque, from 0 to 1, and how wide. */
data class NotificationGlass(val look: GlassLook, val outline: Float, val outlineWidthDp: Float)
