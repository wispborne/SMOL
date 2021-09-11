package views

import SL
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.Router
import com.arkivanov.decompose.pop
import rememberRouter

@OptIn(
    ExperimentalMaterialApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)
@Composable
@Preview
fun settingsView(
    router: Router<Screen, Any>,
    modifier: Modifier = Modifier
) {
    Scaffold(topBar = {
        TopAppBar {
            Button(onClick = router::pop) {
                Text("Back")
            }
        }
    }) {
        Box(modifier) {
            LazyColumn(
                Modifier.padding(16.dp)
            ) {
                item {
                    Row {
                        Text("Game path")
                        Text(SL.appConfig.gamePath ?: "")
                    }
                }
            }
        }
    }
}