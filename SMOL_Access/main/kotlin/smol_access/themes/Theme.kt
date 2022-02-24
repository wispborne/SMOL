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

/**
 * <a href="https://material.io/design/color/the-color-system.html" class="external" target="_blank">Material Design color system</a>.
 *
 * The Material Design color system can help you create a color theme that reflects your brand or
 * style.
 *
 * ![Color image](https://developer.android.com/images/reference/androidx/compose/material/color.png)
 *
 * @property primary The primary color is the color displayed most frequently across your app’s
 * screens and components.
 * @property primaryVariant The primary variant color is used to distinguish two elements of the
 * app using the primary color, such as the top app bar and the system bar.
 * @property secondary The secondary color provides more ways to accent and distinguish your
 * product. Secondary colors are best for:
 * - Floating action buttons
 * - Selection controls, like checkboxes and radio buttons
 * - Highlighting selected text
 * - Links and headlines
 * @property secondaryVariant The secondary variant color is used to distinguish two elements of the
 * app using the secondary color.
 * @property background The background color appears behind scrollable content.
 * @property surface The surface color is used on surfaces of components, such as cards, sheets and
 * menus.
 * @property error The error color is used to indicate error within components, such as text fields.
 * @property onPrimary Color used for text and icons displayed on top of the primary color.
 * @property onSecondary Color used for text and icons displayed on top of the secondary color.
 * @property onBackground Color used for text and icons displayed on top of the background color.
 * @property onSurface Color used for text and icons displayed on top of the surface color.
 * @property onError Color used for text and icons displayed on top of the error color.
 * @property isDark Whether this Colors is considered as a 'light' or 'dark' set of colors. This
 * affects default behavior for some components: for example, in a light theme a [TopAppBar] will
 * use [primary] by default for its background color, when in a dark theme it will use [surface].
 */
data class Theme(
    val isDark: Boolean = true,
    val primary: String? = null,
    val primaryVariant: String? = null,
    val secondary: String? = null,
    val secondaryVariant: String? = null,
    val background: String? = null,
    val surface: String? = null,
    val error: String? = null,
    val onPrimary: String? = null,
    val onSecondary: String? = null,
    val onBackground: String? = null,
    val onSurface: String? = null,
    val onError: String? = null,
    val hyperlink: String? = null,
) {
    constructor(
        isDark: Boolean,
        primary: Long? = null,
        primaryVariant: Long? = null,
        secondary: Long? = null,
        secondaryVariant: Long? = null,
        background: Long? = null,
        surface: Long? = null,
        error: Long? = null,
        onPrimary: Long? = null,
        onSecondary: Long? = null,
        onBackground: Long? = null,
        onSurface: Long? = null,
        onError: Long? = null,
        hyperlink: Long? = null,
    ) : this(
        isDark = isDark,
        primary = primary?.toString(radix = 16),
        primaryVariant = primaryVariant?.toString(radix = 16),
        secondary = secondary?.toString(radix = 16),
        secondaryVariant = secondaryVariant?.toString(radix = 16),
        background = background?.toString(radix = 16),
        surface = surface?.toString(radix = 16),
        error = error?.toString(radix = 16),
        onPrimary = onPrimary?.toString(radix = 16),
        onSecondary = onSecondary?.toString(radix = 16),
        onBackground = onBackground?.toString(radix = 16),
        onSurface = onSurface?.toString(radix = 16),
        onError = onError?.toString(radix = 16),
        hyperlink = hyperlink?.toString(radix = 16),
    )
}