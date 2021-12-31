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
import smol_access.business.Dependencies
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
    val dependencies =
        (mod.findFirstEnabled ?: mod.findHighestVersion)
            ?.run { SL.dependencies.findDependencyStates(modVariant = this, mods = allMods) }
            ?.sortedWith(compareByDescending { it is Dependencies.DependencyState.Disabled })
            ?: emptyList()
    dependencies
        .filter { it is Dependencies.DependencyState.Missing || it is Dependencies.DependencyState.Disabled }
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
                        is Dependencies.DependencyState.Disabled -> "Disabled dependency: ${depState.variant.modInfo.name} ${depState.variant.modInfo.version}"
                        is Dependencies.DependencyState.Missing -> "Missing dependency: ${depState.dependency.name?.ifBlank { null } ?: depState.dependency.id}${depState.dependency.version?.let { " $it" }}"
                        is Dependencies.DependencyState.Enabled -> "you should never see this"
                    }
                )
                SmolButton(
                    modifier = Modifier.align(Alignment.CenterVertically).padding(start = 16.dp),
                    onClick = {
                        when (depState) {
                            is Dependencies.DependencyState.Disabled -> GlobalScope.launch {
                                SL.access.enableModVariant(
                                    depState.variant
                                )
                            }
                            is Dependencies.DependencyState.Missing -> {
                                GlobalScope.launch {
                                    depState.outdatedModIfFound?.getModThreadId()?.openModThread()
                                        ?: createGoogleSearchFor("starsector ${depState.dependency.name ?: depState.dependency.id} ${depState.dependency.versionString}")
                                            .openAsUriInBrowser()
                                }
                            }
                            is Dependencies.DependencyState.Enabled -> TODO("you should never see this")
                        }
                    }
                ) {
                    Text(
                        text = when (depState) {
                            is Dependencies.DependencyState.Disabled -> "Enable ${depState.variant.modInfo.name}"
                            is Dependencies.DependencyState.Missing -> "Search"
                            is Dependencies.DependencyState.Enabled -> "you should never see this"
                        }
                    )
                    if (depState is Dependencies.DependencyState.Missing) {
                        Image(
                            painter = painterResource("open-in-new.svg"),
                            colorFilter = ColorFilter.tint(SmolTheme.dimmedIconColor()),
                            modifier = Modifier.padding(start = 8.dp),
                            contentDescription = null
                        )
                    }
                }
            }
        }
}