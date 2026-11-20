package my.github.MrxSiN.pixellockscreenevolved.core

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.res.Resources
import android.graphics.Typeface
import android.graphics.fonts.Font
import android.graphics.fonts.FontFamily

import java.util.concurrent.ConcurrentHashMap

import my.github.MrxSiN.pixellockscreenevolved.clock.FontSource

/**
 * Reads fonts from this module's own APK, whichever host process asks.
 *
 * The host's package manager opens the module's resources from [module], the
 * module's own application info, so nothing is copied out of the APK. Each font
 * is read once per process; one that cannot be read is reported once and asked
 * for again next time.
 */
internal class ModuleFonts(private val module: ApplicationInfo, private val logger: Logger) : FontSource {

    private val loaded = ConcurrentHashMap<String, Typeface>()

    /** Held so the asset manager the fonts were read through outlives them. */
    @Volatile
    private var resources: Resources? = null

    override fun typeface(context: Context, asset: String): Typeface? = loaded[asset] ?: runCatching {
        val assets = (resources ?: context.packageManager.getResourcesForApplication(module).also { resources = it }).assets
        val family = FontFamily.Builder(Font.Builder(assets, asset).build()).build()
        Typeface.CustomFallbackBuilder(family).build()
    }.onFailure { logger.warn("Font $asset could not be read from the module", it) }
        .getOrNull()
        ?.also { loaded[asset] = it }
}
