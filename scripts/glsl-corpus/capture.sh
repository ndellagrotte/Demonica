#!/usr/bin/env bash
# Records one transform corpus for the glsl-transformer migration (docs/glsl-transformer_adoption/ADOPTION_PLAN.md,
# Step 2) into run/transform-corpus/<name>/, replacing an earlier recording, with the client's log in
# run/corpus-<name>.out. The frames land in run/client/screenshots/corpus-<name>-*.png.
#
#   scripts/glsl-corpus/capture.sh bsl|complementary|vanilla|compat [more Gradle arguments]
#
# One client at a time, and no other Gradle build while it runs. The corpus is third-party shader code: it stays
# under run/, which git ignores.
set -euo pipefail
name=${1:?usage: capture.sh bsl|complementary|vanilla|compat [gradle args]}
shift
root=$(cd "$(dirname "$0")/../.." && pwd)
script=$root/scripts/glsl-corpus/$name.txt
[ -f "$script" ] || { echo "no script $script" >&2; exit 2; }
corpus=$root/run/transform-corpus/$name
rm -rf "$corpus"
props="demonica.glsl.corpus=$corpus,demonica.glsmPerfDebug=true"
extra=()
if [ "$name" = compat ]; then
    extra+=(-PwithCompatMods)
    props="$props,angelica.dumpShaders=true"
    rm -rf "$root/run/client/compat_shaders"
fi
cd "$root"
status=0
./gradlew runClient "-PdevScript=@$script" "-PdevProps=$props" "${extra[@]}" "$@" > "run/corpus-$name.out" 2>&1 || status=$?
rm -rf "$root/run/client/saves/corpus"
echo "capture $name: exit $status, $(find "$corpus" -name case.properties 2>/dev/null | wc -l) cases in $corpus"
exit $status
