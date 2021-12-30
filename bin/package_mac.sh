
#!/bin/bash
#
# Juggling Lab macOS application packager
#
# Copyright 2021 by Jack Boyce and the Juggling Lab contributors
# Released under the GNU General Public License v2
#
# -----------------------------------------------------------------------------
#
# This script packages Juggling Lab into a standalone macOS application
#
# It:
#    (a) builds the macOS application bundle "Juggling Lab.app" in the bin/ directory
#    (b) packages this into a dmg file
#
# Note:
#    - Juggling Lab.app in the bin directory will be overwritten
#    - JugglingLab.jar needs to be built prior to running this, using Ant
#    - Need to be using JDK 16 or later for jpackage to work
#    - Need to have Xcode installed for codesign to work
#
# Documentation at:
#    https://docs.oracle.com/en/java/javase/17/jpackage/packaging-overview.html
#    https://docs.oracle.com/en/java/javase/17/docs/specs/man/jpackage.html
# Note: the "-Xss2048k" JVM argument is needed for Google OR-Tools to work

# Step 1: Build the application bundle "Juggling Lab.app"

rm -rf "Juggling Lab.app"
mkdir target
cp JugglingLab.jar target
cp -r ortools-lib/* target

jpackage --type app-image \
   --input target/ \
   --name "Juggling Lab" \
   --app-version "1.5.3" \
   --main-jar JugglingLab.jar \
   --mac-package-name "Juggling Lab" \
   --resource-dir "../source/resources/package/macos/" \
   --java-options -Xss2048k \
   --java-options -DJL_run_as_bundle=true \
   --java-options -Xdock:name=JugglingLab \
   --verbose

rm -r target

# Step 2: Edit the application bundle in ways that jpackage doesn't support

cp "../source/resources/package/macos/JML_document.icns" \
   "Juggling Lab.app/Contents/Resources/"
cp "../source/resources/package/macos/Juggling Lab.cfg" \
   "Juggling Lab.app/Contents/app/"

# Remove the Oracle signature on the application, which causes Gatekeeper to
# refuse to launch the app since it isn't notarized. With no signature the user
# gets the "Developer cannot be verified" warning but they can launch.
codesign --remove-signature "Juggling Lab.app"

# Step 3: Create the target dmg

jpackage --type dmg \
   --app-image "Juggling Lab.app" \
   --name "Juggling Lab" \
   --app-version "1.5.3" \
   --verbose

find . -name "Juggling Lab*.dmg" -type f \
   -exec bash -c 'rm "${0/Juggling Lab/JugglingLab}"; mv "$0" "${0/Juggling Lab/JugglingLab}"' {} \;

