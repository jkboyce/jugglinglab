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

if [ -z "$1" ]; then
    echo "Usage: $0 [apple|intel] [signing_identity]"
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

# Step 1: Detect optional macOS signing identity

if [ -z "$MAC_SIGNING_IDENTITY" ]; then
    if [ -n "$2" ]; then
        MAC_SIGNING_IDENTITY="$2"
    else
        # Auto-detect using security find-identity
        # First look for Developer ID Application
        MAC_SIGNING_IDENTITY=$(security find-identity -v -p codesigning | grep "Developer ID Application:" | head -n 1 | sed -E 's/.*"([^"]+)".*/\1/')
        if [ -z "$MAC_SIGNING_IDENTITY" ]; then
            # Fall back to Apple Development
            MAC_SIGNING_IDENTITY=$(security find-identity -v -p codesigning | grep "Apple Development:" | head -n 1 | sed -E 's/.*"([^"]+)".*/\1/')
        fi
    fi
fi

if [ -n "$MAC_SIGNING_IDENTITY" ]; then
    echo "Using macOS signing identity: $MAC_SIGNING_IDENTITY"
else
    echo "No macOS signing identity found. The build will not be code-signed."
fi

extra_options=()
if [ -n "$MAC_SIGNING_IDENTITY" ]; then
   extra_options+=(--mac-sign)
   extra_options+=(--mac-signing-key-user-name "$MAC_SIGNING_IDENTITY")
   extra_options+=(--mac-entitlements "packaging/macos/entitlements.plist")
fi

# Step 2: Codesign native libraries inside JugglingLab.jar before packaging

if [ -n "$MAC_SIGNING_IDENTITY" ]; then
   echo "Signing native libraries inside JugglingLab.jar..."
   mkdir -p jar_temp
   cd jar_temp || exit
   
   # Extract list of .dylib and .jnilib files from JugglingLab.jar
   jar tf ../JugglingLab.jar | grep -E '\.(dylib|jnilib)$' > libs.txt
   
   if [ -s libs.txt ]; then
      while read -r lib_path; do
         if [ -n "$lib_path" ]; then
            echo "Extracting, signing, and re-packing $lib_path..."
            jar xf ../JugglingLab.jar "$lib_path"
            codesign --force --options runtime --timestamp --sign "$MAC_SIGNING_IDENTITY" "$lib_path"
            jar uf ../JugglingLab.jar "$lib_path"
         fi
      done < libs.txt
   else
      echo "No native libraries found inside JugglingLab.jar to sign."
   fi
   
   cd ..
   rm -rf jar_temp
   echo "Native libraries inside JugglingLab.jar signed successfully."
fi

# Step 3: Package the jar, JVM, and extra files into Juggling Lab.app

rm -rf "Juggling Lab.app"
mkdir target
cp JugglingLab.jar target
cp -r "ortools-lib/ortools-darwin-${ortools_arch_suffix}/"* target

jpackage --type app-image \
   --input target/ \
   --name "Juggling Lab" \
   --app-version "1.7.5" \
   --main-jar JugglingLab.jar \
   --mac-package-name "Juggling Lab" \
   --resource-dir "packaging/macos/" \
   --java-options -Xss2048k \
   --java-options -DJL_run_as_bundle=true \
   --java-options -Xdock:name=JugglingLab \
   --java-options --enable-native-access=ALL-UNNAMED \
   "${extra_options[@]}" \
   --verbose

rm -r target

# Step 4: Edit the application bundle in ways that jpackage doesn't support

cp "packaging/macos/JML_document.icns" "Juggling Lab.app/Contents/Resources/"
cp "packaging/macos/Juggling Lab.cfg" "Juggling Lab.app/Contents/app/"

# Re-sign the application bundle after modifications (to fix the signature broken by copying files)
if [ -n "$MAC_SIGNING_IDENTITY" ]; then
   echo "Re-signing the application bundle after modifications..."
   codesign --force --options runtime --timestamp --deep --sign "$MAC_SIGNING_IDENTITY" \
      --entitlements "packaging/macos/entitlements.plist" "Juggling Lab.app"
fi

# Step 5: Create the target dmg and rename to our convention

jpackage --type dmg \
   --app-image "Juggling Lab.app" \
   --name "Juggling Lab" \
   --app-version "1.7.5" \
   "${extra_options[@]}" \
   --verbose

if [[ "$architecture" == "arm64" ]]; then
   find . -name "Juggling Lab-*.dmg" \
      -exec bash -c 'no_space="${1// /}"; mv -f "$1" "$no_space"' _ {} \;
elif [[ "$architecture" == "x86_64" ]]; then
   find . \( -name "Juggling Lab-*.dmg" -o -name "JugglingLab-*.dmg" \) \
      -not -name "*-x86.dmg" \
      -exec bash -c 'no_space="${1// /}"; mv -f "$1" "${no_space%.dmg}-x86.dmg"' _ {} \;
fi

# Step 6: Sign the generated DMG installer if an identity is available

if [ -n "$MAC_SIGNING_IDENTITY" ]; then
   echo "Signing the generated DMG installer..."
   find . -name "JugglingLab-*.dmg" | while read -r dmg_file; do
      echo "Signing DMG: $dmg_file"
      codesign --force --sign "$MAC_SIGNING_IDENTITY" "$dmg_file"
   done
fi

# Step 7: Notarize and staple the generated DMG if a notary profile is specified

if [ -n "$NOTARY_PROFILE" ]; then
   echo "Submitting DMG for notarization using profile: $NOTARY_PROFILE..."
   find . -name "JugglingLab-*.dmg" | while read -r dmg_file; do
      echo "Submitting $dmg_file to Apple Notarization Service..."
      if xcrun notarytool submit "$dmg_file" --keychain-profile "$NOTARY_PROFILE" --wait; then
         echo "Notarization succeeded! Stapling ticket to DMG..."
         xcrun stapler staple "$dmg_file"
         echo "DMG successfully notarized and stapled."
      else
         echo "Error: Notarization failed."
      fi
   done
fi
