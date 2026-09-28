#!/usr/bin/env bash
# Records one transform corpus for the glsl-transformer migration (docs/glsl-transformer_adoption/ADOPTION_PLAN.md,
# Step 2) into run/transform-corpus/<name>/, replacing an earlier recording, with the client's log in
# run/corpus-<name>.out. The frames land in run/client/screenshots/corpus-<name>-*.png.
#
#   scripts/glsl-corpus/capture.sh bsl|complementary|vanilla|compat [more Gradle arguments]
#   GLSL_ENGINE=douira scripts/glsl-corpus/capture.sh bsl|complementary|vanilla|compat [more Gradle arguments]
#
# GLSL_ENGINE (Step 6) selects the transform engine (demonica.glsl.engine; default taumc). Another engine than taumc
# records into run/transform-corpus-<engine>/<name>/ with its log in run/corpus-<name>-<engine>.out, so the TauMC
# corpus the replay compares against is never replaced; its frames are copied to run/engine-screenshots/<engine>/.
#
# One client at a time, and no other Gradle build while it runs. The corpus is third-party shader code: it stays
# under run/, which git ignores.
set -euo pipefail
name=${1:?usage: [GLSL_ENGINE=taumc|douira] capture.sh bsl|complementary|vanilla|compat [gradle args]}
shift
root=$(cd "$(dirname "$0")/../.." && pwd)
script=$root/scripts/glsl-corpus/$name.txt
[ -f "$script" ] || { echo "no script $script" >&2; exit 2; }
engine=${GLSL_ENGINE:-taumc}
case "$engine" in
    taumc) corpus=$root/run/transform-corpus/$name; log=run/corpus-$name.out ;;
    douira) corpus=$root/run/transform-corpus-$engine/$name; log=run/corpus-$name-$engine.out ;;
    *) echo "unknown GLSL_ENGINE '$engine' (taumc or douira)" >&2; exit 2 ;;
esac
rm -rf "$corpus"
props="demonica.glsl.corpus=$corpus,demonica.glsmPerfDebug=true"
if [ "$engine" != taumc ]; then
    props="$props,demonica.glsl.engine=$engine"
fi
extra=()
if [ "$name" = compat ]; then
    extra+=(-PwithCompatMods)
    props="$props,angelica.dumpShaders=true"
    rm -rf "$root/run/client/compat_shaders"
fi
cd "$root"
status=0
./gradlew runClient "-PdevScript=@$script" "-PdevProps=$props" "${extra[@]}" "$@" > "$log" 2>&1 || status=$?
rm -rf "$root/run/client/saves/corpus"
if [ "$engine" != taumc ]; then
    mkdir -p "$root/run/engine-screenshots/$engine"
    cp "$root"/run/client/screenshots/corpus-"$name"-*.png "$root/run/engine-screenshots/$engine/" 2>/dev/null || true
fi
echo "capture $name ($engine): exit $status, $(find "$corpus" -name case.properties 2>/dev/null | wc -l) cases in $corpus, log $log"
exit $status
