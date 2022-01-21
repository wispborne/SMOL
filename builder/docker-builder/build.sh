#!/bin/bash

# Clone the code
cd code
GIT_SSH_COMMAND="ssh -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no" git clone --depth 1 https://github.com/davidwhitman/SMOL.git --branch dev

cd SMOL

chmod +x gradlew

# Build SMOL and create update-config.xml file
./gradlew App:createDistributable Updater:run