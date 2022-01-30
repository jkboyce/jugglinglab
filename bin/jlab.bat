@ECHO OFF

REM Windows command line interface for Juggling Lab
REM
REM Copyright 2002-2022 Jack Boyce and the Juggling Lab contributors
REM Released under the GNU General Public License v2

SET "JL_EXE="
SET "JL_BAT_DIR=%~dp0"
SET "JL_WORKING_DIR=%cd%"

IF EXIST "%JL_BAT_DIR%Juggling Lab.exe" (
    SET "JL_EXE=%JL_BAT_DIR%Juggling Lab.exe"
) ELSE (
    REM if script has been moved, look for Juggling Lab executable in its usual
    REM install location

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

    type "%TEMP%\jugglinglab_out.txt"
    del "%TEMP%\jugglinglab_out.txt"
) ELSE (
    ECHO "Juggling Lab.exe" not found on this computer.
)

SET "JL_EXE="
SET "JL_BAT_DIR="
SET "JL_WORKING_DIR="
