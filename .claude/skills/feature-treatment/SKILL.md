---
name: feature-treatment
description: Ship a finished Lumen feature branch the full way - rebase onto master, run a max-effort multi-agent review with Lumen's lenses (TV safety, GPU budget on a weak TV, self-update integrity, correctness, reuse, tests), fix everything confirmed as new layers, update the brain, pass the full gate (release build, lint, coverage floor, shellcheck), fast-forward master keeping the layers, push to Forgejo and sync the GitHub mirror, cut a release, and remove the worktree. Use when the user says "feature treatment", "treat this branch", "review and merge this feature", or names a worktree or branch to ship.
---

# Feature treatment

The pipeline a finished feature branch goes through before it lands:
**rebase -> max review -> fix -> update the brain -> gate -> merge -> release
-> clean up**. Nothing merges without an adversarial review and a green gate.

The branch comes from the argument. If none is given, `wt list` and pick the
non-master worktree; ask if more than one is a candidate. Work inside that
worktree, never in the main checkout (the user works in it live, on master).
Paths are absolute, since `wt switch` cannot move the agent's shell; take the
worktree's path from `wt list` rather than assuming a layout:

```bash
MAIN=$(dirname "$(git rev-parse --path-format=absolute --git-common-dir)")
WT=<path of the branch's worktree, from wt list>
```

Every Gradle and shellcheck command runs through `mise exec --` so it gets the
pinned toolchain (`mise.toml`: temurin-17, the Android SDK, shellcheck).

## 1. Rebase onto master

First decide whether history may be rewritten. If the branch is already pushed
to `origin` (Forgejo, `git ls-remote origin <branch>`) and someone has
reviewed it there (`smith pr list -R Arzaroth/Lumen`), do not rebase: merge
`origin/master` in, or ask. Otherwise:

```bash
cd "$WT" && git branch --show-current && git fetch origin && git rebase origin/master
```

Use the `resolving-merge-conflicts` skill on conflicts.

Then check the size rule: `git diff --name-only origin/master..HEAD | wc -l`
must be at most 149. Over it, stop and propose a split along a seam (one
subsystem per branch: rendering, input, the updater, the scripts) before
reviewing anything.

## 2. Max-effort review (parallel finders)

Diff: `git diff origin/master...HEAD -- . ':(exclude)shots' ':(exclude)backup'`
(binary fonts or drawables under `lumen/res/` only need a finder if one claims
a size or licence problem).

Spawn independent finder agents in one message, each over the same diff with a
different lens. Scale to the feature, 4-6 is typical. Give each one the
repo's `CLAUDE.md`, the README's "Things learned the hard way" and the matching brain docs by path so
it reviews against what the TV already taught rather than rediscovering it.

- **TV safety** - nothing may disable or break what the TV needs: TV, input,
  HDMI and tuner apps, the input menu, remote and Bluetooth pairing, the
  keyboard, Play Store and Play Services, core Android. A new entry in
  `lists/*.txt` or `debloat.txt`, or a change to the `PROTECTED` pattern in
  `scripts/disable.sh`, is read package by package. Lumen must never list or
  launch factory or test packages. Every change a script makes must be undone
  by `scripts/restore.sh` (and recorded by `scripts/backup.sh` first); a new
  `pm disable-user` or setting without its restore path is a finding. Also: a
  Home rival that owns Wi-Fi networks must not be disabled (`install.sh`'s
  guard), `adb shell` inside a `while read` loop eats stdin, and the remote's
  buttons only reach Lumen through `LumenKeys`, whose key filter must stay
  requested only while Lumen is in front (held keys stop repeating otherwise).
- **GPU budget** - the TV is a weak PowerVR part with a 16.7 ms frame. A new
  full-screen layer, per-pixel shader noise, or a per-frame allocation is a
  finding. The sky is composed once off the UI thread and blitted at whole-pixel
  offsets (a fractional blit resamples and blurs it on a 4K panel); anything
  that moves is a `drawVertices` mesh, with its own arrays per run (a non-zero
  vertex offset crashes HWUI on this build). Claims of "cheaper" need numbers
  from the device (`scripts/frames.sh com.arzaroth.lumen`, `dumpsys gfxinfo`),
  not the emulator.
- **Self-update integrity** - `Updater.java` reads GitHub's latest release and
  installs it in place: the APK must stay signed with the same release key (a
  different key cannot update the installed app), Lumen must stay its own
  installer of record (`pm install -i com.arzaroth.lumen` in
  `scripts/install.sh`) so the update needs no prompt
  (`USER_ACTION_NOT_REQUIRED` on SDK 31+), the relaunch mark must be on disk
  before Android kills Lumen (`commit()`, not `apply()`), and `Relaunch` must
  bring Lumen back once and never from a stale mark. The release asset name
  (`lumen-X.Y.Z.apk`) and the versionCode derived from `val version` must keep
  matching what installed versions look for.
- **Correctness** - line by line: inverted conditions, off-by-one in focus and
  row navigation, null from `PackageManager` or a missing app icon, SDK-guarded
  calls below `minSdk 26` paths (`OlderAndroidTest`), time math in the clock
  and date, state lost across a configuration change or a relaunch, threads
  touching views off the UI thread.
- **Reuse / simplify** - helpers re-implemented instead of using `Glyphs`,
  `Fonts`, `Prefs`, `Wall`, `Inputs`; plain Java with no Kotlin and no AndroidX
  (a new dependency is a finding unless it is a test one); explicative comments
  that should not exist.
- **Tests** - every new branch has a Robolectric test (native graphics, so
  drawing code runs rather than being stubbed); the line-coverage floor
  (`LUMEN_COVERAGE_FLOOR`, 98%) still holds without excluding anything; tests
  that assert on real behaviour, not on mocks echoing back.

Each finder returns JSON findings `{file, line, severity, summary,
failure_scenario}`, verified by quoting the line, most severe first, and fixes
nothing. Optionally run one sweep finder over the merged list that hunts only
for gaps.

## 3. Fix what is confirmed

Re-verify each claim against the code before acting; finders produce plausible
wrong items too. Fix every confirmed safety, GPU, update or correctness finding
and the worthwhile quality ones. Real but out of scope goes to the user as one
line in the final report, not dropped.

Fixes land as **new layers on top**, one kind of work per commit, each leaving
the tree green: `[<branch>] fix(lumen): ...` or `fix(scripts): ...`. Do not
fold them into the feature commits. No em-dashes in subjects, bodies or code.

## 4. Update the brain

Apply the `brain` skill's maintain checklist to what the branch changed: a new
or changed feature into `brain/features/` (plus its index and `brain/BRAIN.md`
rows), subsystem changes into `brain/architecture/`, new terms into
`brain/glossary.md`, a decision with real alternatives into
`brain/decisions.md`, a TV fact into the README's "Things learned the hard
way", and a `CHANGELOG.md` `## [Unreleased]` entry for anything a user sees on
the TV, in the plain prose of the entries already there. Commit as its own
`[<branch>] docs: ...` layer so it ships in the same merge.

## 5. Gate

```bash
mise exec -- ./gradlew --no-daemon :lumen:assembleRelease :lumen:lintRelease
mise exec -- scripts/coverage.sh --check
mise exec -- shellcheck scripts/*.sh
```

Lint runs with `warningsAsErrors`; the coverage check fails under the 98% line
floor. Every commit must pass on its own, not just the tip:

```bash
git rebase origin/master --exec 'mise exec -- ./gradlew -q --no-daemon :lumen:lintRelease :lumen:testDebugUnitTest'
```

If the feature changes what is drawn or how fast, install it on the TV
(`ANDROID_SERIAL=<tv ip>:5555 mise exec -- scripts/install.sh`) and measure
with `scripts/frames.sh com.arzaroth.lumen` before and after. If it touches the
remote or the keys service, press the buttons with the real remote:
`adb shell input keyevent` bypasses accessibility key filters.

Re-count files against the 149 limit after the fixes.

## 6. Merge (keep the layers)

Fast-forward `master` onto the branch rather than squashing, so the layers
survive. Do it where master is checked out (the main checkout, if the user
says it is free and its tree is clean; ask otherwise):

```bash
git -C "$MAIN" switch master && git -C "$MAIN" pull --ff-only
git -C "$MAIN" merge --ff-only <branch>
git -C "$MAIN" push origin master
smith api -X POST repos/Arzaroth/Lumen/push_mirrors-sync
```

Forgejo (`origin`) is the source of truth; the sync pushes master to
github.com/Arzaroth/Lumen, where `.github/workflows/ci.yml` runs on the push.
Watch it green:

```bash
gh run list -R Arzaroth/Lumen --workflow ci.yml -L 1
gh run watch -R Arzaroth/Lumen <id> --exit-status
```

## 7. Release

Run the `release` skill. Minor for a user-facing feature, patch for fix-only.
Skip only if the user says to batch it with the next one.

## 8. Clean up

```bash
wt remove <branch>
git push origin --delete <branch>   # only if it was pushed
```

## Done when

The feature is on master with its layers, the brain reflects it, the gate and
CI were green, a tag is pushed (unless skipped), and the worktree is gone.
Report the version and one line on what the review caught.
