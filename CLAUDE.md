# Lumen

An Apple TV 4K style home screen for Android TV / Google TV, and the scripts that debloat a TV and
swap Lumen in as Home, everything reversible. Start with `brain/BRAIN.md` to find how anything works.

## The brain

`brain/` is the committed knowledge base: architecture, features, decisions, glossary. It exists so
Lumen can be understood without reading the source. Navigate it through `brain/BRAIN.md` and the two
`index.md` files rather than grepping the tree; the `brain` skill has the full routine.

- Keep it true to the code. A change that makes a brain doc wrong fixes that doc in the same branch.
- The code wins: if the brain disagrees with reality, correct the brain.

## The TV is somebody's TV

- Never disable TV or input apps, HDMI and tuners, the input menu, the remote and Bluetooth pairing,
  the keyboard, Play Store and Services, or core Android. Never launch factory or test packages.
- Disable, never uninstall; back up first (`scripts/backup.sh`); everything must come back with
  `scripts/restore.sh`. The stock launcher is disabled only once Lumen has taken Home.
- Never disable a Home rival that owns the saved Wi-Fi networks: it deletes them and drops adb.
- Ask before rebooting the TV, taking it over to measure, or anything that interrupts what is on screen.

## The GPU is the budget

The TV's GPU has 16.7 ms a frame and the stage already uses about half
(`brain/architecture/gpu-budget.md`). No new blended full-screen layer: bake static content into an
existing one. Blit the star field on whole pixels. Measure frame costs on the TV after a reboot,
never trust Robolectric for performance, and never commit a diagnostic build.

## Updates replace Lumen on people's TVs

- Every release APK is signed with the release key in `~/.config/lumen/` (CI: repository secrets).
  Never commit a key or a password; a TV refuses an update signed differently.
- Lumen must stay its own installer of record (`pm install -i` in `install.sh`, in-app updates),
  or updates need a confirmation again.
- The relaunch mark is written with `commit()` before the install: Android kills Lumen right after.

## Conventions

- `CHANGELOG.md` `[Unreleased]` gets an entry with every user-facing change; it becomes the release
  notes.
- The gate: `mise exec -- ./gradlew --no-daemon :lumen:assembleRelease :lumen:lintRelease`,
  `mise exec -- scripts/coverage.sh --check` (98% line floor) and `mise exec -- shellcheck scripts/*.sh`.
  CI runs the same.
- Tests read the time through `Wall`, GitHub through `Updater.releases`; see
  `brain/architecture/testing.md`.
- Releases go through the `release` skill; the tag push builds and publishes them.
