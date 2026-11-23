package my.github.MrxSiN.pixellockscreenevolved.host

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView

import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.util.WeakHashMap

import my.github.MrxSiN.pixellockscreenevolved.clock.LockScreenGlassFace
import my.github.MrxSiN.pixellockscreenevolved.core.Logger
import my.github.MrxSiN.pixellockscreenevolved.hook.HostPatch

/**
 * The disc behind the fingerprint's mark on the lock screen, beside a clock of
 * this module's that has a say in it ([LockScreenGlassFace.fingerprintGlass]):
 * glass through the photo behind, clear, or SystemUI's own.
 *
 * SystemUI draws the disc as the picture of an image view of the lock screen's
 * device entry icon (`DeviceEntryIconView.bgView`), tinting it and fading it
 * as the lock screen comes and goes. That picture is swapped for one of this
 * module's ([Disc]), which draws as the clock asks and is as large as the one
 * it stands in for, so SystemUI's fading and placing carry on; the original
 * goes back the moment the clock asks for SystemUI's own, or is gone.
 *
 * The icon is a view hosted by the lock screen scene; it is looked for in the
 * window of a clock that has a say in it, before each draw of that clock, a
 * few times a second at most while it is not found.
 */
internal class LockScreenFingerprint(private val logger: Logger) : HostPatch {

    private var iconType: Class<*>? = null
    private var background: Field? = null

    private val originals = WeakHashMap<ImageView, Drawable?>()
    private var icon = WeakReference<View>(null)
    private var lookedAt = 0L

    override fun install(classLoader: ClassLoader) {
        try {
            val type = classLoader.hostClass(ICON)
            iconType = type
            background = type.field("bgView")
        } catch (error: ReflectiveOperationException) {
            logger.warn("Lock screen fingerprint icon not found; its disc keeps SystemUI's look", error)
        }
    }

    /** Follows the lock screen of a clock SystemUI built, for the fingerprint icon beside it. */
    fun track(clock: ClockControllerAdapter) {
        if (background == null) return
        clock.faces.forEach { face ->
            if (face.viewFace !is LockScreenGlassFace) return@forEach
            BeforeEachDraw.follow(face.view) { runCatching { follow(face.view) }.onFailure { logger.warn("Fingerprint disc failed", it) } }
        }
    }

    /** Swaps the disc's picture in the window [faceView] stands in, or gives SystemUI's back, as the clock there asks. */
    private fun follow(faceView: View) {
        val root = faceView.rootView
        val disc = discIn(root) ?: return
        val face = ModuleClockFaces.glassFaceIn(root)
        val wanted = face?.fingerprintGlass != null
        val shown = disc.drawable
        if (wanted && shown !is Disc) {
            originals[disc] = shown
            disc.setImageDrawable(Disc(disc, shown))
        } else if (!wanted && shown is Disc) {
            disc.setImageDrawable(originals.remove(disc) ?: shown.original)
        }
    }

    /** The disc of the fingerprint icon in [root], looked for again only where it was not found or has gone. */
    private fun discIn(root: View): ImageView? {
        val type = iconType ?: return null
        val known = icon.get()
        val found = if (known != null && known.isAttachedToWindow && known.rootView === root) {
            known
        } else {
            val now = SystemClock.uptimeMillis()
            if (now - lookedAt < LOOK_EVERY_MS) return null
            lookedAt = now
            find(root, type)?.also { icon = WeakReference(it) } ?: return null
        }
        return background?.get(found) as? ImageView
    }

    private fun find(view: View, type: Class<*>): View? {
        if (type.isInstance(view)) return view
        if (view !is ViewGroup) return null
        for (i in 0 until view.childCount) find(view.getChildAt(i), type)?.let { return it }
        return null
    }

    /**
     * The disc's picture while a clock asks for it: a circle filling the
     * picture's bounds, drawn as the clock beside it says, as large as SystemUI's
     * own [original], so the image view lays it out alike.
     */
    private class Disc(private val disc: ImageView, val original: Drawable?) : Drawable() {

        private val painter = GlassSurfacePainter(disc)
        private val circle = RectF()
        private val radii = FloatArray(CORNERS)
        private val viewToPicture = Matrix()
        private val pictureToView = Matrix()

        override fun draw(canvas: Canvas) {
            val face = ModuleClockFaces.glassFaceIn(disc.rootView) ?: return
            val glass = face.fingerprintGlass ?: return
            if (bounds.isEmpty) return
            val side = minOf(bounds.width(), bounds.height()).toFloat()
            circle.set(bounds.exactCenterX() - side / 2, bounds.exactCenterY() - side / 2, bounds.exactCenterX() + side / 2, bounds.exactCenterY() + side / 2)
            radii.fill(side / 2)
            // The image view draws its picture moved by its padding and through its image matrix.
            pictureToView.set(disc.imageMatrix)
            pictureToView.postTranslate(disc.paddingLeft.toFloat(), disc.paddingTop.toFloat())
            if (!pictureToView.invert(viewToPicture)) return
            painter.draw(canvas, circle, radii, glass, face.backdrop, side * REFERENCE_TO_SIDE, viewToPicture)
        }

        override fun getIntrinsicWidth(): Int = original?.intrinsicWidth ?: -1

        override fun getIntrinsicHeight(): Int = original?.intrinsicHeight ?: -1

        override fun setAlpha(alpha: Int) = Unit

        override fun setColorFilter(colorFilter: ColorFilter?) = Unit

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    private companion object {
        const val ICON = "com.android.systemui.keyguard.ui.view.DeviceEntryIconView"
        const val LOOK_EVERY_MS = 500L
        const val CORNERS = 8

        /** Glass on so small a disc is shaped as for one a few times its size, so its bevel still shows. */
        const val REFERENCE_TO_SIDE = 3f
    }
}
