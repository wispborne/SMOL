#!/bin/bash

echo "Running as: $(whoami)"
echo "Dir: $(pwd)"

# Remove existing repo if it exists before re-cloning it.
rm -rfv ./StarsectorModRepo

# Get Mod Repo.
git clone git@github.com:davidwhitman/StarsectorModRepo.git --depth 1 StarsectorModRepo

# Download scraper
curl -L https://github.com/davidwhitman/SMOL/releases/latest/download/Mod_Repo-fat.jar > Mod_Repo-fat.jar

# Remove existing ModRepo.json (if it has broken json then the scraper will fail when trying to update it).
rm -v ModRepo.json

# Run scraper, puts file in current folder.
java -Xmx512m -jar ./Mod_Repo-fat.jar

# Copy file into folder
cp -v ModRepo.json StarsectorModRepo

# Enter folder, staging, commit, and push file.
cd StarsectorModRepo || exit
echo "Dir: $(pwd)"
git add .
git commit -m "Auto-updated ModRepo.json."
git push

# Back out to original folder.
cd ..
echo "Dir: $(pwd)"
