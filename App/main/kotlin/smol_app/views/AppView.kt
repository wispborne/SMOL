import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.text.TextStyle
import com.arkivanov.decompose.extensions.compose.jetbrains.Children
import smol_access.SL
import smol_app.AppState
import smol_app.browser.ModBrowserView
import smol_app.navigation.Screen
import smol_app.themes.SmolTheme
import smol_app.themes.SmolTheme.toColors
import smol_app.views.FileDropper
import smol_app.views.ProfilesView
import smol_app.home.homeView
import smol_app.views.settingsView

@OptIn(ExperimentalStdlibApi::class)
@Composable
@Preview
fun AppState.appView() {
    val theme = SL.themeManager.activeTheme.collectAsState()

    MaterialTheme(
        colors = theme.value.second.toColors(),
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