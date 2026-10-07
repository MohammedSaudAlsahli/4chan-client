# Contributing to Boardwalk

Boardwalk is an unofficial Android reader for 4chan. Contributions are welcome under GPL-3.0-only. Please open an issue before a large change so the intended behavior can be discussed.

Build with JDK 17 and Android SDK platform/build-tools 35. Run `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` before proposing a change. Native UI changes should also be checked on an emulator or device at normal and enlarged font size.

Keep all network requests on the shared client so a configured proxy applies to API and media traffic. Respect 4chan's request budget, avoid background polling, and never include real proxy credentials, cookies, or personal data in reports. User-saved discussions stay on device unless the user exports them.
