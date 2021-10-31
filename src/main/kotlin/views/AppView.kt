import androidx.compose.desktop.DesktopMaterialTheme
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.material.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import com.arkivanov.decompose.extensions.compose.jetbrains.Children
import navigation.Screen
import views.FileDropper
import views.ProfilesView
import views.homeView
import views.settingsView

@OptIn(ExperimentalStdlibApi::class)
@Composable
@Preview
fun AppState.appView() {
    DesktopMaterialTheme(
        colors = SmolTheme.DarkColors,
        typography = Typography(
            button = TextStyle(fontFamily = SmolTheme.orbitronSpaceFont)
        )
    ) {
        Children(router.state) { screen ->
            when (screen.configuration) {
                is Screen.Home -> homeView()
                is Screen.Settings -> settingsView()
                is Screen.Profiles -> ProfilesView()
            }.run { }
            FileDropper()
        }
    }
}