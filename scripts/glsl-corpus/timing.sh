#!/usr/bin/env bash
# Times the shader transforms of one pack load on one engine (docs/glsl-transformer_adoption/reports/S7b-hardening.md):
# runs the pack's corpus script (scripts/glsl-corpus/<name>.txt) with -Ddemonica.glsmPerfDebug=true and the given
# engine, without the corpus recorder, logs to run/timing-<name>-<engine>-<tag>.out and prints the per-transform
# summary of scripts/glsl-corpus/transform-times.py.
#
#   scripts/glsl-corpus/timing.sh bsl|complementary|vanilla taumc|douira <tag> [more Gradle arguments]
#
# Compare engines with runs of the same session, one client at a time, and no other Gradle build while one runs.
set -euo pipefail
name=${1:?usage: timing.sh bsl|complementary|vanilla taumc|douira <tag> [gradle args]}
engine=${2:?usage: timing.sh <name> taumc|douira <tag> [gradle args]}
tag=${3:?usage: timing.sh <name> <engine> <tag> [gradle args]}
shift 3
root=$(cd "$(dirname "$0")/../.." && pwd)
script=$root/scripts/glsl-corpus/$name.txt
[ -f "$script" ] || { echo "no script $script" >&2; exit 2; }
case "$engine" in
    taumc|douira) ;;
    *) echo "unknown engine '$engine' (taumc or douira)" >&2; exit 2 ;;
esac
log=run/timing-$name-$engine-$tag.out
cd "$root"
status=0
./gradlew runClient "-PdevScript=@$script" "-PdevProps=demonica.glsmPerfDebug=true,demonica.glsl.engine=$engine" "$@" \
    > "$log" 2>&1 || status=$?
rm -rf "$root/run/client/saves/corpus"
echo "timing $name ($engine, $tag): exit $status, log $log"
python3 "$root/scripts/glsl-corpus/transform-times.py" "$log"
exit $status
