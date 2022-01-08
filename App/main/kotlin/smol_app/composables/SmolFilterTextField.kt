@file:OptIn(ExperimentalFoundationApi::class)

package smol_app.composables

import AppState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.res.painterResource

@Composable
fun smolSearchField(
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    tooltipText: String,
    label: String,
    onValueChange: (String) -> Unit
) {
    var value by remember { mutableStateOf("") }
    SmolTooltipArea(
        modifier = modifier.run { if (focusRequester != null) this.focusRequester(focusRequester) else this },
        tooltip = { SmolTooltipText(text = tooltipText) }
    ) {
        SmolOutlinedTextField(
            label = { Text(text = label) },
            value = value,
            onValueChange = {
                value = it
                onValueChange(it)
            },
            singleLine = true,
            maxLines = 1,
            leadingIcon = { Icon(painter = painterResource("icon-search.svg"), contentDescription = null) }
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun AppState.searchFocusRequester(): FocusRequester {
    val focuser = remember { FocusRequester() }
    DisposableEffect(focuser) {
        val function: (KeyEvent) -> Boolean = { keyEvent ->
            if (keyEvent.isCtrlPressed && keyEvent.key == Key.F) {
                focuser.requestFocus()
                true
            } else false
        }
        onWindowKeyEventHandlers += function
        onDispose { onWindowKeyEventHandlers -= function }
    }
    return focuser
}