package util

import model.Mod
import model.VersionCheckerInfo
import java.awt.Desktop
import java.net.URI


fun Mod.getModThreadId(): ModThreadId? =
    findFirstEnabled?.versionCheckerInfo?.modThreadId
        ?: findHighestVersion?.versionCheckerInfo?.modThreadId

fun ModThreadId.openModThread() {
    Desktop.getDesktop().browse(URI(FORUM_PAGE_URL + this))
}

typealias ModThreadId = String