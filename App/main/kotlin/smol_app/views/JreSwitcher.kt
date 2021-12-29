package smol_app.views

import AppState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.RadioButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import smol_access.SL
import smol_app.composables.SmolTooltipArea
import smol_app.composables.SmolTooltipText
import utilities.prefer
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.relativeTo

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppState.jreSwitcher(modifier: Modifier = Modifier) {
    val javasFound = remember { mutableStateListOf<Pair<String, Path>>() }
    LaunchedEffect(Unit) {
        javasFound.addAll(
            SL.gamePath.get()?.listDirectoryEntries()
                ?.mapNotNull { path ->
                    val javaExe = path.resolve("bin/java.exe")
                    if (!javaExe.exists()) return@mapNotNull null

                    val versionString = kotlin.runCatching {
                        ProcessBuilder()
                            .command("java.exe", "-version")
                            .directory(path.toFile().resolve("bin"))
                            .redirectErrorStream(true)
                            .start()
                            .inputStream
                            .bufferedReader()
                            .readLines()
                            .prefer { it.contains("build") }
                            .firstOrNull()
                    }
                        .onFailure { timber.ktx.Timber.e(it) { "Error getting java version from $'path'." } }
                        .getOrElse { return@mapNotNull null }!!

                    return@mapNotNull versionString to path
                }
                ?.toList() ?: emptyList()
        )
    }


    SmolTooltipArea(
        tooltip = { SmolTooltipText("Switch between JRE versions") },
        delayMillis = SmolTooltipArea.delay
    ) {
        Column(modifier = modifier.padding(start = 16.dp)) {
            if (javasFound.size > 1) {
                Text(
                    text = "Select a Java Runtime (JRE)",
                    modifier = Modifier.padding(bottom = 8.dp),
                    style = MaterialTheme.typography.subtitle2
                )
            }

            javasFound.forEach { javaNameAndPath ->
                Row {
                    RadioButton(
                        onClick = { },
                        modifier = Modifier.align(Alignment.CenterVertically),
                        selected = javaNameAndPath.second.name == "jre"
                    )
                    Text(
                        modifier = Modifier.align(Alignment.CenterVertically),
                        text = "'${javaNameAndPath.first}' in folder '${javaNameAndPath.second.relativeTo(SL.gamePath.get()!!)}'"
                    )
                }
            }
        }
    }
}