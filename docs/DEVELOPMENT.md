# Development

## Requirements
- JDK 21
- Android SDK (platform 37.0, build-tools 35+), `local.properties` with `sdk.dir=…`

## Modules
- `:core` – pure Kotlin/JVM: rule engine (`CallEngine`), models, phone-number normalisation (libphonenumber). No Android dependencies; fully unit-tested.
- `:app` – Android app: Compose UI, `CallScreeningService`, Room, notifications, contacts, backup. Manual dependency injection (`AppContainer` in `FloodApp.kt`) instead of Hilt: the app is small enough.

## Architecture in short
Incoming call → `FloodCallScreeningService` → `Screener` (normalise number → `EntryCache` lookup) → `Repository.screen()` (serialised; loads state, calls `CallEngine.evaluate`, persists block/call times/log in one Room transaction) → response (reject/silence/allow). Any error or timeout (3.5 s) lets the call through (fail-open). Notifications are posted after the transaction by `BlockNotifier`.

Block state is always decided by the stored timestamps, never by the notification (F-33).

## Commands
```
./gradlew :core:test :app:testDebugUnitTest   # unit + Room/repository tests (Robolectric)
./gradlew ktlintCheck                         # style
./gradlew :app:lintDebug                      # Android Lint (warnings are errors)
./gradlew :app:assembleDebug                  # debug APK
./gradlew :app:installDebug                   # install on a connected device
```

## Release
1. Update `versionName` / `versionCode` in `app/build.gradle.kts`, `CHANGELOG.md`, and add `fastlane/metadata/android/*/changelogs/<versionCode>.txt`.
2. Tag: `git tag v1.0.0 && git push --tags`.
3. The *Release* workflow builds a signed APK and publishes it with the changelog section as notes.

Required repository secrets: `KEYSTORE_BASE64` (base64 of the keystore), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. The keystore is never committed.

## Open verification points (to be tested on a Pixel 6)
| ID | Question | Status |
|---|---|---|
| F-26 | Are calls from saved contacts delivered to the screening service without `READ_CONTACTS`? | **not yet verified** – see `docs/TESTING.md` |
| F-62 | Is a battery-optimisation exemption needed? | **not yet verified** – the app only offers it optionally in Settings |

## Open to-dos
| Topic | Details |
|---|---|
| AGP / compile SDK 37 | `compileSdk`/`targetSdk` are 37, but AGP 8.13.0 is only tested up to 36.1. The warning is silenced with `android.suppressUnsupportedCompileSdk=37.0` in `gradle.properties`. When an AGP version with official SDK 37 support is out: update AGP (check Kotlin/KSP compatibility) and remove that line. |
| Robolectric | Tests are pinned to SDK 36 in `app/src/test/resources/robolectric.properties` because Robolectric does not support 37 yet. Remove the pin once it does. |
| F-26 / F-62 | Device verification pending, see table above and `docs/TESTING.md`. |
| Manual device test | The app has only been compiled and installed on a Pixel 6 Pro, not yet tested with real calls (`docs/TESTING.md`). |
| Screenshot tests | Roborazzi tests (SPEC section 10) are missing. Screens must first be split into stateless composables that take plain data, so they can be rendered with fictitious sample data. Also needed: screenshots and a short GIF for the README and `fastlane/metadata/android/*/images/phoneScreenshots`. |
| Release signing | Create a keystore and add the secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` in the GitHub repository before tagging `v1.0.0`. |
| CI verification | After the lint fixes (`OldTargetApi`, Actions v5) confirm that the CI workflow is green on GitHub. |
| Extra permissions | WorkManager adds `WAKE_LOCK`, `ACCESS_NETWORK_STATE` and `FOREGROUND_SERVICE`. There is no INTERNET permission. Consider removing the unneeded ones via `tools:node="remove"` if F-Droid review asks. |
| Reset counters on answered call | Idea, deliberately not implemented: reset an entry's counters when a call that passed is answered. Would need `READ_PHONE_STATE` and a phone-state receiver; the caller's number is not available there, so it would rely on the last allowed call. Rare edge case. |
