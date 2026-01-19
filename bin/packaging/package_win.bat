@ECHO OFF

REM Juggling Lab Windows application packager
REM
REM Copyright 2022-2026 Jack Boyce and the Juggling Lab contributors
REM Released under the GNU General Public License v2
REM
REM -----------------------------------------------------------------------------
REM
REM This script packages Juggling Lab into a standalone Windows installer
REM
REM It:
REM    - Builds the Windows application bundle "Juggling Lab\" in the bin\ directory
REM    - Packages this into an installer .exe file using Inno Setup
REM
REM Note:
REM    - JugglingLab.jar needs to be built prior to running this, using Maven
REM    - Need to be using JDK 16 or later for jpackage to work
REM    - Need to have Inno Setup 6 or later installed
REM
REM Documentation at:
REM    https://docs.oracle.com/en/java/javase/25/jpackage/packaging-overview.html
REM    https://docs.oracle.com/en/java/javase/25/docs/specs/man/jpackage.html
REM
REM Notes:
REM    - The "-Xss2048k" JVM argument is needed for Google OR-Tools to work
REM    - The "--enable-native-access" JVM argument is needed so optimizer can load
REM      the OR-Tools native library

REM Step 1: Build the application bundle directory "Juggling Lab\"

cd ..
mkdir target
copy JugglingLab.jar target
copy ortools-lib\ortools-win32-x86-64\* target

jpackage --type app-image ^
   --input target ^
   --name "Juggling Lab" ^
   --app-version "1.6.9" ^
   --main-jar JugglingLab.jar ^
   --resource-dir "packaging/windows/" ^
   --java-options -Xss2048k ^
   --java-options -DJL_run_as_bundle=true ^
   --java-options --enable-native-access=ALL-UNNAMED ^
   --verbose

rmdir /S /Q target

REM Step 2: Edit the application bundle in ways that jpackage doesn't support

copy /Y "packaging\windows\Juggling Lab.ico" "Juggling Lab\"
copy /Y "packaging\windows\JML_document.ico" "Juggling Lab\"
copy /Y "packaging\windows\Juggling Lab.cfg" "Juggling Lab\app\"

REM Step 3: Create the installer .exe and clean up

copy /Y "packaging\windows\Juggling Lab.iss" .
copy /Y "packaging\windows\Juggling Lab-setup-icon.bmp" .

iscc "Juggling Lab.iss"

copy /Y Output\*.exe .
rmdir /S /Q Output
rmdir /S /Q "Juggling Lab\"
del "Juggling Lab.iss"
del "Juggling Lab-setup-icon.bmp"


REM To have jpackage create the installer instead of Inno Setup:
REM jpackage --type exe ^
REM    --app-image "Juggling Lab" ^
REM    --app-version "1.6.9" ^
REM    --file-associations "packaging\windows\FAjml.properties" ^
REM    --win-menu ^
REM    --verbose
