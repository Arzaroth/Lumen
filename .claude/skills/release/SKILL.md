---
name: release
description: Cut a Lumen release, the whole shebang - docs sweep (CHANGELOG, README, THIRD-PARTY.md, brain/), version bump in lumen/build.gradle.kts, full local gate (release build, lint, coverage floor, shellcheck), [master] chore(release) commit, annotated vX.Y.Z tag, push to Forgejo and sync the GitHub mirror, watch the release workflow until the GitHub release has its signed APK, then copy the release to Forgejo. Use when the user says "cut a release", "release 1.3.0", "ship a version", or "the whole release shebang" in this repository.
---

# Cutting a Lumen release

There is no release task: the bump is by hand, and the value of this skill is
knowing where the version lives, what CI checks on the tag, and that the
release reaches every TV running Lumen.

**Publishing is outward-facing.** Each TV's Advanced settings reads GitHub's
latest release and updates itself in place from it. Get the user's explicit
go-ahead, with the version and the notes in front of them, before pushing the
tag (step 5).

## 1. Pre-flight

- **Never switch branches in the main checkout** - the user works in it live.
  Find it by absolute path, since `wt switch` cannot move the agent's shell:

  ```bash
  MAIN=$(dirname "$(git rev-parse --path-format=absolute --git-common-dir)")
  git -C "$MAIN" branch --show-current
  git -C "$MAIN" status --short
  ```

  If the main checkout is on master (it usually is), ask the user whether to
  release from it; otherwise `wt switch master` and work from the path `wt list`
  gives. Note whether this run created that worktree; "Done when" depends on
  it. Then `git pull --ff-only` there. Every later command runs from that
  checkout (`$REPO` below).
- Tree clean, up to date with `origin/master` (Forgejo,
  `ssh://git@box.arzaroth.com/Arzaroth/Lumen.git`). Every branch meant for this
  release is merged.
- Commits are authored as `gitea@barrut.me`; check `git config user.email`
  before committing.
- The release key must exist locally only for local release builds:
  `~/.config/lumen/` holds `lumen-release.keystore`, `keystore.password` and
  `keystore.properties`. They are never copied into the repo or committed.
- Previous version: `git describe --tags --abbrev=0` (tags are `vX.Y.Z`).
- Next version: minor for any `### Added` / user-visible `### Changed`, patch
  for fix-only. Each part must stay below 100: the versionCode is the parts
  folded in base 100.

## 2. Docs sweep (against `git log v<last>..HEAD`)

- **`CHANGELOG.md` `## [Unreleased]`** covers every user-visible change since
  the tag. Walk `git log v<last>..HEAD --oneline` and match each `feat`, `fix`
  and `perf` commit to an entry. Keep a Changelog categories. Entries are prose
  that says what the viewer sees on the TV, in the style of the ones already
  there.
- **`README.md`**: new script flags, install steps, results, and anything the
  TV taught ("Things learned the hard way").
- **`THIRD-PARTY.md`**: `git diff v<last>..HEAD -- lumen/build.gradle.kts lumen/res`.
  A new font, image or code taken from a project gets credited there; a dropped
  one comes out.
- **`brain/`**: run the `brain` skill's audit scoped to what the release
  touched.
- Commit anything here as its own layer, `[master] docs: ...`, before the bump.

## 3. Bump

The version lives in exactly one place:

| File | Field |
| --- | --- |
| `lumen/build.gradle.kts` | `val version = "X.Y.Z"` (versionCode and versionName derive from it) |

The release workflow reads it with
`sed -n 's/^val version = "\(.*\)"$/\1/p'`, so keep the line exactly in that
shape.

`CHANGELOG.md`: move the `## [Unreleased]` entries under a new
`## [X.Y.Z] - YYYY-MM-DD` heading and leave an empty `## [Unreleased]` above
it. At the bottom, point `[Unreleased]` at `compare/vX.Y.Z...HEAD` and add
`[X.Y.Z]: https://github.com/Arzaroth/Lumen/compare/v<last>...vX.Y.Z` above
the previous version's link. Then check the notes come out:

```bash
scripts/changelog.sh X.Y.Z
```

It must print the entries. The release workflow fails the build when it prints
nothing.

## 4. Gate (all green before the commit)

The same set CI runs:

```bash
mise exec -- ./gradlew --no-daemon :lumen:assembleRelease :lumen:lintRelease
mise exec -- scripts/coverage.sh --check
mise exec -- shellcheck scripts/*.sh
```

Lint has `warningsAsErrors`; coverage fails under `LUMEN_COVERAGE_FLOOR` (98%).
The release workflow reruns lint and coverage on the tag, so a red gate here is
a failed release there.

## 5. Commit, tag, push

Show the user the version and `scripts/changelog.sh X.Y.Z`, and wait for an
explicit yes. Then:

```bash
git commit -am "[master] chore(release): X.Y.Z"
git tag -a vX.Y.Z -m vX.Y.Z
git push origin master vX.Y.Z
smith api -X POST repos/Arzaroth/Lumen/push_mirrors-sync
```

The subject alone is the house style (`git log -1 --format=%B v<last>`); end
the message with the Co-Authored-By trailer. The mirror sync pushes master and
the tag to github.com/Arzaroth/Lumen, which is what triggers the workflow.

## 6. Watch

The tag triggers `.github/workflows/release.yml`. Its `build` job checks the
tag is on master, matches `val version` and has changelog notes, runs lint and
the coverage floor, decodes the release key from the repository secrets
(`ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`,
`ANDROID_KEY_PASSWORD`) and builds with `LUMEN_REQUIRE_RELEASE_KEY=1`, so a
missing key fails rather than shipping a debug-signed APK that no installed
Lumen could update to. `publish` creates the GitHub release "Lumen X.Y.Z" with
`lumen-X.Y.Z.apk` and the changelog notes as its body.

```bash
gh run list -R Arzaroth/Lumen --workflow release.yml -L 1
gh run watch -R Arzaroth/Lumen <id> --exit-status
gh release view vX.Y.Z -R Arzaroth/Lumen --json assets --jq '.assets[].name'
```

If the run fails after the tag is out: fix on master in a new commit. If only
CI config or a secret was wrong, re-run the failed run
(`gh run rerun <id> -R Arzaroth/Lumen`); if code changed, cut the next patch
instead of moving a published tag.

## 7. Copy the release to Forgejo

```bash
d=$(mktemp -d)
gh release download vX.Y.Z -R Arzaroth/Lumen -D "$d"
scripts/changelog.sh X.Y.Z | python3 -c 'import json, sys; print(json.dumps({"tag_name": "vX.Y.Z", "name": "Lumen X.Y.Z", "body": sys.stdin.read()}))' \
  | smith api -X POST repos/Arzaroth/Lumen/releases --input -
smith release upload vX.Y.Z "$d/lumen-X.Y.Z.apk" -R Arzaroth/Lumen
rm -rf "$d"
```

## Done when

The tag is on Forgejo and GitHub, the GitHub release has `lumen-X.Y.Z.apk`
signed with the release key and its body is the changelog section, and the
Forgejo release carries the same APK and notes. Report the version, the
GitHub release URL and a one-line theme; a TV picks it up from Advanced
settings. Remove the master worktree only if this run created it.
