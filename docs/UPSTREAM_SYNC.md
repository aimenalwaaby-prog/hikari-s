# Reviewing upstream Hikari updates

Hikari S is a private, independent fork of [`codegeasse1/hikari`](https://github.com/codegeasse1/hikari). The original project remains the `upstream` remote; this repository is `origin`. Do not copy files over the fork or force-reset `main` to upstream.

## Current product identity

The fork-owned `hikari-s.properties` defines Hikari S's Android `applicationId`, update repository, version name, and version code. Keep it through upstream merges. `namespace = "com.hikari.app"` intentionally remains unchanged because extension integrations and compiled code depend on those packages. The installed app uses a distinct package so it can coexist with upstream Hikari.

`app/build.gradle.kts` reads the identity file and generates the self-updater's `UPDATE_REPO`. For CI release builds, `APP_VERSION`, `APP_VERSION_CODE`, and `UPDATE_REPO` may override the file; they must be set consistently with the release tag and repo.

## Review workflow

1. Start with a clean worktree and fetch the original project:

   ```sh
   git status --short
   ./scripts/sync-upstream.sh
   ```

2. Review the generated `review-upstream-*` branch. Inspect every conflict and diff before accepting it. Do not resolve conflicts by taking all of upstream or all of Hikari S.
3. Preserve fork identity and product behavior: `hikari-s.properties`, application-label/icon changes, updater repository/assets, Hikari S release workflow, and fork-specific manga work.
4. Run the Android debug build and any available tests. The current environment may not include Android SDK; in that case use the repository's GitHub Actions build and do not claim a local APK was verified.
5. Commit any conflict resolution and push the review branch. Merge it into `main` only after the review/build is green. The helper script never pushes or changes `main`.

## Conflict areas to inspect carefully

- `app/build.gradle.kts`: upstream may change dependencies, ABI packaging, version metadata or BuildConfig fields. Keep the `hikari-s.properties` loader, Hikari S application ID, app version and `UPDATE_REPO` wiring while incorporating upstream build fixes.
- `app/src/main/AndroidManifest.xml`, `res/values*/strings.xml`, and launcher resources: retain Hikari S's app label and icon; preserve all existing activity aliases and permissions.
- `app/src/main/java/com/hikari/app/net/Updater.kt` and Settings links: Hikari S must never silently offer an upstream Hikari APK.
- Manga readers, page sources and caches: merge upstream bug fixes, but keep durable offline files separate from the reader's evictable page cache.

## Manual recovery

If a merge conflicts, the helper stops and leaves Git's normal conflict state in place. Resolve files individually, run `git diff --check`, build/test, then complete the merge. To abandon only the uncommitted review branch, use Git's normal `git merge --abort`; do not reset or delete `main`.
