package my.github.MrxSiN.pixellockscreenevolved.host

import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.WeakHashMap

import my.github.MrxSiN.pixellockscreenevolved.clock.ClockFrame
import my.github.MrxSiN.pixellockscreenevolved.clock.ClockOption

/**
 * What Wallpaper & style's process knows about this module's clocks while a
 * person edits one.
 *
 * The picker has no place of its own for a clock size or position, so the
 * controls the module adds keep their choices here until Apply, show them on
 * every preview clock the picker has built, and hand them to the setting Apply
 * writes.
 */
internal class PickerClockState(private val api: ClockPluginApi) {

    /** The picker's clock registry, once it exists. */
    @Volatile
    var registry: Any? = null

    /** The size step moved to since the picker opened, or null if the slider was not touched. */
    @Volatile
    var chosenSizeStep: Float? = null
        private set

    /** The position chosen since the picker opened, or null if it was not touched. */
    @Volatile
    var chosenPosition: ClockPosition? = null
        private set

    /** The frame a person stretched and moved the clock to since the picker opened, or null if they did not. */
    @Volatile
    var chosenFrame: ClockFrame? = null
        private set

    /** The step each option was moved to since the picker opened, by key, with the option it was chosen for. */
    private val chosenOptions = ConcurrentHashMap<String, Pair<ClockOption, Int>>()

    private val choiceListeners = mutableListOf<() -> Unit>()

    private val previews = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<ClockControllerAdapter, Boolean>()))

    fun track(preview: ClockControllerAdapter) {
        previews.add(preview)
    }

    /** Calls [listener] whenever a size or a position is chosen from now on. */
    fun onAxesChosen(listener: () -> Unit) {
        choiceListeners.add(listener)
    }

    /** Records [step], shows it on every preview clock, and tells whoever listens. */
    fun chooseSizeStep(step: Float) {
        chosenSizeStep = step
        synchronized(previews) { previews.toList() }.forEach { it.previewSizeStep(step) }
        choiceListeners.forEach { it() }
    }

    /** Records [frame], shows it on every preview clock, and tells whoever listens. */
    fun chooseFrame(frame: ClockFrame) {
        chosenFrame = frame
        synchronized(previews) { previews.toList() }.forEach { it.previewFrame(frame) }
        choiceListeners.forEach { it() }
    }

    /** The frame the setting in effect stores. */
    fun storedFrame(): ClockFrame = ClockFrame.of(
        storedAxis(ClockAxes.FRAME_SCALE_X_KEY),
        storedAxis(ClockAxes.FRAME_SCALE_Y_KEY),
        storedAxis(ClockAxes.FRAME_SHIFT_X_KEY),
    )

    /** The frame shown now: the one chosen since the picker opened, or the stored one. */
    fun shownFrame(): ClockFrame = chosenFrame ?: storedFrame()

    /** Records [step] of [option], shows it on every preview clock, and tells whoever listens. */
    fun chooseOption(option: ClockOption, step: Int) {
        chosenOptions[option.key] = option to step
        synchronized(previews) { previews.toList() }.forEach { it.previewOption(option.key, step.toFloat()) }
        choiceListeners.forEach { it() }
    }

    /** The step the option under [key] was moved to since the picker opened, or null if it was not touched. */
    fun chosenOptionStep(key: String): Int? = chosenOptions[key]?.second

    /** Records [position], shows it on every preview clock, and tells whoever listens. */
    fun choosePosition(position: ClockPosition) {
        chosenPosition = position
        synchronized(previews) { previews.toList() }.forEach { it.previewPosition(position) }
        choiceListeners.forEach { it() }
    }

    /** Whether the size step moved to differs from the one the setting in effect stores. */
    fun isSizeStepEdited(): Boolean {
        val chosen = chosenSizeStep ?: return false
        return chosen != (storedAxis(ClockAxes.SIZE_KEY) ?: 0f)
    }

    /** Whether the position chosen differs from the one the setting in effect stores. */
    fun isPositionEdited(): Boolean {
        val chosen = chosenPosition ?: return false
        return chosen != storedPosition()
    }

    /** Whether any option moved to differs from the step the setting in effect stores. */
    fun isOptionEdited(): Boolean = chosenOptions.values.any { (option, step) -> step != option.stepOf(storedAxis(option.key)) }

    /** Whether the size, the position or an option chosen differs from what the setting in effect stores. */
    fun isAxisEdited(): Boolean = isSizeStepEdited() || isPositionEdited() || isOptionEdited() || isFrameEdited()

    /** Whether the frame chosen differs from the one the setting in effect stores. */
    fun isFrameEdited(): Boolean = chosenFrame?.let { it != storedFrame() } ?: false

    /** The axes chosen since the picker opened, for a preset to carry to the preview and to Apply. */
    fun chosenAxes(): Map<String, Float> = buildMap {
        chosenSizeStep?.let { put(ClockAxes.SIZE_KEY, it) }
        chosenPosition?.let {
            put(ClockAxes.POSITION_KEY, it.modeValue)
            put(ClockAxes.POSITION_SHARE_KEY, it.share)
        }
        chosenOptions.forEach { (key, chosen) -> put(key, chosen.second.toFloat()) }
        chosenFrame?.let {
            put(ClockAxes.FRAME_SCALE_X_KEY, it.scaleX)
            put(ClockAxes.FRAME_SCALE_Y_KEY, it.scaleY)
            put(ClockAxes.FRAME_SHIFT_X_KEY, it.shiftX)
        }
    }

    /** The position the setting in effect stores. */
    fun storedPosition(): ClockPosition =
        ClockPosition.of(storedAxis(ClockAxes.POSITION_KEY), storedAxis(ClockAxes.POSITION_SHARE_KEY))

    /** The position shown now: the one chosen since the picker opened, or the stored one. */
    fun shownPosition(): ClockPosition = chosenPosition ?: storedPosition()

    /** The tabs this module adds to the clock sheet, riding on its Size tab's slot ([PickerPositionTab]). */
    enum class SheetTab {
        POSITION, GLASS, NOTIFICATIONS;

        companion object {
            /** The tab an option shown outside the Size tab is in, or null for the Size tab. */
            fun of(tab: ClockOption.Tab): SheetTab? = when (tab) {
                ClockOption.Tab.SIZE -> null
                ClockOption.Tab.GLASS -> GLASS
                ClockOption.Tab.NOTIFICATIONS -> NOTIFICATIONS
            }
        }
    }

    /** Which of this module's tabs is open, or null while the picker's own are; [onTabChanged] hears each change. */
    @Volatile
    var openTab: SheetTab? = null
        set(value) {
            if (field == value) return
            field = value
            tabListeners.forEach { it(value) }
        }

    /** Whether the clock sheet's Position tab is open, so the preview's clock can be dragged. */
    var isEditingPosition: Boolean
        get() = openTab == SheetTab.POSITION
        set(value) {
            openTab = if (value) SheetTab.POSITION else openTab.takeUnless { it == SheetTab.POSITION }
        }

    private val tabListeners = mutableListOf<(SheetTab?) -> Unit>()

    /** Calls [listener] whenever one of this module's tabs opens or closes from now on. */
    fun onTabChanged(listener: (SheetTab?) -> Unit) {
        tabListeners.add(listener)
    }

    /** Forgets a size or position chosen earlier, as a newly opened picker should. */
    fun reset() {
        chosenSizeStep = null
        chosenPosition = null
        chosenOptions.clear()
        chosenFrame = null
        openTab = null
    }

    /** The value the setting in effect stores for [key], or null when it stores none. */
    fun storedAxis(key: String): Float? = registry?.let(api::registrySettings)?.let { api.axis(it, key) }

    /** The id of the clock the setting in effect chooses. */
    fun storedClockId(): String? = registry?.let(api::registrySettings)?.let(api::clockId)
}
