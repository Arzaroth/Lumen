# Updates

Lumen updates itself from GitHub's latest release, from Settings › Lumen settings › Advanced
settings.

- **Check**: opening Advanced asks GitHub again (unless an update is under way); the row shows
  Checking, Lumen is up to date, Update to X, Downloading X%, Installing, or Couldn't update. If Lumen
  may not install apps it opens that setting; without it, it says to run `scripts/install.sh`.
- **Download and install**: the APK streams into a `PackageInstaller` session asking for no user
  action. Lumen waits 4 s (`Updater.hold`) so its screen plays out, writes the relaunch mark, then
  commits. When Lumen installed itself last time (installer of record) Android replaces it without a
  prompt; otherwise it asks (state `CONFIRMING`).
- **Screen** (`Ascent`): over the dimmed sky the new version gathers out of stardust as it
  downloads, crystallises into thin Outfit text with a ring of light while installing, and drifts
  back into the sky if it fails or is cancelled. It holds the remote; Back dismisses it once Android
  has the install.
- **Coming back**: Android kills Lumen to replace it and shows the last app. The new Lumen's
  `Relaunch` receiver (`MY_PACKAGE_REPLACED`) tries to bring Home back at once; `LumenKeys`, rebound
  by the system, does it for sure. The mark only counts for 10 minutes. Lumen's window shows black
  at once when it starts, so the last app shows as little as possible.

## Sources

- `lumen/src/com/arzaroth/lumen/Updater.java`, `Ascent.java`, `Relaunch.java`, `LumenKeys.java`, `LumenSettings.java`
- `lumen/AndroidManifest.xml`, `lumen/res/values-v31/styles.xml`
