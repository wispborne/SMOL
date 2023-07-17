#!/bin/bash

#
# This file is distributed under the GPLv3. An informal description follows:
# - Anyone can copy, modify and distribute this software as long as the other points are followed.
# - You must include the license and copyright notice with each and every distribution.
# - You may this software for commercial purposes.
# - If you modify it, you must indicate changes made to the code.
# - Any modifications of this code base MUST be distributed with the same license, GPLv3.
# - This software is provided without warranty.
# - The software author or license can not be held liable for any damages inflicted by the software.
# The full license is available from <https://www.gnu.org/licenses/gpl-3.0.txt>.
#

# Clone the code
cd code
GIT_SSH_COMMAND="ssh -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no" git clone --depth 1 https://github.com/wispborne/SMOL.git --branch dev

cd SMOL

chmod +x gradlew

# Build SMOL and create update-config.xml file
./gradlew App:createDistributable Updater:run