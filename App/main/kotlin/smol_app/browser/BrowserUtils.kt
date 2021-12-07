package smol_app.browser

import timber.ktx.Timber
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

object BrowserUtils {
    @Throws(Exception::class)
    fun getData(address: String): String? =
        runCatching {
            (URL(address).openConnection() as HttpURLConnection).let { conn ->
                try {
                    conn.connect()
                    InputStreamReader(conn.content as InputStream)
                    InputStreamReader(
                        conn.content as InputStream
                    ).use { it.readText() }
                } finally {
                    conn.disconnect()
                }
            }
        }
            .onFailure { Timber.w(it) }
            .getOrNull()
}