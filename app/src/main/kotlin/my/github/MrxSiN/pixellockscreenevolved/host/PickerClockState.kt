package my.github.MrxSiN.pixellockscreenevolved.host

import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.WeakHashMap

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
    fun isAxisEdited(): Boolean = isSizeStepEdited() || isPositionEdited() || isOptionEdited()

    /** The axes chosen since the picker opened, for a preset to carry to the preview and to Apply. */
    fun chosenAxes(): Map<String, Float> = buildMap {
        chosenSizeStep?.let { put(ClockAxes.SIZE_KEY, it) }
        chosenPosition?.let {
            put(ClockAxes.POSITION_KEY, it.modeValue)
            put(ClockAxes.POSITION_SHARE_KEY, it.share)
        }
        chosenOptions.forEach { (key, chosen) -> put(key, chosen.second.toFloat()) }
    }

    /** The position the setting in effect stores. */
    fun storedPosition(): ClockPosition =
        ClockPosition.of(storedAxis(ClockAxes.POSITION_KEY), storedAxis(ClockAxes.POSITION_SHARE_KEY))

    /** The position shown now: the one chosen since the picker opened, or the stored one. */
    fun shownPosition(): ClockPosition = chosenPosition ?: storedPosition()

    /**
     * Whether the clock sheet's Position tab is open, so the preview's clock
     * can be dragged; [onPositionEditing] hears each change.
     */
    @Volatile
    var isEditingPosition = false
        set(value) {
            if (field == value) return
            field = value
            editingListeners.forEach { it(value) }
        }

    private val editingListeners = mutableListOf<(Boolean) -> Unit>()

    /** Calls [listener] whenever the Position tab opens or closes from now on. */
    fun onPositionEditing(listener: (Boolean) -> Unit) {
        editingListeners.add(listener)
    }

    /** Forgets a size or position chosen earlier, as a newly opened picker should. */
    fun reset() {
        chosenSizeStep = null
        chosenPosition = null
        chosenOptions.clear()
        isEditingPosition = false
    }

    /** The value the setting in effect stores for [key], or null when it stores none. */
    fun storedAxis(key: String): Float? = registry?.let(api::registrySettings)?.let { api.axis(it, key) }

    /** The id of the clock the setting in effect chooses. */
    fun storedClockId(): String? = registry?.let(api::registrySettings)?.let(api::clockId)
}
