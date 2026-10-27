#!/usr/bin/env bash
# Called only after the same workflow's build/test job succeeds on a main push.
set -euo pipefail

: "${GITHUB_SHA:?Missing commit SHA}"
: "${GITHUB_REPOSITORY:?Missing repository}"
artifact_root=${1:?Usage: publish-release.sh ARTIFACT_DIRECTORY}
version=$(sed -n 's/^mod_version=//p' gradle.properties | tr -d '\r')
[[ "$version" =~ ^[0-9A-Za-z.+_-]+$ ]] || { echo 'Invalid mod_version' >&2; exit 1; }
[[ "$GITHUB_SHA" =~ ^[0-9a-f]{40}$ ]] || { echo 'Invalid commit SHA' >&2; exit 1; }
[[ "$(git rev-parse HEAD)" == "$GITHUB_SHA" ]] || { echo 'Checkout differs from the tested commit' >&2; exit 1; }

script_dir=$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
mapfile -t mc_versions < <(python3 "$script_dir/build-targets.py" --lines)
[[ ${#mc_versions[@]} -gt 0 ]] || { echo 'No supported Minecraft targets' >&2; exit 1; }
jars=()
for minecraft in "${mc_versions[@]}"; do
  mapfile -d '' matches < <(find "$artifact_root" -type f -name "craftlocator-${version}-mc${minecraft}.jar" -print0)
  [[ ${#matches[@]} -eq 1 ]] || { echo "Expected exactly one tested Minecraft $minecraft JAR" >&2; exit 1; }
  jars+=("${matches[0]}")
done

tag="main-${GITHUB_SHA}"
short_sha=${GITHUB_SHA:0:7}
repo_url="${GITHUB_SERVER_URL:-https://github.com}/${GITHUB_REPOSITORY}"
work_dir=$(mktemp -d "${RUNNER_TEMP:-${TMPDIR:-/tmp}}/craftlocator-release.XXXXXX")
trap 'rm -rf -- "$work_dir"' EXIT

exists=false
if gh api "repos/${GITHUB_REPOSITORY}/releases/tags/${tag}" > "$work_dir/existing.json" 2> "$work_dir/lookup.err"; then
  exists=true
  draft=$(python3 -c 'import json,sys; print(str(json.load(open(sys.argv[1]))["draft"]).lower())' "$work_dir/existing.json")
  if [[ "$draft" == false ]]; then
    python3 -c 'import json,sys; print("Release already published: " + json.load(open(sys.argv[1]))["html_url"])' "$work_dir/existing.json"
    exit 0
  fi
  [[ "$draft" == true ]] || { echo 'Unexpected release state' >&2; exit 1; }
elif ! grep -q 'HTTP 404' "$work_dir/lookup.err"; then
  cat "$work_dir/lookup.err" >&2
  exit 1
fi

# Only ancestor release tags define the change range, even if newer builds finish first.
previous=$(git describe --tags --match 'main-*' --abbrev=0 "${GITHUB_SHA}^" 2>/dev/null || true)
range=$GITHUB_SHA
if [[ -n "$previous" ]]; then range="${previous}..${GITHUB_SHA}"; fi
{
  printf 'Automated release of CraftLocator **%s** from [`%s`](%s/commit/%s).\n\n' "$version" "$short_sha" "$repo_url" "$GITHUB_SHA"
  printf '## Changes\n\n'
  git log --reverse --no-merges --format='- %s (%h)' "$range"
  printf '\n## Downloads\n\nEach attached JAR passed compilation, unit tests and NeoForge GameTests on its labelled Minecraft version. Each `.sha256` file verifies the corresponding download. KotlinLangForge remains a separate runtime dependency.\n'
  for minecraft in "${mc_versions[@]}"; do
    printf '\n- Minecraft **%s**: `craftlocator-%s-mc%s.jar`\n' "$minecraft" "$version" "$minecraft"
  done
  if [[ -n "$previous" ]]; then
    printf '\n[Full changes](%s/compare/%s...%s)\n' "$repo_url" "$previous" "$tag"
  fi
} > "$work_dir/notes.md"

assets=()
for jar in "${jars[@]}"; do
  jar_name=$(basename -- "$jar")
  cp -- "$jar" "$work_dir/$jar_name"
  (cd "$work_dir" && sha256sum -- "$jar_name" > "$jar_name.sha256")
  assets+=("$work_dir/$jar_name" "$work_dir/$jar_name.sha256")
done

# Keep partial uploads unpublished. A rerun resumes a draft, or skips a published release.
if [[ "$exists" == false ]]; then
  gh release create "$tag" --repo "$GITHUB_REPOSITORY" --target "$GITHUB_SHA" \
    --title "CraftLocator ${version} (${short_sha})" --notes-file "$work_dir/notes.md" --draft
fi
gh release upload "$tag" "${assets[@]}" \
  --repo "$GITHUB_REPOSITORY" --clobber
gh release edit "$tag" --repo "$GITHUB_REPOSITORY" --draft=false
gh release view "$tag" --repo "$GITHUB_REPOSITORY" --json url --jq .url
