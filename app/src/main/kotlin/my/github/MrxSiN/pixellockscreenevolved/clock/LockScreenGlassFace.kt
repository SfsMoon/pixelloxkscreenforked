package my.github.MrxSiN.pixellockscreenevolved.clock

import my.github.MrxSiN.pixellockscreenevolved.clock.parts.SurfaceGlass

/**
 * A face that says how the lock screen's surfaces beside it are drawn: its
 * notification cards and the disc behind the fingerprint's mark, as glass
 * through the photo behind, clear, or SystemUI's own.
 *
 * Kept apart from [ClockFace] so a face with no say in them is never made to answer.
 */
interface LockScreenGlassFace : ClockFace {

    /** How notification cards are drawn beside this face, or null for SystemUI's own backgrounds. */
    val notificationGlass: SurfaceGlass?

    /** How the fingerprint icon's disc is drawn beside this face, or null for SystemUI's own. */
    val fingerprintGlass: SurfaceGlass?

    /** The photo behind, as this face draws through it, or null where there is none. */
    val backdrop: Backdrop?
}
