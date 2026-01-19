# Juggling Lab juggling animator

Juggling Lab is an open-source application for creating and animating juggling patterns. Its main goals are to help people learn juggling patterns, and to assist with inventing new ones.

The [project site](https://jugglinglab.org) has more information and download links.

There is also a [web service](https://jugglinglab.org/html/animinfo.html) to generate animated GIFs from a pattern description.

## The code

Juggling Lab is written in Kotlin and uses standard Swing components, so it should run on any platform supported by Java 11 or later. Clone the repository and play around with it! I always appreciate bug reports (file under "Issues" above), and pull requests (bug fixes, new/updated pattern files, new features).

Juggling Lab is undergoing a transition from Swing to Compose Multiplatform, which will let us bring Juggling Lab to mobile and the web. By default it runs with the legacy Swing UI, but you can enable the Compose UI as described below (note it's still rough around the edges).

**Building Juggling Lab.** The project is built with [Gradle](https://gradle.org/). You can run these commands in the base directory of the repo:
- `gradlew run` – compile and run Juggling Lab
- `gradlew run -PJLcompose` – compile and run with the experimental Compose-based interface
- `gradlew build` – build the combined JAR file `/bin/JugglingLab.jar` (this is mostly for distributing the desktop application)

**Working on Juggling Lab code.** Since Juggling Lab is evolving into a Compose Multiplatform application, the best IDE to use is [IntelliJ IDEA](https://www.jetbrains.com/idea/) from JetBrains. Opening the repo in IDEA configures the relevant plugins. Currently two run configurations are available in the dropdown at the top of the project window: `jugglinglab [run]` and `jugglinglab [run Compose]`.

## Contributors

Juggling Lab has been in development since 1997 – the earliest days of the Java language. It started as an AWT applet running in a browser, then migrated to Swing with the release of Java 1.2. Eventually Juggling Lab morphed into the desktop application it is today.

Over that long span of time the project has seen contributions from many people, including:

- Jack Boyce – Most Juggling Lab code, project administration
- Roman Auvrey – Fixes to language localization code, French language translation
- Daniel Bareket – Hebrew translations
- Dominik Braun – Fun With Juggling Lab patterns, and many design ideas including camangle, showground, and hidejugglers settings, and the '^' repeat notation
- Vincent Bruel – Suggestions for improved bouncing support (hyperlift/hyperforce patterns), ball-bounce audio sample
- Brian Campbell – Bookmarklet
- Jason Haslam – Ring prop, bitmapped-image prop, improved ball graphic, visual editor enhancements, internationalization of user interface including Spanish and Portuguese translations, and many bug fixes
- Steve Healy (JAG) – Many invaluable design suggestions and bug reports, especially of siteswap notation component
- Anselm Heaton – Orbit-finding code, other design suggestions
- Lewis Jardine – Apache Ant build file, GPL clarifications
- Roman Karavia – CheerpJ conversion to run Juggling Lab in the browser
- Ken Matsuoka – JuggleMaster pattern library, used here with his permission
- Rupert Millard – Implementation of '*' shortcut for sync notation
- Herve Nicol – Bug fixes
- Denis Paumier – Suggestions for passing and multiplexing improvements to siteswap generator
- Andrew Peterson – Performance profiling of animation routines
- Greg Phillips – Compose Multiplatform refactoring help and ideas
- Romain Richard and Frédéric Rayar – [Android version](https://code.google.com/archive/p/jugglinglab/) ([Google Play](https://play.google.com/store/apps/details?id=com.jonglen7.jugglinglab))
- Frédéric Roudaut – Design ideas for siteswap notation component, French language translation
- Daniel Simu – Bug reports, many design suggestions
- P. R. Vaidyanathan – Hand Siteswap feature input
- Xavier Verne – French translation of user interface
- Johannes Waldmann – Source code documentation
- Mahit Warhadpande – Hand Siteswap feature design and implementation
- Alan Weathers – Alanz pattern files, bug reports, many application improvement ideas
