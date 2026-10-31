#!/usr/bin/env bash
set -euo pipefail

repo_root="$(git rev-parse --show-toplevel 2>/dev/null)" || {
  echo "Run this script from inside the Hikari S Git repository." >&2
  exit 2
}
cd "$repo_root"

if [[ -n "$(git status --porcelain)" ]]; then
  echo "Refusing to sync with a dirty worktree. Commit or stash local changes first." >&2
  exit 2
fi

if ! git remote get-url upstream >/dev/null 2>&1; then
  echo "The 'upstream' remote is missing. Expected https://github.com/codegeasse1/hikari.git." >&2
  exit 2
fi

git fetch upstream main
branch="review-upstream-$(date -u +%Y%m%d-%H%M%S)"
git switch -c "$branch" origin/main

if git merge --no-ff --no-edit upstream/main; then
  git diff --check
  echo
  echo "Upstream merge is committed on $branch. Review it and run the Android build before merging to main."
  echo "No changes were pushed. See docs/UPSTREAM_SYNC.md."
else
  echo
  echo "Merge conflicts remain on $branch. Resolve each deliberately; do not overwrite the Hikari S identity or update channel."
  echo "No changes were pushed. See docs/UPSTREAM_SYNC.md."
  exit 1
fi

echo
echo "Suggested verification: gradle :app:assembleDebug --no-daemon"
