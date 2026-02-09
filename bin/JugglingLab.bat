@ECHO OFF

REM Windows launcher for Juggling Lab application
REM
REM Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
REM Released under the GNU General Public License v2

SET "JL_BAT_DIR=%~dp0"

REM Try to launch JugglingLab.jar using the system Java installation

IF EXIST "%JL_BAT_DIR%\JugglingLab.jar" (
    IF EXIST "%JAVA_HOME%\bin\java.exe" (
        "%JAVA_HOME%\bin\java.exe" -cp "%JL_BAT_DIR%\JugglingLab.jar" -Xss2048k ^
            -Djava.library.path="%JL_BAT_DIR%\ortools-lib\ortools-win32-x86-64" ^
            --enable-native-access=ALL-UNNAMED org.jugglinglab.MainKt
    ) ELSE (
        ECHO Java not found. Install Java 11 or higher and ensure JAVA_HOME
        ECHO environment variable is set.
    )
) ELSE (
    ECHO "JugglingLab.jar" not found in same directory as this script.
)

SET "JL_BAT_DIR="
