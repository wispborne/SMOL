#!/bin/bash

# Clone the code
rm -rf SMOL
GIT_SSH_COMMAND="ssh -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no" git clone --depth 1 https://github.com/davidwhitman/SMOL.git --branch dev

cd SMOL

chmod +x gradlew

# Build SMOL
./gradlew App:createDistributable

# Create update-config.xml file
./gradlew Updater:run