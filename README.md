# tv-lab

Debloat an Android TV / Google TV and replace its home screen with **Lumen**, an Apple TV 4K style
launcher, everything reversible. Built and verified first on a local Android TV 14 emulator.

Inspired by the wave of "I vibe-coded my TV" posts (x.com/SimbasJungles/status/2112231637840765012) and
the open-source [CleanYourTV](https://github.com/Gathin23/cleanyourtv) project, whose safety rules
(disable never uninstall, back up first, never touch core packages, Home guard) this follows.

![Lumen home](docs/home.jpg)

| All apps | Inputs | Screensaver |
|---|---|---|
| ![grid](docs/grid.jpg) | ![inputs](docs/inputs.jpg) | ![screensaver](docs/screensaver.jpg) |

## Lumen

- Hero backdrop: a soft colour wash of the focused app's banner with an ambient glow sampled from it,
  cross-fading as focus moves
- Greeting, thin Outfit clock and date; frosted pills for Inputs (glass dropdown: Live TV + HDMI
  inputs via the TV input framework), network (Wi-Fi name or Ethernet) and Settings
- Glass dock band of 5 apps, a spring-animated glass highlight, focused tile lifts with a real shadow
  and shows its name; all apps in a grid below that scrolls the dock up like tvOS
- Deterministic D-pad: Up/Down move exactly one row, Up from the dock reaches the top bar, Back
  returns to the dock, Home always lands on the dock; last focus remembered across restarts
- Long-press OK (or Menu) to rearrange: arrows move the tile, OK saves
- Lumen Clock screensaver: thin drifting clock, moves every minute against burn-in
- Plain Java, no dependencies, no INTERNET permission, 152 KB APK, minSdk 26

## Layout

| Path | What |
|---|---|
| `debloat.txt` | Packages disabled on the emulator image (Google TV ads, recommendations, telemetry, preloads) |
| `scripts/backup.sh` | Captures packages, settings, getprop, Home and screensaver before any change |
| `scripts/disable.sh` | Disables a list, one `pm disable-user` per package, refuses protected core packages |
| `scripts/install.sh` | Builds Lumen, installs it, makes it Home, disables the stock launcher only behind a guard that re-enables it if Lumen does not take Home |
| `scripts/restore.sh` | Re-enables everything, gives Home back, restores the screensaver, uninstalls Lumen (`--keep-lumen` to keep it) |
| `scripts/measure.sh`, `scripts/frames.sh` | Reboot + RAM measurement, D-pad frame-time measurement |
| `lumen/` | Launcher source; `lumen/build.sh` builds with the bare SDK (aapt2, javac, d8, apksigner) |
| `backup/` | Per-TV state written by `backup.sh` and the other scripts; gitignored (holds serials and device names) |
| `shots/` | Verification screenshots; gitignored |

## Requirements

`adb`, JDK 17+, and an Android SDK with `build-tools` 34+ and `platforms;android-34`
(`ANDROID_HOME` if not in `~/Android/Sdk`). `lumen/build.sh` creates `~/.android/debug.keystore`
if it is missing. To try it without a TV: an emulator from `system-images;android-34;android-tv;x86`
with the `tv_1080p` device.

## Results on the emulator (Android TV 14, 1 GB RAM)

Measured the same way for both: reboot, settle 90 s, `dumpsys meminfo`; 30 D-pad presses, `dumpsys gfxinfo` (host GPU).

| | Stock | Lumen + debloat |
|---|---|---|
| Home app PSS | ~90 MB (`tvlauncher`) | 26 MB (-71%) |
| Used RAM | 732 MB | 633 MB (-99 MB) |
| Running processes | 392 | 325 |
| Frame time p50 / p90 / p99 | 18 / 23 / 31 ms | 19 / 25 / 32 ms |

Lumen renders about 5x more frames than stock (springs, cross-fades) at the same frame pacing. It is
not "3x faster"; it is the same speed while doing a lot more, on a third of the memory.

## Things learned the hard way

- On this Google TV build `cmd package set-home-activity` and the HOME role both report success and
  change nothing: the stock launcher's HOME filter has priority 2, which beats any preferred choice.
  Lumen only becomes Home once the stock launcher is disabled, hence the guarded swap in `install.sh`.
- `adb shell` inside a `while read` loop eats the loop's stdin; the first restore re-enabled one
  package out of nine. `restore.sh` reads the list up front.
- The first backdrop painted seven full-screen layers per frame. It is now composed once per focus
  change, off the UI thread, into a 480x270 bitmap and blitted as one texture.

## On a real TV

1. TV: Settings > Device Preferences > About > press Build 7 times, then Developer options > USB
   debugging (and Network debugging if shown). Note the TV's IP.
2. `adb connect <tv ip>:5555`, accept the prompt on the TV, then `export ANDROID_SERIAL=<tv ip>:5555`.
3. `scripts/backup.sh` (package list, settings, getprop, Home, screensaver); it refuses to overwrite an existing `backup/`.
4. Build a list for *that* TV: manufacturer packages differ from this emulator. Check the matching
   folder in CleanYourTV's `examples/` first; never disable what the TV needs (TV/input apps, remote,
   Bluetooth, keyboard, Play Store/Services, core Android).
5. `scripts/disable.sh <list>`, then `scripts/install.sh`, then reboot and check Home and the list.
6. When done: turn USB/Network debugging off on the TV.

Undo everything: `scripts/restore.sh`.

## Status

Done and verified on the emulator. The real TV (`192.168.5.200`) was not reachable from the first
machine (`192.168.42.x`, routed through the UniFi gateway: no ping, TCP 5555 timed out), so nothing
has been run on it yet.
