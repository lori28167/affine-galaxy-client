# AFFiNE Tablet Client

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

1. Open this folder in Android Studio (Koala/2024.1+). Let it sync — if it asks to create/upgrade
   the Gradle wrapper, accept; the wrapper jar isn't checked in.
2. Run on a Galaxy Tab (S Pen models: Tab S6 and up) via USB debugging, or build an APK via
   *Build > Generate Signed/Unsigned APK*.
3. On first launch, enter your self-hosted AFFiNE server URL (e.g. `https://affine.example.com`,
   or `http://10.x.x.x:3010` if it's only reachable over your WireGuard tunnel/LAN). Cleartext
   http is allowed for exactly this reason — see `app/src/main/res/xml/network_security_config.xml`.
4. Sign in as normal through AFFiNE's own login page inside the app.

The top-right menu lets you reload, toggle a desktop-layout user agent, switch servers, or sign
out and wipe local WebView storage/cookies.

## What I could not verify here

This was written and reasoned through without a local Android SDK/Gradle/JDK available in this
environment, so it has **not been compiled or run**. Before you rely on it:

- Open it in Android Studio and let Gradle sync — that will catch any dependency-version
  mismatches (AGP 8.7.0 / Kotlin 2.0.20 / Compose BOM 2024.09.03 were current at time of writing
  but may have moved on).
- Actually test the palm-rejection behavior on your specific Tab model with your S Pen — the
  hover-based pre-activation is the part most worth confirming, since hover range/reliability
  varies by device generation.
