package smol_app.browser.javafx

import AppState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import javafx.application.Platform
import javafx.embed.swing.JFXPanel
import javafx.scene.Group
import javafx.scene.Scene
import javafx.scene.paint.Color
import javafx.scene.web.WebView
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import smol_access.Constants
import smol_access.SL
import smol_app.UI
import smol_app.browser.BrowserUtils
import smol_app.browser.ForumWebpageModifier
import smol_app.browser.WebViewHolder
import timber.ktx.Timber
import java.net.URI

@Composable
fun AppState.javaFxBrowser(
    jfxpanel: JFXPanel,
    background: androidx.compose.ui.graphics.Color,
    linkLoader: ((String) -> Unit)?
) {
    var linkLoader1 = linkLoader
    javaFXPanel(
        modifier = Modifier
            .padding(start = 16.dp)
            .fillMaxHeight()
            .fillMaxWidth()
            .sizeIn(minWidth = 200.dp),
        root = window,
        panel = jfxpanel
    ) {
        val root = Group()
        val scene = Scene(
            root,
            Color.rgb(background.red.toInt(), background.green.toInt(), background.blue.toInt())
        )
        if (WebViewHolder.webView == null) {
            createWebView(linkLoader1) { linkLoader1 = it }
        }
        WebViewHolder.webView!!.prefWidth = jfxpanel.width.toDouble()
        WebViewHolder.webView!!.prefHeight = jfxpanel.height.toDouble()
        jfxpanel.scene = scene
        root.children.add(WebViewHolder.webView)
    }
}

fun createWebView(
    linkLoader: ((String) -> Unit)?,
    setLinkLoader: ((String) -> Unit) -> Unit
) {
    WebViewHolder.webView =
        WebView().apply {
            this.engine.apply {
                isJavaScriptEnabled = true
                locationProperty().addListener { _, oldLoc, newLoc ->
                    // check to see if newLoc is downloadable.
                    // Use GlobalScope, we don't want this to be canceled
                    GlobalScope.launch {
                        SL.UI.javaFxDownloader.download(url = newLoc)
                    }
                }
                loadWorker.exceptionProperty().addListener { _, _, throwable ->
                    Timber.i(throwable)
                }
                setOnError {
                    Timber.d { it.message }
                }
            }

            setLinkLoader { url ->
                Platform.runLater {
                    this.engine.loadContent(
                        (BrowserUtils.getData(url) ?: "")
                            .let { html ->
                                if (URI.create(url).host.equals(
                                        Constants.FORUM_HOSTNAME,
                                        ignoreCase = true
                                    )
                                ) {
                                    ForumWebpageModifier.filterToFirstPost(html)
                                } else {
                                    html
                                }
                            }
                    )
                }
            }

            linkLoader?.invoke(Constants.FORUM_MOD_INDEX_URL)
        }
}