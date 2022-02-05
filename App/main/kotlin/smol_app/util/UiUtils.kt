package smol_app.util

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Typography
import androidx.compose.runtime.Composable
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
import smol_access.Constants
import smol_access.SL
import smol_access.model.Mod
import smol_access.themes.ThemeManager
import smol_app.themes.SmolTheme
import smol_app.themes.SmolTheme.toColors
import timber.ktx.Timber
import utilities.equalsAny
import utilities.exists
import java.awt.Desktop
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.math.ceil


fun Mod.getModThreadId(): ModThreadId? =
    findFirstEnabled?.versionCheckerInfo?.modThreadId
        ?: findHighestVersion?.versionCheckerInfo?.modThreadId

fun ModThreadId.openModThread() {
    (Constants.FORUM_MOD_PAGE_URL + this).openAsUriInBrowser()
}

fun ModThreadId.getModThreadUrl(): String = Constants.FORUM_MOD_PAGE_URL + this

fun String.openAsUriInBrowser() {
    Desktop.getDesktop().browse(URI(this))
}

fun Path.openInDesktop() = Desktop.getDesktop().open(this.toFile())

typealias ModThreadId = String

/**
 * Return a string with a maximum length of `length` characters.
 * If there are more than `length` characters, then string ends with an ellipsis ("...").
 */
fun String.ellipsizeAfter(length: Int): String {
    // The letters [iIl1] are slim enough to only count as half a character.
    var lengthMod = length
    lengthMod += ceil(this.replace("[^iIl]".toRegex(), "").length / 2.0).toInt()
    return if (this.length > lengthMod) {
        this.substring(0, lengthMod - 3) + "…"
    } else this
}

/**
 * A megabyte is 8^6 bytes.
 */
val Long.bitsToMB: Float
    get() = (this / 8000000f)

/**
 * A megabyte is 1^6 byte.
 */
val Long.bytesToMB: Float
    get() = (this / 1000000f)

/**
 * 0.111 MB
 */
val Long.bytesAsReadableMB: String
    get() = "%.3f MB".format(this.bytesToMB)

/**
 * 0.1 MB
 */
val Long.bytesAsShortReadableMB: String
    get() = "%.2f MB".format(this.bytesToMB)

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
fun Modifier.onSubmitKeyPress(onKeyEvent: () -> Boolean): Modifier {
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

@Composable
fun smolPreview(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    MaterialTheme(
        colors = ThemeManager.defaultTheme.second.toColors(),
        typography = Typography(
            button = TextStyle(fontFamily = SmolTheme.orbitronSpaceFont)
        )
    ) {
        Box(modifier.padding(24.dp)) {
            content.invoke()
        }
    }
}

fun Constants.isJCEFEnabled() =
    kotlin.runCatching {
        Path.of("libs").listDirectoryEntries().any { it.name.startsWith("jcef") }
    }
        .onFailure { Timber.d { it.message ?: "Couldn't find jcef" } }
        .getOrElse { false }

fun Constants.isModBrowserEnabled() = isJCEFEnabled() && SL.gamePathManager.path.value.exists()
fun Constants.isModProfilesEnabled() = SL.gamePathManager.path.value.exists()

fun createGoogleSearchFor(query: String) = "https://google.com/search?q=" + query.replace(' ', '+')