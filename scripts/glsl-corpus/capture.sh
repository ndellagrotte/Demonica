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
# GLSL_CORPUS_ROOT (Step 7) records into <root>/<name>/ instead (a path under the repository, or absolute), logs to
# run/corpus-<name>-<basename of root>.out and copies the frames to run/engine-screenshots/<basename of root>/; the
# default roots stay untouched. Example, the Distant Horizons programs on the TauMC engine:
#   GLSL_CORPUS_ROOT=run/transform-corpus-dh scripts/glsl-corpus/capture.sh complementary -PwithCompatMods
#
# The frames are the script's `shot <name>` steps. Only frames written during this run count (Step 7b): a frame that is
# missing or older than the run's start (a run that stopped before its `shot` step leaves the previous run's file) is
# named on stderr, nothing stale is copied, and the script exits 3 if the client itself exited 0.
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
frames=
if [ "$engine" != taumc ]; then
    frames=$root/run/engine-screenshots/$engine
fi
if [ -n "${GLSL_CORPUS_ROOT:-}" ]; then
    case "$GLSL_CORPUS_ROOT" in
        /*) corpus_root=$GLSL_CORPUS_ROOT ;;
        *) corpus_root=$root/$GLSL_CORPUS_ROOT ;;
    esac
    corpus_root=${corpus_root%/}
    tag=$(basename "$corpus_root")
    case "$tag" in
        transform-corpus|transform-corpus-taumc|transform-corpus-douira)
            echo "GLSL_CORPUS_ROOT must not be a default corpus root ($tag)" >&2; exit 2 ;;
    esac
    corpus=$corpus_root/$name
    log=run/corpus-$name-$tag.out
    frames=$root/run/engine-screenshots/$tag
fi
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
screenshots=$root/run/client/screenshots
# The frames this run must write: the script's shot steps.
mapfile -t shots < <(sed -n 's/^[[:space:]]*shot[[:space:]]\+\([^[:space:]]\+\).*/\1/p' "$script")
# A marker older than anything the client writes: a frame counts only if it is newer.
started=$(mktemp "${TMPDIR:-/tmp}/capture-$name.XXXXXX")
trap 'rm -f "$started"' EXIT
sleep 1
status=0
./gradlew runClient "-PdevScript=@$script" "-PdevProps=$props" "${extra[@]}" "$@" > "$log" 2>&1 || status=$?
rm -rf "$root/run/client/saves/corpus"
missing=()
fresh=()
for shot in "${shots[@]}"; do
    frame=$screenshots/$shot.png
    if [ -f "$frame" ] && [ "$frame" -nt "$started" ]; then
        fresh+=("$frame")
    else
        missing+=("$shot")
    fi
done
if [ -n "$frames" ] && [ ${#fresh[@]} -gt 0 ]; then
    mkdir -p "$frames"
    cp "${fresh[@]}" "$frames/"
fi
echo "capture $name ($engine): exit $status, $(find "$corpus" -name case.properties 2>/dev/null | wc -l) cases in $corpus, log $log, frames ${#fresh[@]} of ${#shots[@]}"
if [ ${#missing[@]} -gt 0 ]; then
    echo "capture $name: MISSING frames (not written by this run): ${missing[*]}" >&2
    if [ "$status" -eq 0 ]; then
        status=3
    fi
fi
exit $status
