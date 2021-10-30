package util

import FORUM_PAGE_URL
import model.Mod
import java.awt.Desktop
import java.net.URI


fun Mod.getModThreadId(): ModThreadId? =
    findFirstEnabled?.versionCheckerInfo?.modThreadId
        ?: findHighestVersion?.versionCheckerInfo?.modThreadId

fun ModThreadId.openModThread() {
    Desktop.getDesktop().browse(URI(FORUM_PAGE_URL + this))
}

typealias ModThreadId = String