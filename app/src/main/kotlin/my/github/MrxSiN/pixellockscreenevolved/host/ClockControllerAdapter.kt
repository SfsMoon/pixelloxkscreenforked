package my.github.MrxSiN.pixellockscreenevolved.host

import android.content.Context

import java.io.PrintWriter

import my.github.MrxSiN.pixellockscreenevolved.clock.ClockFrame
import my.github.MrxSiN.pixellockscreenevolved.clock.ClockStyle
import my.github.MrxSiN.pixellockscreenevolved.clock.FaceSize
import my.github.MrxSiN.pixellockscreenevolved.clock.WeatherClockFace

/**
 * A [ClockStyle] presented to SystemUI as one `ClockController`: a large and a
 * small face, the clock-wide events, and the configuration that names it.
 */
internal class ClockControllerAdapter(
    private val api: ClockPluginApi,
    private val proxies: InterfaceProxy,
    private val style: ClockStyle,
    private val context: Context,
    settings: Any,
) {

    private val initialTheme = api.theme(isDarkTheme = true, seedColor = api.seedColor(settings))

    private val large = face(FaceSize.LARGE)
    private val small = face(FaceSize.SMALL)

    /** Both faces, for whatever needs the one on screen. */
    val faces: List<ClockFaceAdapter> = listOf(large, small)

    init {
        val width = api.axis(settings, ClockAxes.WIDTH_KEY)
        val sizeStep = api.axis(settings, ClockAxes.SIZE_KEY)
        faces.forEach {
            it.setWidth(width)
            it.setSizeStep(sizeStep)
        }
        large.setPosition(api.axis(settings, ClockAxes.POSITION_KEY), api.axis(settings, ClockAxes.POSITION_SHARE_KEY))
        framed().forEach {
            it.setFrame(
                ClockFrame.of(
                    api.axis(settings, ClockAxes.FRAME_SCALE_X_KEY),
                    api.axis(settings, ClockAxes.FRAME_SCALE_Y_KEY),
                    api.axis(settings, ClockAxes.FRAME_SHIFT_X_KEY),
                ),
            )
        }
        style.options.forEach { option ->
            val stored = api.axis(settings, option.key)
            faces.forEach { it.setOption(option.key, stored) }
        }
    }

    /** The large face, the one the depth effect draws in front of. */
    val largeFace: ClockFaceAdapter get() = large

    /** The small face, shown while notifications are. */
    val smallFace: ClockFaceAdapter get() = small

    /** Shows [step] on the large face at once, as the picker's size slider moves. */
    fun previewSizeStep(step: Float) {
        framed().forEach { it.setSizeStep(step) }
    }

    /** The faces a size or a frame chosen for the large face applies to: the small one too where it follows it. */
    private fun framed(): List<ClockFaceAdapter> = if (style.smallFaceFollowsLarge) faces else listOf(large)

    /** Shows [step] of the option under [key] on both faces at once, as the picker's option sliders move. */
    fun previewOption(key: String, step: Float) {
        faces.forEach { it.setOption(key, step) }
    }

    /** Shows [frame] on the large face at once, as a person stretches or moves it in the preview. */
    fun previewFrame(frame: ClockFrame) {
        framed().forEach { it.setFrame(frame) }
    }

    /** Shows [position] on the large face at once, as the picker's position controls change. */
    fun previewPosition(position: ClockPosition) {
        large.clockLayout.position = position
    }

    /** Anything that changes how the time is written redraws both faces. */
    private val refreshFaces: MethodHandler = { _ -> faces.forEach { it.refresh() } }

    private val events = proxies.create(
        api.eventsType,
        mapOf(
            "onTimeZoneChanged" to refreshFaces,
            "onTimeFormatChanged" to refreshFaces,
            "onLocaleChanged" to refreshFaces,
            "onAlarmDataChanged" to IgnoreCall,
            "onWeatherDataChanged" to { args ->
                val weather = api.weather(args.firstOrNull())
                faces.forEach { (it.viewFace as? WeatherClockFace)?.setWeather(weather) }
            },
            "onZenDataChanged" to IgnoreCall,
        ),
    )

    private val config = api.clockConfig(style)
    private val eventListeners = api.eventListeners()

    /** The `ClockController` SystemUI holds for this clock. */
    val controller: Any by lazy { createController() }

    private fun createController(): Any = proxies.create(
        api.controllerType,
        mapOf(
            "getSmallClock" to { _ -> small.controller },
            "getLargeClock" to { _ -> large.controller },
            "getEvents" to { _ -> events },
            "getConfig" to { _ -> config },
            "getEventListeners" to { _ -> eventListeners },
            "initialize" to { args -> initialize(args[0] as Boolean, args[1] as Float) },
            "dump" to { args -> (args[0] as PrintWriter).println("${style.id}: ${style.name}") },
        ),
    )

    private fun initialize(isDarkTheme: Boolean, dozeFraction: Float) {
        faces.forEach {
            it.setDarkTheme(isDarkTheme)
            it.setDoze(dozeFraction)
            it.refresh()
        }
    }

    private fun face(size: FaceSize) = ClockFaceAdapter(
        api = api,
        proxies = proxies,
        face = style.createFace(context, size),
        size = size,
        sizeSteps = style.sizeSteps,
        movable = style.isMovable,
        options = style.options,
        keepsPlaceOnAod = style.keepsPlaceOnAod,
        followsLarge = style.smallFaceFollowsLarge,
        context = context,
        initialTheme = initialTheme,
    )
}
