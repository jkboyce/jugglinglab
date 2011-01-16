#!/bin/csh -f

cd ..
cd Juggling\ Lab.app/Contents/Resources/Java
java -cp JugglingLab.jar jugglinglab/generator/siteswapGenerator $*
