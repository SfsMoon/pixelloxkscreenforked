package my.github.MrxSiN.pixellockscreenevolved.clock.parts

import my.github.MrxSiN.pixellockscreenevolved.clock.ClockOption

/**
 * How the lock screen's notifications are drawn beside a clock drawn in glass,
 * as chosen in the clock sheet's Glass tab: on SystemUI's own backgrounds, or
 * as glass, the clock's own or a look of their own. Kept free of Android so it
 * can be unit tested.
 */
data class NotificationLook(
    val glass: Boolean = true,
    val likeClock: Boolean = true,
    /** Their own look, where it is not the clock's: what matters for a card, the rest taken from the clock. */
    val own: GlassLook = OWN,
) {

    /**
     * The glass the notifications are drawn in beside a clock in [clock], or
     * null for SystemUI's own backgrounds. A card always keeps some of its own
     * colour, so its text reads over any photo.
     */
    fun glassBeside(clock: GlassLook): GlassLook? {
        if (!glass) return null
        val look = if (likeClock) clock else own.copy(bevel = clock.bevel, dispersion = clock.dispersion, lightDegrees = clock.lightDegrees)
        return look.copy(tint = maxOf(look.tint, LEAST_TINT))
    }

    /** This look with the option stored under [key] at [step], or null where [key] is not one of the notifications'. */
    fun with(key: String, step: Int): NotificationLook? = when (key) {
        GLASS_KEY -> copy(glass = step >= 1)
        STYLE_KEY -> copy(likeClock = step <= 0)
        FROST_KEY -> copy(own = own.copy(frost = GlassLook.share(step)))
        REFRACTION_KEY -> copy(own = own.copy(refraction = GlassLook.share(step)))
        HIGHLIGHTS_KEY -> copy(own = own.copy(highlights = GlassLook.share(step)))
        TINT_KEY -> copy(own = own.copy(tint = GlassLook.share(step)))
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

        /** Frosted and darkened more than the clock's, so a card's text reads over any photo. */
        private val OWN = GlassLook(frost = 0.8f, refraction = 0.3f, highlights = 0.5f, tint = 0.5f)

        /** The least of its own colour a card keeps. */
        private const val LEAST_TINT = 0.3f

        /** The Glass tab's notification sliders. */
        val options: List<ClockOption> = listOf(
            ClockOption(GLASS_KEY, "Notifications", listOf("Stock", "Glass"), 1, ClockOption.Tab.GLASS),
            ClockOption(STYLE_KEY, "Notification look", listOf("Like clock", "Own"), 0, ClockOption.Tab.GLASS),
            GlassLook.share(FROST_KEY, "Notification frost", OWN.frost),
            GlassLook.share(REFRACTION_KEY, "Notification refraction", OWN.refraction),
            GlassLook.share(HIGHLIGHTS_KEY, "Notification highlights", OWN.highlights),
            GlassLook.share(TINT_KEY, "Notification tint", OWN.tint),
        )
    }
}
