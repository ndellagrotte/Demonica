#!/usr/bin/env bash
# Records one transform corpus (docs/glsl-transformer_adoption/ADOPTION_PLAN.md, Step 2) on the transform engine
# (glsl-transformer, recorded as out.douira.*) into run/transform-corpus-douira/<name>/, replacing an earlier recording
# there, with the client's log in run/corpus-<name>-douira.out; the frames land in
# run/client/screenshots/corpus-<name>-*.png and are copied to run/engine-screenshots/douira/.
#
#   scripts/glsl-corpus/capture.sh bsl|complementary|vanilla|compat [more Gradle arguments]
#
# The reference corpora the replay compares against, run/transform-corpus/<name>/ and run/transform-corpus-dh/, hold
# TauMC's recorded outputs (out.taumc.*). TauMC's engine was removed in Step 11, so they can no longer be re-recorded:
# this script never writes into them (until Step 11 it recorded TauMC into run/transform-corpus/<name>/ by default,
# and GLSL_ENGINE chose the engine).
#
# GLSL_CORPUS_ROOT (Step 7) records into <root>/<name>/ instead (a path under the repository, or absolute), logs to
# run/corpus-<name>-<basename of root>.out and copies the frames to run/engine-screenshots/<basename of root>/; the
# reference roots are refused. Example, the Distant Horizons programs:
#   GLSL_CORPUS_ROOT=run/transform-corpus-dh-douira scripts/glsl-corpus/capture.sh complementary -PwithCompatMods
#
# The frames are the script's `shot <name>` steps. Only frames written during this run count (Step 7b): a frame that is
# missing or older than the run's start (a run that stopped before its `shot` step leaves the previous run's file) is
# named on stderr, nothing stale is copied (and its copy from an earlier run is removed from the frame directory), and
# the script exits 3 if the client itself exited 0. The summary line prints the script's exit status and the client's.
#
# One client at a time, and no other Gradle build while it runs. The corpus is third-party shader code: it stays
# under run/, which git ignores.
set -euo pipefail
name=${1:?usage: capture.sh bsl|complementary|vanilla|compat [gradle args]}
shift
root=$(cd "$(dirname "$0")/../.." && pwd)
script=$root/scripts/glsl-corpus/$name.txt
[ -f "$script" ] || { echo "no script $script" >&2; exit 2; }
if [ -n "${GLSL_ENGINE:-}" ] && [ "$GLSL_ENGINE" != douira ]; then
    echo "GLSL_ENGINE is gone since Step 11: the only engine is glsl-transformer (douira)" >&2; exit 2
fi
corpus=$root/run/transform-corpus-douira/$name
log=run/corpus-$name-douira.out
frames=$root/run/engine-screenshots/douira
if [ -n "${GLSL_CORPUS_ROOT:-}" ]; then
    case "$GLSL_CORPUS_ROOT" in
        /*) corpus_root=$GLSL_CORPUS_ROOT ;;
        *) corpus_root=$root/$GLSL_CORPUS_ROOT ;;
    esac
    corpus_root=${corpus_root%/}
    tag=$(basename "$corpus_root")
    case "$tag" in
        transform-corpus|transform-corpus-dh|transform-corpus-taumc|transform-corpus-douira)
            echo "GLSL_CORPUS_ROOT must not be a default or reference corpus root ($tag)" >&2; exit 2 ;;
    esac
    corpus=$corpus_root/$name
    log=run/corpus-$name-$tag.out
    frames=$root/run/engine-screenshots/$tag
fi
rm -rf "$corpus"
props="demonica.glsl.corpus=$corpus,demonica.glsmPerfDebug=true"
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
if [ -n "$frames" ]; then
    mkdir -p "$frames"
    # A missing shot's copy from an earlier run must not sit next to this run's frames.
    for shot in "${missing[@]}"; do
        rm -f "$frames/$shot.png"
    done
    if [ ${#fresh[@]} -gt 0 ]; then
        cp "${fresh[@]}" "$frames/"
    fi
fi
client=$status
if [ ${#missing[@]} -gt 0 ]; then
    echo "capture $name: MISSING frames (not written by this run): ${missing[*]}" >&2
    if [ "$status" -eq 0 ]; then
        status=3
    fi
fi
echo "capture $name: exit $status (client $client), $(find "$corpus" -name case.properties 2>/dev/null | wc -l) cases in $corpus, log $log, frames ${#fresh[@]} of ${#shots[@]}"
exit $status
