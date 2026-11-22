package my.github.MrxSiN.pixellockscreenevolved.clock

import my.github.MrxSiN.pixellockscreenevolved.clock.parts.GlassLook

/**
 * A face that says how the lock screen's notifications are drawn beside it:
 * as glass through the photo behind, or on SystemUI's own backgrounds.
 *
 * Kept apart from [ClockFace] so a face with no say in it is never made to answer.
 */
interface NotificationGlassFace : ClockFace {

    /** The glass notifications are drawn in beside this face, or null for SystemUI's own backgrounds. */
    val notificationGlass: GlassLook?

    /** The photo behind, as this face draws through it, or null where there is none. */
    val backdrop: Backdrop?
}
