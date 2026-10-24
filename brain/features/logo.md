# Logo

The focused app's banner rebuilt as lit 3D voxels in the middle of Home, standing on a floor with a
reflection. Each cycle plays an entrance, a hold and an exit picked at random, never the same kind
twice in a row; switching apps tears the logo down with a quick exit and brings the next one in.

- After an app switch, the new logo first materialises out of its tile's banner and flies up into
  place. Its later cycles pick freely.
- Entrances: converge, rain, print, warp, spiral, depth, sweep. Entrances glide into place and never
  overshoot; converge comes in from around the logo, never back from where an exit left the cubes.
- Holds: shimmer, pulse, ripple, meteor storm, flag, spin (fireworks style: firework show).
- Exits: detonate, vortex (supernova), dust, melt, glitch, hyperspace, zoom, shatter (fireworks
  style: launch).
- Settings: Animations (lively, calm, off) and Logo (3D voxels or flat). Calm uses a gentler subset.
- Explosion rays keep flying as they fade; the leftover cubes of an interrupted exit fade on their own.
- Hyperspace jumps to light speed: the bright stars streak and fly out past the edges, then the sky
  fades back in.

The machinery is in [../architecture/stage.md](../architecture/stage.md).

## Sources

- `lumen/src/com/arzaroth/lumen/Stage.java`
