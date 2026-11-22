package my.github.MrxSiN.pixellockscreenevolved.diagnostics

import android.os.Build

import java.io.File

import kotlin.concurrent.thread

import my.github.MrxSiN.pixellockscreenevolved.core.Logger
import my.github.MrxSiN.pixellockscreenevolved.hook.HostPatch

/**
 * Writes down the running host's build ([HostDescriptor]) when a developer asks
 * for it, so the build can join the recorded builds the tests check every
 * contract against.
 *
 * Asked for with `adb shell setprop debug.pixel_lock_screen_evolved.capture 1`
 * before the host starts; `scripts/capture-host-fixture.sh` asks, restarts the
 * hosts and pulls what they wrote. The description is taken through the same
 * class loader the contract check reads, so it is the host exactly as the check
 * sees it. Nothing is read or written unless asked.
 */
class HostDescriptorCapture(
    private val host: Host,
    private val types: HostTypes,
    private val file: File,
    private val logger: Logger,
    /**
     * Lists the host's named classes in packages a developer is exploring, or
     * null where none are; what it lists is described into [DISCOVERY_FILE]
     * beside [file], so a feature not yet written can be written against the
     * host's real names rather than guessed ones.
     */
    private val discover: ((List<String>) -> List<String>)? = null,
) : HostPatch {

    override fun install(classLoader: ClassLoader) {
        if (!isRequested()) return
        // Off the host's start: a description reads a few hundred classes.
        thread(name = "PixelLockScreenEvolved-capture") {
            runCatching {
                // Written aside and moved in whole, so a reader never sees half of it.
                val written = File(file.path + ".tmp")
                written.writeText(HostDescriptor.write(Build.ID, host, types, HostContracts.classesNamed(host), HostSymbols.current))
                check(written.renameTo(file)) { "Could not move $written to $file" }
                logger.info("Described ${host.packageName} on ${Build.ID} in $file")
                val explored = EXPLORED[host].orEmpty()
                if (explored.isNotEmpty() && discover != null) {
                    val found = discover.invoke(explored)
                    val target = File(file.parentFile, DISCOVERY_FILE)
                    target.writeText(HostDescriptor.write(Build.ID, host, types, found))
                    logger.info("Described ${found.size} classes of ${host.packageName} being explored in $target")
                }
            }.onFailure { logger.warn("Could not describe ${host.packageName}", it) }
        }
    }

    private fun isRequested(): Boolean = runCatching {
        Class.forName("android.os.SystemProperties").getMethod("get", String::class.java).invoke(null, PROPERTY) == "1"
    }.getOrDefault(false)

    companion object {
        const val PROPERTY = "debug.pixel_lock_screen_evolved.capture"

        /** Where each host writes its description, in its own device-protected storage. */
        const val FILE = "pixel_lock_screen_evolved_host_descriptor.txt"

        /** Where each host writes what it was asked to explore, beside [FILE]. */
        const val DISCOVERY_FILE = "pixel_lock_screen_evolved_host_discovery.txt"

        /**
         * The packages explored for features being written: the lock screen's
         * notifications, where they stand and how they are drawn, for glass
         * notifications and notifications placed under the clock.
         */
        private val EXPLORED = mapOf(
            Host.SYSTEMUI to listOf(
                "com.android.systemui.statusbar.notification.stack",
                "com.android.systemui.statusbar.notification.row",
                "com.android.systemui.statusbar.notification.shared",
                "com.android.systemui.keyguard.ui.composable",
                "com.android.systemui.notifications.ui",
            ),
        )
    }
}
