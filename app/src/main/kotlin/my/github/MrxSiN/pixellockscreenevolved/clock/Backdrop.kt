package my.github.MrxSiN.pixellockscreenevolved.clock

import android.graphics.Bitmap
import android.graphics.Matrix
import android.view.View

/**
 * The lock screen photo behind a face, for glass to show through, nearly sharp
 * and frosted, and where it stands on screen at this moment.
 */
interface Backdrop {

    /** The photo, small and nearly sharp; a face may draw it, never change or recycle it. */
    val picture: Bitmap

    /** The same photo blurred as frosted glass softens it, at any size, covering what [picture] covers. */
    val frosted: Bitmap

    /**
     * Sets [toView] to map [picture]'s pixels onto [view]'s own as the photo
     * stands behind it now, or returns false where it cannot say, and the face
     * should draw without it. Asked on every draw, on the main thread.
     */
    fun place(view: View, toView: Matrix): Boolean
}

/**
 * A face that draws something through the photo behind it, such as glass.
 *
 * Kept apart from [ClockFace] so a face that draws over the photo alone is
 * never made to answer for it.
 */
interface BackdropClockFace : ClockFace {

    /** Draws through [backdrop] from now on, or without one where it is null. */
    fun setBackdrop(backdrop: Backdrop?)
}
