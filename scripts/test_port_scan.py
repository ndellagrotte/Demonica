#!/usr/bin/env python3
"""Decide which of Actinium's tests Demonica can port verbatim.

A test under Actinium's src/test/java is ported only if:
  - its package is in scope (the Iris tree, glsm, or a net/minecraft stub);
  - every import resolves to the Iris, glsm or vendored roots, to another
    ported test, or to an allow-listed library on Demonica's test classpath;
  - it has no same-package or fully qualified reference to a host class
    (a class in Actinium's root project, which Demonica does not carry).

Tests that depend on a skipped test are skipped too. A test resource is
ported if a ported test names it.

The result must match the `tests` scope of scripts/provenance_audit.py for the
actinium layout; the scan exits 1 if it does not.

Examples:
  scripts/test_port_scan.py
  scripts/test_port_scan.py --write docs/provenance/actinium-fee5de38/tests.md
"""
import argparse
import re
import subprocess
import sys
from collections import defaultdict
from pathlib import Path

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).resolve().parent))
import provenance_audit as audit  # noqa: E402

ACTINIUM = "fee5de3834352050fc453bde1d93030c6d434983"
TEST_ROOT = "src/test/java"
TEST_RESOURCES = "src/test/resources"
ENGINE_ROOTS = {
    "shader/src/main/java": "iris",
    "glsm/src/main/java": "glsm",
    "src/lwjglCommon/java": "glsm",
    "src/lwjgl3/java": "glsm",
    "GTNHLib/src/main/java": "vendor",
    "celeritas-common/src/main/java": "vendor",
}
HOST_ROOT = "src/main/java"
IN_SCOPE = (
    "net/coderbot/", "kroppeb/", "net/irisshaders/",
    "com/gtnewhorizons/angelica/", "com/mitchej123/",
    "net/minecraft/",  # stubs of Minecraft classes
)
# Packages on Demonica's test classpath (JDK, JUnit, and the root compile classpath).
LIBRARIES = (
    "java.", "javax.", "sun.misc.", "org.junit.", "org.lwjgl.", "org.joml.",
    "it.unimi.dsi.fastutil.", "com.google.common.", "com.google.gson.",
    "org.objectweb.asm.", "org.antlr.v4.", "org.taumc.glsl.", "org.anarres.cpp.",
    "org.apache.logging.log4j.", "org.jetbrains.annotations.", "lombok.",
    "net.minecraft.", "net.minecraftforge.",
)

COMMENT_OR_LITERAL = re.compile(
    r'//[^\n]*|/\*.*?\*/|"""(?:\\.|[^\\])*?"""|"(?:\\.|[^"\\\n])*"|\'(?:\\.|[^\'\\\n])*\'',
    re.S)
PACKAGE = re.compile(r'^\s*package\s+([\w.]+)\s*;', re.M)
IMPORT = re.compile(r'^\s*import\s+(static\s+)?([\w.]+(?:\.\*)?)\s*;', re.M)
QUALIFIED = re.compile(r'(?<![\w.])((?:[a-z_$][\w$]*\.)+[A-Z][\w$]*)')
WORD = re.compile(r'[A-Za-z_$][\w$]*')


def git_lines(*cmd: str) -> list[str]:
    out = subprocess.run(["git", *cmd], capture_output=True, text=True, check=True).stdout
    return [e for e in out.split("\0") if e]


def list_blobs(ref: str, bases) -> dict[str, str]:
    files = {}
    for entry in git_lines("ls-tree", "-r", "-z", ref, "--", *bases):
        meta, path = entry.split("\t", 1)
        files[path] = meta.split()[2]
    return files


def read_blobs(shas: list[str]) -> dict[str, str]:
    proc = subprocess.run(["git", "cat-file", "--batch"], input=("\n".join(shas) + "\n").encode(),
                          capture_output=True, check=True)
    out, pos, blobs = proc.stdout, 0, {}
    for sha in shas:
        header_end = out.index(b"\n", pos)
        size = int(out[pos:header_end].split()[2])
        blobs[sha] = out[header_end + 1:header_end + 1 + size].decode("utf-8")
        pos = header_end + 1 + size + 1
    return blobs


def fqcn(rel: str) -> str:
    return rel.removesuffix(".java").replace("/", ".")


class ClassIndex:
    """Top-level classes by FQCN, each tagged iris/glsm/vendor/host/test."""

    def __init__(self):
        self.owner: dict[str, str] = {}
        self.packages: dict[str, set[str]] = defaultdict(set)

    def add(self, name: str, kind: str):
        if name in self.owner and self.owner[name] != kind:
            raise SystemExit(f"error: {name} is both {self.owner[name]} and {kind}")
        self.owner[name] = kind
        self.packages[name.rpartition(".")[0]].add(kind)

    def resolve(self, dotted: str) -> tuple[str, str] | None:
        """Longest dotted prefix naming an indexed class: (class, kind)."""
        parts = dotted.split(".")
        for n in range(len(parts), 0, -1):
            name = ".".join(parts[:n])
            if name in self.owner:
                return name, self.owner[name]
        return None


def library(name: str) -> bool:
    return any(name.startswith(p) for p in LIBRARIES)


def scan(ref: str):
    blobs = list_blobs(ref, [*ENGINE_ROOTS, HOST_ROOT, TEST_ROOT, TEST_RESOURCES])
    index = ClassIndex()
    tests: dict[str, str] = {}  # rel path -> blob sha
    resources: dict[str, str] = {}
    for path, sha in blobs.items():
        for base, kind in ENGINE_ROOTS.items():
            if path.startswith(base + "/"):
                break
        else:
            if path.startswith(TEST_ROOT + "/"):
                base, kind = TEST_ROOT, "test"
            elif path.startswith(TEST_RESOURCES + "/"):
                resources[path[len(TEST_RESOURCES) + 1:]] = sha
                continue
            elif path.startswith(HOST_ROOT + "/"):
                base, kind = HOST_ROOT, "host"
            else:
                continue
        rel = path[len(base) + 1:]
        if not rel.endswith(".java"):
            continue
        index.add(fqcn(rel), kind)
        if kind == "test":
            tests[rel] = sha

    sources = read_blobs(sorted(set(tests.values())))
    blockers: dict[str, list[str]] = {}
    edges: dict[str, set[str]] = defaultdict(set)
    by_class = {fqcn(rel): rel for rel in tests}

    for rel, sha in sorted(tests.items()):
        if not rel.startswith(IN_SCOPE):
            blockers[rel] = [f"out of scope: package `{fqcn(rel).rpartition('.')[0]}`"]
            continue
        raw = sources[sha]
        code = COMMENT_OR_LITERAL.sub(lambda m: " " if m.group(0)[0] in "\"'" else "", raw)
        pkg_match = PACKAGE.search(code)
        pkg = pkg_match.group(1) if pkg_match else ""
        reasons = []

        def note(target: str, kind: str, how: str):
            if kind == "host":
                reasons.append(f"{how} host class `{target}`")
            elif kind == "test" and by_class[target] != rel:
                edges[rel].add(by_class[target])

        for static, name in IMPORT.findall(code):
            wildcard = name.endswith(".*")
            stem = name.removesuffix(".*")
            hit = index.resolve(stem)
            if hit:
                note(hit[0], hit[1], "imports")
            elif wildcard and stem in index.packages:
                kinds = index.packages[stem]
                if kinds == {"host"}:
                    reasons.append(f"imports host package `{stem}.*`")
                for cls, kind in index.owner.items():
                    if kind == "test" and cls.rpartition(".")[0] == stem:
                        note(cls, kind, "imports")
            elif not library(stem):
                reasons.append(f"unresolved import `{name}` (not on Demonica's test classpath)")

        words = set(WORD.findall(code))
        for cls, kind in index.owner.items():
            if kind in ("host", "test") and cls.rpartition(".")[0] == pkg:
                if cls.rpartition(".")[2] in words:
                    note(cls, kind, "same-package reference to")

        body = IMPORT.sub("", code)
        for dotted in set(QUALIFIED.findall(body)):
            hit = index.resolve(dotted)
            if hit:
                note(hit[0], hit[1], "fully qualified reference to")

        if reasons:
            blockers[rel] = sorted(set(reasons))

    ported = {rel for rel in tests if rel not in blockers}
    changed = True
    while changed:
        changed = False
        for rel in sorted(ported):
            missing = sorted(d for d in edges[rel] if d not in ported)
            if missing:
                ported.discard(rel)
                blockers[rel] = [f"depends on skipped test `{fqcn(d)}`" for d in missing]
                changed = True

    ported_sources = [sources[tests[rel]] for rel in ported]
    ported_resources, skipped_resources = set(), {}
    for rel in resources:
        top = rel.split("/", 1)[0]
        if any(top in src or rel in src for src in ported_sources):
            ported_resources.add(rel)
        else:
            skipped_resources[rel] = ["not referenced by a ported test"]

    return tests, ported, blockers, resources, ported_resources, skipped_resources


def check_against_audit(ref, ported, ported_resources) -> list[str]:
    roots = audit.LAYOUTS["actinium"]["tests"]
    files = audit.list_files(ref, {r.base for r in roots})
    covered = set()
    for path in files:
        for root in roots:
            if root.key(path) is not None:
                covered.add(path)
    expected = {f"{TEST_ROOT}/{r}" for r in ported} | {f"{TEST_RESOURCES}/{r}" for r in ported_resources}
    problems = [f"scan ports but audit scope misses: {p}" for p in sorted(expected - covered)]
    problems += [f"audit scope covers but scan skips: {p}" for p in sorted(covered - expected)]
    return problems


def report(ref, tests, ported, blockers, resources, ported_resources, skipped_resources) -> str:
    skipped = {rel: why for rel, why in blockers.items()}
    lines = [
        "# Test port scan",
        "",
        f"- source: `actinium@{ref}` (`{TEST_ROOT}`, `{TEST_RESOURCES}`)",
        f"- generated by `scripts/test_port_scan.py`",
        "",
        "| | java | resources |",
        "|---|---|---|",
        f"| ported | {len(ported)} | {len(ported_resources)} |",
        f"| skipped | {len(skipped)} | {len(skipped_resources)} |",
        f"| total | {len(tests)} | {len(resources)} |",
        "",
        "Ported tests are byte-identical to Actinium; a test that cannot be ported",
        "verbatim is skipped, never edited.",
        "",
        "## Skipped",
        "",
    ]
    out_of_scope = defaultdict(int)
    for rel, why in sorted(skipped.items()):
        if why[0].startswith("out of scope"):
            out_of_scope["/".join(rel.split("/")[:2])] += 1
            continue
        lines.append(f"- `{rel}`")
        lines += [f"  - {w}" for w in why]
    for rel, why in sorted(skipped_resources.items()):
        lines.append(f"- `{TEST_RESOURCES}/{rel}`: {why[0]}")
    lines += ["", "Out of scope (host, Celeritas or options-screen tests):", ""]
    lines += [f"- `{top}/**`: {n}" for top, n in sorted(out_of_scope.items())]
    lines += ["", f"## Ported ({len(ported)} + {len(ported_resources)})", ""]
    lines += [f"- `{rel}`" for rel in sorted(ported)]
    lines += [f"- `{TEST_RESOURCES}/{rel}`" for rel in sorted(ported_resources)]
    lines.append("")
    return "\n".join(lines)


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--ref", default=ACTINIUM, help="Actinium commit to scan")
    ap.add_argument("--write", help="write the markdown report to this file")
    args = ap.parse_args()

    result = scan(args.ref)
    tests, ported, blockers, resources, ported_resources, skipped_resources = result
    text = report(args.ref, *result)
    print(f"ported: {len(ported)} java + {len(ported_resources)} resources  "
          f"skipped: {len(blockers)} java + {len(skipped_resources)} resources")
    if args.write:
        with open(args.write, "w") as f:
            f.write(text)
        print(f"wrote {args.write}")
    else:
        sys.stdout.write("\n" + text)

    problems = check_against_audit(args.ref, ported, ported_resources)
    for p in problems:
        print(f"MISMATCH: {p}", file=sys.stderr)
    return 1 if problems else 0


if __name__ == "__main__":
    raise SystemExit(main())
