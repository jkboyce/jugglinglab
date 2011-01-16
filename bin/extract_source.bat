@echo off

jar xvf JugglingLab_source.jar
rmdir /S /Q META-INF
move build.xml ..
move source ..

