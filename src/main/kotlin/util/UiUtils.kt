package util

import FORUM_PAGE_URL
import model.Mod
import org.jetbrains.skija.impl.Platform
import java.awt.Desktop
import java.net.URI
import kotlin.math.ceil


fun Mod.getModThreadId(): ModThreadId? =
    findFirstEnabled?.versionCheckerInfo?.modThreadId
        ?: findHighestVersion?.versionCheckerInfo?.modThreadId

fun ModThreadId.openModThread() {
    (FORUM_PAGE_URL + this).openAsUriInBrowser()
}

fun String.openAsUriInBrowser() {
    Desktop.getDesktop().browse(URI(this))
}

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

val currentPlatform =
    when (Platform.CURRENT) {
        Platform.WINDOWS -> config.Platform.Windows
        Platform.MACOS_X64,
        Platform.MACOS_ARM64 -> config.Platform.MacOS
        Platform.LINUX -> config.Platform.Linux
        else -> config.Platform.Windows // *crosses fingers*
    }