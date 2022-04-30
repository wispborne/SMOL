/*
 * This file is distributed under the GPLv3. An informal description follows:
 * - Anyone can copy, modify and distribute this software as long as the other points are followed.
 * - You must include the license and copyright notice with each and every distribution.
 * - You may this software for commercial purposes.
 * - If you modify it, you must indicate changes made to the code.
 * - Any modifications of this code base MUST be distributed with the same license, GPLv3.
 * - This software is provided without warranty.
 * - The software author or license can not be held liable for any damages inflicted by the software.
 * The full license is available from <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package smol.app.util

import AppScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.currentRecomposeScope
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.key.*
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.res.useResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import dev.andrewbailey.diff.differenceOf
import smol.access.Constants
import smol.access.SL
import smol.access.model.Mod
import smol.access.themes.ThemeManager
import smol.app.WindowState
import smol.app.navigation.Screen
import smol.app.navigation.rememberRouter
import smol.app.themes.SmolTheme
import smol.app.themes.SmolTheme.toColors
import smol.timber.ktx.Timber
import smol.utilities.equalsAny
import smol.utilities.exists
import java.awt.Desktop
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name


fun Mod.getModThreadId(): ModThreadId? =
    findFirstEnabled?.versionCheckerInfo?.modThreadId
        ?: findHighestVersion?.versionCheckerInfo?.modThreadId

fun Mod.getNexusId(): ModThreadId? =
    findFirstEnabled?.versionCheckerInfo?.modNexusId
        ?: findHighestVersion?.versionCheckerInfo?.modNexusId

fun ModThreadId.openModThread() {
    (Constants.FORUM_MOD_PAGE_URL + this).openAsUriInBrowser()
}

fun ModThreadId.getModThreadUrl(): String = Constants.FORUM_MOD_PAGE_URL + this

fun String.getNexusModsUrl(): String = Constants.NEXUS_MODS_PAGE_URL + this

fun String.openAsUriInBrowser() {
    Desktop.getDesktop().browse(URI(this))
}

fun Path.openInDesktop() = Desktop.getDesktop().open(this.toFile())

typealias ModThreadId = String

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

fun String.hexToColor(): Color? =
    this.removePrefix("#")
        .padStart(length = 8, padChar = 'F')
        .toLongOrNull(radix = 16)
        ?.let { Color(it) }

fun String.acronym(): String =
    this.map { if (!it.isLetter()) ' ' else it }
        .joinToString(separator = "")
        .split(" ")
        .filter { it.isNotBlank() }
        .joinToString(separator = "") { it.firstOrNull()?.toString() ?: "" }

@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.onEnterKeyPressed(onKeyEvent: () -> Boolean): Modifier {
    return this.onKeyEvent { event ->
        if (event.type == KeyEventType.KeyUp && (event.key.equalsAny(
                Key.Enter,
                Key.NumPadEnter
            ))
        ) {
            onKeyEvent.invoke()
        } else false
    }
}

@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.onEscKeyPressed(onKeyEvent: () -> Boolean): Modifier {
    return this.onKeyEvent { event ->
        if (event.type == KeyEventType.KeyUp && (event.key.equalsAny(
                Key.Escape,
            ))
        ) {
            onKeyEvent.invoke()
        } else false
    }
}

@Composable
fun smolPreview(modifier: Modifier = Modifier, content: @Composable AppScope.() -> Unit) {
    MaterialTheme(
        colors = ThemeManager.defaultTheme.second.toColors(),
        typography = Typography(
            button = TextStyle(fontFamily = SmolTheme.orbitronSpaceFont)
        )
    ) {
        Box(modifier.padding(24.dp)) {
            content.invoke(
                AppScope(
                    windowState = WindowState()
                        .apply {
                            router = rememberRouter(
                                initialConfiguration = { Screen.Home },
                                handleBackButton = true
                            )
                        }, recomposer = currentRecomposeScope
                )
            )
        }
    }
}

fun Constants.isJCEFEnabled() =
    kotlin.runCatching {
        Path.of("libs").listDirectoryEntries().any { it.name.startsWith("jcef") }
    }
        .onFailure { Timber.d { it.message ?: "Couldn't find jcef" } }
        .getOrElse { false }

fun Constants.isModBrowserEnabled() = doesGamePathExist()
fun Constants.isModProfilesEnabled() = doesGamePathExist()
fun Constants.doesGamePathExist() = SL.gamePathManager.path.value.exists()

fun createGoogleSearchFor(query: String) = "https://google.com/search?q=" + query.replace(' ', '+')

fun String.replaceTabsWithSpaces() = this.replace(oldValue = "\t", newValue = "    ")