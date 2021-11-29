package smol_app.themes

import androidx.compose.material.Colors
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.ui.graphics.Color
import smol_access.SL
import smol_access.business.UserManager
import smol_access.themes.ThemeConfig
import smol_app.SmolTheme
import timber.ktx.Timber

class ThemeManager(
    private val userManager: UserManager,
    private val themeConfig: ThemeConfig
) {
    fun getActiveTheme(): Colors =
        SL.userManager.getUserProfile().theme?.let { themeName ->
            if (themeName.equals("default", ignoreCase = true)) {
                SmolTheme.DefaultTheme
            } else {
                kotlin.runCatching { SL.themeConfig.themes[SL.userManager.getUserProfile().theme] }
                    .onFailure { Timber.w(it) }
                    .getOrNull()
                    ?.let { theme ->
                        var builder = if (theme.isDark) darkColors() else lightColors()
                        theme.primary?.hexToColor()?.run { builder = builder.copy(primary = this) }
                        theme.primaryVariant?.hexToColor()?.run { builder = builder.copy(primaryVariant = this) }
                        theme.secondary?.hexToColor()?.run { builder = builder.copy(secondary = this) }
                        theme.secondaryVariant?.hexToColor()?.run { builder = builder.copy(secondaryVariant = this) }
                        theme.background?.hexToColor()?.run { builder = builder.copy(background = this) }
                        theme.surface?.hexToColor()?.run { builder = builder.copy(surface = this) }
                        theme.error?.hexToColor()?.run { builder = builder.copy(error = this) }
                        theme.onPrimary?.hexToColor()?.run { builder = builder.copy(onPrimary = this) }
                        theme.onSecondary?.hexToColor()?.run { builder = builder.copy(onSecondary = this) }
                        theme.onBackground?.hexToColor()?.run { builder = builder.copy(onBackground = this) }
                        theme.onSurface?.hexToColor()?.run { builder = builder.copy(onSurface = this) }
                        theme.onError?.hexToColor()?.run { builder = builder.copy(onError = this) }
                        builder
                    }
            }
        }
            ?: SmolTheme.DefaultTheme

    fun String.hexToColor(): Color? =
        this
            .removePrefix("#")
            .padStart(length = 8, padChar = 'F')
            .toLongOrNull(radix = 16)
            ?.let { Color(it) }
}