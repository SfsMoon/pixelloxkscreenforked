package my.github.MrxSiN.pixellockscreenevolved.host

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.view.View

import java.util.concurrent.Executors

import my.github.MrxSiN.pixellockscreenevolved.clock.Backdrop
import my.github.MrxSiN.pixellockscreenevolved.clock.BackdropClockFace
import my.github.MrxSiN.pixellockscreenevolved.core.Logger

/**
 * The lock screen photo behind the clocks Wallpaper & style previews itself, for
 * glass to show through ([BackdropClockFace]).
 *
 * The picker draws its preview's wallpaper itself, where SystemUI's photo
 * ([GlassBackdrop]) never reaches, so the lock screen wallpaper is read from
 * the wallpaper manager instead, once for each wallpaper set, and laid over the
 * preview as the picker lays it: filling the preview, cropped about its middle.
 * A photo cropped or scrolled otherwise is shown through the glass a little off
 * where it stands; on the lock screen itself it is placed exactly.
 */
internal class PickerGlassBackdrop(private val logger: Logger) {

    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()

    /** The photo read last, and the wallpaper it was read for; written on the main thread, read on the worker. */
    @Volatile private var photo: Bitmap? = null
    @Volatile private var photoId = NONE

    /** Shows the lock screen photo through every face of [clock] that draws through one. */
    fun track(clock: ClockControllerAdapter) {
        val faces = clock.faces.filter { it.viewFace is BackdropClockFace }
        if (faces.isEmpty()) return
        val context = faces.first().context
        worker.execute {
            val read = read(context)
            main.post {
                if (read != null) {
                    photo = read.second
                    photoId = read.first
                }
                val shown = photo ?: return@post
                faces.forEach { face -> (face.viewFace as BackdropClockFace).setBackdrop(Placed(shown)) }
            }
        }
    }

    /** The lock screen wallpaper's id and its blurred photo, read again only when another one is set; or null to keep the last. */
    private fun read(context: Context): Pair<Int, Bitmap>? = runCatching {
        val manager = WallpaperManager.getInstance(context)
        val lockId = manager.getWallpaperId(WallpaperManager.FLAG_LOCK)
        val which = if (lockId > 0) WallpaperManager.FLAG_LOCK else WallpaperManager.FLAG_SYSTEM
        val id = manager.getWallpaperId(which)
        if (id == photoId && photo != null) return null
        val drawable: Drawable = manager.getDrawable(which) ?: return null
        val bitmap = (drawable as? BitmapDrawable)?.bitmap ?: return null
        val small = GlassPhoto.shrink(bitmap) ?: return null
        id to GlassPhoto.blur(small)
    }.onFailure { logger.warn("The lock screen wallpaper could not be read for the clock's glass", it) }.getOrNull()

    /** [picture] laid over the preview the face stands in, filling it and cropped about its middle. */
    private class Placed(override val picture: Bitmap) : Backdrop {

        private val previewToWindow = Matrix()
        private val viewToWindow = Matrix()
        private val windowToView = Matrix()

        override fun place(view: View, toView: Matrix): Boolean {
            // The large face stands straight in the preview's frame, as tall and wide as the preview.
            val preview = generateSequence(view.parent as? View) { it.parent as? View }.firstOrNull { it.height > 0 } ?: return false
            val scale = maxOf(preview.width.toFloat() / picture.width, preview.height.toFloat() / picture.height)
            toView.setScale(scale, scale)
            toView.postTranslate((preview.width - picture.width * scale) / 2f, (preview.height - picture.height * scale) / 2f)
            ViewBounds.toWindow(preview, previewToWindow)
            toView.postConcat(previewToWindow)
            ViewBounds.toWindow(view, viewToWindow)
            if (!viewToWindow.invert(windowToView)) return false
            toView.postConcat(windowToView)
            return true
        }
    }

    private companion object {
        const val NONE = -1
    }
}
