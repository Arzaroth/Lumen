# Overview

Lumen is one Android app (`lumen/`, package `com.arzaroth.lumen`) and a set of shell scripts
(`scripts/`) that prepare a TV for it.

## The app

- `LumenActivity` is the Home activity (singleTask, `HOME` category). `buildViews` stacks, in a
  `FrameLayout`: the `Stage` (logo and sky, full screen), the scrolling content (greeting,
  `ClockView`, `DateView`, the pills, the app title, the dock band, the `Highlight`, the tiles, the
  "All apps" header, the `Chevron`), the `Menu` dropdown, the rearrange hint, and the `Ascent`
  update screen on top.
- `Apps` lists launchable apps (`CATEGORY_LEANBACK_LAUNCHER`), renders their banners and picks an
  accent colour; `AppEntry` holds one. `Tile` shows a banner with its focus lift.
- `Stage` turns the focused app's banner into a voxel logo and runs its animations; it owns a
  `Cosmos`, the sky. See [stage.md](stage.md) and [cosmos.md](cosmos.md).
- `Menu` is the dropdown; `LumenSettings` and `Inputs` build its pages. See [menus.md](menus.md).
- `Updater` checks GitHub and installs updates; `Ascent` is its screen; `Relaunch` brings Home back
  after. See [../features/updates.md](../features/updates.md).
- `LumenKeys` is an accessibility service for the remote's app buttons; `LumenDream` is the
  screensaver. `Wall` is the wall clock seam, `Glyphs` samples text into points, `Fonts` loads the
  Outfit variable font, `Prefs` names the preference keys.

## A key press

`LumenActivity.dispatchKeyEvent` handles everything. While the update screen is up it swallows
keys (volume passes, Back dismisses it once Android has the install). Otherwise D-pad goes to
`navigate` (menu first if open), OK to `activate` (long press starts rearranging), Back to `back`,
Menu to rearranging. Focus moves are applied by `applyFocus`, which also updates the highlight, the
title, the clock and date accent, the stage's scene and the parallax.

## State

All in the `lumen` shared preferences: the settings (keys in `Prefs`), `focus` (last focused app),
`order` (tile order), `doots_off`, the intro's boot id, and `relaunch_at` during an update.

## Sources

- `lumen/src/com/arzaroth/lumen/LumenActivity.java`, `Apps.java`, `AppEntry.java`, `Tile.java`, `Prefs.java`
- `lumen/AndroidManifest.xml`
