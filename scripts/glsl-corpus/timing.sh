#!/usr/bin/env bash
# Times the shader transforms of one pack load (docs/glsl-transformer_adoption/reports/S7b-hardening.md): runs the
# pack's corpus script (scripts/glsl-corpus/<name>.txt) with -Ddemonica.glsmPerfDebug=true, without the corpus
# recorder, logs to run/timing-<name>-douira-<tag>.out and prints the per-transform summary of
# scripts/glsl-corpus/transform-times.py. Until Step 11 a second argument chose the engine (taumc or douira); the
# log name keeps the engine for comparison with those runs.
#
#   scripts/glsl-corpus/timing.sh bsl|complementary|vanilla <tag> [more Gradle arguments]
#
# One client at a time, and no other Gradle build while one runs.
set -euo pipefail
name=${1:?usage: timing.sh bsl|complementary|vanilla <tag> [gradle args]}
tag=${2:?usage: timing.sh <name> <tag> [gradle args]}
shift 2
engine=douira
root=$(cd "$(dirname "$0")/../.." && pwd)
script=$root/scripts/glsl-corpus/$name.txt
[ -f "$script" ] || { echo "no script $script" >&2; exit 2; }
log=run/timing-$name-$engine-$tag.out
cd "$root"
status=0
./gradlew runClient "-PdevScript=@$script" "-PdevProps=demonica.glsmPerfDebug=true" "$@" \
    > "$log" 2>&1 || status=$?
rm -rf "$root/run/client/saves/corpus"
echo "timing $name ($engine, $tag): exit $status, log $log"
python3 "$root/scripts/glsl-corpus/transform-times.py" "$log"
exit $status
