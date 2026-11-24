# Manual test guide

The decision logic is covered by unit tests (`./gradlew :core:test :app:testDebugUnitTest`).
Screening on a real device needs a second phone/number. Use **short values** so you do not wait hours.

## Setup
1. Install the debug APK: `./gradlew :app:installDebug`.
2. Run the first-run wizard and set the app as *Caller ID & spam app*.
3. Add a second number of your own (a second SIM, another phone, a VoIP number) as an entry with:
   - Allowed calls **2**, within **2** minutes, block duration **2** minutes (0 h 2 min), action *Reject*.

## Scenarios
| # | Steps | Expected |
|---|---|---|
| 1 | Call twice within 2 minutes (hang up in between). | Both ring normally. |
| 2 | Call a third time within the same 2 minutes. | Call is rejected. Notification "Blocked until hh:mm" with countdown appears. Overview shows *Blocked until*. Log: "Block started" + "Call rejected". |
| 3 | Call again during the block. | Rejected again; block end time unchanged; no new notification. |
| 4 | Wait until the block ends, call again. | Rings normally. Log shows "Block expired". |
| 5 | Repeat 1–2, then tap *Lift block* in the notification. | Notification disappears, next call rings. Log: "Block lifted". |
| 6 | Set allowed calls to **0**. | Every call is rejected, no notification. |
| 7 | Set action *Silence*, trigger a block. | Phone does not ring, call is visible and can be answered. |
| 8 | Toggle *Monitoring enabled* off during a block. | Block ends immediately, notification disappears. |
| 9 | Trigger a block, reboot the phone. | After boot the notification is back (until the block ends). |
| 10 | Notification style *Show briefly* / *Off* in Settings. | Heads-up (silent) / no notification; block still works. |

## Open verification points (record the result in `docs/DEVELOPMENT.md`)
- **F-26**: Are calls from *saved contacts* delivered to the screening service without the contacts permission? Test scenario 1–3 with a number that is saved in the address book, once with and once without the permission.
- **F-62**: Does screening stay reliable without a battery-optimization exemption (e.g. after the phone idled overnight)?
