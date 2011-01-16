#!/bin/sh

jar xvf JugglingLab_source.jar
rm -r META-INF
mv build.xml ..
mv source ..

