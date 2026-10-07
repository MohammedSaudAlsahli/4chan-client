# Releasing Boardwalk

## Source release and F-Droid

1. Run unit tests, lint, debug build, and Android UI tests on an emulator or device. Check Home, board categories, thread nesting, image gestures, backup import/export, and proxy behavior on a real device before treating the release as fully validated.
2. Confirm the version name/code in `app/build.gradle.kts`, the matching `fastlane/metadata/android/en-US/changelogs/<code>.txt`, and `.fdroid.yml` match the intended source tag.
3. Commit the source and create a signed or annotated `v<version>` Git tag on the tested commit. Push the commit and tag.
4. Submit the app to F-Droid by opening a merge request against `fdroid/fdroiddata` with metadata based on `.fdroid.yml`, or an official Request For Packaging issue if a maintainer must prepare the recipe. The upstream license, icon, screenshots, and descriptions are already in this repository. F-Droid's own build and review must pass before any listing exists.
5. Keep `CurrentVersionCode` and the source tag current for later releases. F-Droid builds and signs its distributed APK; installing a debug build first generally requires uninstalling it before installing the F-Droid build.

The app depends on 4chan for content, so the F-Droid metadata declares `NonFreeNet`. Boardwalk's Popular Threads section ranks a sample of public catalogs locally; it does not claim to reproduce the website's private homepage ranking.

## GitHub and other open-source stores

GitHub can host a source release from the same tag. A public installable APK for IzzyOnDroid needs a **release-signed** APK attached to a tagged GitHub Release. Do not publish a debug APK as the release APK. Keep the release signing key and passwords in durable private storage: future upgrades must use the same key. Never commit a keystore or passwords. A GitHub APK and the F-Droid APK may have different signatures, so users may need to uninstall when changing distribution sources. Once a signing key is securely retained, build and test the release APK, attach it to GitHub, and request IzzyOnDroid inclusion using its official process.

References: [F-Droid submission guide](https://fdroid.gitlab.io/jekyll-fdroid/docs/Submitting_to_F-Droid_Quick_Start_Guide/), [F-Droid inclusion policy](https://fdroid.gitlab.io/jekyll-fdroid/en/docs/Inclusion_Policy/), [IzzyOnDroid inclusion policy](https://izzyondroid.org/docs/general/AppInclusionPolicy/).
