# Release

The steps of one release. Versions, version codes, channels and compatibility
rules are in [releases](product/releases.md). Never mutate production data for
testing, and never commit the signing or environment secrets.

`B/` and `M/` name files in the
[Backend](https://github.com/alllexey-dev/itmo-widgets-backend) and
[MyItmoApi](https://github.com/alllexey-dev/my-itmo-api) repositories.

## 0. State and order

Write down before the first step:

- the release version, its `versionCode` (scheme in
  [releases](product/releases.md#distribution)) and the channel: GitHub
  release, GitHub prerelease, Play track, TestFlight;
- the latest public release and its version code;
- the Backend image and Flyway version in production, the advertised
  `latestVersion` and `minVersion` from `version-info`;
- the MyItmoApi version the app and Backend need and whether it is on Maven
  Central;
- the Web commit on development and production.

Order: MyItmoApi -> Backend on production -> Web on production -> Android build
-> GitHub release -> advertised version -> Google Play -> iOS. A step starts
only when the steps it depends on are verified.

## 1. Check `master`

1. Nothing else merges into `master` until the release commit.
2. CI on the release candidate is green: `android-ci`, `android-ui`, `ios` and
   `android-ship` (version scheme, unsigned release builds and the Play policy
   check, the 2.2 -> head upgrade on an emulator, the shrunk builds started on
   an emulator). Locally:

   ```bash
   git status --short --branch
   ./gradlew verifyFull
   scripts/check-docs.sh --strict
   scripts/ship-check.sh --no-device     # stages 1-3; with an emulator and ANDROID_SERIAL set, all six
   ```

3. Visual pass, once per release: the full appearance matrix of the
   screenshot tests and the instrumentation suite on an emulator, including
   the upgrade from the previous release's data directory
   (`ANDROID_SERIAL=emulator-<port> scripts/verify.sh ui all`). Any failure
   that is not a known stale one blocks the release.

## 2. MyItmoApi to Maven Central

Only when the release needs a MyItmoApi version that is not on Central yet.

1. Set the release version in the build file and `M/CHANGELOG.md`, run the
   tests, commit `Release <version>`.
2. Push `master` and the tag; the tag runs the release workflow, which deploys
   to Central and creates the GitHub release.
3. If the deployment waits as `VALIDATED` on central.sonatype.com, press
   «Publish».
4. Verify; Central may lag about 30 minutes:

   ```bash
   curl -s https://repo1.maven.org/maven2/dev/alllexey/my-itmo-api-kmp/<version>/ | head -3
   gh release view <version> --repo alllexey-dev/my-itmo-api
   ```

5. Bump `myItmoApiKmp` in `gradle/libs.versions.toml`; start the next
   snapshot version in MyItmoApi.

Core 2.0 lives in this repository (`:shared:backend-client`) and is not
published; Core 1.x stays at 1.7.0.

## 3. Backend on production

1. Set the release version in the build file and `B/CHANGELOG.md`; run its
   Testcontainers build, dependencies from Maven Central only.
2. Commit `Release <version>` on `dev` and push: `deliver` builds the image,
   deploys development and fast-forwards `master`.
3. On development: `version-info`, logs without `ERROR`, the sport catalog
   refresh and a people lookup from a debug build; let it soak.
4. Before production: `origin/master` is the release commit, the backup
   volume has room for the dump, `platform doctor` is clean.
5. Run the release workflow and approve the `production` environment in the
   run:

   ```bash
   gh workflow run release.yml --repo alllexey-dev/itmo-widgets-backend --ref master -f version=<version>
   ```

   It re-tags the tested image, dumps the database, deploys, waits for
   `version-info`, switches back on failure, then tags and creates the GitHub
   release.
6. Verify:

   ```bash
   ssh alllexey.dev platform history itmowidgets
   ssh alllexey.dev platform logs itmowidgets backend | grep -E "Successfully applied|ERROR"
   curl -s https://widgets.alllexey.dev/api/app/version-info
   ```

7. Record the deployment in `B/docs/ops/deployments.md`.
8. Rollback is image only: `ssh alllexey.dev platform rollback itmowidgets`.
   Restoring the dump loses writes after the deploy and is a separate
   decision.

## 4. Web on production

1. Deploy the Web commit to development, then to production, as its README
   describes.
2. Verify every public path answers 200 and the App Links file is served as
   `application/json`:

   ```bash
   for p in / /privacy.html /delete-account /app/ /u/1 /sport/1 /.well-known/assetlinks.json; do
     echo "$p $(curl -s -o /dev/null -w '%{http_code}' https://widgets.alllexey.dev$p)"; done
   ```

3. Record the deploy in the server repository's change log.

## 5. Android build

1. Release commit content: `versionName` and `versionCode` in
   `app/build.gradle.kts`, the dependency versions in
   `gradle/libs.versions.toml`, the `Unreleased` section of `CHANGELOG.md`
   renamed to the version and date, the release row in
   [releases](product/releases.md).
2. Build and check:

   ```bash
   ./gradlew clean :app:assembleGithubRelease :app:bundlePlayRelease
   scripts/check-play-policy.sh
   ```

3. Sign with the release key (`keystore.properties`, never committed). Verify
   the APK:

   ```bash
   APK=app/build/outputs/apk/github/release/app-github-release.apk
   "$(ls -d $HOME/Library/Android/sdk/build-tools/*/apksigner | tail -1)" verify --print-certs "$APK" | grep SHA-256
   "$(ls -d $HOME/Library/Android/sdk/build-tools/*/aapt2 | tail -1)" dump badging "$APK" | head -1
   ```

   The certificate is `939e9bd5…f071`, the package `dev.alllexey.itmowidgets`
   with the planned version code and name.
4. Manual pass on a phone over the previous release (release-signed 2.2 ->
   signed candidate), against production: sign-in kept, widgets, sport, the
   ITMO.Widgets connection, a subject page with links, a teacher profile with
   reviews, the background check switches, calendar sync, the account and
   privacy rows, web sign-in, a shared `/u/<isu>` link opening the app, the
   demo session.
5. Commit `Release <version>` and push `master`.

## 6. GitHub release

```bash
cp app/build/outputs/apk/github/release/app-github-release.apk /tmp/itmo-widgets-v<version>.apk
gh release create v<version> /tmp/itmo-widgets-v<version>.apk --repo alllexey-dev/ITMO.Widgets \
  --target master --title "<version>" --notes-file <notes>
curl -sIL https://github.com/alllexey-dev/ITMO.Widgets/releases/latest | grep -i '^location' | tail -1
```

A beta gets `--prerelease`, so `releases/latest` and the update offer of
every `github` install stay on the last release.

## 7. Advertised version

Set `app.latest` (and `app.note`; `app.minimum` only as a separate decision)
in the web admin «Система»; these rows win over the server environment.
Verify `version-info` and the update offer: a phone on the previous release
with the connection on offers the new version at most once a day, and
«Обновить» opens the right page.

## 8. Google Play

Upload `app-play-release.aab` from the same commit to the chosen track in the
Play Console; the upload consumes its `versionCode`. Production needs the
closed-test condition in [releases](product/releases.md#distribution).

## 9. iOS

Upload the CI-built archive to TestFlight, internal first, then external
testing or App Store review. The marketing version follows the app; build
numbers come from CI.

## 10. After the release

- Next development line: `versionName` `<next>-SNAPSHOT` and its development
  `versionCode`, a new `Unreleased` section in `CHANGELOG.md`, the development
  line in [releases](product/releases.md).
- A maintenance branch `release/<major.minor>` at the tag, if the line needs
  one.
- Release notes and the announcement post.
- The Google Play button in the README and on the site only after the listing
  is public.

## Defaults

| Decision | Default |
|---|---|
| Backend production release | at least a week before the release candidate |
| `app.minimum` | unchanged; never above the oldest version the release line promises |
| GitHub release or prerelease | prerelease for betas |
| Play track | closed test until the production condition holds |
| Production switches (AI summaries, Reviews sync, first admin, ISU cookie) | unchanged by a release |
