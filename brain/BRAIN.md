# BRAIN - Lumen knowledge base

The map of the codebase, written so a developer or AI can understand Lumen **without reading the
source**. Start here, follow the links, stop when you have the answer. Every leaf doc cites the real
source files if you need to go deeper.

> Product: **Lumen** - an Apple TV 4K style home screen for Android TV / Google TV, plus the scripts
> that debloat a TV and swap Lumen in as Home, everything reversible. Plain Java, no dependencies,
> built with Gradle; run on a Thomson 4K Google TV (Android 14). The current version is
> `val version` in `lumen/build.gradle.kts`.

## How to use this

1. Know the topic? Jump straight to its file via the indexes below.
2. Have a question, not a topic? Scan **Find by question** first.
3. Need a term defined? See [glossary.md](glossary.md).
4. Want the "why" behind a design? See [decisions.md](decisions.md).
5. About to change something? The rules a change can break are in the repo's `CLAUDE.md`.

The brain is a tree: this file -> two folder indexes
([architecture/index.md](architecture/index.md), [features/index.md](features/index.md)) -> leaf
docs. Don't grep the whole tree; use the indexes.

## Top-level docs

| Doc | What's here |
|---|---|
| [glossary.md](glossary.md) | Domain and technical terms, with the synonyms to avoid. |
| [decisions.md](decisions.md) | The non-obvious decisions and their rationale, including why the name. |

## Architecture (the how) - [architecture/index.md](architecture/index.md)

| Doc | What's here |
|---|---|
| [overview.md](architecture/overview.md) | The app's pieces, the view tree, where each feature lives. Read first. |
| [stage.md](architecture/stage.md) | The voxel logo: mosaics, the enter/hold/exit cycle, sparks, ghosts, meshes. |
| [cosmos.md](architecture/cosmos.md) | The sky: baked field, live stars, per-app nebula and airglow, meteors, parallax, rotation. |
| [gpu-budget.md](architecture/gpu-budget.md) | What a frame costs on the TV, how to measure it, the rules that keep it smooth. |
| [menus.md](architecture/menus.md) | The glass dropdown, its pages and rows, navigation. |
| [tv-scripts.md](architecture/tv-scripts.md) | Backup, debloat, install, restore: the safety rules and the Home guard. |
| [distribution.md](architecture/distribution.md) | Gradle build, release signing, CI, releases, the in-app updater. |
| [testing.md](architecture/testing.md) | Robolectric with real Skia, the test seams, coverage floor, measuring on the TV. |

## Features (the what) - [features/index.md](features/index.md)

| Doc | What's here |
|---|---|
| [home.md](features/home.md) | Dock, grid, focus rules, rearranging, the chevron. |
| [logo.md](features/logo.md) | The focused app's logo in voxels and its animations. |
| [sky.md](features/sky.md) | Backdrops, meteors, sky rotation, parallax. |
| [clock-and-date.md](features/clock-and-date.md) | The living clock and date. |
| [boot-intro.md](features/boot-intro.md) | The first appearance after a boot. |
| [settings.md](features/settings.md) | System settings, Lumen settings and their groups. |
| [inputs.md](features/inputs.md) | Live TV and HDMI inputs. |
| [remote-keys.md](features/remote-keys.md) | The remote's app buttons on Home. |
| [screensaver.md](features/screensaver.md) | Lumen Clock. |
| [updates.md](features/updates.md) | Update check, the stardust update screen, prompt-free install, relaunch. |
| [seasonal.md](features/seasonal.md) | Spooky doot. |

## Find by question

| If you're asking... | Go to |
|---|---|
| Why does an animation stutter? How do I measure a frame on the TV? | [architecture/gpu-budget.md](architecture/gpu-budget.md) |
| Why is the horizon glow inside the nebula bitmap? | [architecture/cosmos.md](architecture/cosmos.md), [decisions.md](decisions.md) |
| Why is the star field drawn on whole pixels? | [architecture/cosmos.md](architecture/cosmos.md) |
| How do I add an entrance, a hold or an exit? | [architecture/stage.md](architecture/stage.md) |
| Why do explosion rays never brake to a stop? | [architecture/stage.md](architecture/stage.md) |
| How do I add a setting? | [features/settings.md](features/settings.md), [architecture/menus.md](architecture/menus.md) |
| Why can't Lumen just be set as Home? | [architecture/tv-scripts.md](architecture/tv-scripts.md) |
| What must never be disabled on a TV? | [architecture/tv-scripts.md](architecture/tv-scripts.md) |
| How does an update install without asking, and why does Lumen come back? | [features/updates.md](features/updates.md) |
| How do I cut a release? Where is the signing key? | [architecture/distribution.md](architecture/distribution.md) |
| Why do the remote's YouTube and Input buttons work on Home? | [features/remote-keys.md](features/remote-keys.md) |
| How do tests control the time of day? | [architecture/testing.md](architecture/testing.md) |

## Maintaining the brain

This brain is **load-bearing documentation**: it must stay true to the code. When a change makes a
brain doc wrong, fix the doc in the same change. When the brain disagrees with reality, the code
wins: correct the brain. New feature -> new `features/<name>.md` + a row in
[features/index.md](features/index.md) + a row here. New subsystem -> an `architecture/*.md` + rows
in its index and here. New term -> [glossary.md](glossary.md); new decision ->
[decisions.md](decisions.md).
