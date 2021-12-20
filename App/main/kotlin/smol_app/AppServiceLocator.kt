package smol_app

import smol_access.SL
import smol_access.ServiceLocator
import smol_app.browser.DownloadManager
import smol_app.browser.javafx.JavaFxDownloader

var SL_UI = AppServiceLocator()

class AppServiceLocator internal constructor(
    val downloadManager: DownloadManager = DownloadManager(SL.access),
    val javaFxDownloader: JavaFxDownloader = JavaFxDownloader(downloadManager)
)

val ServiceLocator.UI
    get() = SL_UI