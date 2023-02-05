#!/bin/bash
#
# Juggling Lab Debian Linux application packager
#
# Copyright 2022-2023 by Jack Boyce and the Juggling Lab contributors
# Released under the GNU General Public License v2
#
# -----------------------------------------------------------------------------
#
# This script packages Juggling Lab into a standalone Debian application
#
# It:
#    - Builds the package "juggling-lab_<version>_amd64.deb" in the bin/ directory
#
# Note:
#    - JugglingLab.jar needs to be built prior to running this, using Maven
#    - Need to be using JDK 16 or later for jpackage to work
#
# Documentation at:
#    https://docs.oracle.com/en/java/javase/17/jpackage/packaging-overview.html
#    https://docs.oracle.com/en/java/javase/17/docs/specs/man/jpackage.html
# Note: the "-Xss2048k" JVM argument is needed for Google OR-Tools to work

cd ..
mkdir target
cp JugglingLab.jar target
cp -r ortools-lib/ortools-linux-x86-64/* target

jpackage \
   --input target/ \
   --name "Juggling Lab" \
   --linux-package-name "juggling-lab" \
   --app-version "1.6.4" \
   --main-jar JugglingLab.jar \
   --resource-dir "packaging/debian/" \
   --java-options -Xss2048k \
   --java-options -DJL_run_as_bundle=true \
   --java-options -Djava.library.path=\$APPDIR \
   --verbose

rm -r target

# Remove Debian revision number from filename

find . -name "*-1_amd64.deb" -type f \
   -exec bash -c 'rm "${0/-1_amd64.deb/_amd64.deb}"; mv "$0" "${0/-1_amd64.deb/_amd64.deb}"' {} \;
