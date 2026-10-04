# Release checklist

The order and the checks of one release of the ecosystem. Copy the steps
into the release's local tracker, fill the state block, and tick them there;
this file stays a template. Versions, lines and compatibility rules are in
[releases](../product/releases.md).

**(owner)** marks a step that publishes, pushes a protected ref, tags,
deploys, uploads to a store or changes the server: it runs only after the
owner's explicit word for that step, every time. Unmarked steps are checks an
agent may run. Never mutate production data for testing, and never open the
signing or environment secrets; the owner builds signed artifacts.

`B/`, `M/` and `W/` name files in the Backend, MyItmoApi and Web
repositories.

## 0. State and order

Write down before the first step:

- the release version, its `versionCode` (scheme in
  [releases](../product/releases.md#distribution)) and the channel: GitHub
  release, GitHub prerelease, Play track, TestFlight;
- the latest public release and its version code;
- the Backend image and Flyway version in production, the advertised
  `latestVersion` and `minVersion` from `version-info`;
- the MyItmoApi version the app and Backend need and whether it is on Maven
  Central;
- the Web commit on development and production.

Order: MyItmoApi → Backend on production → Web on production → Android build
→ GitHub release → advertised version → Google Play → iOS. A step starts only
when the steps it depends on are verified.

## 1. Freeze `master`

1. **(owner)** The release batches are on `master`; nothing else merges until
   the release commit.
2. Checks on `master`:

   ```bash
   git status --short --branch
   scripts/verify.sh ship          # scripts/ship-check.sh, all five stages
   scripts/check-docs.sh --strict
   scripts/changelog.sh check
   ```

   Every prerelease head and the release candidate need a green
   `scripts/ship-check.sh` with all five stages, run by the integrator on
   `emulator-5554` on the head being tagged
   ([ship check](integration.md#ship-check)); record the path of its
   `~/proj/.wt/run/ship/<sha7>/summary.md`. The upgrade from the release-signed
   2.2 to the signed candidate stays a manual owner step: the manual pass
   of section 5, item 4.

3. Visual pass, once per release: the full appearance matrix of the
   screenshot tests and the instrumentation suite on a pool emulator,
   including the upgrade from the previous release's data directory.

   ```bash
   export "$(scripts/emulator.sh up --api 35)"   # ANDROID_SERIAL=emulator-<port>
   scripts/verify.sh ui all
   scripts/emulator.sh down
   ```

   Any failure that is not a known stale one blocks the release.

## 2. MyItmoApi to Maven Central (owner: push, tag, «Publish»)

Only when the release needs a MyItmoApi version that is not on Central yet.

1. Set the release version in the build file and `M/CHANGELOG.md`, run the
   tests, commit `Release <version>`.
2. **(owner)** Push `master` and the tag; the tag runs the release workflow,
   which deploys to Central and creates the GitHub release.
3. **(owner)** If the deployment waits as `VALIDATED` on
   central.sonatype.com, press «Publish».
4. Verify; Central may lag about 30 minutes:

   ```bash
   curl -s https://repo1.maven.org/maven2/dev/alllexey/<artifact>/<version>/ | head -3
   gh release view <version> --repo alllexey-dev/my-itmo-api
   ```

5. **(owner)** Start the next snapshot version and push it.

Core: from 2.3 Core 2.0 lives in this repository and is not published;
Core 1.x stays at 1.7.0 and gets a release only for a fix on the owner's word.

## 3. Backend on production (owner: push `dev`, run the release, approve `production`)

1. Set the release version in the build file and `B/CHANGELOG.md`; check with
   Testcontainers (command in `B/AGENTS.md`), dependencies from Maven Central
   only.
2. **(owner)** Commit `Release <version>` on `dev` and push: `deliver` builds
   the image, deploys development and fast-forwards `master`.
3. On development: `version-info`, logs without `ERROR`, the sport catalog
   refresh and a people lookup from a debug build; let it soak.
4. Read-only before production: `origin/master` is the release commit, the
   backup volume has room for the dump, `platform doctor` is clean.
5. **(owner)** Run the release workflow and approve the `production`
   environment in the run:

   ```bash
   gh workflow run release.yml --repo alllexey-dev/itmo-widgets-backend --ref master -f version=<version>
   ```

   It re-tags the tested image, dumps the database, deploys, waits for
   `version-info`, switches back on failure, then tags and creates the GitHub
   release.
6. Verify, read-only:

   ```bash
   ssh alllexey.dev platform history itmowidgets
   ssh alllexey.dev platform logs itmowidgets backend | grep -E "Successfully applied|ERROR"
   curl -s https://widgets.alllexey.dev/api/app/version-info
   ```

7. **(owner)** Record the deployment in `B/docs/ops/deployments.md`.
8. Rollback, if needed and on the owner's word, is image only:
   `ssh alllexey.dev platform rollback itmowidgets`. Restoring the dump loses
   writes after the deploy and is a separate approved operation.

## 4. Web on production (owner)

1. **(owner)** Deploy the Web commit to development, then to production, as
   `W/README.md` describes.
2. Verify every public path answers 200 and the App Links file is served as
   `application/json`:

   ```bash
   for p in / /privacy.html /delete-account /app/ /u/1 /sport/1 /.well-known/assetlinks.json; do
     echo "$p $(curl -s -o /dev/null -w '%{http_code}' https://widgets.alllexey.dev$p)"; done
   ```

3. **(owner)** Record the deploy in the server repository's change log.

## 5. Android build (owner builds)

1. Release commit content: `versionName` and `versionCode` in
   `app/build.gradle.kts`, the dependency versions in
   `gradle/libs.versions.toml`, `scripts/changelog.sh collect <version> <date>`
   (folds `changelog.d/` into `CHANGELOG.md`), the release row in
   [releases](../product/releases.md).
2. Build without local Maven artifacts and check:

   ```bash
   scripts/verify.sh ship
   ./gradlew clean :app:assembleGithubRelease :app:bundlePlayRelease
   ```

3. **(owner)** Sign with the release key. Verify the APK:

   ```bash
   APK=app/build/outputs/apk/github/release/app-github-release.apk
   "$(ls -d $HOME/Library/Android/sdk/build-tools/*/apksigner | tail -1)" verify --print-certs "$APK" | grep SHA-256
   "$(ls -d $HOME/Library/Android/sdk/build-tools/*/aapt2 | tail -1)" dump badging "$APK" | head -1
   ```

   The certificate is `939e9bd5…f071`, the package `dev.alllexey.itmowidgets`
   with the planned version code and name.
4. Manual pass on the owner's phone over the previous release, against
   production: sign-in kept, widgets, sport, the ITMO.Widgets connection, a
   subject page with links, a teacher profile with reviews, the background
   check switches, calendar sync, the account and privacy rows, web sign-in,
   a shared `/u/<isu>` link opening the app, the demo session.
5. **(owner)** Commit `Release <version>` and push `master`.

## 6. GitHub release (owner)

```bash
cp app/build/outputs/apk/github/release/app-github-release.apk /tmp/itmo-widgets-v<version>.apk
gh release create v<version> /tmp/itmo-widgets-v<version>.apk --repo alllexey-dev/ITMO.Widgets \
  --target master --title "<version>" --notes-file <notes>
curl -sIL https://github.com/alllexey-dev/ITMO.Widgets/releases/latest | grep -i '^location' | tail -1
```

A beta gets `--prerelease`, so `releases/latest` and the update offer of
every `github` install stay on the last release.

## 7. Advertised version (owner)

Set `app.latest` (and `app.note`; `app.minimum` only on its own decision) in
the web admin «Система»; these rows win over the server environment. Verify
`version-info` and the update offer: a phone on the previous release with the
connection on offers the new version at most once a day, and «Обновить» opens
the right page.

## 8. Google Play (owner)

Upload `app-play-release.aab` from the same commit to the chosen track in the
Play Console; the upload consumes its `versionCode`. Production needs the
closed-test condition in [releases](../product/releases.md#distribution).

## 9. iOS (owner; after the Apple account gate)

Upload the CI-built archive to TestFlight, internal first, then external
testing or App Store review as the roadmap's ladder case allows. The
marketing version follows the app; build numbers come from CI.

## 10. After the release

- Next development line: `versionName` `<next>-SNAPSHOT` and its development
  `versionCode`, `CHANGELOG.md` `## <next> — development`, the development
  line in [releases](../product/releases.md).
- **(owner)** A maintenance branch `release/<major.minor>` at the tag, if the
  line needs one.
- Release notes and the announcement post; the owner sends them.
- The Google Play button in the README and on the site only after the listing
  is public.

## Owner decision points

| Decision | Default |
|---|---|
| Backend production release and its date | at least a week before the release candidate |
| `app.minimum` | unchanged; never above the oldest version the release line promises |
| GitHub release or prerelease | prerelease for betas |
| Play track | closed test until the production condition holds |
| iOS channel | by the Apple account date; an unsigned preview only on the owner's word |
| Production switches (AI summaries, Reviews sync, first admin, ISU cookie) | unchanged by a release |
