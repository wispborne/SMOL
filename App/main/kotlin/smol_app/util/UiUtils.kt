package smol_app.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.res.useResource
import dev.andrewbailey.diff.differenceOf
import smol_access.Constants
import smol_access.SL
import smol_access.ServiceLocator
import smol_access.business.VmParamsManager
import smol_access.config.Platform
import smol_access.model.Mod
import smol_app.themes.ThemeManager
import java.awt.Desktop
import java.net.URI
import java.nio.file.Path
import kotlin.math.ceil


fun Mod.getModThreadId(): ModThreadId? =
    findFirstEnabled?.versionCheckerInfo?.modThreadId
        ?: findHighestVersion?.versionCheckerInfo?.modThreadId

fun ModThreadId.openModThread() {
    (Constants.FORUM_PAGE_URL + this).openAsUriInBrowser()
}

fun String.openAsUriInBrowser() {
    Desktop.getDesktop().browse(URI(this))
}

fun Path.openInDesktop() = Desktop.getDesktop().open(this.toFile())

typealias ModThreadId = String

/**
 * Return a string with a maximum length of `length` characters.
 * If there are more than `length` characters, then string ends with an ellipsis ("...").
 *
 * @param text
 * @param length
 * @return
 */
fun String.ellipsizeAfter(length: Int): String? {
    // The letters [iIl1] are slim enough to only count as half a character.
    var lengthMod = length
    lengthMod += ceil(this.replace("[^iIl]".toRegex(), "").length / 2.0).toInt()
    return if (this.length > lengthMod) {
        this.substring(0, lengthMod - 3) + "…"
    } else this
}

/**
 * A mebibyte is 2^20 bytes (1024 KiB instead of 1000 KB).
 */
val Long.bytesAsReadableMiB: String
    get() = "%.3f MiB".format(this / 1048576f)

/**
 * From [https://github.com/JetBrains/skija/blob/ebd63708b35e23667c1bf65845182430d0cf0860/shared/java/impl/Platform.java].
 */
val currentPlatform: Platform
    get() {
        val os = System.getProperty("os.name").toLowerCase()

        return if (os.contains("mac") || os.contains("darwin")) {
            if ("aarch64" == System.getProperty("os.arch"))
                Platform.MacOS
            else Platform.MacOS
        } else if (os.contains("windows"))
            Platform.Windows
        else if (os.contains("nux") || os.contains("nix"))
            Platform.Linux
        else throw RuntimeException(
            "Unsupported platform: $os"
        )
    }

/**
 * Synchronously load an image file stored in resources for the application.
 * Deprecated by Compose, but the replacement doesn't give an [ImageBitmap] so it's useless.
 *
 * @param resourcePath path to the image file
 * @return the decoded image data associated with the resource
 */
@Composable
fun imageResource(resourcePath: String): ImageBitmap {
    return remember(resourcePath) {
        useResource(resourcePath, ::loadImageBitmap)
    }
}

fun <T> MutableList<T>.replaceAllUsingDifference(newList: List<T>, doesOrderMatter: Boolean) {
    differenceOf(
        original = this,
        updated = newList,
        detectMoves = doesOrderMatter
    )
        .applyDiff(
            remove = { this.removeAt(it) },
            insert = { item, index -> this.add(index, item) },
            move = { oldIndex, newIndex ->
                this.add(
                    element = this.removeAt(oldIndex),
                    index = if (newIndex < oldIndex) {
                        newIndex
                    } else {
                        newIndex - 1
                    }
                )
            }
        )
}

val ServiceLocator.vmParamsManager: VmParamsManager
    get() = VmParamsManager(gamePath, currentPlatform)

val ServiceLocator.themeManager: ThemeManager
    get() = ThemeManager(SL.userManager, SL.themeConfig)