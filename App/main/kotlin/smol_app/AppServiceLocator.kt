package smol_app

import smol_app.browser.DownloadManager
import smol_app.browser.javafx.Downloader

var SLUI = AppServiceLocator()

class AppServiceLocator internal constructor(
    val downloadManager: DownloadManager = DownloadManager(),
    val downloader: Downloader = Downloader(downloadManager)
)