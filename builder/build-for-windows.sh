#!/bin/bash

# Java 16 or higher required
(cd .. && ./gradlew App:createDistributable Updater:run)