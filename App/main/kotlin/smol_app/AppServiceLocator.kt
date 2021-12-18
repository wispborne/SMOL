package smol_app

import smol_access.ServiceLocator
import smol_app.browser.DownloadManager
import smol_app.browser.javafx.Downloader

var SL_UI = AppServiceLocator()

class AppServiceLocator internal constructor(
    val downloadManager: DownloadManager = DownloadManager(),
    val downloader: Downloader = Downloader(downloadManager)
)

val ServiceLocator.UI
    get() = SL_UI