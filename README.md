# FloodCallBlocker

**Temporarily blocks callers who call too often – without blocking them forever.**

[![CI](https://github.com/wwwindisch/FloodCallBlocker/actions/workflows/ci.yml/badge.svg)](https://github.com/wwwindisch/FloodCallBlocker/actions/workflows/ci.yml)
[![License: GPLv3](https://img.shields.io/badge/license-GPLv3-blue.svg)](LICENSE)
[![Release](https://img.shields.io/github/v/release/wwwindisch/FloodCallBlocker)](https://github.com/wwwindisch/FloodCallBlocker/releases)

> **AI-only project:** This app was written entirely by an AI (Claude Code). There are no comprehensive human code reviews. Automated tests and linting run in CI, but please review the code yourself before relying on it, and report any problems as issues.

## The problem

Some people call again and again, minute after minute. You do not want to block them permanently because you do want to talk to them – just not on a minute-by-minute basis.

## How it works

You add a number or a contact and give it a rule. Example:

> Max may call **2 times within 5 minutes**. The third call is rejected and Max is blocked for **3 hours**. Afterwards he gets through normally again.

| Time | Calls in the last 5 min | Result |
|---|---|---|
| 10:00 | 1 | passes |
| 10:03 | 2 | passes |
| 10:09 | 1 (older ones expired) | passes |
| 10:10 | 2 | passes |
| 10:12 | 3 → over the limit | **rejected, blocked until 13:12** |
| 11:30 | – (blocked) | rejected, block stays until 13:12 |
| 13:15 | 1 | passes |

Calls during a block do not count and do not extend it. With **0 allowed calls** the entry is blocked permanently.

<!-- Screenshots are generated into fastlane/metadata/android/*/images/phoneScreenshots -->

## Features

- Per number or contact: allowed calls, time window, block duration, reject or silence
- Contacts: all numbers count together and follow your address book
- Ongoing notification with countdown and a *Lift block* button (discreet, brief popup, or off)
- Log of blocks and rejected calls (kept 30 days)
- JSON export / import
- Light / dark theme, Material You, English and German
- 100 % offline

## Installation

Download the APK from [GitHub Releases](https://github.com/wwwindisch/FloodCallBlocker/releases) and install it (Android 10 or newer). F-Droid is planned.

## First-time setup and permissions

| Permission / role | Why | Required |
|---|---|---|
| *Caller ID & spam app* role | Lets the app screen, reject and silence incoming calls | yes |
| Notifications (Android 13+) | Shows the "blocked until …" notification | recommended |
| Contacts | Pick contacts, keep their numbers up to date | optional |

Permissions are requested only when needed. A status card on the overview tells you if something is missing.

## Privacy

The app has **no internet permission**, no accounts, no analytics, and no Google Play Services. All data stays on your device. The app only looks at numbers you added to it.

## FAQ

**Why no call forwarding for individual callers?** Android's screening API only knows *allow*, *reject* and *silence*. Call forwarding is a network feature that applies to all callers.

**Why no iOS version?** iOS does not let apps observe incoming calls in the background or decide per call.

**What does the caller see?** With *Reject* the call is declined like a normal "decline". With *Silence* the phone just does not ring.

**Does it work for saved contacts?** Yes. On some Android versions screening of contacts' calls may need the contacts permission – see `docs/DEVELOPMENT.md` for the verification status.

---

## Kurzfassung (Deutsch)

FloodCallBlocker sperrt eine Nummer oder einen Kontakt **automatisch und vorübergehend**, wenn zu viele Anrufe in kurzer Zeit eingehen – z. B. „2 Anrufe in 5 Minuten, beim dritten wird abgelehnt und für 3 Stunden gesperrt“. Danach kommt die Person wieder normal durch. Die App läuft vollständig offline (keine Internet-Berechtigung), speichert alles nur auf dem Gerät und ist Freie Software (GPLv3). Einrichtung: APK installieren, die App als „Anrufer-ID- & Spam-App“ festlegen, Nummer oder Kontakt hinzufügen.

## Kurzer Hinweis zur Entstehung

Dies ist ein „AI-only“-Projekt: Der gesamte Code wurde von einer KI (Claude Code) geschrieben, es gibt keine umfassenden menschlichen Code-Reviews.

## Contributing

Issues and pull requests are welcome. See [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) for build and test instructions. Please use only fictitious phone numbers in tests, issues and screenshots.

## License

[GPLv3](LICENSE)
