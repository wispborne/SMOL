package navigation

import com.arkivanov.essenty.parcelable.Parcelable

sealed class Screen : Parcelable {
//    @Parcelize
    object Home : Screen()

//    @Parcelize
    object Settings : Screen()
}