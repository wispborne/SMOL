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

package smol.updatestager

import smol.access.Constants
import smol.update_installer.BaseAppUpdater
import smol.update_installer.SmolUpdater
import smol.update_installer.UpdateChannel
import smol.utilities.toPathOrNull

class Main {
    companion object {
        /**
         * First arg must be `directoryOfFilesToAddToManifest`.
         * Second arg must be `channel`: [stable, unstable, test].
         */
        @JvmStatic
        fun main(args: Array<String>) {
            val channel = when (args[1].lowercase()) {
                "stable", "main" -> UpdateChannel.Stable
                "unstable" -> UpdateChannel.Unstable
                "test" -> UpdateChannel.Test
                else -> throw RuntimeException("Unrecognized channel: ${args[0]}.")
            }
            val url = when (channel) {
                UpdateChannel.Stable -> Constants.UPDATE_URL_STABLE
                UpdateChannel.Unstable -> Constants.UPDATE_URL_UNSTABLE
                UpdateChannel.Test -> Constants.UPDATE_URL_TEST
            }

            WriteLocalUpdateConfig.run(
                onlineUrl = url,
                directoryOfFilesToAddToManifest = args[0].toPathOrNull()!!,
                updater = SmolUpdater(),
                channel = channel
            )
            WriteLocalUpdateConfig.run(
                onlineUrl = url,
                directoryOfFilesToAddToManifest = args[0].toPathOrNull()!!,
                updater = UpdaterUpdater(),
                channel = channel
            )
        }
    }
}