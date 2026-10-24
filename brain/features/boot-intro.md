# Boot intro

The stock boot animation cannot be replaced on a locked Google TV, so Lumen's first appearance after
each boot is the show: the stars come out one by one, the Milky Way rises, meteors burst from the
radiant, the interface lifts in while the clock gathers out of stardust, and the logo enters last.

- Lumen settings › Boot intro: on or off, and Play the intro now.
- The boot it last played on is kept (`intro_boot`) so it plays once per boot.

## Sources

- `lumen/src/com/arzaroth/lumen/LumenActivity.java` (`playIntro`), `Stage.java` (`intro`), `Cosmos.java` (`intro`)
