package my.github.MrxSiN.pixellockscreenevolved.host

import android.content.ContentResolver
import android.content.res.Configuration
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver

import java.util.Collections
import java.util.WeakHashMap

import kotlin.math.abs

import my.github.MrxSiN.pixellockscreenevolved.clock.Backdrop
import my.github.MrxSiN.pixellockscreenevolved.clock.parts.GlassLook
import my.github.MrxSiN.pixellockscreenevolved.clock.parts.LiquidGlass
import my.github.MrxSiN.pixellockscreenevolved.core.Logger
import my.github.MrxSiN.pixellockscreenevolved.hook.Hooks
import my.github.MrxSiN.pixellockscreenevolved.hook.HostPatch

/**
 * The lock screen's notifications beside a clock of this module's that has a
 * say in them ([my.github.MrxSiN.pixellockscreenevolved.clock.NotificationGlassFace]):
 *
 * - each card's background is drawn as the face's notification glass, through
 *   the photo behind ([LiquidGlass]), in place of SystemUI's ([CardGlass]);
 * - the stack is lifted to stand right under the clock, where the scene left
 *   room for rows it does not show; it is never pushed down;
 * - whether full notifications stand on the lock screen is told to the depth
 *   effect ([shown]), which leaves the time whole while they do, as cards and
 *   a subject in front of the time made too much to read. Compact
 *   notifications, a row of icons, leave it be.
 *
 * Away from such a clock, in the shade or beside a Pixel clock, everything is
 * SystemUI's own.
 */
internal class LockScreenNotifications(
    private val hooks: Hooks,
    private val logger: Logger,
) : HostPatch {

    private val cards = WeakHashMap<View, CardGlass>()
    private val stacks = Collections.newSetFromMap(WeakHashMap<View, Boolean>())
    private val lifted = WeakHashMap<View, Float>()
    private var compact: SecureIntSetting? = null
    private var compactNow = true

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
                if (stack == null) status.skip("no stack view") else follow(stack)
            }.onFailure { status.failed("stack", it) }
        }
    }

    /** Draws [view]'s card as glass into [canvas], where the clock beside it asks; false to leave it to SystemUI. */
    private fun drawGlass(api: LockScreenNotificationApi, view: View, canvas: Canvas): Boolean {
        if (!canvas.isHardwareAccelerated) return status.skip("software canvas")
        val face = ModuleClockFaces.notificationGlassIn(view.rootView) ?: return status.skip("no clock beside")
        val look = face.notificationGlass ?: return status.skip("stock chosen")
        val backdrop = face.backdrop ?: return status.skip("no photo")
        val card = cards.getOrPut(view) { CardGlass(view) }
        val bounds = card.bounds
        if (!api.backgroundBounds(view, bounds)) return status.skip("empty card")
        if (!card.draw(canvas, api.backgroundRadii(view), look, backdrop)) return status.skip("photo not placed")
        status.drew(view.context.contentResolver)
        return true
    }

    /** Follows the lock screen's notification [stack] before each draw, once. */
    private fun follow(stack: View) {
        if (!stacks.add(stack)) return
        status.followed++
        if (compact == null) {
            compact = COMPACT
            stack.context.contentResolver.let { resolver -> watchCompact(resolver) }
        }
        BeforeEachDraw.follow(stack) { place(stack) }
    }

    private fun watchCompact(resolver: ContentResolver) {
        COMPACT.observe(resolver) { compactNow = COMPACT.get(resolver) != 0 }
    }

    private val rowBounds = RectF()
    private val faceBounds = RectF()

    /** Lifts [stack] to stand right under the clock, and says whether full notifications stand on the lock screen. */
    private fun place(stack: View) {
        val face = ModuleClockFaces.notificationGlassIn(stack.rootView)?.view
        val firstRow = firstRowTop(stack)
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
        val nowShown = face != null && firstRow != null && !compactNow
        if (nowShown != shown) {
            shown = nowShown
            face?.invalidate()
        }
        status.placed(stack.context.contentResolver, face != null, firstRow != null, wanted, compactNow, nowShown)
    }

    /** The top, in window pixels, of the highest card [stack] shows, or null where it shows none. */
    private fun firstRowTop(stack: View): Float? {
        val group = stack as? ViewGroup ?: return null
        var top: Float? = null
        for (i in 0 until group.childCount) {
            val row = group.getChildAt(i)
            if (row.visibility != View.VISIBLE || row.height <= 0 || row.alpha <= 0f) continue
            ViewBounds.inWindow(row, rowBounds, beforeDraw = true)
            if (rowBounds.height() <= 0f) continue
            if (top == null || rowBounds.top < top) top = rowBounds.top
        }
        return top
    }

    /**
     * One card's background as glass: the rounded rectangle SystemUI draws it
     * in, its height field built again only as its size or look changes, and
     * the card drawn again before any frame it has moved in, so the photo
     * through it stays where the photo is.
     */
    private class CardGlass(private val view: View) {
        val bounds = RectF()
        private val liquid = LiquidGlass()
        private val path = Path()
        private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.WHITE
            alpha = RIM_ALPHA
            strokeWidth = view.resources.displayMetrics.density
        }
        private val shaped = RectF()
        private var shapedRadii: FloatArray? = null
        private var shapedLook: GlassLook? = null
        private var shapedTint = 0
        private var placedFor: Backdrop? = null
        private var sharp: BitmapShader? = null
        private var frosted: BitmapShader? = null
        private val sharpToView = Matrix()
        private val frostedToView = Matrix()
        private val drawnAt = Matrix()
        private val standsAt = Matrix()

        init {
            view.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    if (placedFor != null) {
                        standsAt.reset()
                        view.transformMatrixToGlobal(standsAt)
                        if (standsAt != drawnAt) view.invalidate()
                    }
                    return true
                }
            })
        }

        fun draw(canvas: Canvas, radii: FloatArray?, look: GlassLook, backdrop: Backdrop): Boolean {
            val tint = cardColour(view)
            if (bounds != shaped || !radii.contentEquals(shapedRadii) || look != shapedLook || tint != shapedTint || !liquid.isReady) {
                shaped.set(bounds)
                shapedRadii = radii?.copyOf()
                shapedLook = look
                shapedTint = tint
                path.reset()
                path.addRoundRect(bounds, radii ?: FloatArray(8), Path.Direction.CW)
                liquid.prepare(path, bounds, REFERENCE_DP * view.resources.displayMetrics.density, tint, look)
            }
            if (backdrop !== placedFor) {
                placedFor = backdrop
                sharp = BitmapShader(backdrop.picture, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                frosted = BitmapShader(backdrop.frosted, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            }
            val sharpShader = sharp ?: return false
            val frostedShader = frosted ?: return false
            drawnAt.reset()
            view.transformMatrixToGlobal(drawnAt)
            if (!backdrop.place(view, sharpToView)) return false
            sharpShader.setLocalMatrix(sharpToView)
            frostedToView.setScale(backdrop.picture.width.toFloat() / backdrop.frosted.width, backdrop.picture.height.toFloat() / backdrop.frosted.height)
            frostedToView.postConcat(sharpToView)
            frostedShader.setLocalMatrix(frostedToView)
            liquid.draw(canvas, path, sharpShader, frostedShader, 1f)
            canvas.drawPath(path, rim)
            return true
        }

        /** The colour a card is tinted toward: a dark card's in the dark theme, a light one's otherwise. */
        private fun cardColour(view: View): Int {
            val night = view.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            return if (night) DARK_CARD else LIGHT_CARD
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

        fun placed(resolver: ContentResolver, clock: Boolean, rows: Boolean, lifted: Float, compact: Boolean, cardsShown: Boolean) {
            lift = "clock=$clock rows=$rows lifted=${lifted.toInt()}px compact=$compact depthOff=$cardsShown"
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

        /** SystemUI's own setting for compact lock screen notifications, a row of icons; on unless turned off. */
        private val COMPACT = SecureIntSetting("lock_screen_notification_minimalism", 1)

        /** What SystemUI's background is drawn into while glass stands in its place: nothing. */
        private val NOTHING = Canvas()

        /** Space between the clock and the first card. */
        const val GAP_DP = 4f

        /** The most the stack is lifted, against the window's height. */
        const val MOST_LIFT_SHARE = 0.5f

        const val SETTLED_PX = 0.5f

        /** The size glass on a card is shaped for: its bevel and how far it bends what is behind. */
        const val REFERENCE_DP = 220f

        const val RIM_ALPHA = 46
        val DARK_CARD = Color.rgb(24, 24, 28)
        val LIGHT_CARD = Color.rgb(246, 246, 250)
    }
}
