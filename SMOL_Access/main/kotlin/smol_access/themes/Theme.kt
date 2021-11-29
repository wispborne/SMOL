package smol_access.themes

data class Theme(
    val isDark: Boolean = true,
    val primary: String?,
    val primaryVariant: String?,
    val secondary: String?,
    val secondaryVariant: String?,
    val background: String?,
    val surface: String?,
    val error: String?,
    val onPrimary: String?,
    val onSecondary: String?,
    val onBackground: String?,
    val onSurface: String?,
    val onError: String?,
)