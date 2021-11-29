import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import com.arkivanov.decompose.extensions.compose.jetbrains.Children
import smol_access.SL
import smol_app.AppState
import smol_app.SmolTheme
import smol_app.navigation.Screen
import smol_app.util.themeManager
import smol_app.views.*

@OptIn(ExperimentalStdlibApi::class)
@Composable
@Preview
fun AppState.appView() {
    MaterialTheme(
        colors = SL.themeManager.getActiveTheme(),
        typography = Typography(
            button = TextStyle(fontFamily = SmolTheme.orbitronSpaceFont)
        )
    ) {
        Children(router.state) { screen ->
            when (screen.configuration) {
                is Screen.Home -> homeView()
                is Screen.Settings -> settingsView()
                is Screen.Profiles -> ProfilesView()
                is Screen.ModBrowser -> ModBrowserView()
            }.run { }
            FileDropper()
        }
    }
}