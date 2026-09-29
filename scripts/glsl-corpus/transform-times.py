#!/usr/bin/env python3
"""Summarizes the shader transform times of dev-client logs (docs/glsl-transformer_adoption/reports/S7b-hardening.md).

    scripts/glsl-corpus/transform-times.py run/timing-bsl-douira-1.out [more logs]

Reads the "[ShaderTransformCache] ... miss transformMs=N" lines that -Ddemonica.glsmPerfDebug=true logs for each cache
miss (the transform on its Shader-Transform thread, from the call to the result) and prints, per log: the engine the
log names, the number of transforms, the median, the 90th percentile and the sum in milliseconds, and the median of the
first 30 transforms against the rest (warm-up). For the glsl-transformer engine it also sums the
"[AstShaderTransformer] ... timing" lines: the ANTLR parses, the AST builds, and the waits for and holds of
ShaderAst.BUILD_LOCK.
"""
import re
import statistics
import sys

MISS = re.compile(r"\[ShaderTransformCache\] \w+ miss transformMs=([0-9.]+)")
ENGINE = re.compile(r"GLSL transform engine: (\w+)")
TIMING = re.compile(r"\[AstShaderTransformer\] \w+ timing totalMs=([0-9.]+) parseMs=([0-9.]+) buildMs=([0-9.]+) "
                    r"lockWaitMs=([0-9.]+) lockHeldMs=([0-9.]+) locks=(\d+) contended=(\d+)")


def percentile(values, fraction):
    ordered = sorted(values)
    position = (len(ordered) - 1) * fraction
    low = int(position)
    if low + 1 >= len(ordered):
        return ordered[low]
    return ordered[low] + (ordered[low + 1] - ordered[low]) * (position - low)


def summarize(path):
    with open(path, errors="replace") as log:
        text = log.read()
    times = [float(value) for value in MISS.findall(text)]
    engine = ENGINE.search(text)
    engine = engine.group(1) if engine else "?"
    if not times:
        print(f"{path}: engine={engine} no transformMs lines")
        return
    first, rest = times[:30], times[30:]
    line = (f"{path}: engine={engine} transforms={len(times)} medianMs={statistics.median(times):.1f} "
            f"p90Ms={percentile(times, 0.9):.1f} sumMs={sum(times):.1f} first30MedianMs={statistics.median(first):.1f}")
    if rest:
        line += f" restMedianMs={statistics.median(rest):.1f}"
    print(line)
    rows = [tuple(float(v) for v in match) for match in TIMING.findall(text)]
    if rows:
        sums = [sum(row[i] for row in rows) for i in range(7)]
        print(f"    timing lines={len(rows)} totalMs={sums[0]:.1f} parseMs={sums[1]:.1f} buildMs={sums[2]:.1f} "
              f"lockWaitMs={sums[3]:.1f} lockHeldMs={sums[4]:.1f} locks={int(sums[5])} contended={int(sums[6])} "
              f"medianLockWaitMs={statistics.median(row[3] for row in rows):.2f}")


if __name__ == "__main__":
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    for argument in sys.argv[1:]:
        summarize(argument)
