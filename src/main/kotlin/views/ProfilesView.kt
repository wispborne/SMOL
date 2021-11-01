package views

import AppState
import SL
import SmolButton
import SmolTheme
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arkivanov.decompose.pop
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import util.ellipsizeAfter

@OptIn(
    ExperimentalMaterialApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)
@Composable
@Preview
fun AppState.ProfilesView(
    modifier: Modifier = Modifier
) {

    Scaffold(topBar = {
        TopAppBar {
            SmolButton(onClick = router::pop, modifier = Modifier.padding(start = 16.dp)) {
                Text("Back")
            }
        }
    }) {
        Box(modifier.padding(16.dp)) {
            var userProfile by remember { mutableStateOf(SL.userManager.getUserProfile()) }
            var modVariants by remember {
                mutableStateOf(SL.access.getMods(noCache = false).flatMap { it.variants }.associateBy { it.smolId })
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                this.items(items = userProfile.modProfiles) { modProfile ->
                    val isActiveProfile = userProfile.activeModProfileId == modProfile.id
                    Card(
                        modifier = Modifier.width(370.dp).wrapContentHeight()
                            .run {
                                // Highlight active profile
                                if (isActiveProfile) this.border(
                                    width = 4.dp,
                                    color = MaterialTheme.colors.onSurface.copy(alpha = .45f),
                                    shape = SmolTheme.smolFullyClippedButtonShape()
                                ) else this
                            },
                        shape = SmolTheme.smolFullyClippedButtonShape()
                    ) {
                        SelectionContainer {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row {
                                    Text(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(start = 16.dp, end = 16.dp)
                                            .align(Alignment.CenterVertically),
                                        fontWeight = FontWeight.Bold,
                                        text = modProfile.name,
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        modifier = Modifier.align(Alignment.CenterVertically),
                                        text = "#${modProfile.id}",
                                        fontFamily = SmolTheme.fireCodeFont,
                                        fontSize = 12.sp,
                                    )
                                }
                                Box {
                                    Text(
                                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp).align(Alignment.Center),
                                        fontFamily = SmolTheme.fireCodeFont,
                                        fontWeight = FontWeight.Light,
                                        fontSize = 14.sp,
                                        text = modProfile.enabledModVariants.joinToString(separator = "\n") {
                                            val modVariant = modVariants[it.smolVariantId]
                                            "${modVariant?.modInfo?.name?.ellipsizeAfter(28)}: "
                                        })
                                    Text(
                                        modifier = Modifier.padding(top = 16.dp).align(Alignment.CenterEnd),
                                        fontFamily = SmolTheme.fireCodeFont,
                                        fontWeight = FontWeight.Light,
                                        fontSize = 14.sp,
                                        text = modProfile.enabledModVariants.joinToString(separator = "\n") {
                                            val modVariant = modVariants[it.smolVariantId]
                                            modVariant?.modInfo?.version.toString()
                                        })
                                }
                                DisableSelection {
                                    if (isActiveProfile) {
                                        Text(
                                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                            textAlign = TextAlign.Center,
                                            fontWeight = FontWeight.SemiBold,
                                            text = "Active"
                                        )
                                    } else {
                                        Button(modifier = Modifier.align(Alignment.CenterHorizontally),
                                            onClick = {
                                                GlobalScope.launch {
                                                    SL.userManager.switchModProfile(modProfile.id)
                                                    userProfile = SL.userManager.getUserProfile()
                                                    modVariants =
                                                        SL.access.getMods(noCache = false).flatMap { it.variants }
                                                            .associateBy { it.smolId }
                                                }
                                            }) {
                                            Text(
                                                text = "Activate"
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}