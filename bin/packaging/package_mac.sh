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
#    - Packages this into a dmg file
#
# Note:
#    - Juggling Lab.app in the bin directory will be overwritten
#    - JugglingLab.jar needs to be built prior to running this, using Maven
#    - Need to be using JDK 16 or later for jpackage to work
#    - Need to have Xcode installed for codesign to work
#    - Need to run under an x86-based version of JDK in order to build a binary
#      that runs on any Mac (jpackage builds for the CPU type the active JDK uses,
#      and all Macs can run Intel code either natively or via Rosetta)
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
cp -r ortools-lib/ortools-darwin-aarch64/* target

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

# Remove the Oracle signature on the application, which causes Gatekeeper to
# refuse to launch the app since it isn't notarized. With no signature the user
# gets the "Developer cannot be verified" warning but they can launch.
#
# Note: this step no longer seems necessary with Java 25
#
# codesign --remove-signature "Juggling Lab.app"

# Step 3: Create the target dmg

jpackage --type dmg \
   --app-image "Juggling Lab.app" \
   --name "Juggling Lab" \
   --app-version "1.6.6" \
   --verbose

find . -name "Juggling Lab*.dmg" -type f \
   -exec bash -c 'rm -f "${0/Juggling Lab/JugglingLab}"; mv "$0" "${0/Juggling Lab/JugglingLab}"' {} \;

