package smol_app.browser.chromium

//import sun.tools.jconsole.inspector.XDataViewer

import com.sun.java.accessibility.util.AWTEventMonitor
import org.cef.CefApp
import org.cef.CefClient
import org.cef.CefSettings
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.browser.CefMessageRouter
import org.cef.callback.CefBeforeDownloadCallback
import org.cef.callback.CefDownloadItem
import org.cef.callback.CefDownloadItemCallback
import org.cef.handler.CefAppHandlerAdapter
import org.cef.handler.CefDownloadHandler
import org.cef.handler.CefLifeSpanHandlerAdapter
import smol_app.browser.DownloadHander
import tests.detailed.handler.MessageRouterHandler
import tests.detailed.handler.MessageRouterHandlerEx
import java.awt.BorderLayout
import java.awt.Component
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.JPanel
import javax.swing.JTextField
import kotlin.io.path.absolutePathString


/**
 * This is a simple example application using JCEF.
 * It displays a JFrame with a JTextField at its top and a CefBrowser in its
 * center. The JTextField is used to enter and assign an URL to the browser UI.
 * No additional handlers or callbacks are used in this example.
 *
 * The number of used JCEF classes is reduced (nearly) to its minimum and should
 * assist you to get familiar with JCEF.
 *
 * For a more feature complete example have also a look onto the example code
 * within the package "example.detailed".
 *
 * Source: [https://github.com/viglucci/app-jcef-example/blob/14f5d7fc5c26a9492601cfedb346cf5974335fb3/src/main/java/example/simple/SimpleFrameExample.java]
 */
class CefBrowserPanel
// calling System.exit(0) appears to be causing assert errors,
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

//    Beside the normal handler instances, we're registering a MessageRouter
//    as well. That gives us the opportunity to reply to JavaScript method
//    calls (JavaScript binding). We're using the default configuration, so
//    that the JavaScript binding methods "cefQuery" and "cefQueryCancel"
//    are used.

// (5) All UI components are assigned to the default content pane of this
//     JFrame and afterwards the frame is made visible to the user.
//        getContentPane().add(address_, BorderLayout.NORTH)
//        getContentPane().add(browerUI_, BorderLayout.CENTER)

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
    private val downloadHandler: DownloadHander
) : JPanel(), ChromiumBrowser {

    companion object {
        private val serialVersionUID = -5570653778104813836L
        var address: JTextField? = null
        var cefApp: CefApp? = null
        var client: CefClient? = null
        var browser: CefBrowser? = null
        var browserUI: Component? = null
    }

    init {
        if (cefApp == null) {
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
            cefApp = CefApp.getInstance(settings)

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
            client = cefApp?.createClient()

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
            browser = client?.createBrowser(startURL, useOSR, isTransparent)
            browserUI = browser?.uiComponent

            // (4) For this minimal browser, we need only a text field to enter an URL
            //     we want to navigate to and a CefBrowser window to display the content
            //     of the URL. To respond to the input of the user, we're registering an
            //     anonymous ActionListener. This listener is performed each time the
            //     user presses the "ENTER" key within the address field.
            //     If this happens, the entered value is passed to the CefBrowser
            //     instance to be loaded as URL.
            address = JTextField(startURL, 100)
            address?.addActionListener { browser?.loadURL(address?.text) }

            //    Beside the normal handler instances, we're registering a MessageRouter
            //    as well. That gives us the opportunity to reply to JavaScript method
            //    calls (JavaScript binding). We're using the default configuration, so
            //    that the JavaScript binding methods "cefQuery" and "cefQueryCancel"
            //    are used.
            val msgRouter = CefMessageRouter.create()
            msgRouter.addHandler(MessageRouterHandler(), true)
            msgRouter.addHandler(MessageRouterHandlerEx(client), false)
            client!!.addMessageRouter(msgRouter)
//            client!!.addDownloadHandler(DownloadDialog(JFrame()))
            client!!.addDownloadHandler(cefDownloadHandler())
            client!!.addLifeSpanHandler(object : CefLifeSpanHandlerAdapter() {
                override fun onBeforePopup(
                    browser: CefBrowser?, frame: CefFrame?, target_url: String?, target_frame_name: String?
                ): Boolean {
                    target_url?.run { loadUrl(target_url) }
                    return true
                }
            })
        }
        layout = BorderLayout()
        add(browserUI)
        setSize(800, 600)
        isVisible = true
        AWTEventMonitor.addWindowListener(object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent?) {
                CefApp.getInstance().dispose()
            }
        })
    }

    override fun loadUrl(url: String) {
        browser?.loadURL(url)
    }

    fun cefDownloadHandler() = object : CefDownloadHandler {
        override fun onBeforeDownload(
            browser: CefBrowser?, item: CefDownloadItem,
            suggestedName: String?, callback: CefBeforeDownloadCallback
        ) {
            downloadHandler.onStart(
                itemId = item.id.toString(),
                suggestedFileName = suggestedName,
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
            callback.Continue(downloadHandler.getDownloadPathFor(suggestedName).absolutePathString(), false)
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
                item.isComplete -> downloadHandler.onCompleted(itemId = item.id.toString())
                !item.isValid || item.isCanceled -> downloadHandler.onCanceled(itemId = item.id.toString())
                else -> downloadHandler.onProgressUpdate(
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