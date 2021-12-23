package smol_app.browser.chromium

interface ChromiumBrowser {
    fun loadUrl(url: String)
    fun goBack()
    fun goForward()
}