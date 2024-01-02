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

import AppScope
import androidx.compose.foundation.layout.*
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
import com.arkivanov.decompose.replaceCurrent
import smol.access.SL
import smol.app.composables.SmolButton
import smol.app.composables.SmolCheckboxWithText
import smol.app.composables.SmolText
import smol.app.navigation.Screen

@Composable
fun AppScope.jre8NagToast(
    requestToastDismissalAfter: (delayMs: Long) -> Unit
) {
    Row {
        Column {
            Text(
                text = "Upgrade game to JRE 8?",
            )

            Text(
                text = "Increased performance. Compatible and safe.",
                fontSize = 14.sp
            )

            SmolCheckboxWithText(
                checked = false,
                onCheckedChange = {
                    SL.appConfig.doNotNagAboutJre8 = true
                    requestToastDismissalAfter.invoke(0)
                },
                modifier = Modifier.offset(x = -(14.dp)),
                text = {
                    SmolText(
                        "Do not show again",
                        fontSize = 14.sp,
                        modifier = Modifier.padding(bottom = 1.dp)
                    )
                }
            )

            Row {
                SmolButton(
                    modifier = Modifier
                        .padding(top = 4.dp),
                    onClick = {
                        router.replaceCurrent(Screen.Settings)
                    }
                ) {
                    Text("Go to Settings")
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