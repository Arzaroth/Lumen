# Glossary

One-stop definitions for the terms used across the brain and the code. Each term is the one to use
in code, docs and commits; the **Avoid** list names the synonyms that blur it.

## Home

- **Home** - the launcher Android shows on the Home button; Lumen replaces the stock one. See
  [architecture/tv-scripts.md](architecture/tv-scripts.md). Avoid: desktop, main screen.
- **Dock** - the glass band of the first five apps at the bottom of Home. See
  [features/home.md](features/home.md). Avoid: shelf, row 0.
- **Grid** - every app below the dock, reached with Down; the content scrolls up like tvOS.
  Avoid: drawer, all-apps screen.
- **Tile** - one app's banner in the dock or grid. Avoid: card, icon.
- **Pill** - one of the frosted buttons in the top bar: Inputs, network, Settings.
- **Stage** - the empty middle of Home where the focused app's logo plays. See
  [architecture/stage.md](architecture/stage.md).

## The logo

- **Mosaic** - an app banner turned into cells (positions, palette, sparks, theme), built off the
  UI thread and cached per banner. Avoid: grid (taken), bitmap.
- **Voxel** - one lit cube of the logo, drawn as up to three faces. Avoid: pixel, particle.
- **Enter / hold / exit** - the three phases of a logo cycle, each picked at random and never the
  same kind twice in a row.
- **Ghost** - a voxel of the previous logo handed over to drift and fade while the next one enters.
- **Spark** - a short-lived streak or dot from an effect (explosions, sweeps, fireworks).
- **Ray** - a spark flagged `RAY`: it barely brakes and dims as it slows, so it never hangs.
- **Floor** - the line every logo stands on, with its reflection below. Avoid: underline, horizon
  (taken by the sky).
- **Art** - the bounds of the logo's actual cells inside its box; animations play around it.

## The sky

- **Cosmos** - the night sky behind the logo. See [architecture/cosmos.md](architecture/cosmos.md).
- **Field** - the baked star field and Milky Way, twice the screen wide.
- **Live stars** - the brightest stars, drawn every frame so they can scintillate.
- **Nebula** - an app's own gas cloud, generated per theme; it also carries the airglow.
- **Airglow** - the faint glow of the air above the horizon, tinted per app.
- **Theme** - one app's sky: radiant, meteor rate and tint, airglow, nebula.
- **Radiant** - the point meteors stream away from.
- **Parallax** - the sky's layers sliding at different depths with the focus.
- **Sky rotation** - the sky turning at the Earth's real rate (a setting, off by default).

## Updates

- **Ascent** - the update screen: the version gathering out of stardust.
- **Installer of record** - the app Android remembers as having installed a package; Lumen must be
  its own for prompt-free updates.
- **Relaunch mark** - `relaunch_at` in the preferences, written before Lumen replaces itself.
