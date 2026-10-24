# Remote keys

On Google TV the remote's Input, YouTube, Netflix and Disney+ buttons are wired to the stock
launcher, which is disabled. `LumenKeys`, an accessibility service, takes them while Lumen is in
front: Input opens Lumen's Inputs menu, the app buttons open their apps (YouTube or SmartTube, per
the YouTube button setting), or the Play Store page if missing.

- The key filter is only requested while Home is in front: any accessibility key filter stops held
  keys from repeating (held volume).
- `adb shell input keyevent` bypasses accessibility filters: test with the real remote.
- The service is also how Lumen comes back after an update (see [updates.md](updates.md)).
- Lumen settings › Remote › Remote buttons turns it off.

## Sources

- `lumen/src/com/arzaroth/lumen/LumenKeys.java`, `lumen/res/xml/keys.xml`
