@ECHO OFF

REM Windows command line interface for Juggling Lab
REM
REM Copyright 2019 by Jack Boyce and others
REM Released under the GNU General Public License v2

SET "JL_exe="
SET "JL_bat_dir=%~dp0"
SET "JL_working_dir=%cd%"

IF EXIST "%JL_bat_dir%Juggling Lab.exe" (
    SET JL_exe="%JL_bat_dir%Juggling Lab.exe"
) ELSE (
    REM if script has been moved, look for Juggling Lab executable in its usual
    REM install location

    IF EXIST "C:\Program Files\Juggling Lab\Juggling Lab.exe" (
        SET JL_exe="C:\Program Files\Juggling Lab\Juggling Lab.exe"
    )
)

IF DEFINED JL_exe (
    IF "%1"=="" (
        REM if no arguments then print a help message
        %JL_exe% help >%TEMP%\jugglinglab_out.txt
    ) ELSE (
        REM otherwise pass the args to Juggling Lab
        %JL_exe% %* >%TEMP%\jugglinglab_out.txt
    )

    REM We direct output to a temp file because Juggling Lab.exe launches Java using
    REM the javaw command, which swallows console output BUT allows it to be
    REM directed to a file. This solution works fine but it delays output until
    REM execution is finished, which may be a problem for long runs of the siteswap
    REM generator.

    type %TEMP%\jugglinglab_out.txt
    del %TEMP%\jugglinglab_out.txt
) ELSE (
    ECHO "Juggling Lab.exe" not found in same directory as this script.
)

SET "JL_exe="
SET "JL_batch_dir="
SET "JL_working_dir="
