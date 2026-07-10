# Juggling Lab juggling animator

Juggling Lab is an open-source application for creating and animating juggling patterns. Its main goals are to help people learn juggling patterns, and to assist with inventing new ones.

The [project site](https://jugglinglab.org) has more information, download links, and the [web application](https://jugglinglab.org/anim).

## The code

Juggling Lab is a Compose Multiplatform application written in Kotlin. It supports all desktop platforms (macOS, Windows, Linux), Android, iOS, and web/WASM. Clone the repository and play around with it! We appreciate bug reports (file under "Issues" above), and pull requests (bug fixes, new/updated pattern files, new features).

**Building Juggling Lab.** The project is built with [Gradle](https://gradle.org/). Use the command `./gradlew run` in the base directory of the repo to compile and run the desktop application.

For mobile development we recommend [Android Studio](https://developer.android.com/studio) (Android) and [Xcode](https://developer.apple.com/xcode/) (iOS). Both have good support for Gradle, and device emulation and testing. Open the repo in either app to configure the relevant plugins.

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
- Daniel Ortuño Juárez – Android testing and many improvement ideas
- Denis Paumier – Suggestions for passing and multiplexing improvements to siteswap generator
- Andrew Peterson – Performance profiling of animation routines
- Greg Phillips – Compose Multiplatform refactoring help and ideas, ANTLR siteswap parser, Android testing and ideas
- Romain Richard and Frédéric Rayar – Android version ([Google Play](https://play.google.com/store/apps/details?id=com.jonglen7.jugglinglab))
- Frédéric Roudaut – Design ideas for siteswap notation component, French language translation
- Daniel Simu – Bug reports, many design suggestions
- P. R. Vaidyanathan – Hand Siteswap feature input
- Matt Van Horn – Bug fix parsing multiplex patterns with 0s
- Xavier Verne – French translation of user interface
- Johannes Waldmann – Source code documentation
- Mahit Warhadpande – Hand Siteswap feature design and implementation
- Alan Weathers – Alanz pattern files, Android testing, bug reports, many application improvement ideas
