package my.github.MrxSiN.pixellockscreenevolved.host

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.drawable.BitmapDrawable
import android.os.Handler
import android.os.Looper
import android.view.View

import java.util.concurrent.Executors

import my.github.MrxSiN.pixellockscreenevolved.clock.Backdrop
import my.github.MrxSiN.pixellockscreenevolved.clock.BackdropClockFace
import my.github.MrxSiN.pixellockscreenevolved.core.Logger

/**
 * The lock screen wallpaper as the wallpaper manager stores it, for glass to
 * show through ([BackdropClockFace]) where the photo SystemUI draws
 * ([GlassBackdrop]) is not at hand: in Wallpaper & style's own preview, which
 * it never reaches, and on the lock screen until SystemUI draws a photo again.
 *
 * It is read once for each wallpaper set, and laid over [screenOf] a face, the
 * view that stands for the screen around it, as a still wallpaper is laid by
 * default: filling it, cropped about its middle. A photo cropped or scrolled
 * otherwise is shown through the glass a little off where it stands, until
 * SystemUI's own photo, placed exactly, takes its place.
 */
internal class StoredWallpaperGlass(
    private val logger: Logger,
    /** The view that stands for the screen around [View], or null where there is none yet. */
    private val screenOf: (View) -> View?,
) {

    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()

    /** The pictures read last, and the wallpaper they were read for; written on the main thread, read on the worker. */
    @Volatile private var pictures: GlassPhoto.Pictures? = null
    @Volatile private var picturesId = NONE

    /**
     * Shows the wallpaper through every face of [clock] that draws through one,
     * once it is read, while [stillWanted] says nothing better has come.
     */
    fun track(clock: ClockControllerAdapter, stillWanted: () -> Boolean = { true }) {
        val faces = clock.faces.filter { it.viewFace is BackdropClockFace }
        if (faces.isEmpty()) return
        val context = faces.first().context
        worker.execute {
            val read = read(context)
            main.post {
                if (read != null) {
                    pictures = read.second
                    picturesId = read.first
                }
                val shown = pictures ?: return@post
                if (!stillWanted()) return@post
                faces.forEach { face -> (face.viewFace as BackdropClockFace).setBackdrop(Placed(shown)) }
            }
        }
    }

    /** The lock screen wallpaper's id and its pictures, read again only when another one is set; or null to keep the last. */
    private fun read(context: Context): Pair<Int, GlassPhoto.Pictures>? = runCatching {
        val manager = WallpaperManager.getInstance(context)
        val which = LockWallpaperId.which(manager)
        val id = manager.getWallpaperId(which)
        if (id == picturesId && pictures != null) return null
        val bitmap = (manager.getDrawable(which) as? BitmapDrawable)?.bitmap ?: return null
        val small = GlassPhoto.shrink(bitmap) ?: return null
        id to GlassPhoto.pictures(small)
    }.onFailure { logger.warn("The lock screen wallpaper could not be read for the clock's glass", it) }.getOrNull()

    /** The pictures laid over the screen around the face, filling it and cropped about its middle. */
    private inner class Placed(pictures: GlassPhoto.Pictures) : Backdrop {

        override val picture: Bitmap = pictures.sharp

        override val frosted: Bitmap = pictures.frosted

        private val screenToWindow = Matrix()
        private val viewToWindow = Matrix()
        private val windowToView = Matrix()

        override fun place(view: View, toView: Matrix): Boolean {
            val screen = screenOf(view)?.takeIf { it.width > 0 && it.height > 0 } ?: return false
            val scale = maxOf(screen.width.toFloat() / picture.width, screen.height.toFloat() / picture.height)
            toView.setScale(scale, scale)
            toView.postTranslate((screen.width - picture.width * scale) / 2f, (screen.height - picture.height * scale) / 2f)
            ViewBounds.toWindow(screen, screenToWindow)
            toView.postConcat(screenToWindow)
            ViewBounds.toWindow(view, viewToWindow)
            if (!viewToWindow.invert(windowToView)) return false
            toView.postConcat(windowToView)
            return true
        }
    }

    companion object {
        private const val NONE = -1

        /** In Wallpaper & style's preview: the nearest view around the face that has a size, the preview's frame. */
        val PREVIEW: (View) -> View? = { view -> generateSequence(view.parent as? View) { it.parent as? View }.firstOrNull { it.height > 0 } }

        /** On the lock screen: the window's root, as large as the screen. */
        val LOCK_SCREEN: (View) -> View? = { view -> view.rootView }
    }
}
