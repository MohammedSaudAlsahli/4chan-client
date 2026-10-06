# Development build validation

## Version 0.4.0 — Thread hierarchy, native links, and sorting

Validated October 6, 2026. All 33 JVM tests, debug APK build, APK v2 signature verification, and Android lint pass. On the Android 15 AOSP ATD emulator, the offline nested-thread UI test, independent image/discussion tap test, and native cross-board link tap test pass. The thread screenshot was reviewed with emulator rendering enabled: the opening post, parent labels, reply lines, and two-level indentation remain readable on a phone screen. Parser tests cover cross-board/board-only links, external-host rejection, last-reply extraction, and Hot/New/Latest reply ordering. The quoted-post card and cross-board navigation use the same API and proxy client as normal thread browsing, but a live cross-board link was not exercised on a physical device. The debug APK is a test build; release signing and real-device testing remain before a GitHub Release.

APK: `Boardwalk-0.4.0-debug.apk`

SHA-256: `54cbb0993a733af1867c45c1873d13f181eaf0c95c6a06bfaf07dbb1e98a2eb8`

The Gradle `connectedDebugAndroidTest --offline` task could not resolve an uncached Android test-platform host artifact. The compiled app and test APKs were installed with `adb`, and the three relevant instrumentation tests were run directly with `am instrument`.

## Version 0.3.0 — Nested replies and local recovery

Validated October 5, 2026. Debug build and lint pass; all 29 JVM tests pass (including 5 reply-tree and 6 backup tests). Five Android emulator tests pass: independent image/discussion taps, live proxy/feed/media regression, exported-state restoration into fresh storage, interrupted-restore journal recovery, and offline/nested UI behavior. A final focused recovery rerun covers AtomicFile backup revision reads.

The default favorites test verifies news and biz, and verifies that an explicitly empty favorites list round-trips. Backup tests reject unsupported versions, credential fields, path traversal, unbookmarked snapshots, and invalid proxy configuration. The native offline test uses a deterministic discussion fixture with an unavailable proxy; its screenshots are test evidence, not live 4chan content. Existing live API/media tests remain separate. Independent source/native review passed after hardening local-only picker contracts, revision-preserving backup writes, serialized archives, and journaled restore.

**Not yet verified:** this AOSP ATD image has no DocumentsUI picker. Real folder selection, persisted SAF grants, backup rotation through an actual local document provider, picker-based import after uninstall, and attachment export must be tested on a physical phone or full Android image before release. No claim of end-to-end uninstall/reinstall recovery is made yet. Cloud backup stays disabled. Physical-device performance, TalkBack, actual video/GIF playback, and Saudi-provider connectivity retain their earlier limitations.

APK: `Boardwalk-0.3.0-debug.apk`

SHA-256: `02bbe9ce70c576055d2ea6f2028934829bfab12c7e104c40244abc8a244c0a40`

## Version 0.2.0 — Home timeline

Validated October 5, 2026. Debug APK, instrumentation APK, and lint pass. All 18 JVM tests pass (7 Home, 8 network, 3 parser). Both emulator tests pass against the final build. The live flow additionally loads Home through the authenticated proxy, finds a second board, checks image-close scroll preservation, changes sorting, opens a thread from its board label, and returns to Home. Unit tests cover cross-board ID collisions, timestamp sorting, visibility/favorites, expired-thread replacement, partial failures retaining previous content, and cancellation.

Native Home captures at 1.0x and 1.3x font scale are in screenshots/. Independent finish review found no material defects. Existing physical-device/provider/video coverage limitations below remain.

APK: `Boardwalk-0.2.0-debug.apk`

SHA-256: `c306974fc545cdb2abb56dab12080fa9abb8353d1300d35a33e1a4c550f34962`

## Version 0.1.0 baseline

Validated on October 5, 2026. Version 0.1.0, Android 8.0+ (API 26), target API 35. This is a debug-signed development build.

## Results

- Debug APK and instrumentation APK compiled successfully.
- 11 JVM tests passed: API parsing, proxy validation/routing/authentication, HTTPS CONNECT, no direct fallback, caching, and conditional responses.
- 2 instrumentation tests passed on an Android 15 AOSP ATD arm64 emulator: independent thumbnail/discussion targets, and an opt-in live proxy/media flow.
- The live flow configured an authenticated loopback HTTP proxy through the app, tested and saved it, loaded real API and image content, checked encrypted credential storage, exercised double-tap/pinch/swipe, and verified the exact scrolled catalog position after closing the viewer.
- Final Android lint completed with no errors. Dependency-version and Kotlin-style suggestions remain.
- APK signature verified using Android apksigner (v2).
- Native visual review passed for boards, catalog, reader, media viewer, zoom, proxy settings, dark mode, and catalog at 1.3x font scale. Captures are in `screenshots/`.

## Reproduction and scope

Run `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`. The regular connected test command in README runs the feed test; the live test skips unless instrumentation argument `proxyPort` is provided. It expects an authenticated HTTP CONNECT proxy reachable from the emulator at `10.0.2.2`, using test credentials `reader` / `test-only`. These are test-only values and are not bundled in the application configuration. Its current live fixture is the `/po/` Pizza Cube thread, which can change or expire.

The live test used a local test proxy, not a commercial provider or a Saudi network. No proxy service is included. Physical-device performance, TalkBack, tablet layouts, actual video/GIF playback, and system-bar appearance remain unverified. ATD screenshots omit system bars. Images and API transport were verified; video/GIF support is implemented but not demonstrated by the live test.

## Delivered APK

Filename: `Boardwalk-0.1.0-debug.apk`

SHA-256: `5c22b2e4f2952016be33cc61863a46cf5d1c693cbb34eb92d783188e4200d9c3`
