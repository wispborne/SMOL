package smol_app.home

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.Button
import androidx.compose.material.Divider
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import smol_access.Constants
import smol_access.model.Mod
import smol_app.composables.SmolAlertDialog
import smol_app.themes.SmolTheme
import smol_app.util.parseHtml

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun debugDialog(
    modifier: Modifier = Modifier,
    mod: Mod,
    onDismiss: () -> Unit
) {
    val scrollState = rememberScrollState()
    SmolAlertDialog(
        modifier = modifier
            .widthIn(min = 700.dp),
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = onDismiss) { Text("Ok") }
        },
        title = { Text(text = mod.id, style = SmolTheme.alertDialogTitle()) },
        text = {
            SelectionContainer {
                Box(
                    Modifier
                        .padding(top = SmolTheme.topBarHeight)
                ) {
                    Column {//(Modifier.verticalScroll(scrollState)) {
//                        Text(
//                            "<b>NOTE: The wacky scrolling is due to a bug in the UI framework.</b>\n(https://github.com/JetBrains/compose-jb/issues/976)".parseHtml(),
//                            modifier = Modifier.padding(bottom = 8.dp)
//                        )
                        Text("<b>Id</b>: <code>${mod.id}</code>".parseHtml())
                        Text(
                            "<b>Enabled in ${Constants.ENABLED_MODS_FILENAME}?</b>: ${mod.isEnabledInGame}".parseHtml(),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        mod.variants.forEach { variant ->
                            Divider(Modifier.padding(top = 8.dp, bottom = 4.dp).height(2.dp).fillMaxWidth())
                            Text(
                                "<b>SMOL variant id</b>: ${variant.smolId}".parseHtml(),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                            Text(
                                "<b>Version</b>: ${variant.modInfo.version}".parseHtml(),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                            Text(
                                "<b>Version Checker info</b>\n${variant.versionCheckerInfo}".parseHtml(),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                            Text(
                                "<b>/mods folder</b>: ${variant.modsFolderInfo?.folder}".parseHtml(),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                            Text(
                                "<b>Mod Info</b>\n${variant.modInfo}".parseHtml(),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }

//                    VerticalScrollbar(
//                        modifier = Modifier.align(Alignment.CenterEnd).width(8.dp).fillMaxHeight(),
//                        adapter = rememberScrollbarAdapter(scrollState)
//                    )
                }
            }
        }
    )
}