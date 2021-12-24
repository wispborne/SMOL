package smol_app.navigation

import com.arkivanov.essenty.parcelable.Parcelable

sealed class Screen : Parcelable {
    object Home : Screen()
    object Settings : Screen()
    object Profiles : Screen()
    data class ModBrowser(val defaultUri: String? = null) : Screen()
}