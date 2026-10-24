# Stage

The empty middle of Home: the focused app's banner becomes a logo of lit voxels that cycles through
an entrance, a hold and an exit, never the same kind twice in a row.

## Mosaics

`setScene` builds (or takes from the cache) a `Mosaic` on a background handler: the banner is cut
into cells kept by **coverage** (enough foreground pixels) and coloured by those pixels alone, so
thin wordmarks stay whole; up to `CAP` (4000) cells, shuffled so caps thin evenly. A mosaic also
carries the spark palette and the app's sky `Theme`. `apply` hands it to the cycle; `adopt` takes it
at the next entrance.

## The floor and the art

`adopt` lifts each logo so its lowest cell sits on the bottom of the logo box: every logo stands on
the same floor (`floorY = logo.bottom + floorGap`), with a fixed-width horizon line and the
reflection below. `art` is the bounds of the cells actually lit; every effect centre (explosions,
vortex, the core light, the 3D projection, the print beam) uses `art`, never the box.

## The cycle

- Entrances `E_*`: converge, rain, print, warp, spiral, depth, sweep. Holds `H_*`: shimmer, pulse,
  ripple, show (fireworks only), storm, flag, spin. Exits `X_*`: detonate, vortex, dust, melt,
  glitch, launch (fireworks only), hyperspace, zoom, shatter. Lists per style and pace
  (`COSMOS_*`, `FIREWORK_*`, `CALM_*`, `QUICK_EXITS` for app switches).
- Switching apps mid-cycle speeds the current exit up (`speed`) or starts a quick one. When the
  activity passes the focused tile's banner (`setScene(app, animate, banner)`), the next entrance is
  `E_TILE`: each cell starts white-hot at its spot on the banner (`source`, minus the floor `lift`)
  and glides up on an arc into place.
- Entrances ease with `settle` (exponential, no overshoot). Converge restarts any hidden or faded
  cell from a ring around the logo.
- `ghost` hands the old logo's visible cells to fading ghosts, thinned to `GHOST_CAP` (1200).

## Effects

`emit`/`spark` add sparks (life, drag, gravity, colour, tail, width, flags); `ring` adds
shockwaves; `flash` and `chroma` (colour fringe) are short-lived globals. Rays (`RAY` flag) barely
brake and dim below `RAY_FADE` so they never hang in the air.

## Drawing

`onDraw` updates, then draws `Cosmos.drawBack`, the reflection (quality 2), the horizon line,
lights, glow quads and the voxel core. `buildMeshes` fills the vertex arrays: voxels as faces turned
to the camera, a glow halo on one cell in three (one in two while entering), sparks as segments.
`adapt` drops the reflection, then the glow, after 90 slow frames, for the rest of the session.

## Sources

- `lumen/src/com/arzaroth/lumen/Stage.java`
- `lumen/test/com/arzaroth/lumen/StageTest.java`
