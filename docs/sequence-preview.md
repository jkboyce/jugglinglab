# Finite sequences and beat inspection

This change adds finite-sequence preview and beat controls to the existing
Juggling Lab Compose application. It uses the upstream parser, throw scheduler,
JML physical layout, renderer, camera controls, and ladder diagram. Normal loop
creation and validation still follow the existing path.

## Trying it

Open Pattern Entry. With **Play once (finite sequence)** checked, enter `345`
and press Run. Play advances through the sequence and stops after the last
landing. Restart, − Beat, + Beat, and the slider inspect individual beats.

The Playback speed menu beside these controls exposes the existing animation
Slowdown preference as a multiplier of Juggling Lab’s default speed, from
0.25× to 2×. Changing speed preserves the current beat and paused/playing state.
Animation Preferences still allows a custom slowdown factor.

Props held in the same hand are fanned out in screen space so their inventory
can be counted. The first prop stays anchored to the physical hand; additional
props form a small outward stack. Incoming catches smoothly make room by moving
resident balls aside; the arriving ball reaches the native hand position.
Departing balls return smoothly to that position before release, and remaining
inventory settles afterward. Motion is derived from native carry intervals in
simulation time, so scrubbing works in either direction and playback speed also
slows the handoff. Only drawing coordinates change; throw scheduling, physical
trajectories, hand positions, and exported JML do not.

A hand may catch while holding balls reserved for later throws. This is not an
error. For example, finite `357` and `3555555` can finish, but repeating them
conflicts at beats 7 and 11 respectively, when two arrivals compete for one
scheduled slot. Preview initial inventory supplies missing incoming balls; it
does not excuse a later overbooked landing.

With Play once unchecked, valid patterns animate normally. Enter `32` to
preview an invalid loop: it stops on beat 4 and reports the affected hand and
catch capacity. Multiplex destinations are checked against their planned
capacity rather than a universal one-ball limit.

The existing notation remains available, including `(4,4)`, `(4,2x)*`,
`24[54]`, `(4,2)(2x,[44x])`, `<3p 3|3p 3>`, numbered passes, mixed sync/async,
short-beat `!`, hand specifiers, and grouped repeats.

## Implementation

`SiteswapPattern.inspectThrows()` runs the native parser and its two scheduling
passes without requiring an integer repeating average or assigning periodic
paths. Finite input does not receive the implicit mirrored repeat used for odd
asynchronous loop periods. Inspection has bounded input/expansion limits.

`SequencePreviewBuilder.plan()` detects incompatible landings and identifies
the first beat, juggler, hand, and capacity. `build()` follows the native throws
into ball paths and creates JML for the prefix. Initial balls are supplied to
hands as needed. A hidden closing tail satisfies JML's periodic representation;
the preview window stops before that tail. Native hand-motion gap completion
and hold fixing are reused. The animation state and frame loop enforce the
window, including after time scaling or replay.

Finite previews currently use default hand/body trajectories; normal repeating
patterns retain the original choreography and modifier handling. The finite
preview's stopping window is session state: exporting its JML exports the
underlying closed representation, not a portable finite-playback flag. These
limits should be considered before extending the feature to shared/exported
finite sequences.

The timeline/form integration targets the shared Compose app (web/mobile and
Compose desktop). The default desktop Swing controls are unchanged. Desktop
and mobile builds have not been exercised in this work.

## Web-only build

The ordinary project build files are unchanged. The optional browser build
avoids configuring Android/iOS targets on a web-only workstation. It uses the
same application sources and dependency versions.

Requires JDK 21 and network access for Gradle dependencies:

```sh
./gradlew -p browser-build :composeApp:wasmJsBrowserDistribution
```

Output: `composeApp/build/dist/wasmJs/productionExecutable/`.
Serve that directory with an ordinary HTTP server.

Focused tests of the real WebAssembly engine, including native JML layout:

```sh
NODE_OPTIONS="--import=$(pwd)/browser-build/test-node-preload.mjs" \
  ./gradlew -p browser-build :composeApp:wasmJsNodeTest \
  --tests '*SequencePreviewTest*' --tests '*HeldPropLayoutTest*'
```

The Node preload supplies browser platform metadata and local WASM loading for
Skia; it does not replace the native engine or render a UI. Existing upstream
tests that assert hard-coded English error text require localized resources to
be prewarmed when run under Node; the normal browser app already prewarms them.
