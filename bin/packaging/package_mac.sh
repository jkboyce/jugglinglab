#!/bin/bash
#
# Juggling Lab macOS application packager
#
# Copyright 2022-2025 by Jack Boyce and the Juggling Lab contributors
# Released under the GNU General Public License v2
#
# -----------------------------------------------------------------------------
#
# This script packages Juggling Lab into a standalone macOS application
#
# It:
#    - Builds the macOS application bundle "Juggling Lab.app" in the bin/ directory
#    - Application is targeted to the same architecture of Mac (Arm vs. x86) that
#      the script is run on
#    - Packages the application into a dmg file
#
# Note:
#    - Juggling Lab.app in the bin directory will be overwritten
#    - JugglingLab.jar needs to be built prior to running this, using Maven
#    - Need to be using JDK 16 or later for `jpackage` to work
#
# Documentation at:
#    https://docs.oracle.com/en/java/javase/25/jpackage/packaging-overview.html
#    https://docs.oracle.com/en/java/javase/25/docs/specs/man/jpackage.html
#
# Notes:
#    - The "-Xss2048k" JVM argument is needed for Google OR-Tools to work
#    - The "--enable-native-access" JVM argument is needed so optimizer can load
#      the OR-Tools native library

# Step 1: Build the application bundle "Juggling Lab.app"

cd ..
rm -rf "Juggling Lab.app"
mkdir target
cp JugglingLab.jar target

architecture=$(uname -m)
if [[ "$architecture" == "arm64" ]]; then
   echo "Building installer for Apple Silicon (ARM)"
   cp -r ortools-lib/ortools-darwin-aarch64/* target
elif [[ "$architecture" == "x86_64" ]]; then
   echo "Building installer for Intel (x86)"
   cp -r ortools-lib/ortools-darwin-x86-64/* target
else
   echo "Unknown architecture: $architecture"
   rm -r target
   exit
fi

jpackage --type app-image \
   --input target/ \
   --name "Juggling Lab" \
   --app-version "1.6.6" \
   --main-jar JugglingLab.jar \
   --mac-package-name "Juggling Lab" \
   --resource-dir "packaging/macos/" \
   --java-options -Xss2048k \
   --java-options -DJL_run_as_bundle=true \
   --java-options -Xdock:name=JugglingLab \
   --java-options --enable-native-access=ALL-UNNAMED \
   --verbose

rm -r target

# Step 2: Edit the application bundle in ways that jpackage doesn't support

cp "packaging/macos/JML_document.icns" "Juggling Lab.app/Contents/Resources/"
cp "packaging/macos/Juggling Lab.cfg" "Juggling Lab.app/Contents/app/"

# Step 3: Create the target dmg and rename to our convention

jpackage --type dmg \
   --app-image "Juggling Lab.app" \
   --name "Juggling Lab" \
   --app-version "1.6.6" \
   --verbose

if [[ "$architecture" == "arm64" ]]; then
   find . -name "Juggling Lab-*.dmg" \
      -exec bash -c 'no_space="${1// /}"; mv -f "$1" "$no_space"' _ {} \;
elif [[ "$architecture" == "x86_64" ]]; then
   find . \( -name "Juggling Lab-*.dmg" -o -name "JugglingLab-*.dmg" \) \
      -not -name "*-x86.dmg" \
      -exec bash -c 'no_space="${1// /}"; mv -f "$1" "${no_space%.dmg}-x86.dmg"' _ {} \;
fi
