package smol_app.browser.chromium

interface ChromiumBrowser {
    val currentUrl: String?

    fun loadUrl(url: String)
    fun goBack()
    fun goForward()

    /**
     * Once you quit, you can't create it again.
     */
    fun quit()
}