package my.github.MrxSiN.pixellockscreenevolved.clock

import android.content.Context
import android.graphics.Typeface

/**
 * Fonts this module ships in its own APK, for a style drawn in a font the
 * system does not carry.
 *
 * The clocks run inside SystemUI and Wallpaper & style, whose own assets do
 * not hold them, so a style asks here rather than opening a file itself.
 */
fun interface FontSource {

    /** The font at [asset] in the module's assets, or null where it cannot be read. */
    fun typeface(context: Context, asset: String): Typeface?
}
