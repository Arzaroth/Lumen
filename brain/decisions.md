# Decisions

The non-obvious design decisions and the "why" behind them: what you cannot recover by reading the
code. Each entry links the doc that implements it. Decisions with real alternatives get a number;
smaller ones are bullets.

## 0001 Why the name

**Decision.** The project started as `tv-lab` (debloat experiments) and became **Lumen** once the
launcher was the point: a home screen made of light (stars, glowing voxels, stardust digits). App id
`com.arzaroth.lumen`; the first builds used `dev.arzaroth.lumen`, which `scripts/install.sh` retires.

## 0002 Plain Java, no dependencies

**Decision.** The app is plain Java on the platform APIs: no Kotlin, no AndroidX, no libraries.

**Why.** A Home app on a 1.7 GB TV must start fast and stay small; every animation is hand-drawn on a
Canvas anyway. Tests use Robolectric, Mockito and JUnit, none of which ship in the APK.

## 0003 Everything that moves is a triangle mesh

**Decision.** Voxels, sparks, stars and dust are batched into `drawVertices` meshes.

**Why.** Thick `drawPoints`/`drawLines` become one path each on the GPU renderer: a few thousand
cells drew at about 9 fps. One mesh per layer costs one draw call. See
[architecture/stage.md](architecture/stage.md).

## 0004 Bake what does not move

**Decision.** The star field, the noise of each nebula and the airglow are baked into bitmaps once;
per frame Lumen only blits and draws meshes.

**Why.** This TV's PowerVR GPU pays about 5 ms for each blended full-screen layer. Per-pixel noise in
shaders cost 10-25 ms a frame; the airglow drawn live over half the screen cost 5.6 ms of a 16.7 ms
frame until it was baked into the nebula (14.3 ms -> 8.6 ms GPU per frame). Rejected: drawing the
airglow from a stretched strip bitmap (still 2.6 ms: the cost is the blended area, not the source).
See [architecture/gpu-budget.md](architecture/gpu-budget.md).

## 0005 The sky on whole pixels

**Decision.** The field is blitted at whole-pixel offsets, for sky rotation and parallax alike.

**Why.** Its faint stars are single pixels. At a half-pixel offset bilinear filtering spreads each
over two pixels at half brightness: the whole field dimmed and sharpened every few seconds. See
[architecture/cosmos.md](architecture/cosmos.md).

## 0006 Self-update from GitHub, prompt-free

**Decision.** Lumen checks GitHub's latest release, streams the APK into a `PackageInstaller`
session and asks for no user action; it waits 4 s before committing so the update screen plays out,
and comes back to the front afterwards.

**Why.** Sideloading over adb for every release is impractical on a TV. Android 12+ lets an app
update itself silently when it is its own installer of record, which in-app updates (and
`pm install -i`) make it. Rejected: a manifest-only receiver that relaunches Home (background
activity starts are blocked; the bound accessibility service is the reliable path, the receiver a
faster first try). See [features/updates.md](features/updates.md).

## 0007 Disable, never uninstall

**Decision.** The debloat disables packages per user and keeps a backup; everything is undone by
`scripts/restore.sh`. The stock launcher is disabled only once Lumen has taken Home.

**Why.** Followed from CleanYourTV's rules: a disabled package comes back with one command, an
uninstalled system app does not. See [architecture/tv-scripts.md](architecture/tv-scripts.md).

## 0008 The real sky, framed like the imagined one

**Decision.** The real sky is baked into the same structure as the imagined one (a field and live
stars), facing south over 120 degrees, from the Yale Bright Star Catalogue plus faint stars seeded
per patch of sky, with planets and the Moon from simplified orbital elements.

**Why.** Reusing the bake keeps every effect (parallax, weather, meteors, nebula) and the GPU cost
unchanged. The catalogue is public domain and 72 KB packed; a degree of accuracy is plenty at 16
pixels a degree. Faint stars and a noise-textured Milky Way keep the real sky as rich as the
imagined one; seeding them per patch keeps them fixed on the sky. Rejected: a full-sky projection
redrawn every frame (per-frame cost), and rebaking every minute (the sky moves 8 px in two minutes,
which a cross-fade hides, for half the CPU).

## Smaller decisions

- Logos stand on a common floor (bottom-aligned in their box) so the line under them stays put;
  animations centre on the logo's own cells (`art`), not the box.
- Explosion rays barely brake and fade by age: rays that decelerated to a stop read as a bug. For
  the same reason entrances never overshoot (`settle`, not a back-out curve), converge never flies
  cells back from where an exit left them, and hyperspace's star streaks fly away instead of
  retracting.
- The update screen is drawn in Lumen's own language (stardust, thin Outfit, the sky behind) after a
  fiery "forge" version clashed with the rest.
- A scattered logo leaves at most 1200 ghosts: they draw on top of the next logo and doubled the mesh.
