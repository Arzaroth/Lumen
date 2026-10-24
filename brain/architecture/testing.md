# Testing

Unit tests in `lumen/test/` run on the JVM with Robolectric 4.14 and **native graphics**
(`robolectric.graphicsMode=NATIVE`): drawing goes through real Skia, so drawing code is exercised,
and tests can render frames to PNG to look at them.

## Seams and helpers

- `Wall.clock`: the wall clock; Robolectric does not fake `System.currentTimeMillis`.
- `Robo`: the context, real canvases, `idle(ms)` to move the main looper's clock, `layout`, `frames`,
  and reflection helpers (`get`, `set`, `call`).
- `Releases`: a local HTTP server standing in for GitHub's latest release and its APK.
- `Updater.releases` and `Updater.hold`: the release URL and the pause before installing.
- Views that post work need a window: attach them to `Robolectric.buildActivity(...)`.

## Coverage

`scripts/coverage.sh` runs the tests with JaCoCo; `--check` fails below `LUMEN_COVERAGE_FLOOR` (98).
CI runs it. Line coverage sits near 99.8%; the few misses are random branches in `Stage`.

## On the TV

Robolectric cannot measure performance: frame costs are measured on the TV. See
[gpu-budget.md](gpu-budget.md).

## Sources

- `lumen/test/com/arzaroth/lumen/*.java`, `lumen/build.gradle.kts`, `scripts/coverage.sh`
