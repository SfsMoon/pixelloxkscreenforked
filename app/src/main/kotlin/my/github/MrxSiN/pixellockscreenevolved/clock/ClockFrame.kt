package my.github.MrxSiN.pixellockscreenevolved.clock

/**
 * How a person has stretched and moved a face by hand, beyond the size and the
 * position down the screen: [scaleX] and [scaleY] times as wide and as tall as
 * its size draws it, and moved [shiftX] of the screen's width to the right
 * (negative to the left) from the middle. Kept free of Android so it can be
 * unit tested.
 */
data class ClockFrame(val scaleX: Float = 1f, val scaleY: Float = 1f, val shiftX: Float = 0f) {

    /** This frame within what a face can show: neither scale past [MIN_SCALE] or [MAX_SCALE], nor the middle off the screen. */
    fun clamped(): ClockFrame = ClockFrame(
        scaleX.coerceIn(MIN_SCALE, MAX_SCALE),
        scaleY.coerceIn(MIN_SCALE, MAX_SCALE),
        shiftX.coerceIn(-MAX_SHIFT, MAX_SHIFT),
    )

    companion object {
        val NATURAL = ClockFrame()

        const val MIN_SCALE = 0.4f
        const val MAX_SCALE = 2.5f
        const val MAX_SHIFT = 0.5f

        /** The frame stored [scaleX], [scaleY] and [shiftX] axis values choose; a value never stored is natural. */
        fun of(scaleX: Float?, scaleY: Float?, shiftX: Float?): ClockFrame =
            ClockFrame(scaleX ?: 1f, scaleY ?: 1f, shiftX ?: 0f).clamped()
    }
}

/**
 * A face a person can stretch and move across by hand in Wallpaper & style's
 * preview, as other systems let their lock screen clocks be.
 *
 * Kept apart from [ClockFace] so a face that keeps its own proportions is never
 * made to answer for them.
 */
interface FramedClockFace : ClockFace {

    /** Redraws stretched and moved as [frame] says. */
    fun setFrame(frame: ClockFrame)
}
