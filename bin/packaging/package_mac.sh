#!/bin/bash
#
# Juggling Lab macOS application packager
#
# Copyright 2022-2026 by Jack Boyce and the Juggling Lab contributors
# Released under the GNU General Public License v2
#
# -----------------------------------------------------------------------------
#
# This script packages Juggling Lab into a standalone macOS application
#
# It:
#    - Targets the processor architecture specified on the command line
#      ("apple" or "intel")
#    - Builds the macOS application bundle "Juggling Lab.app" in the bin/
#      directory
#    - Packages the application into a dmg file
#
# Note:
#    - Juggling Lab.app in the bin directory will be overwritten
#    - JugglingLab.jar needs to be built prior to running this, using Maven
#    - Need to be using JDK 16 or later for `jpackage` to work
#    - Need to be using an Intel JDK if targeting Intel, Arm JDK otherwise
#
# Documentation at:
#    https://docs.oracle.com/en/java/javase/25/jpackage/packaging-overview.html
#    https://docs.oracle.com/en/java/javase/25/docs/specs/man/jpackage.html
#
# Notes:
#    - The "-Xss2048k" JVM argument is needed for Google OR-Tools to work
#    - The "--enable-native-access" JVM argument is needed so optimizer can
#      load the OR-Tools native library

# Step 1: Build the application bundle "Juggling Lab.app"

if [ -z "$1" ]; then
    echo "Usage: $0 [apple|intel]"
    exit 1
fi
case "$1" in
    apple)
        echo "Building installer for Apple Silicon (ARM)"
        architecture="arm64"
        ortools_arch_suffix="aarch64"
        ;;
    intel)
        echo "Building installer for Intel (x86)"
        architecture="x86_64"
        ortools_arch_suffix="x86-64"
        ;;
    *)
        echo "Invalid argument: $1. Use 'apple' or 'intel'."
        exit 1
        ;;
esac

cd ..
rm -rf "Juggling Lab.app"
mkdir target
cp JugglingLab.jar target
cp -r "ortools-lib/ortools-darwin-${ortools_arch_suffix}/"* target

jpackage --type app-image \
   --input target/ \
   --name "Juggling Lab" \
   --app-version "1.6.8" \
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
   --app-version "1.6.8" \
   --verbose

if [[ "$architecture" == "arm64" ]]; then
   find . -name "Juggling Lab-*.dmg" \
      -exec bash -c 'no_space="${1// /}"; mv -f "$1" "$no_space"' _ {} \;
elif [[ "$architecture" == "x86_64" ]]; then
   find . \( -name "Juggling Lab-*.dmg" -o -name "JugglingLab-*.dmg" \) \
      -not -name "*-x86.dmg" \
      -exec bash -c 'no_space="${1// /}"; mv -f "$1" "${no_space%.dmg}-x86.dmg"' _ {} \;
fi
