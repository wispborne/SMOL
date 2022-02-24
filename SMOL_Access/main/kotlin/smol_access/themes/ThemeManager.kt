/*
 * This file is distributed under the GPLv3. An informal description follows:
 * - Anyone can copy, modify and distribute this software as long as the other points are followed.
 * - You must include the license and copyright notice with each and every distribution.
 * - You may this software for commercial purposes.
 * - If you modify it, you must indicate changes made to the code.
 * - Any modifications of this code base MUST be distributed with the same license, GPLv3.
 * - This software is provided without warranty.
 * - The software author or license can not be held liable for any damages inflicted by the software.
 * The full license is available from <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package smol_access.themes

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import smol_access.Constants
import smol_access.business.UserManager
import timber.ktx.Timber
import utilities.Jsanity
import java.nio.file.Path

class ThemeManager(
    private val userManager: UserManager,
    jsanity: Jsanity
) {
    companion object {
        val defaultTheme = "Starfarer" to Theme(
            isDark = true,
            primary = 0xFF184957,
            primaryVariant = 0xFF00212e,
            surface = 0xFF0A1D22,
            secondary = 0xFFFCCF00,
            background = 0xFF091A1F,
            onPrimary = 0xFFA8DBFC,
            hyperlink = 0xFF00FFFF,
//        onBackground = 0xFF2d304e
        )
    }

    private val baseThemeConfig: ThemeConfig = ThemeConfig(gson = jsanity, path = Constants.BASE_THEME_CONFIG_PATH!!)
    private val userThemeConfig: ThemeConfig = ThemeConfig(gson = jsanity, path = Constants.USER_THEME_CONFIG_PATH!!)
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
        kotlin.runCatching {
            baseThemeConfig.themes + userThemeConfig.themes
        }
            .onFailure { Timber.w(it) }
            .getOrElse { emptyMap() }
            .run {
                // Add default theme if it isn't present
                if (!this.containsKey(defaultTheme.first)) mapOf(defaultTheme) + this
                else this
            }

    private fun getActiveTheme(): Pair<String, Theme> {
        val activeThemeName = userManager.activeProfile.value.theme
        return activeThemeName?.let { themeName ->
            kotlin.runCatching { activeThemeName to getThemes()[themeName]!! }
                .onFailure { Timber.w(it) }
                .getOrNull()
        } ?: defaultTheme
    }

    fun reloadThemes() {
        kotlin.runCatching {
            baseThemeConfig.reload()
            userThemeConfig.reload()
        }
            .onFailure { Timber.w(it) }
        activeThemeInner.value = getActiveTheme()
    }

    fun editTheme(themeKey: String): Path {
        if (!userThemeConfig.themes.containsKey(themeKey)) {
            kotlin.runCatching {
                // Add the theme to the user config so they can edit it.
                reloadThemes()
                userThemeConfig.themes = (userThemeConfig.themes.toMutableMap()
                    .apply { this[themeKey] = baseThemeConfig.themes[themeKey]!! })
            }
                .onFailure { Timber.w(it) }
        }

        return userThemeConfig.path
    }
}