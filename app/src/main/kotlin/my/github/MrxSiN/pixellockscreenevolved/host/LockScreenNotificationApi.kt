package my.github.MrxSiN.pixellockscreenevolved.host

import android.graphics.Canvas
import android.graphics.RectF
import android.view.View

import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * The lock screen's notifications as SystemUI draws and places them, reached
 * through its class loader. HOOK_NOTES.md records the shapes.
 *
 * Each card draws its background in a `NotificationBackgroundView`, a rounded
 * rectangle as wide and as tall as the card shows, clipped at its top and
 * bottom as the stack overlaps cards. Android 17's composed lock screen hosts
 * the stack, a view, in a scene element of its own
 * (`NotificationStackElementProvider$NotificationElement`).
 */
internal class LockScreenNotificationApi(classLoader: ClassLoader) {

    private val background = classLoader.hostClass(BACKGROUND)

    /** Draws a card's background. */
    val backgroundDraw: Method = background.getDeclaredMethod("onDraw", Canvas::class.java)

    private val actualWidth: Field = background.field("mActualWidth")
    private val actualHeight: Field = background.field("mActualHeight")
    private val clipTop: Field = background.field("mClipTopAmount")
    private val clipBottom: Field = background.field("mClipBottomAmount")
    private val cornerRadii: Field = background.field("mCornerRadii")
    private val alignedToRight: Method = background.getDeclaredMethod("isAlignedToRight").apply { isAccessible = true }

    private val card = classLoader.hostClass(CARD)
    private val cardOnKeyguard: Field = card.field("mOnKeyguard")
    private val stack = classLoader.hostClass(STACK)
    private val stackOnKeyguard: Method = stack.getDeclaredMethod("onKeyguard").apply { isAccessible = true }

    private val element = classLoader.hostClass("$STACK_ELEMENTS\$NotificationElement")

    /** Composes the stack into the lock screen scene, where it can be found and followed. */
    val stackComposed: Method = element.declaredMethods.first { it.name == "LockscreenElement" }
    private val elementProvider: Field = element.field("this\$0")
    private val stackView: Field = classLoader.hostClass(STACK_ELEMENTS).field("stackScrollView")

    /** Whether [view] is a card's background. */
    fun isBackground(view: Any?): Boolean = background.isInstance(view)

    /**
     * Writes into [out] the rectangle a card's [view] draws its background in,
     * in the view's own pixels, as SystemUI draws it; false where it draws none.
     */
    fun backgroundBounds(view: View, out: RectF): Boolean {
        val width = (actualWidth.getInt(view)).takeIf { it > 0 } ?: view.width
        val left = if (alignedToRight.invoke(view) == true) (view.width - width).toFloat() else 0f
        val top = clipTop.getInt(view).toFloat()
        val bottom = (actualHeight.getInt(view) - clipBottom.getInt(view)).toFloat()
        if (bottom <= top || width <= 0) return false
        out.set(left, top, left + width, bottom)
        return true
    }

    /**
     * Whether the card [background] is drawn for stands on the lock screen
     * rather than in the shade, as SystemUI's own state says: the shade pulled
     * down over the lock screen holds its cards there no longer. Null where
     * [background] is not in a card.
     */
    fun cardOnLockScreen(background: View): Boolean? {
        var parent = background.parent
        while (parent != null && !card.isInstance(parent)) parent = parent.parent
        return parent?.let { cardOnKeyguard.getBoolean(it) }
    }

    /** Whether the notification [stackView] stands on the lock screen rather than in the shade; null where it cannot say. */
    fun stackOnLockScreen(stackView: View): Boolean? =
        if (stack.isInstance(stackView)) stackOnKeyguard.invoke(stackView) as? Boolean else null

    /** The eight corner radii a card's background is drawn with, as `Path.addRoundRect` takes them. */
    fun backgroundRadii(view: View): FloatArray? = cornerRadii.get(view) as FloatArray?

    /**
     * The stack view the element composed by [composedElement] hosts, or null.
     * SystemUI may hold it lazily, behind a `dagger.Lazy` or a
     * `javax.inject.Provider`; either is asked for it.
     */
    fun stackOf(composedElement: Any): View? {
        val held = elementProvider.get(composedElement)?.let { stackView.get(it) } ?: return null
        if (held is View) return held
        return runCatching { held.javaClass.getMethod("get").invoke(held) }.getOrNull() as? View
    }

    private companion object {
        const val BACKGROUND = "com.android.systemui.statusbar.notification.row.NotificationBackgroundView"
        const val CARD = "com.android.systemui.statusbar.notification.row.ActivatableNotificationView"
        const val STACK = "com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayout"
        const val STACK_ELEMENTS = "com.android.systemui.keyguard.ui.composable.elements.NotificationStackElementProvider"
    }
}
