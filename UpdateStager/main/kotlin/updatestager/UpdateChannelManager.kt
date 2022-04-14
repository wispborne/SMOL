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

package updatestager

import org.update4j.Configuration
import smol_access.config.AppConfig
import update_installer.BaseAppUpdater
import update_installer.UpdateChannel

class UpdateChannelManager {
    /**
     * Sets the release channel for the user.
     */
    fun setUpdateChannel(updateChannel: UpdateChannel, appConfig: AppConfig) {
        appConfig.updateChannel = when (updateChannel) {
            UpdateChannel.Stable -> AppConfig.UpdateChannel.Stable
            UpdateChannel.Unstable -> AppConfig.UpdateChannel.Unstable
            UpdateChannel.Test -> AppConfig.UpdateChannel.Test
        }
    }


    /**
     * Downloads the [Configuration] file from GitHub for the specified release channel.
     */
    suspend fun fetchRemoteConfig(updater: BaseAppUpdater, appConfig: AppConfig): Configuration =
        updater.fetchRemoteConfig(getUpdateChannelSetting(appConfig))

    companion object {
        /**
         * Gets the current channel (eg stable/unstable).
         */
        fun getUpdateChannelSetting(appConfig: AppConfig): UpdateChannel {
            return when (appConfig.updateChannel) {
                AppConfig.UpdateChannel.Stable -> UpdateChannel.Stable
                AppConfig.UpdateChannel.Unstable -> UpdateChannel.Unstable
                AppConfig.UpdateChannel.Test -> UpdateChannel.Test
            }
        }
    }
}