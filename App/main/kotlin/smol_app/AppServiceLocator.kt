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

package smol_app

import smol_access.SL
import smol_access.ServiceLocator
import smol_access.business.VmParamsManager
import smol_app.browser.DownloadManager
import smol_app.toasts.ToasterState
import update_installer.BaseAppUpdater
import update_installer.SmolUpdater
import updatestager.UpdateChannelManager
import updatestager.UpdaterUpdater
import utilities.currentPlatform

var SL_UI = AppServiceLocator()

class AppServiceLocator internal constructor(
    val downloadManager: DownloadManager = DownloadManager(access = SL.access, gamePathManager = SL.gamePathManager),
    val uiConfig: UIConfig = UIConfig(gson = SL.jsanity),
    val toaster: ToasterState = ToasterState(),
    val vmParamsManager: VmParamsManager = VmParamsManager(gamePathManager = SL.gamePathManager, platform = currentPlatform),
    val smolUpdater: BaseAppUpdater = SmolUpdater(),
    val updateChannelManager: UpdateChannelManager = UpdateChannelManager(),
    val updaterUpdater: BaseAppUpdater = UpdaterUpdater(),
)

val ServiceLocator.UI
    get() = SL_UI