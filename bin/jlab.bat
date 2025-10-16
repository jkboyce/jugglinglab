@ECHO OFF

REM Windows command line interface for Juggling Lab
REM
REM Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
REM Released under the GNU General Public License v2

SET "JL_EXE="
SET "JL_BAT_DIR=%~dp0"
SET "JL_WORKING_DIR=%cd%"

REM First try to run JugglingLab.jar using system java installation.

IF EXIST "%JL_BAT_DIR%\JugglingLab.jar" (
    IF EXIST "%JAVA_HOME%\bin\java.exe" (
        IF "%1"=="" (
            REM if no arguments then print a help message
            "%JAVA_HOME%\bin\java.exe" -cp "%JL_BAT_DIR%\JugglingLab.jar" jugglinglab.JugglingLab help
        ) ELSE (
            REM otherwise pass the args to Juggling Lab
            "%JAVA_HOME%\bin\java.exe" -cp "%JL_BAT_DIR%\JugglingLab.jar" -Xss2048k ^
                -Djava.library.path="%JL_BAT_DIR%\ortools-lib\ortools-win32-x86-64" ^
                --enable-native-access=ALL-UNNAMED jugglinglab.JugglingLab %*
        )

        SET "JL_EXE="
        SET "JL_BAT_DIR="
        SET "JL_WORKING_DIR="
        EXIT /B
    )
)

REM Otherwise, run using the installed Juggling Lab Windows binary

IF EXIST "%JL_BAT_DIR%\Juggling Lab.exe" (
    SET "JL_EXE=%JL_BAT_DIR%\Juggling Lab.exe"
) ELSE (
    IF EXIST "C:\Program Files\Juggling Lab\Juggling Lab.exe" (
        SET "JL_EXE=C:\Program Files\Juggling Lab\Juggling Lab.exe"
    )
)

IF DEFINED JL_EXE (
    REM Direct output to a temp file because Juggling Lab.exe launches Java using
    REM the javaw command, which swallows console output BUT allows it to be
    REM directed to a file. Note this delays output until execution is finished,
    REM something to remember for long runs of the siteswap generator.

    IF "%1"=="" (
        REM if no arguments then print a help message
        "%JL_EXE%" help >"%TEMP%\jugglinglab_out.txt"
    ) ELSE (
        REM otherwise pass the args to Juggling Lab
        "%JL_EXE%" %* >"%TEMP%\jugglinglab_out.txt"
    )

    TYPE "%TEMP%\jugglinglab_out.txt"
    DEL "%TEMP%\jugglinglab_out.txt"
) ELSE (
    ECHO "Juggling Lab.exe" not found.
)

SET "JL_EXE="
SET "JL_BAT_DIR="
SET "JL_WORKING_DIR="
