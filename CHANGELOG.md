# Changelog

All notable changes to Lumen are recorded here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- A sky that lives: stars that scintillate, a nebula generated for each app that drifts and breathes, the odd nova and satellite train.
- The sky can turn at the Earth's rate, smoothly, behind the Sky rotation setting (off by default).
- The date comes alive like the clock, more quietly.
- Advanced settings shows Lumen's version, checks GitHub for a newer release and installs it in place.
- In menus, Up and Down roll over from the last row to the first, Right acts as OK and Left as Back.
- Release APKs are signed with Lumen's own release key and published by CI on each tag.

### Changed

- The app id is now `com.arzaroth.lumen`; `scripts/install.sh` retires the old `dev.arzaroth.lumen`.
- The build moved to Gradle, with lint, Robolectric unit tests and a 98% line-coverage floor in CI.
- Lumen is licensed MIT OR WTFPL.

### Fixed

- The sky no longer jumps when it turns.

## [1.0.0] - 2026-10-23

### Added

- The focused app's logo rebuilt as lit 3D voxels under a night sky (Milky Way, twinkling stars, a meteor shower from a per-app radiant, a passing satellite), cycling 7 entrances, 6 holds and 8 exits at random.
- A living clock: digits morph through stardust each minute, with idle moments in between.
- Glass dock, all-apps grid, rearranging, Inputs dropdown (Live TV and HDMI), remote buttons wired on Home.
- Boot intro on the first appearance after each boot.
- Settings: system settings, or Lumen's own (backdrop, animation pace, meteors, 3D or flat logo, greeting, clock format, remote buttons, boot intro, tile order).
- Lumen Clock screensaver.
- `scripts/install.sh` builds and installs Lumen and makes it Home behind a guard; `scripts/restore.sh` undoes everything.
- Per-TV debloat lists, backups, and measurement scripts.

[Unreleased]: https://github.com/Arzaroth/Lumen/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/Arzaroth/Lumen/releases/tag/v1.0.0
