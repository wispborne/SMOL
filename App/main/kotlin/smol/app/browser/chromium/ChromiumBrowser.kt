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

package smol.app.browser.chromium

import kotlinx.coroutines.flow.StateFlow

interface ChromiumBrowser {
    /**
     * [Int] is used as a cachebuster, otherwise StateFlow won't trigger an update when a page is refreshed (same url).
     */
    val currentUrl: StateFlow<Pair<String, Int>>

    fun loadUrl(url: String)
    fun goBack()
    fun goForward()
    val canGoBack: Boolean?
    val canGoForward: Boolean?

    /**
     * Once you quit, you can't create it again.
     */
    fun quit()
}