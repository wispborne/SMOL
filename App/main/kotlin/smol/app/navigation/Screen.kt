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

package smol.app.navigation

import com.arkivanov.essenty.parcelable.Parcelable

sealed class Screen : Parcelable {
    data object Home : Screen()
    data object Settings : Screen()
    data object Profiles : Screen()
    data class ModBrowser(val defaultUri: String? = null) : Screen()
    data object Tips : Screen()
    data object About : Screen()
}