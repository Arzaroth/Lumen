# Distribution

## Build

Gradle wrapper, AGP 8.13, compileSdk/targetSdk 34, minSdk 26; JDK 17 and the SDK pinned by mise.
`val version` in `lumen/build.gradle.kts` sets versionName; versionCode is derived from it
(1.2.1 -> 10201). The APK is `lumen/build/outputs/apk/release/lumen-release.apk`. Lint runs with
warnings as errors (`lumen/lint.xml` lists the accepted exceptions).

## Signing

The release key is read from `~/.config/lumen/keystore.properties` locally (keystore and password
files beside it, never committed) or from `LUMEN_KEYSTORE*` environment variables in CI. Without a
key, release builds fall back to the debug key unless `LUMEN_REQUIRE_RELEASE_KEY` is set. A TV only
accepts an update signed with the same key as the installed Lumen.

## CI and releases

- `.github/workflows/ci.yml` on master and pull requests: build, lint, tests with the 98% coverage
  floor, shellcheck.
- `.github/workflows/release.yml` on a `vX.Y.Z` tag: checks the tag is on master, matches the
  version and has changelog notes (`scripts/changelog.sh`), runs the gate, builds the APK signed from
  the repo secrets and publishes the GitHub release with `lumen-X.Y.Z.apk`.
- Forgejo is the source of truth and push-mirrors to GitHub; the release and its APK are then copied
  to the Forgejo release. The project's `release` skill walks the whole pipeline.

## Updates on the TV

Lumen updates itself from GitHub's latest release; see [../features/updates.md](../features/updates.md).

## Sources

- `lumen/build.gradle.kts`, `lumen/lint.xml`, `.github/workflows/ci.yml`, `.github/workflows/release.yml`
- `scripts/changelog.sh`, `.claude/skills/release/SKILL.md`
