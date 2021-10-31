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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arkivanov.decompose.pop
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
    val userProfile = SL.userManager.getUserProfile()
    val modVariants = SL.access.getMods(noCache = false).flatMap { it.variants }.associateBy { it.smolId }

    Scaffold(topBar = {
        TopAppBar {
            SmolButton(onClick = router::pop, modifier = Modifier.padding(start = 16.dp)) {
                Text("Back")
            }
        }
    }) {
        Box(modifier.padding(16.dp)) {
            LazyRow {
                this.items(items = userProfile.modProfiles) { modProfile ->
                    val isActiveProfile = userProfile.activeModProfileId == modProfile.id
                    Card(modifier = Modifier.width(350.dp).wrapContentHeight()
                        .run {
                            // Highlight active profile
                            if (isActiveProfile) this.border(
                                width = 4.dp,
                                color = MaterialTheme.colors.onSurface.copy(alpha = .25f)
                            ) else this
                        }) {
                        SelectionContainer {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    modifier = Modifier.align(Alignment.End),
                                    text = modProfile.id.toString(),
                                    fontFamily = SmolTheme.fireCodeFont,
                                    fontSize = 12.sp
                                )
                                Text(
                                    modifier = Modifier.align(Alignment.CenterHorizontally),
                                    fontWeight = FontWeight.Bold,
                                    text = modProfile.name
                                )
                                Row {
                                    Text(
                                        modifier = Modifier.padding(top = 16.dp).weight(1f),
                                        fontFamily = SmolTheme.fireCodeFont,
                                        fontWeight = FontWeight.Light,
                                        fontSize = 14.sp,
                                        text = modProfile.enabledModVariants.joinToString(separator = "\n") {
                                            val modVariant = modVariants[it.smolVariantId]
                                            "${modVariant?.modInfo?.name?.ellipsizeAfter(28)}: "
                                        })
                                    Text(
                                        modifier = Modifier.padding(top = 16.dp),
                                        fontFamily = SmolTheme.fireCodeFont,
                                        fontWeight = FontWeight.Light,
                                        fontSize = 14.sp,
                                        text = modProfile.enabledModVariants.joinToString(separator = "\n") {
                                            val modVariant = modVariants[it.smolVariantId]
                                            modVariant?.modInfo?.version.toString()
                                        })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}