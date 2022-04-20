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

package smol_app.home

import AppScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arkivanov.decompose.replaceCurrent
import smol_access.Constants
import smol_access.model.Mod
import smol_access.model.VersionCheckerInfo
import smol_app.UI
import smol_app.composables.SmolAlertDialog
import smol_app.composables.SmolButton
import smol_app.composables.SmolSecondaryButton
import smol_app.navigation.Screen
import smol_app.util.getModThreadUrl
import smol_app.util.isModBrowserEnabled
import smol_app.util.openModThread
import smol_app.util.parseHtml
import timber.ktx.Timber

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun AppScope.DirectDownloadAlertDialog(
    ddUrl: String,
    mod: Mod,
    onlineVersion: VersionCheckerInfo.Version?
) {
    SmolAlertDialog(
        onDismissRequest = { alertDialogSetter(null) },
        confirmButton = {
            SmolButton(onClick = {
                alertDialogSetter(null)
                smol_access.SL.UI.downloadManager.downloadFromUrl(
                    url = ddUrl,
                    appScope = this,
                    shouldInstallAfter = true
                )
            }) { Text("Take the risk") }
        },
        dismissButton = {
            SmolSecondaryButton(onClick = {
                alertDialogSetter(null)
            }) { Text("Cancel") }
        },
        title = {
            Text(
                text = "Auto-update ${mod.findFirstEnabledOrHighestVersion?.modInfo?.name}",
                style = smol_app.themes.SmolTheme.alertDialogTitle()
            )
        },
        text = {
            Column {
                Text(
                    text = ("Do you want to automatically download and update <b>${mod.findFirstEnabledOrHighestVersion?.modInfo?.name}</b> " +
                            "from version <b>${mod.findFirstEnabledOrHighestVersion?.modInfo?.version}</b> " +
                            "to version <b>$onlineVersion</b>?")
                        .parseHtml(),
                    fontSize = 16.sp
                )
                Text(
                    text = "WARNING",
                    color = smol_app.themes.SmolTheme.warningOrange,
                    modifier = androidx.compose.ui.Modifier.padding(top = 16.dp)
                        .align(androidx.compose.ui.Alignment.CenterHorizontally),
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    fontSize = 18.sp
                )
                Text(
                    text = "This may break your save",
                    modifier = androidx.compose.ui.Modifier.align(
                        androidx.compose.ui.Alignment.CenterHorizontally
                    ),
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    fontSize = 18.sp
                )
                Text(
                    text = "Save compatibility is not guaranteed when updating a mod. " +
                            "Check the mod's patch notes to see if save compatibility is mentioned.",
                    modifier = androidx.compose.ui.Modifier.padding(top = 16.dp),
                    fontSize = 16.sp
                )
                Text(
                    text = "Bug reports about saves broken by using this feature will be ignored.",
                    modifier = androidx.compose.ui.Modifier.padding(top = 8.dp),
                    fontSize = 16.sp
                )
                val modThreadId =
                    mod.findHighestVersion?.versionCheckerInfo?.modThreadId
                if (modThreadId != null) {
                    SmolButton(
                        modifier = androidx.compose.ui.Modifier.padding(
                            top = 16.dp
                        ),
                        onClick = {
                            if (Constants.isModBrowserEnabled()) {
                                router.replaceCurrent(Screen.ModBrowser(modThreadId.getModThreadUrl()))
                                alertDialogSetter(null)
                            } else {
                                kotlin.runCatching {
                                    modThreadId.openModThread()
                                }
                                    .onFailure { Timber.w(it) }
                            }
                        }) {
                        Icon(
                            modifier = androidx.compose.ui.Modifier.padding(
                                end = 8.dp
                            ),
                            painter = painterResource(
                                "icon-open-in-app.svg"
                            ),
                            contentDescription = null
                        )
                        Text("Mod Page")
                    }
                }
            }
        }
    )
}