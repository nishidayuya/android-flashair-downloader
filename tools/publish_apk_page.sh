#!/bin/bash

# Publishes the APKs built from one commit to the GitHub Pages branch, so that
# they can be installed straight from a phone browser.
#
#   PAGES_BASE_URL=https://nishidayuya.github.io/android-flashair-downloader \
#     tools/publish_apk_page.sh app-debug.apk app-release.apk
#
# Each commit gets its own directory with the APKs, a page listing them and a QR
# code pointing at that page. The branch is rewritten as a single commit every
# time: APKs never change and are never worth a history, and keeping one would
# grow the repository by a few megabytes per build forever. Only the newest
# KEEP_BUILDS commits are kept.
#
# Environment:
#   PAGES_BASE_URL   where the branch is served from (required)
#   COMMIT_SHA       the commit being published (default: HEAD)
#   COMMIT_SUBJECT   its subject line, shown on the index (default: from git)
#   PUBLISH_REMOTE   where to push (default: the origin of this checkout)
#   PUBLISH_BRANCH   which branch to push to (default: gh-pages)
#   KEEP_BUILDS      how many commits to keep (default: 20)
#
# Writes page_url to $GITHUB_OUTPUT when running under GitHub Actions.

set -eu

: "${PAGES_BASE_URL:?PAGES_BASE_URL is required}"
COMMIT_SHA="${COMMIT_SHA:-$(git rev-parse HEAD)}"
COMMIT_SUBJECT="${COMMIT_SUBJECT:-$(git log -1 --format=%s "$COMMIT_SHA")}"
PUBLISH_REMOTE="${PUBLISH_REMOTE:-$(git remote get-url origin)}"
PUBLISH_BRANCH="${PUBLISH_BRANCH:-gh-pages}"
KEEP_BUILDS="${KEEP_BUILDS:-20}"

if [ $# -eq 0 ]; then
  echo "usage: $0 <apk>..." >&2
  exit 1
fi
for apk in "$@"; do
  [ -f "$apk" ] || { echo "no such file: $apk" >&2; exit 1; }
done
command -v qrencode > /dev/null || { echo "qrencode is not installed" >&2; exit 1; }

base_url="${PAGES_BASE_URL%/}"
page_url="$base_url/apk/$COMMIT_SHA/"
site="$(mktemp -d)"
trap 'rm -rf "$site"' EXIT

escape_html() {
  sed -e 's/&/\&amp;/g' -e 's/</\&lt;/g' -e 's/>/\&gt;/g'
}

human_size() {
  awk -v bytes="$(stat -c %s "$1")" 'BEGIN { printf "%.1f MB", bytes / 1024 / 1024 }'
}

echo "==> fetching the current $PUBLISH_BRANCH"
if git fetch --depth 1 "$PUBLISH_REMOTE" "$PUBLISH_BRANCH" 2> /dev/null; then
  git archive FETCH_HEAD | tar -x -C "$site"
else
  echo "    (there is none yet: starting an empty site)"
fi

echo "==> adding the build of $COMMIT_SHA"
build_dir="$site/apk/$COMMIT_SHA"
rm -rf "$build_dir"
mkdir -p "$build_dir"
for apk in "$@"; do
  cp "$apk" "$build_dir/"
done
qrencode -o "$build_dir/qr.png" -s 6 -m 2 "$page_url"

subject_html="$(printf '%s' "$COMMIT_SUBJECT" | escape_html)"
short_sha="${COMMIT_SHA:0:7}"
built_at="$(date -u '+%Y-%m-%d %H:%M UTC')"

{
  cat <<EOF
<!doctype html>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>FlashAir Downloader $short_sha</title>
<h1>FlashAir Downloader</h1>
<p>Commit <code>$COMMIT_SHA</code><br>$subject_html<br>Built $built_at</p>
<ul>
EOF
  for apk in "$@"; do
    name="$(basename "$apk")"
    printf '<li><a href="%s">%s</a> (%s)</li>\n' "$name" "$name" "$(human_size "$apk")"
  done
  cat <<EOF
</ul>
<p><img src="qr.png" alt="QR code for this page"></p>
<p>The debug build has the application id suffix <code>.debug</code>, so it
installs next to the release build rather than over it. It is signed with a key
generated on the build machine, which differs from run to run: uninstall the
previous debug build before installing a newer one.</p>
<p><a href="../../">All builds</a></p>
EOF
} > "$build_dir/index.html"

echo "==> updating the index"
manifest="$site/builds.tsv"
touch "$manifest"
kept="$(mktemp)"
{
  printf '%s\t%s\t%s\n' "$(date -u +%s)" "$COMMIT_SHA" "$COMMIT_SUBJECT" | tr -d '\r'
  cat "$manifest"
} | awk -F'\t' 'NF == 3 && !seen[$2]++' | sort -t"$(printf '\t')" -k1,1nr | head -n "$KEEP_BUILDS" > "$kept"
mv "$kept" "$manifest"

# Anything that fell off the end of the list goes with it.
for directory in "$site"/apk/*/; do
  [ -d "$directory" ] || continue
  sha="$(basename "$directory")"
  cut -f2 "$manifest" | grep -qx "$sha" || rm -rf "$directory"
done

{
  cat <<EOF
<!doctype html>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>FlashAir Downloader builds</title>
<h1>FlashAir Downloader builds</h1>
<p>The last $KEEP_BUILDS builds, newest first.</p>
<ul>
EOF
  while IFS="$(printf '\t')" read -r epoch sha subject; do
    printf '<li><a href="apk/%s/">%s</a> &mdash; %s &mdash; %s</li>\n' \
      "$sha" "${sha:0:7}" "$(date -u -d "@$epoch" '+%Y-%m-%d %H:%M UTC')" \
      "$(printf '%s' "$subject" | escape_html)"
  done < "$manifest"
  echo "</ul>"
} > "$site/index.html"

echo "==> pushing to $PUBLISH_BRANCH"
(
  cd "$site"
  git init -q -b "$PUBLISH_BRANCH"
  git add -A
  git -c user.name="github-actions[bot]" \
      -c user.email="41898282+github-actions[bot]@users.noreply.github.com" \
      commit -q -m "Publish the APKs of $short_sha"
  git push -q --force "$PUBLISH_REMOTE" "$PUBLISH_BRANCH"
)

echo "==> $page_url"
if [ -n "${GITHUB_OUTPUT:-}" ]; then
  echo "page_url=$page_url" >> "$GITHUB_OUTPUT"
fi
