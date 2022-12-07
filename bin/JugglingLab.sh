#!/bin/bash
#
# Command line launcher for Juggling Lab application
#
# Copyright 2002-2022 Jack Boyce and the Juggling Lab contributors
# Released under the GNU General Public License v2


DIR=$(dirname "$0")
export JL_WORKING_DIR="`pwd`"

# Make sure an adequate version of Java is installed
if type java >/dev/null 2>&1; then
    _java=java
elif [[ -n "${JAVA_HOME}" ]] && [[ -x "${JAVA_HOME}/bin/java" ]]; then
    _java="${JAVA_HOME}/bin/java"
else
    echo >&2 "Java not found. Be sure Java 11 or higher is installed and \$JAVA_HOME"
    echo >&2 "environment variable is set."
    exit 1
fi

if [[ "$_java" ]]; then
    version=$("$_java" -version 2>&1 | awk -F '"' '/version/ {print $2}')
    version1=$(echo "$version" | awk -F. '{printf("%03d%03d",$1,$2);}')
    if [ "$version1" \< "011000" ]; then
        echo >&2 "Java installed is version ${version}; need Java 11 or higher to run."
        exit 1
    fi
fi

# Launch the jar
JL_JAR="${DIR}/JugglingLab.jar"

if [ -a "${JL_JAR}" ]; then
    "$_java" -cp "${JL_JAR}" -Xss2048k -Djava.library.path=ortools-lib/ortools-linux-x86-64 jugglinglab.JugglingLab
    exit 1
else
    echo >&2 "\"JugglingLab.jar\" not found in same directory as this script."
    exit 1
fi
