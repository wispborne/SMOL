package smol_app.browser.chromium

interface ChromiumBrowser {
    val currentUrl: String?

    fun loadUrl(url: String)
    fun goBack()
    fun goForward()
}