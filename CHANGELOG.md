# Changelog

All notable changes to this project are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and the project adheres to [Semantic Versioning](https://semver.org/).

## [Unreleased]

## [1.0.0] - 2026-11-24

### Added
- Monitor numbers or contacts; block them temporarily when they call too often
  (allowed calls, sliding window, block duration, reject or silence).
- "Always blocked" mode (allowed calls = 0).
- Contact entries: all numbers of a contact count together and follow the address book.
- Ongoing notification with countdown and "Lift block" action, restored after reboot.
- Log of blocks and rejected calls (30 days), filterable per entry.
- JSON export/import of entries.
- First-run wizard, status card for missing prerequisites.
- Light/dark theme, Material You, German and English.
