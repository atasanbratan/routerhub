#!/usr/bin/env bash
# Writes the notes of a GitHub release to stdout, in the shape of Mihon's releases: the version's
# section of CHANGELOG.md, then a checksum table for the APKs and the signing certificate.
#
#   release-notes.sh <version> <signer sha-256> <apk>...
set -euo pipefail

version="$1"
signer="$2"
shift 2

# Everything between "## [<version>]" and the next "## [" heading, without the heading itself.
notes="$(awk -v v="$version" '
  /^## \[/ { inside = (index($0, "## [" v "]") == 1); next }
  /^\[[^]]+\]: / { next }
  inside
' CHANGELOG.md | sed -e '/./,$!d')"

if [ -z "$(printf '%s' "$notes" | tr -d '[:space:]')" ]; then
  echo "CHANGELOG.md has no entries under '## [$version]'. Move the Unreleased entries there before tagging." >&2
  exit 1
fi

previous="$(git describe --tags --abbrev=0 "v$version^" 2>/dev/null || true)"

printf '%s\n\n' "$notes"
if [ -n "$previous" ]; then
  printf '**Full changelog:** https://github.com/%s/compare/%s...v%s\n\n' "$GITHUB_REPOSITORY" "$previous" "$version"
fi
printf -- '---\n\n### Checksums\n\n| File | SHA-256 |\n| --- | --- |\n'
for apk in "$@"; do
  printf '| %s | `%s` |\n' "$(basename "$apk")" "$(sha256sum "$apk" | cut -d' ' -f1)"
done
printf '\nSigned with the certificate whose SHA-256 is `%s`.\n' "$signer"
printf 'Extensions are installed separately; see the [extension list](https://atasanbratan.github.io/routerhub/extensions/).\n'
