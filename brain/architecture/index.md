# Architecture

The technical layers and cross-cutting subsystems. Start with [overview.md](overview.md) for the big
picture, then jump to the layer you need. Back to the root map: [../BRAIN.md](../BRAIN.md).

| File | What's here | Grep keywords |
|---|---|---|
| [overview.md](overview.md) | The classes, the view tree, how a key press flows, where state is kept | LumenActivity, buildViews, dispatchKeyEvent, Prefs |
| [stage.md](stage.md) | Mosaics, the enter/hold/exit cycle, sparks, rings, ghosts, the floor, meshes | Stage, Mosaic, adopt, ghost, emit, RAY, buildMeshes, floorY, art |
| [cosmos.md](cosmos.md) | Baked field, live stars, nebula with airglow, meteors, satellites, parallax, rotation | Cosmos, Sky, Theme, nebula, airglow, setParallax, turned, margin |
| [themes.md](themes.md) | The Theme, Backdrop and Palette interfaces, the registry, the plan to community themes | Theme, Backdrop, Palette, Themes, SpaceTheme, VolcanoTheme, setTheme |
| [volcano.md](volcano.md) | The volcanic night: baked land, lava mask, plume, embers, eruptions, its palette | Volcano, lava, plume, ember, eruption, PALETTE |
| [gpu-budget.md](gpu-budget.md) | Frame costs per layer on the TV, the measuring method, the rules | gfxinfo, framestats, GpuCompleted, CLIENT composition, perf.py |
| [menus.md](menus.md) | The glass dropdown: pages, rows, questions, navigation, in-place refresh | Menu, Page, Row, swap, showing, LumenSettings |
| [tv-scripts.md](tv-scripts.md) | backup, disable, install, restore; the Home guard; what must never be disabled | install.sh, restore.sh, disable.sh, launcherx, setupwraith |
| [distribution.md](distribution.md) | Gradle, signing, CI, the release workflow, Forgejo copy, the updater | build.gradle.kts, keystore.properties, release.yml, Updater |
| [testing.md](testing.md) | Robolectric with native graphics, Wall, Robo helpers, coverage floor | Robo, Wall, coverage.sh, Releases, JaCoCo |

## How to use this folder

- Each file stands alone but cites real source paths so you can jump to code.
- The regression rules stay in the repo's `CLAUDE.md`; these docs describe the mechanism.
- Product features live in [../features/](../features/index.md); this folder is the plumbing.
