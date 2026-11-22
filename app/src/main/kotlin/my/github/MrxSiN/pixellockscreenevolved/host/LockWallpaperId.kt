package my.github.MrxSiN.pixellockscreenevolved.host

import android.app.WallpaperManager
import android.content.Context

/** Which wallpaper the lock screen shows: its own, or the home screen's where it shares that one. */
internal object LockWallpaperId {

    /** The wallpaper manager's flag for the wallpaper the lock screen shows. */
    fun which(manager: WallpaperManager): Int =
        if (manager.getWallpaperId(WallpaperManager.FLAG_LOCK) > 0) WallpaperManager.FLAG_LOCK else WallpaperManager.FLAG_SYSTEM

    /** The id of the wallpaper the lock screen shows, which changes each time one is set, or -1 where it cannot be read. */
    fun current(context: Context): Int = runCatching {
        val manager = WallpaperManager.getInstance(context)
        manager.getWallpaperId(which(manager))
    }.getOrDefault(-1)
}
