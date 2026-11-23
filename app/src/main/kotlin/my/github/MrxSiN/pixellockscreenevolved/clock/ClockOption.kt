package my.github.MrxSiN.pixellockscreenevolved.clock

import kotlin.math.roundToInt

/**
 * One stepped choice a style offers beyond its size, width and position, such
 * as how its hours are drawn or how opaque it is.
 *
 * Wallpaper & style shows each as a slider of [values] in one of the clock
 * sheet's tabs ([tab]), and the step chosen rides in the clock setting under
 * [key], as the size does. Kept free of Android so the stepping can be unit tested.
 */
data class ClockOption(
    /** Axis key the step is stored under. Renaming one silently resets that choice on every device. */
    val key: String,
    /** Name shown beside the slider. */
    val label: String,
    /** What each step is called, the first step first. */
    val values: List<String>,
    /** The step a clock that never stored one is drawn at. */
    val defaultStep: Int,
    /** Which of the clock sheet's tabs shows it. */
    val tab: Tab = Tab.SIZE,
) {

    /** The clock sheet's tabs an option can be shown in. */
    enum class Tab {
        /** With the clock's size. */
        SIZE,

        /** A tab of its own for how glass is drawn. */
        GLASS,

        /** A tab of its own for the lock screen's notifications beside the clock. */
        NOTIFICATIONS,

        /** A tab of its own for the lock screen's fingerprint icon beside the clock. */
        FINGERPRINT,
    }

    init {
        require(values.size >= 2) { "An option needs at least two values: $key" }
        require(defaultStep in values.indices) { "Default step out of range: $key" }
    }

    val lastStep: Int get() = values.lastIndex

    /** The step a stored value chooses; a clock that never stored one is at [defaultStep]. */
    fun stepOf(stored: Float?): Int = (stored?.roundToInt() ?: defaultStep).coerceIn(0, lastStep)

    /** "Hours: Glass", the slider's label at [step]. */
    fun describe(step: Int): String = "$label: ${values[step.coerceIn(0, lastStep)]}"
}

/**
 * A face that draws the [ClockOption]s its style offers.
 *
 * Kept apart from [ClockFace] so a style with no options is never made to
 * answer for them.
 */
interface ConfigurableClockFace : ClockFace {

    /** Redraws with the option stored under [key] at [step]; a key the face does not know is ignored. */
    fun setOption(key: String, step: Int)
}
