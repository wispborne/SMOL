package smol_app.browser.chromium

import kotlinx.coroutines.flow.StateFlow

interface ChromiumBrowser {
    /**
     * [Int] is used as a cachebuster, otherwise StateFlow won't trigger an update when a page is refreshed (same url).
     */
    val currentUrl: StateFlow<Pair<String, Int>>

    fun loadUrl(url: String)
    fun goBack()
    fun goForward()

    /**
     * Once you quit, you can't create it again.
     */
    fun quit()
}