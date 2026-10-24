# Changelog

All notable changes to Lumen are recorded here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.1.5] - 2026-10-24

### Changed

- Updates install without a confirmation once Lumen installed itself (Android 12 and later), and Lumen comes back to the front when the update is done instead of the last app.

## [1.1.4] - 2026-10-24

### Fixed

- No more hitch as the next logo arrives after a big explosion: the scattered logo leaves fewer fading ghosts behind, and the explosion's flash and colour fringe cost less.

## [1.1.3] - 2026-10-24

### Changed

- The update is written in the sky: the new version gathers out of stardust as it downloads and lights up whole when Android installs it, in place of the forge.

### Fixed

- The logo's animations play around the logo itself again, since logos now stand on a common floor.

## [1.1.2] - 2026-10-24

### Changed

- A stronger parallax.

### Fixed

- Every logo stands on the same floor, so the line under it no longer jumps between apps.

## [1.1.1] - 2026-10-24

### Added

- The update burns in a forge: the new version stands in iron and fills with fire as it downloads, blazes white and throws out a pair of burning wings when Android installs it, and cools to ash if it fails.
- Parallax: the sky slides gently as the focus moves along the apps, the Milky Way least, the nebula more, the bright stars and meteors most. On by default, under Settings, Lumen settings, Sky.

### Changed

- Lumen settings are grouped into Sky, Home screen, Remote, Boot intro and Advanced settings.
- Advanced settings checks for an update each time it opens.

### Fixed

- With Sky rotation on, the stars no longer dim every few seconds and the sky no longer slows the frame.

## [1.1.0] - 2026-10-24

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

[Unreleased]: https://github.com/Arzaroth/Lumen/compare/v1.1.5...HEAD
[1.1.5]: https://github.com/Arzaroth/Lumen/compare/v1.1.4...v1.1.5
[1.1.4]: https://github.com/Arzaroth/Lumen/compare/v1.1.3...v1.1.4
[1.1.3]: https://github.com/Arzaroth/Lumen/compare/v1.1.2...v1.1.3
[1.1.2]: https://github.com/Arzaroth/Lumen/compare/v1.1.1...v1.1.2
[1.1.1]: https://github.com/Arzaroth/Lumen/compare/v1.1.0...v1.1.1
[1.1.0]: https://github.com/Arzaroth/Lumen/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/Arzaroth/Lumen/releases/tag/v1.0.0
