#!/bin/bash

# Download and run scraper
curl -L https://github.com/davidwhitman/SMOL/releases/latest/download/Mod_Repo-fat.jar > Mod_Repo-fat.jar
java -jar ./Mod_Repo-fat.jar

# Get Mod Repo (update it if it exists), copy the new file into it, and push the change.
git clone git@github.com:davidwhitman/StarsectorModRepo.git StarsectorModRepo
cd StarsectorModRepo
git pull
cp ../ModRepo.json .
git add .
git commit -m "Auto-updated ModRepo.json."
git push
cd ..