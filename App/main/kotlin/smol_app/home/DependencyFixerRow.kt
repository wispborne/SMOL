package smol_app.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import smol_access.SL
import smol_access.business.DependencyFinder
import smol_access.model.Mod
import smol_app.composables.SmolButton
import smol_app.themes.SmolTheme
import smol_app.util.createGoogleSearchFor
import smol_app.util.getModThreadId
import smol_app.util.openAsUriInBrowser
import smol_app.util.openModThread

@Composable
fun DependencyFixerRow(
    mod: Mod,
    allMods: List<Mod>
) {
    val dependencyFinder =
        (mod.findFirstEnabled ?: mod.findHighestVersion)
            ?.run { SL.dependencyFinder.findDependencyStates(modVariant = this, mods = allMods) }
            ?.sortedWith(compareByDescending { it is DependencyFinder.DependencyState.Disabled })
            ?: emptyList()
    Column {
        dependencyFinder
            .filter { it is DependencyFinder.DependencyState.Missing || it is DependencyFinder.DependencyState.Disabled }
            .forEach { depState ->
                Row(Modifier.padding(start = 16.dp)) {
                    Image(
                        painter = painterResource("icon-warning.svg"),
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(24.dp)
                            .align(Alignment.CenterVertically),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(color = MaterialTheme.colors.onBackground)
                    )
                    Text(
                        modifier = Modifier.align(Alignment.CenterVertically),
                        text = when (depState) {
                            is DependencyFinder.DependencyState.Disabled -> "Disabled dependency: ${depState.variant.modInfo.name} ${depState.variant.modInfo.version}"
                            is DependencyFinder.DependencyState.Missing -> "Missing dependency: ${depState.dependency.name?.ifBlank { null } ?: depState.dependency.id}${depState.dependency.version?.let { " $it" }}"
                            is DependencyFinder.DependencyState.Enabled -> "you should never see this"
                        }
                    )
                    SmolButton(
                        modifier = Modifier.align(Alignment.CenterVertically).padding(start = 16.dp),
                        onClick = {
                            when (depState) {
                                is DependencyFinder.DependencyState.Disabled -> GlobalScope.launch {
                                    SL.access.enableModVariant(
                                        depState.variant
                                    )
                                }
                                is DependencyFinder.DependencyState.Missing -> {
                                    GlobalScope.launch {
                                        depState.outdatedModIfFound?.getModThreadId()?.openModThread()
                                            ?: createGoogleSearchFor("starsector ${depState.dependency.name ?: depState.dependency.id} ${depState.dependency.version?.raw}")
                                                .openAsUriInBrowser()
                                    }
                                }
                                is DependencyFinder.DependencyState.Enabled -> TODO("you should never see this")
                            }
                        }
                    ) {
                        Text(
                            text = when (depState) {
                                is DependencyFinder.DependencyState.Disabled -> "Enable ${depState.variant.modInfo.name}"
                                is DependencyFinder.DependencyState.Missing -> "Search"
                                is DependencyFinder.DependencyState.Enabled -> "you should never see this"
                            }
                        )
                        if (depState is DependencyFinder.DependencyState.Missing) {
                            Image(
                                painter = painterResource("icon-open-in-new.svg"),
                                colorFilter = ColorFilter.tint(SmolTheme.dimmedIconColor()),
                                modifier = Modifier.padding(start = 8.dp),
                                contentDescription = null
                            )
                        }
                    }
                }
            }
    }
}