package smol_app

import smol_access.SL
import smol_access.ServiceLocator
import smol_app.browser.DownloadManager
import smol_app.toasts.ToasterState

var SL_UI = AppServiceLocator()

class AppServiceLocator internal constructor(
    val downloadManager: DownloadManager = DownloadManager(SL.access),
    val uiConfig: UIConfig = UIConfig(SL.jsanity),
    val toaster: ToasterState = ToasterState()
)

val ServiceLocator.UI
    get() = SL_UI