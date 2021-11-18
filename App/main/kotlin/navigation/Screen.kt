package navigation

import com.arkivanov.essenty.parcelable.Parcelable

sealed class Screen : Parcelable {
    object Home : Screen()
    object Settings : Screen()
    object Profiles : Screen()
    object ModBrowser : Screen()
}