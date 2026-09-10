# AFFiNE Galaxy Tab Client

A thin native Android shell around your self-hosted [AFFiNE](https://affine.pro) instance,
built for Galaxy Tab + S Pen: full-screen webapp, real palm rejection while drawing, and a
couple of tablet conveniences (desktop layout toggle, back-button = webview history).

## Why a WebView shell instead of a "real" native app

AFFiNE's editor (BlockSuite) is a large, actively-changing web codebase built on Yjs CRDTs.
There's no public native SDK for it, and reimplementing its document/whiteboard engine in
Kotlin would be its own multi-year project. Wrapping the real web client gets you the actual,
up-to-date AFFiNE — the part this app adds is what a browser tab can't give you: OS-level palm
rejection, an app icon, and a kiosk-like full-screen layout.

## What the S Pen support actually does

Browsers only see pointer *type* (pen/touch/mouse) — they have no idea a given touch is a palm
resting on the glass. Real palm rejection has to happen one layer down, in Android's raw
`MotionEvent` stream, before it ever reaches the WebView/Chromium. That's what
[`PalmRejectingWebView`](app/src/main/java/com/affinetablet/client/PalmRejectingWebView.kt) does:

- Watches S Pen **hover** (`ACTION_HOVER_*`) to know the pen is in play *before* it touches down —
  this is what stops a resting palm's first touch from sneaking through in the gap between the
  pen approaching and the hand landing.
- While the pen is "active" (touching or recently hovering), any pointer in a touch event that
  isn't `TOOL_TYPE_STYLUS`/`TOOL_TYPE_ERASER` is stripped out of the event before it's dispatched
  to the WebView. If a whole event is palm-only, it's swallowed entirely.
- The stylus pointer itself is untouched, so pressure/tilt still reach AFFiNE's edgeless canvas
  normally via Chromium's own `PointerEvent` synthesis — free draw with pressure-sensitive line
  width works as it does on desktop Chrome with a stylus.

Known limitation: if the palm lands *before* the pen ever hovers (e.g. you rest your hand down
first, then bring the pen in from the side at a shallow angle the digitizer doesn't see early),
the first touch can still get through. This matches the behavior of most third-party palm
rejection implementations that don't have privileged access to Samsung's own digitizer stack.

## Setup

1. Open this folder in Android Studio (Koala/2024.1+) and let it sync, or build from the command
   line with `./gradlew assembleDebug` (JDK 17 required). The Gradle wrapper is checked in.
2. Run on a Galaxy Tab (S Pen models: Tab S6 and up) via USB debugging, or install the APK from
   a [release](../../releases) built by the CI workflow below.
3. On first launch, enter your self-hosted AFFiNE server URL (e.g. `https://affine.example.com`,
   or `http://10.x.x.x:3010` if it's only reachable over your WireGuard tunnel/LAN). Cleartext
   http is allowed for exactly this reason — see `app/src/main/res/xml/network_security_config.xml`.
4. Sign in as normal through AFFiNE's own login page inside the app.

The top-right menu lets you reload, toggle a desktop-layout user agent, switch servers, or sign
out and wipe local WebView storage/cookies.

## Building a tagged release (CI)

`.github/workflows/build-and-tag.yml` is manual-only (`workflow_dispatch`). Run it from the
**Actions** tab, or `gh workflow run build-and-tag.yml -f bump=patch` (`bump` is `patch`/`minor`/
`major`). It:

1. Looks at the latest `vX.Y.Z` git tag and bumps it.
2. Builds a **debug** APK stamped with that version (debug-signed — fine for sideloading onto
   your own tablet, not for Play Store distribution; there's no release signing config set up).
3. Pushes the new tag and publishes a GitHub Release with the APK attached.

## What's actually been verified vs. not

This was built without a local Android SDK/JDK, so I bootstrapped one temporarily (JDK 17 +
Gradle 8.9 + SDK platform 35/build-tools) to actually compile it rather than just eyeballing the
Kotlin. `./gradlew assembleDebug` builds cleanly and the version-override path the CI workflow
relies on (`-PappVersionName=... -PappVersionCode=...`) was confirmed against the built APK's
manifest. What's **not** verified: the app hasn't run on an actual device/emulator, so the UI
flow and — most importantly — the S Pen hover/palm-rejection behavior in `PalmRejectingWebView`
still need a real Galaxy Tab to confirm. Hover range/reliability varies by device generation.
