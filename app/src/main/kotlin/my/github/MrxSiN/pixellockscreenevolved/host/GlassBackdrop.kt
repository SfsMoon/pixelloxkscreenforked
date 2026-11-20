package my.github.MrxSiN.pixellockscreenevolved.host

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Handler
import android.os.Looper
import android.view.View

import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.Executors

import my.github.MrxSiN.pixellockscreenevolved.clock.Backdrop
import my.github.MrxSiN.pixellockscreenevolved.clock.BackdropClockFace
import my.github.MrxSiN.pixellockscreenevolved.core.Logger

/**
 * The lock screen photo behind every face that draws through it
 * ([BackdropClockFace]), as glass does.
 *
 * Each new photo ([LockWallpaperFeed]) is shrunk and blurred once, off the main
 * thread, and handed to the faces with where it stands on screen, worked out on
 * every draw as the depth effect places its subject ([DepthLayer]): from the
 * wallpaper's placement on screen into the face's own pixels. A face is drawn
 * again whenever the wallpaper's placement changes or the scene moves the face
 * on screen, so the photo through the glass stays where the photo is. In a
 * preview of another photo, which the picker draws itself
 * ([KeyguardPreviewWallpapers]), the faces are told there is none.
 */
internal class GlassBackdrop(
    private val feed: LockWallpaperFeed,
    private val previews: KeyguardPreviewWallpapers,
    logger: Logger,
) : LockWallpaperFeed.Listener {

    /** The wallpaper as stored, shown until SystemUI draws its photo again. */
    private val stored = StoredWallpaperGlass(logger, StoredWallpaperGlass.LOCK_SCREEN)

    private val faces = Collections.synchronizedMap(WeakHashMap<ClockFaceAdapter, Follower>())
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()

    /** The photo as the faces draw it, and the size of the surface it is stretched to; on the main thread. */
    private var photo: Photo? = null

    private class Photo(val pictures: GlassPhoto.Pictures, val surface: WallpaperPlacement.Size)

    /** Shows the photo through every face of [clock] that draws through one. */
    fun track(clock: ClockControllerAdapter) {
        main.post {
            clock.faces.forEach { face ->
                val drawn = face.viewFace as? BackdropClockFace ?: return@forEach
                if (face in faces) return@forEach
                val follower = Follower(face, drawn) { previews.showsOtherWallpaper(clock.controller) }
                faces[face] = follower
                follower.show(photo)
            }
            // The photo is handed on only as SystemUI draws one it has not drawn before, and the same photo is
            // not drawn again until another is set: until then the wallpaper as stored is shown, laid out by default.
            if (photo == null) {
                feed.forgetPhoto()
                stored.track(clock) { photo == null && !previews.showsOtherWallpaper(clock.controller) }
            }
        }
    }

    override fun onPhoto(photo: Bitmap, surface: WallpaperPlacement.Size, key: String, context: Context) {
        // The engine may let its photo go once this returns, so the small copy is made now and blurred later.
        val small = GlassPhoto.shrink(photo) ?: return
        worker.execute {
            val blurred = GlassPhoto.pictures(small)
            main.post {
                val next = Photo(blurred, surface)
                this.photo = next
                synchronized(faces) { faces.values.toList() }.forEach { it.show(next) }
            }
        }
    }

    override fun onPlacementChanged() {
        synchronized(faces) { faces.values.toList() }.forEach { it.invalidate() }
    }

    /** One face drawn through the photo, and what keeps it drawn where the photo is. */
    private inner class Follower(
        private val face: ClockFaceAdapter,
        private val drawn: BackdropClockFace,
        private val overOtherWallpaper: () -> Boolean,
    ) {
        private val faceToWindow = Matrix()
        private val drawnAt = Matrix()
        private val sceneFrames = SceneFrames(face.view) { if (moved()) invalidate() }

        init {
            BeforeEachDraw.follow(face.view) {
                if (moved()) invalidate()
                sceneFrames.start()
            }
        }

        fun show(photo: Photo?) {
            drawn.setBackdrop(photo?.let { Placed(it) })
        }

        fun invalidate() {
            face.timeView.invalidate()
        }

        /** Whether the face stands elsewhere on screen than when it was last drawn, remembering where it stands now. */
        private fun moved(): Boolean {
            ViewBounds.toWindow(face.view, faceToWindow)
            if (faceToWindow == drawnAt) return false
            drawnAt.set(faceToWindow)
            return true
        }

        /** [photo], placed on screen as the wallpaper is. */
        private inner class Placed(private val photo: Photo) : Backdrop {

            override val picture: Bitmap get() = photo.pictures.sharp

            override val frosted: Bitmap get() = photo.pictures.frosted

            private val viewToWindow = Matrix()
            private val rootToWindow = Matrix()
            private val windowToRoot = Matrix()
            private val rootToView = Matrix()
            private var screen = WallpaperPlacement.Size(0, 0)

            override fun place(view: View, toView: Matrix): Boolean {
                if (overOtherWallpaper()) return false
                val root = view.rootView
                val metrics = view.resources.displayMetrics
                if (screen.width != metrics.widthPixels || screen.height != metrics.heightPixels) {
                    screen = WallpaperPlacement.Size(metrics.widthPixels, metrics.heightPixels)
                }
                if (screen.width <= 0 || screen.height <= 0 || root.width <= 0 || root.height <= 0) return false
                val placed = feed.place(screen, photo.surface)
                toView.setScale(placed.width / picture.width, placed.height / picture.height)
                toView.postTranslate(placed.left, placed.top)
                toView.postScale(placed.scale, placed.scale, placed.pivotX, placed.pivotY)
                // The screen onto the root view's own pixels: the same on the lock screen; in a preview the root
                // stands for the screen, drawn smaller in the picker.
                toView.postScale(root.width.toFloat() / screen.width, root.height.toFloat() / screen.height)
                ViewBounds.toWindow(view, viewToWindow)
                ViewBounds.toWindow(root, rootToWindow)
                if (!rootToWindow.invert(windowToRoot)) return false
                viewToWindow.postConcat(windowToRoot)
                if (!viewToWindow.invert(rootToView)) return false
                toView.postConcat(rootToView)
                return true
            }
        }
    }
}
