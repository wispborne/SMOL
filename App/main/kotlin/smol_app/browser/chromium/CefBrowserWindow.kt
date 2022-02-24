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

package smol_app.browser.chromium

import com.sun.java.accessibility.util.AWTEventMonitor
import org.cef.CefApp
import org.cef.CefClient
import org.cef.CefSettings
import org.cef.browser.CefBrowser
import org.cef.callback.CefBeforeDownloadCallback
import org.cef.callback.CefDownloadItem
import org.cef.callback.CefDownloadItemCallback
import org.cef.handler.CefAppHandlerAdapter
import org.cef.handler.CefDownloadHandler
import smol_app.browser.DownloadHander
//import sun.tools.jconsole.inspector.XDataViewer
import java.awt.BorderLayout
import java.awt.Component
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.JFrame
import javax.swing.JTextField


class CefBrowserWindow//                XDataViewer.dispose(null)// calling System.exit(0) appears to be causing assert errors,
// as its firing before all of the CEF objects shutdown.
//System.exit(0);

// (2) JCEF can handle one to many browser instances simultaneous. These
//     browser instances are logically grouped together by an instance of
//     the class CefClient. In your application you can create one to many
//     instances of CefClient with one to many CefBrowser instances per
//     client. To get an instance of CefClient you have to use the method
//     "createClient()" of your CefApp instance. Calling an CTOR of
//     CefClient is not supported.
//
//     CefClient is a connector to all possible events which come from the
//     CefBrowser instances. Those events could be simple things like the
//     change of the browser title or more complex ones like context menu
//     events. By assigning handlers to CefClient you can control the
//     behavior of the browser. See example.detailed.SimpleFrameExample for an example
//     of how to use these handlers.

// (3) One CefBrowser instance is responsible to control what you'll see on
//     the UI component of the instance. It can be displayed off-screen
//     rendered or windowed rendered. To get an instance of CefBrowser you
//     have to call the method "createBrowser()" of your CefClient
//     instances.
//
//     CefBrowser has methods like "goBack()", "goForward()", "loadURL()",
//     and many more which are used to control the behavior of the displayed
//     content. The UI is held within a UI-Compontent which can be accessed
//     by calling the method "getUIComponent()" on the instance of CefBrowser.
//     The UI component is inherited from a java.awt.Component and therefore
//     it can be embedded into any AWT UI.

// (4) For this minimal browser, we need only a text field to enter an URL
//     we want to navigate to and a CefBrowser window to display the content
//     of the URL. To respond to the input of the user, we're registering an
//     anonymous ActionListener. This listener is performed each time the
//     user presses the "ENTER" key within the address field.
//     If this happens, the entered value is passed to the CefBrowser
//     instance to be loaded as URL.

// (5) All UI components are assigned to the default content pane of this
//     JFrame and afterwards the frame is made visible to the user.

// (6) To take care of shutting down CEF accordingly, it's important to call
//     the method "dispose()" of the CefApp instance if the Java
//     application will be closed. Otherwise you'll get asserts from CEF.
// Shutdown the app if the native CEF part is terminated// (1) The entry point to JCEF is always the class CefApp. There is only one
//     instance per application and therefore you have to call the method
//     "getInstance()" instead of a CTOR.
//
//     CefApp is responsible for the global CEF context. It loads all
//     required native libraries, initializes CEF accordingly, starts a
//     background task to handle CEF's message loop and takes care of
//     shutting down CEF after disposing it.
    (
    startURL: String,
    useOSR: Boolean,
    isTransparent: Boolean,
    private val downloadHandler: DownloadHander?
) : JFrame() {//, ChromiumBrowser {
    companion object {
        private const val serialVersionUID = -5570653778104813836L
    }

    private val address_: JTextField
    private val cefApp_: CefApp
    private val client_: CefClient
    val browser: CefBrowser
    private val browserUI_: Component


    init {
        CefApp.addAppHandler(object : CefAppHandlerAdapter(null) {
            override fun stateHasChanged(state: CefApp.CefAppState) {
                // Shutdown the app if the native CEF part is terminated
                if (state == CefApp.CefAppState.TERMINATED) {
                    // calling System.exit(0) appears to be causing assert errors,
                    // as its firing before all of the CEF objects shutdown.
                    //System.exit(0);
                }
            }
        })
        val settings = CefSettings()
        settings.windowless_rendering_enabled = useOSR
        cefApp_ = CefApp.getInstance(settings)
        client_ = cefApp_.createClient()
        browser = client_.createBrowser(startURL, useOSR, isTransparent)
        browserUI_ = browser.uiComponent
        address_ = JTextField(startURL, 100)
        address_.addActionListener { browser.loadURL(address_.text) }
        client_.addDownloadHandler(cefDownloadHandler())
        contentPane.add(address_, BorderLayout.NORTH)
        contentPane.add(browserUI_, BorderLayout.CENTER)
        pack()
        setSize(800, 600)
        isVisible = true
        AWTEventMonitor.addWindowListener(object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent?) {
                CefApp.getInstance().dispose()
//                XDataViewer.dispose(null)
            }
        })
    }

    val currentUrl: String?
        get() = browser.url

    fun loadUrl(url: String) = browser.loadURL(url)

    fun goBack() = browser.goBack()

    fun goForward() = browser.goForward()

    fun quit() {
        CefApp.getInstance().dispose()
    }

    fun cefDownloadHandler() = object : CefDownloadHandler {
        override fun onBeforeDownload(
            browser: CefBrowser?, item: CefDownloadItem,
            suggestedName: String?, callback: CefBeforeDownloadCallback
        ) {
            downloadHandler?.onStart(
                itemId = item.id.toString(),
                suggestedFileName = item.suggestedFileName,
                totalBytes = item.totalBytes
            )
            /**
            public void Continue(String downloadPath, boolean showDialog);
             * Call to continue the download.
             * @param downloadPath Set it to the full file path for the download
             * including the file name or leave blank to use the suggested name and
             * the default temp directory.
             * @param showDialog Set it to true if you do wish to show the default
             * "Save As" dialog.
             */
            /**
            public void Continue(String downloadPath, boolean showDialog);
             * Call to continue the download.
             * @param downloadPath Set it to the full file path for the download
             * including the file name or leave blank to use the suggested name and
             * the default temp directory.
             * @param showDialog Set it to true if you do wish to show the default
             * "Save As" dialog.
             */
            callback.Continue(suggestedName, false)
        }

        /**
         * @param callback Callback interface used to asynchronously modify download status.
         */
        /**
         * @param callback Callback interface used to asynchronously modify download status.
         */
        override fun onDownloadUpdated(
            browser: CefBrowser?,
            item: CefDownloadItem,
            callback: CefDownloadItemCallback?
        ) {
            when {
                item.isComplete -> downloadHandler?.onCompleted(item.id.toString())
                !item.isValid || item.isCanceled -> downloadHandler?.onCanceled(item.id.toString())
                else -> downloadHandler?.onProgressUpdate(
                    itemId = item.id.toString(),
                    progressBytes = item.receivedBytes,
                    totalBytes = item.totalBytes,
                    speedBps = item.currentSpeed,
                    endTime = item.endTime
                )
            }
        }
    }
}