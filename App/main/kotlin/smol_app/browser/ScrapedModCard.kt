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

package smol_app.browser

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mod_repo.ModSource
import mod_repo.ScrapedMod
import smol_app.themes.SmolTheme
import smol_app.themes.SmolTheme.lighten
import smol_app.util.smolPreview
import java.net.URI

@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
fun scrapedModCard(mod: ScrapedMod, linkLoader: MutableState<((String) -> Unit)?>) {
    Card(
        modifier = Modifier
            .wrapContentHeight()
            .border(
                width = 1.dp,
                color = MaterialTheme.colors.surface.lighten(),
                shape = SmolTheme.smolFullyClippedButtonShape()
            )
            .clickable {
                mod.forumPostLink?.run { linkLoader.value?.invoke(this.toString()) }
            },
        shape = SmolTheme.smolFullyClippedButtonShape()
    ) {
        Row(modifier = Modifier.padding(12.dp)) {
            Column(
                modifier = Modifier.align(Alignment.CenterVertically)
                    .weight(1f)
                    .padding(end = 16.dp)
            ) {
                Text(
                    modifier = Modifier,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    fontFamily = SmolTheme.orbitronSpaceFont,
                    text = mod.name.ifBlank { "???" }
                )
                if (mod.authors.isNotBlank()) {
                    Text(
                        modifier = Modifier.padding(top = 8.dp),
                        fontSize = 11.sp,
                        fontStyle = FontStyle.Italic,
                        text = mod.authors
                    )
                }

                val tags = remember {
                    mod.categories + when (mod.source) {
                        ModSource.Index -> "Index"
                        ModSource.ModdingSubforum -> "Modding Subforum"
                    }
                }
                if (tags.isNotEmpty()) {
                    Row(modifier = Modifier.padding(top = 12.dp)) {
                        Icon(
                            modifier = Modifier.size(12.dp).align(Alignment.CenterVertically),
                            painter = painterResource("icon-tag.svg"),
                            contentDescription = null
                        )
                        Text(
                            modifier = Modifier.align(Alignment.CenterVertically).padding(start = 6.dp),
                            fontSize = 11.sp,
                            text = tags.joinToString()
                        )
                    }
                }
            }
            browserIcon(modifier = Modifier.align(Alignment.Top), mod = mod)
        }
    }
}

@Preview
@Composable
fun scrapedModCardPreview() = smolPreview {
    scrapedModCard(
        ScrapedMod(
            name = "Archean Order",
            gameVersionReq = "0.95a",
            authors = "Morrokain",
            forumPostLink = URI.create("index0026.html?PHPSESSID=30de1ecdaac1b579e7b6ddd2b384c554&topic=13183.0"),
            categories = listOf("Total Conversions"),
            source = ModSource.Index
        ),
        mutableStateOf({})
    )
}