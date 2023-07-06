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

package smol.utilities

import com.google.gson.JsonElement
import smol.timber.ktx.Timber
import java.nio.file.Path
import java.util.prefs.Preferences
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.reflect.KType
import kotlin.reflect.javaType
import kotlin.reflect.typeOf

class JsonFilePrefStorage(private val gson: Jsanity, private val file: Path) : IConfig.PrefStorage {
    init {
        if (!file.exists()) {
            file.parent?.createDirectories()
            file.createFile()
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    override fun <T> get(key: String, defaultValue: T, type: KType): T =
        IOLock.read {
            ((gson.fromJson<Map<*, JsonElement>>(
                json = file.readText(),
                filename = file.fileName.toString(),
                typeOfT = typeOf<Map<*, JsonElement>?>().javaType,
                shouldStripComments = false
            )
                .get(key))
                ?.run { gson.fromJson<T>(this, type.javaType) }
                ?: defaultValue)
                .also { Timber.v { "Read '$key' as '$it' in '${file.fileName}'." } }
        }

    @OptIn(ExperimentalStdlibApi::class)
    override fun <T> put(key: String, value: T?, type: KType) =
        IOLock.write {
            (gson.fromJson<Map<*, *>>(
                json = file.readText(),
                filename = file.fileName.toString(),
                typeOfT = typeOf<Map<*, *>?>().javaType,
                shouldStripComments = false
            )
                .toMutableMap().apply { this[key] = value as T }
                .run { file.writeText(gson.toJson(this)) })
                .also { Timber.v { "Set '$key' to '$it' in '${file.fileName}'." } }
        }

    override fun clear() = Preferences.userRoot().clear()
    override fun reload() = Unit
}