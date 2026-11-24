# Development

## Requirements
- JDK 21
- Android SDK (platform 36, build-tools 35+), `local.properties` with `sdk.dir=…`

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

## Not yet implemented
- Roborazzi screenshot tests (SPEC §10) and the README/fastlane screenshots and GIF.
