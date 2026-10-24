# Roadmap

Features Lumen may grow next. Order is not priority. Small fixes and chores go in
[TODO.md](TODO.md). When a feature ships, its entry leaves this file and a doc for it lands in
`brain/features/`.

## M0 - Foundation

- [x] Debloat scripts with backup and restore, the guarded Home swap.
- [x] Lumen: dock, grid, voxel logo, living sky, clock and date, menus, inputs, remote keys,
      screensaver, boot intro.
- [x] Gradle build, Robolectric tests, 98% coverage floor, CI, signed releases, in-app updates.

## Lighter frames

The dock, tiles and clock now cost more GPU than the whole stage (5.2 ms of about 8.6 ms). Find what
in them is expensive (glass band, highlight, shadows, text glow) and bake or simplify it, measured
on the TV.

## Top Shelf

Apple TV shows content from the focused app above the dock. Android TV apps publish channels and
programs through `TvContract` (watch next, recommendations); reading them needs
`READ_TV_LISTINGS` / the launcher's access, which a sideloaded Home may not get. Decide after a spike.

## Voice and search

The remote's microphone goes to the Google Assistant through the stock launcher. Find whether a
sideloaded Home can hand it a search intent.

## Later

- More holds and exits in the logo's repertoire.
- A second TV's debloat list.

## Not planned

- Replacing the boot animation: the system partitions are read-only on a locked Google TV.
- Unlocking the bootloader: it wipes the TV.
