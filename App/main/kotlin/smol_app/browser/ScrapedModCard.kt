package smol_app.browser

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.Card
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mod_repo.ScrapedMod
import smol_app.themes.SmolTheme

@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
fun scrapedModCard(mod: ScrapedMod, linkLoader: ((String) -> Unit)?) {
    Card(
        modifier = Modifier
            .wrapContentHeight()
            .clickable {
                mod.forumPostLink?.run { linkLoader?.invoke(this.toString()) }
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
                    text = mod.name
                )
                Text(
                    modifier = Modifier.padding(top = 8.dp),
                    fontSize = 11.sp,
                    fontStyle = FontStyle.Italic,
                    text = mod.authors
                )
            }
            browserIcon(modifier = Modifier.align(Alignment.Top), mod = mod)
        }
    }
}