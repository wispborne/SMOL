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

package smol.app.toasts

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import smol.access.SL
import smol.access.model.ModVariant
import smol.app.composables.SmolButton
import smol.app.util.openInDesktop
import smol.app.util.smolPreview

@Composable
fun toastInstalledCard(
    modVariant: ModVariant,
    requestToastDismissalAfter: (delayMs: Long) -> Unit
) {
    // As soon as we start living, we start dying.
    requestToastDismissalAfter.invoke(ToasterState.defaultTimeoutMillis)

    Row {
        Column {
            Text(
                modifier = Modifier,
                text = "${modVariant.modInfo.name} ${modVariant.modInfo.version} found.",
                fontSize = 12.sp
            )

            Row {
                if (modVariant.mod(SL.access)?.isEnabled(modVariant) == false) {
                    SmolButton(
                        modifier = Modifier
                            .padding(top = 8.dp, end = 8.dp),
                        onClick = {
                            GlobalScope.launch {
                                SL.access.changeActiveVariant(
                                    mod = modVariant.mod(SL.access) ?: return@launch,
                                    modVariant = modVariant
                                )
                            }
                            requestToastDismissalAfter.invoke(0)
                        }
                    ) {
                        Text("Enable")
                    }
                }

                SmolButton(
                    modifier = Modifier
                        .padding(top = 8.dp),
                    onClick = {
                        modVariant.modsFolderInfo.folder.openInDesktop()
                    }
                ) {
                    Text("Open folder")
                }
            }
        }

        IconButton(
            modifier = Modifier
                .padding(start = 8.dp)
                .align(Alignment.CenterVertically)
                .size(16.dp),
            onClick = {
                requestToastDismissalAfter.invoke(0)
            }
        ) {
            Icon(imageVector = Icons.Default.Close, contentDescription = null)
        }
    }
}

@Preview
@Composable
fun toastInstalledCardPreview() = smolPreview {
    toastInstalledCard(ModVariant.MOCK, requestToastDismissalAfter = {})
}