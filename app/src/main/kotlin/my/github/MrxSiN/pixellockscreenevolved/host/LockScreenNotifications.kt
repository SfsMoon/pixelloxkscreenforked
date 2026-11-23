package my.github.MrxSiN.pixellockscreenevolved.host

import android.content.ContentResolver
import android.graphics.Canvas
import android.graphics.RectF
import android.view.View
import android.view.ViewGroup

import java.util.Collections
import java.util.WeakHashMap

import kotlin.math.abs

import my.github.MrxSiN.pixellockscreenevolved.core.Logger
import my.github.MrxSiN.pixellockscreenevolved.hook.Hooks
import my.github.MrxSiN.pixellockscreenevolved.hook.HostPatch

/**
 * The lock screen's notifications beside a clock of this module's that has a
 * say in them ([my.github.MrxSiN.pixellockscreenevolved.clock.LockScreenGlassFace]):
 *
 * - each card's background is drawn as the face's notification glass, through
 *   the photo behind, in place of SystemUI's ([GlassSurfacePainter]);
 * - the stack is lifted to stand right under the clock, where the scene left
 *   room for rows it does not show; it is never pushed down;
 * - whether a full card stands on the lock screen is told to the depth effect
 *   ([shown]), which leaves the time whole while one does, as cards and a
 *   subject in front of the time made too much to read. The newest of compact
 *   notifications is a full card too; a shelf of icons alone leaves it be.
 *
 * Away from such a clock, in the shade or beside a Pixel clock, everything is
 * SystemUI's own.
 */
internal class LockScreenNotifications(
    private val hooks: Hooks,
    private val logger: Logger,
) : HostPatch {

    private val cards = WeakHashMap<View, GlassSurfacePainter>()
    private val cardBounds = RectF()
    private val stacks = Collections.newSetFromMap(WeakHashMap<View, Boolean>())
    private val lifted = WeakHashMap<View, Float>()

    override fun install(classLoader: ClassLoader) {
        val api = try {
            LockScreenNotificationApi(classLoader)
        } catch (error: ReflectiveOperationException) {
            logger.warn("Lock screen notifications not found; they keep SystemUI's look and place", error)
            return
        }
        // A failure here is caught and told in the status, rather than counted against the feature: the
        // feature stopped as faulty took the glass, the lift and the depth effect's rule with it.
        hooks.replaceArguments(api.backgroundDraw) { background, args ->
            val view = background as? View ?: return@replaceArguments args
            val canvas = args.firstOrNull() as? Canvas ?: return@replaceArguments args
            // Drawn here as glass, SystemUI's own background is drawn into nothing.
            val glass = runCatching { drawGlass(api, view, canvas) }.onFailure { status.failed("glass", it) }.getOrDefault(false)
            if (glass) listOf(NOTHING) else args
        }
        hooks.after(api.stackComposed) { element, _ ->
            runCatching {
                val stack = api.stackOf(requireNotNull(element))
                if (stack == null) status.skip("no stack view") else follow(stack, { api.stackOnLockScreen(stack) != false }, api::isCard)
            }.onFailure { status.failed("stack", it) }
        }
    }

    /** Draws [view]'s card as glass into [canvas], where the clock beside it asks; false to leave it to SystemUI. */
    private fun drawGlass(api: LockScreenNotificationApi, view: View, canvas: Canvas): Boolean {
        if (!canvas.isHardwareAccelerated) return status.skip("software canvas")
        val face = ModuleClockFaces.glassFaceIn(view.rootView) ?: return status.skip("no clock beside")
        // In the shade, pulled down over the lock screen, the cards are SystemUI's own again.
        if (api.cardOnLockScreen(view) == false) return status.skip("in the shade")
        val glass = face.notificationGlass ?: return status.skip("stock chosen")
        if (!api.backgroundBounds(view, cardBounds)) return status.skip("empty card")
        val painter = cards.getOrPut(view) { GlassSurfacePainter(view) }
        val reference = REFERENCE_DP * view.resources.displayMetrics.density
        if (!painter.draw(canvas, cardBounds, api.backgroundRadii(view), glass, face.backdrop, reference)) return status.skip("photo not placed")
        status.drew(view.context.contentResolver)
        return true
    }

    /** Follows the lock screen's notification [stack] before each draw, once. */
    private fun follow(stack: View, onLockScreen: () -> Boolean, isCard: (View) -> Boolean) {
        if (!stacks.add(stack)) return
        status.followed++
        BeforeEachDraw.follow(stack) { place(stack, onLockScreen(), isCard) }
    }

    /** What the stack shows, as last looked at: the top of its highest row, and whether any row is a full card. */
    private var rowTop: Float? = null
    private var cardShown = false

    private val rowBounds = RectF()
    private val faceBounds = RectF()

    /** Lifts [stack] to stand right under the clock, and says whether full notifications stand on the lock screen. */
    private fun place(stack: View, onLockScreen: Boolean, isCard: (View) -> Boolean) {
        // In the shade, pulled down over the lock screen, the stack is SystemUI's to place, and the depth effect's to keep.
        val face = ModuleClockFaces.glassFaceIn(stack.rootView)?.view?.takeIf { onLockScreen }
        lookAtRows(stack, isCard)
        val firstRow = rowTop
        val applied = lifted[stack] ?: 0f
        var wanted = 0f
        if (face != null && firstRow != null && face.isShown) {
            ViewBounds.inWindow(face, faceBounds, beforeDraw = true)
            val gap = GAP_DP * stack.resources.displayMetrics.density
            // Only ever up: a stack already under the clock stays where the scene put it.
            wanted = (applied + faceBounds.bottom + gap - firstRow).coerceIn(-MOST_LIFT_SHARE * stack.rootView.height, 0f)
        }
        if (abs(wanted - applied) >= SETTLED_PX) {
            stack.translationY += wanted - applied
            if (wanted == 0f) lifted.remove(stack) else lifted[stack] = wanted
        }
        // A full card, compact notifications' newest included; a shelf of icons alone leaves the depth effect be.
        val nowShown = face != null && cardShown
        if (nowShown != shown) {
            shown = nowShown
            face?.invalidate()
        }
        status.placed(stack.context.contentResolver, face != null, firstRow != null, wanted, cardShown, nowShown)
    }

    /**
     * Looks at what [stack] shows: the top, in window pixels, of its highest
     * row ([rowTop], null where it shows none), and whether any row is a full
     * card ([cardShown]) rather than the shelf of icons compact notifications fold into.
     */
    private fun lookAtRows(stack: View, isCard: (View) -> Boolean) {
        rowTop = null
        cardShown = false
        val group = stack as? ViewGroup ?: return
        for (i in 0 until group.childCount) {
            val row = group.getChildAt(i)
            if (row.visibility != View.VISIBLE || row.height <= 0 || row.alpha <= 0f) continue
            ViewBounds.inWindow(row, rowBounds, beforeDraw = true)
            if (rowBounds.height() <= 0f) continue
            val top = rowTop
            if (top == null || rowBounds.top < top) rowTop = rowBounds.top
            if (isCard(row)) cardShown = true
        }
    }

    /**
     * What the feature did lately, written at most once a second to a secure
     * setting, [STATUS_SETTING], for whoever follows it where the module's log
     * does not reach logcat: `settings get secure pixel_lock_screen_evolved_notifications_status`.
     */
    private class Status {
        var followed = 0
        private var drawn = 0
        private var skipped = 0
        private var lastSkip = "none"
        private var failure = "none"
        private var lift = "never placed"
        private var writtenAt = 0L

        fun skip(reason: String): Boolean {
            skipped++
            lastSkip = reason
            return false
        }

        fun drew(resolver: ContentResolver) {
            drawn++
            write(resolver)
        }

        fun failed(where: String, error: Throwable) {
            failure = "$where: ${error.javaClass.simpleName}: ${error.message}"
        }

        fun placed(resolver: ContentResolver, clock: Boolean, rows: Boolean, lifted: Float, card: Boolean, depthOff: Boolean) {
            lift = "clock=$clock rows=$rows lifted=${lifted.toInt()}px fullCard=$card depthOff=$depthOff"
            write(resolver)
        }

        private fun write(resolver: ContentResolver) {
            val now = System.currentTimeMillis()
            if (now - writtenAt < WRITE_EVERY_MS) return
            writtenAt = now
            runCatching {
                android.provider.Settings.Secure.putString(
                    resolver, STATUS_SETTING,
                    "stacks=$followed drawn=$drawn skipped=$skipped lastSkip=$lastSkip | $lift | failure=$failure",
                )
            }
        }
    }

    private val status = Status()

    companion object {
        private const val STATUS_SETTING = "pixel_lock_screen_evolved_notifications_status"
        private const val WRITE_EVERY_MS = 1000L

        /** Whether full notifications stand on the lock screen beside a clock of this module's; read before each frame. */
        @Volatile
        var shown: Boolean = false
            private set


        /** What SystemUI's background is drawn into while glass stands in its place: nothing. */
        private val NOTHING = Canvas()

        /** Space between the clock and the first card. */
        const val GAP_DP = 4f

        /** The most the stack is lifted, against the window's height. */
        const val MOST_LIFT_SHARE = 0.5f

        const val SETTLED_PX = 0.5f

        /** The size glass on a card is shaped for: its bevel and how far it bends what is behind. */
        const val REFERENCE_DP = 220f

    }
}
