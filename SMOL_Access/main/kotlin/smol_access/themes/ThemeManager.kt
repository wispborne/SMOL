package smol_access.themes

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import smol_access.business.UserManager
import timber.ktx.Timber

class ThemeManager(
    private val userManager: UserManager,
    private val themeConfig: ThemeConfig
) {
    private val defaultTheme = "Default" to Theme(
        isDark = true,
        primary = 0xFF184957,
        primaryVariant = 0xFF00212e,
        surface = 0xFF0A1D22,
        secondary = 0xFFFCCF00,
        background = 0xFF091A1F,
        onPrimary = 0xFFA8DBFC,
//        onBackground = 0xFF2d304e
    )

    private val activeThemeInner = MutableStateFlow(getActiveTheme())
    val activeTheme = activeThemeInner.asStateFlow()

    fun setActiveTheme(themeName: String) {
        val theme = getThemes()[themeName]
        if (theme == null) {
            Timber.w(NullPointerException("Unable to switch to nonexistent theme '$themeName'."))
        } else {
            userManager.updateUserProfile { it.copy(theme = themeName) }
            activeThemeInner.tryEmit(themeName to theme)
        }
    }

    fun getThemes(): Map<String, Theme> =
        mapOf(defaultTheme) +
                kotlin.runCatching {
                    themeConfig.themes
                }
                    .onFailure { Timber.w(it) }
                    .getOrElse { emptyMap() }

    private fun getActiveTheme(): Pair<String, Theme> {
        val activeThemeName = userManager.getUserProfile().theme
        return activeThemeName?.let { themeName ->
            kotlin.runCatching { activeThemeName to getThemes()[themeName]!! }
                .onFailure { Timber.w(it) }
                .getOrNull()
        } ?: defaultTheme
    }
}