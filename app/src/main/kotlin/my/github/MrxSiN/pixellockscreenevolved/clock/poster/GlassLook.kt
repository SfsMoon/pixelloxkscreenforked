package my.github.MrxSiN.pixellockscreenevolved.clock.poster

import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

import my.github.MrxSiN.pixellockscreenevolved.clock.ClockOption

/**
 * How glass is drawn ([LiquidGlass]), as chosen in the clock sheet's Glass tab:
 * each part from 0 to 1 but the light, which is an angle.
 */
data class GlassLook(
    /** How frosted: 0 shows the photo nearly sharp through the glass, 1 blurs it fully. */
    val frost: Float = 0.6f,
    /** How far the bevel bends the photo. */
    val refraction: Float = 0.5f,
    /** How wide the bevel is, from a thin edge to a rounded stroke. */
    val bevel: Float = 0.4f,
    /** How far apart the bevel splits the colours. */
    val dispersion: Float = 0.3f,
    /** How bright the light catches and reflects off the glass. */
    val highlights: Float = 0.6f,
    /** Where the light comes from, in degrees clockwise from the right of the screen: 240 is the top left. */
    val lightDegrees: Float = 240f,
    /** How much of the clock's colour tints the glass. */
    val tint: Float = 0.2f,
) {
    /** The direction the light comes from, across and down the screen. */
    val lightX: Float get() = cos(Math.toRadians(lightDegrees.toDouble())).toFloat()
    val lightY: Float get() = sin(Math.toRadians(lightDegrees.toDouble())).toFloat()

    /** This look with the option stored under [key] at [step], or null where [key] is not one of glass's. */
    fun with(key: String, step: Int): GlassLook? = when (key) {
        FROST_KEY -> copy(frost = share(step))
        REFRACTION_KEY -> copy(refraction = share(step))
        BEVEL_KEY -> copy(bevel = share(step))
        DISPERSION_KEY -> copy(dispersion = share(step))
        HIGHLIGHTS_KEY -> copy(highlights = share(step))
        LIGHT_KEY -> copy(lightDegrees = step.coerceIn(0, LIGHT_STEPS - 1) * LIGHT_STEP_DEGREES)
        TINT_KEY -> copy(tint = share(step))
        else -> null
    }

    companion object {
        /** Axis keys. Renaming one silently resets that choice on every device. */
        const val FROST_KEY = "PIXEL_LOCK_SCREEN_EVOLVED_GLASS_FROST"
        const val REFRACTION_KEY = "PIXEL_LOCK_SCREEN_EVOLVED_GLASS_REFRACTION"
        const val BEVEL_KEY = "PIXEL_LOCK_SCREEN_EVOLVED_GLASS_BEVEL"
        const val DISPERSION_KEY = "PIXEL_LOCK_SCREEN_EVOLVED_GLASS_DISPERSION"
        const val HIGHLIGHTS_KEY = "PIXEL_LOCK_SCREEN_EVOLVED_GLASS_HIGHLIGHTS"
        const val LIGHT_KEY = "PIXEL_LOCK_SCREEN_EVOLVED_GLASS_LIGHT"
        const val TINT_KEY = "PIXEL_LOCK_SCREEN_EVOLVED_GLASS_TINT"

        /** Stops on each 0 to 1 slider. */
        private const val STEPS = 11

        /** The light turns in twelve steps of 30 degrees. */
        private const val LIGHT_STEPS = 12
        private const val LIGHT_STEP_DEGREES = 30f

        private val DEFAULT = GlassLook()

        /** The Glass tab's sliders, each starting at [DEFAULT]. */
        val options: List<ClockOption> = listOf(
            share(FROST_KEY, "Frost", DEFAULT.frost),
            share(REFRACTION_KEY, "Refraction", DEFAULT.refraction),
            share(BEVEL_KEY, "Edge", DEFAULT.bevel),
            share(DISPERSION_KEY, "Dispersion", DEFAULT.dispersion),
            share(HIGHLIGHTS_KEY, "Highlights", DEFAULT.highlights),
            ClockOption(
                LIGHT_KEY, "Light",
                List(LIGHT_STEPS) { "${(it * LIGHT_STEP_DEGREES).roundToInt()}°" },
                (DEFAULT.lightDegrees / LIGHT_STEP_DEGREES).roundToInt(),
                ClockOption.Tab.GLASS,
            ),
            share(TINT_KEY, "Tint", DEFAULT.tint),
        )

        private fun share(step: Int): Float = step.coerceIn(0, STEPS - 1) / (STEPS - 1f)

        private fun share(key: String, label: String, initial: Float) = ClockOption(
            key, label, List(STEPS) { "${it * 10}%" }, (initial * (STEPS - 1)).roundToInt(), ClockOption.Tab.GLASS,
        )
    }
}
