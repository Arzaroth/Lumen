# Home

- **Dock**: a glass band with the first five apps (`COLS`); the focused tile lifts with a real shadow
  and shows its name, under a spring-animated glass `Highlight`.
- Settings and Live TV are left out of the dock and grid (`Apps.HIDDEN`): the Settings and Inputs
  pills open them. doot is left out too: it is not an app to open.
- **Grid**: every other app below, reached with Down; the content scrolls the dock up like tvOS and
  the stage dims. An "All apps" header shows in the grid.
- **Chevron**: a faint downward chevron under the dock when there are more apps below; it nods in
  once and fades out in the grid.
- **Focus**: Up/Down move exactly one row, Up from the dock reaches the top bar pills, Back returns
  to the dock, Home always lands on the dock. The last focused app is remembered (`focus`).
- **Order** (Lumen settings › Home screen, Manual by default): "Most used" sorts the apps by
  `Habits`, a score per package that gains a point at each launch from Lumen (tiles, the Inputs
  menu, the remote's app buttons) and halves every week. The dock is re-sorted when you come back
  Home, the focus staying on its app. Rearranging switches back to Manual, keeping the order shown.
- **Rearranging**: long-press OK (or Menu), arrows move the tile, OK saves the order (`order`);
  Settings › Lumen settings › Home screen › Reset tile order undoes it.
- Each focus change sets the stage's scene, the clock and date accent and the sky's parallax.

## Sources

- `lumen/src/com/arzaroth/lumen/LumenActivity.java` (`applyFocus`, `navigate`, `hintMore`, `startMoving`)
- `Tile.java`, `Highlight.java`, `Chevron.java`, `Apps.java`
