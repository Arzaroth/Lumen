# TV scripts

Shell scripts in `scripts/` run against a TV over adb (`ANDROID_SERIAL=<ip>:5555`, through
`mise exec --`). Rules from CleanYourTV: disable, never uninstall; back up first; never touch core
packages; guard the Home swap.

## The scripts

- `backup.sh`: packages, settings, getprop, Home and screensaver into `backup/` (gitignored); refuses
  to overwrite an existing backup.
- `disable.sh <list>`: one `pm disable-user` per package; refuses protected core packages.
- `install.sh`: builds the release APK, installs it as its own installer (`pm install -i`), retires
  the old `dev.arzaroth.lumen`, grants location, `DUMP` and the install appop, enables the remote
  keys service and the screensaver, makes Lumen Home and only then disables the stock launcher,
  re-enabling it if Lumen does not take Home.
- `restore.sh`: re-enables everything, gives Home back, restores the screensaver, removes Lumen.
- `measure.sh`, `frames.sh`: RAM after a reboot, frame times on D-pad presses.

## Never disable

TV and input apps, HDMI and tuners, the input menu, the remote and Bluetooth pairing, the keyboard,
Play Store and Services, core Android. Never launch factory or test packages.

## Google TV traps

- The stock launcher's HOME filter (priority 2) beats any preferred choice: Lumen becomes Home only
  once launcherx is disabled.
- setupwraith's `RecoveryActivity` claims HOME and may own the saved Wi-Fi networks: disabling it
  deletes them and drops adb. `install.sh` refuses to disable a Home rival that owns networks.
- Package state is written lazily: wait about 30 s before rebooting after `pm disable-user`.
- Network debugging does not survive a reboot on the Thomson; switch it back on in Developer options.

## Sources

- `scripts/*.sh`, `lists/thomson-4k-ratv3.txt`, `debloat.txt`
