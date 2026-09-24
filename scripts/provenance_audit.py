#!/usr/bin/env python3
# Historical: Demonica forked from Actinium at 4a19c959 (docs/FORK.md). This tool still works
# against the sync-era tags, but it is no longer part of the workflow for new changes.
"""Provenance audit for Demonica's synced trees.

Classifies every file in a scope against a baseline ref as:
  verbatim  - byte-identical (same git blob SHA) to the baseline
  adapted   - exists in both, content differs
  new       - only exists in the compared ref
  dropped   - only exists in the baseline

A scope is a list of source roots, each with optional include/exclude paths.
Files are keyed by their path relative to their source root, so the same
class matches across trees that put it under different prefixes (Angelica's
src/main/java vs Actinium's shader/src/main/java vs Demonica's src/main/java).
Two roots of one scope yielding the same key is an error.

A layout says where each scope lives in a given tree:
  angelica  - the Angelica baseline (1.7.10 GTNH build); iris and glsm only
  actinium  - Actinium's multi-project build
  demonica  - Demonica's engine-library build

The pseudo-ref INDEX reads the git index (`git ls-files -s`), so staged files
can be audited before they are committed.

Examples:
  # Exactness of Demonica against the Actinium syncline, all scopes:
  scripts/provenance_audit.py --scope all \\
      --a-ref fee5de3834352050fc453bde1d93030c6d434983 --a-layout actinium \\
      --expect-identical

  # Same, against staged files before committing a sync:
  scripts/provenance_audit.py --scope glsm --a-ref <sha> --a-layout actinium \\
      --b-ref INDEX --expect-identical

  # What Actinium changed relative to the Angelica baseline:
  scripts/provenance_audit.py --scope iris --b-ref <sha> --b-layout actinium

  # Legacy single-root mode (no --scope):
  scripts/provenance_audit.py --b-ref actinium/main --b-prefix shader/src/main/java
"""
import argparse
import subprocess
import sys
from dataclasses import dataclass

BASELINE = "angelica-baseline/9fd02900ef"
INDEX = "INDEX"
SCOPES = ("iris", "glsm", "gtnhlib", "celeritas-common", "resources", "tests")
LEGACY_PATHS = ("net/coderbot", "kroppeb", "net/irisshaders")


@dataclass(frozen=True)
class Root:
    base: str
    include: tuple[str, ...] = ()  # paths relative to base; empty = everything
    exclude: tuple[str, ...] = ()

    def key(self, path: str) -> str | None:
        """Return path relative to this root if the root covers it, else None."""
        if not path.startswith(self.base + "/"):
            return None
        rel = path[len(self.base) + 1:]
        if self.include and not any(_under(rel, p) for p in self.include):
            return None
        if any(_under(rel, p) for p in self.exclude):
            return None
        return rel


def _under(rel: str, prefix: str) -> bool:
    return rel == prefix or rel.startswith(prefix + "/")


IRIS_PACKAGES = LEGACY_PATHS
# Shader-side support classes Actinium keeps next to the Iris tree.
IRIS_SUPPORT = (
    "com/github/bsideup/jabel/Desugar.java",
    "com/gtnewhorizons/angelica/client/rendering/TextureTracker.java",
    "com/gtnewhorizons/angelica/compat/iris/BiomeCategoryCache.java",
    "com/gtnewhorizons/angelica/compat/iris/ModdedBiomeDetector.java",
    "com/gtnewhorizons/angelica/compat/mojang/NativeImage.java",
)
# Angelica root classes that Actinium moved into glsm. compat/mojang/NativeImage
# stays with the Iris tree, so the package is listed file by file.
GLSM_MOVED_ROOT_CLASSES = tuple(
    f"com/gtnewhorizons/angelica/{c}.java" for c in (
        "AngelicaMod",
        "compat/ModStatus",
        *(f"compat/mojang/{m}" for m in (
            "AutoClosableAbstractTexture", "ByteBufferBackedInputStream", "Camera",
            "ChunkOcclusionData", "ChunkOcclusionDataBuilder", "ChunkPos",
            "ChunkSectionPos", "CompatMathHelper", "Constants", "Drawable", "Element",
            "GameModeUtil", "InteractionHand", "ParentElement",
        )),
        "rendering/AngelicaRenderQueue",
        "rendering/RenderingState",
    )
)
RENDER_BACKEND_SERVICE = "META-INF/services/com.gtnewhorizons.angelica.glsm.backend.RenderBackend"
# src/main/resources/LICENSE was not synced: Actinium replaced Angelica's ShadersMod
# relicensing notice with its own GPL-3.0 text, and Demonica kept Angelica's notice
# until the license was settled (docs/PROVENANCE.md). Since 0.1.0 the jar takes the
# root license files instead, and the resource is gone.
ROOT_RESOURCES = (
    "assets/iris",
    "assets/angelica/shaders/centerDepth.vsh",
    "assets/angelica/shaders/centerDepth.fsh",
    "assets/actinium/shaders/include/chunk_vertex.glsl",
)
PORTED_TEST_PACKAGES = ("net/coderbot", "com/gtnewhorizons/angelica/glsm", "net/minecraft")
# Needs the host's AngelicaRedirector and the JourneyMap jar (see scripts/test_port_scan.py).
UNPORTED_TESTS = ("com/gtnewhorizons/angelica/glsm/redirect/JourneyMapRedirectorTest.java",)
PORTED_TEST_RESOURCES = ("compat_shaders",)

LAYOUTS: dict[str, dict[str, tuple[Root, ...]]] = {
    "angelica": {
        "iris": (Root("src/main/java", IRIS_PACKAGES + IRIS_SUPPORT),),
        "glsm": (
            Root("glsm/src/main/java"),
            Root("lwjgl3-backend/src/main/java"),
            Root("src/main/java", GLSM_MOVED_ROOT_CLASSES),
            Root("glsm/src/main/resources", (RENDER_BACKEND_SERVICE,)),
        ),
    },
    "actinium": {
        "iris": (Root("shader/src/main/java"),),
        "glsm": (
            Root("glsm/src/main/java"),
            Root("src/lwjglCommon/java"),
            Root("src/lwjgl3/java"),
            Root("src/main/resources", (RENDER_BACKEND_SERVICE,)),
        ),
        "gtnhlib": (Root("GTNHLib/src/main/java"),),
        "celeritas-common": (Root("celeritas-common/src/main/java"),),
        "resources": (Root("src/main/resources", ROOT_RESOURCES),),
        "tests": (
            Root("src/test/java", PORTED_TEST_PACKAGES, UNPORTED_TESTS),
            Root("src/test/resources", PORTED_TEST_RESOURCES),
        ),
    },
    "demonica": {
        "iris": (Root("src/main/java"),),
        "glsm": (
            Root("glsm/src/main/java"),
            Root("src/lwjglCommon/java"),
            Root("src/lwjgl3/java"),
            Root("glsm/src/main/resources", (RENDER_BACKEND_SERVICE,)),
        ),
        "gtnhlib": (Root("vendor/GTNHLib/src/main/java"),),
        "celeritas-common": (Root("vendor/celeritas-common/src/main/java"),),
        "resources": (Root("src/main/resources", ROOT_RESOURCES),),
        "tests": (Root("src/test/java"), Root("src/test/resources")),
    },
}


def list_files(ref: str, bases: set[str]) -> dict[str, str]:
    """Return {path: blob_sha} for every file under the given bases at ref."""
    if ref == INDEX:
        cmd = ["git", "ls-files", "-s", "-z", "--", *sorted(bases)]
    else:
        cmd = ["git", "ls-tree", "-r", "-z", ref, "--", *sorted(bases)]
    out = subprocess.run(cmd, capture_output=True, text=True, check=True).stdout
    files = {}
    for entry in filter(None, out.split("\0")):
        meta, path = entry.split("\t", 1)
        fields = meta.split()
        if ref == INDEX:
            mode, sha, stage = fields
            if stage != "0":
                raise SystemExit(f"error: {path} is unmerged in the index")
        else:
            mode, _type, sha = fields
        files[path] = sha
    return files


def collect(ref: str, roots: tuple[Root, ...], files: dict[str, str]) -> dict[str, str]:
    """Map every file covered by roots to {key: blob_sha}; keys must be unique."""
    keyed: dict[str, str] = {}
    origin: dict[str, str] = {}
    for path, sha in files.items():
        for root in roots:
            key = root.key(path)
            if key is None:
                continue
            if key in keyed:
                raise SystemExit(f"error: key collision at {ref}: {origin[key]} and {path} "
                                 f"both map to {key}")
            keyed[key] = sha
            origin[key] = path
    return keyed


@dataclass
class Result:
    name: str
    a_roots: tuple[Root, ...]
    b_roots: tuple[Root, ...]
    verbatim: list[str]
    adapted: list[str]
    new: list[str]
    dropped: list[str]

    @property
    def identical(self) -> bool:
        return not (self.adapted or self.new or self.dropped)

    def counts(self) -> str:
        return (f"verbatim: {len(self.verbatim)}  adapted: {len(self.adapted)}  "
                f"new: {len(self.new)}  dropped: {len(self.dropped)}")


def compare(name, a_ref, a_roots, b_ref, b_roots, cache) -> Result:
    def tree(ref, roots):
        bases = {r.base for r in roots}
        files = cache.setdefault(ref, {})
        missing = bases - files.keys()
        if missing:
            listing = list_files(ref, missing)
            for base in missing:
                files[base] = {p: s for p, s in listing.items() if p.startswith(base + "/")}
        merged = {}
        for base in bases:
            merged.update(files[base])
        return collect(ref, roots, merged)

    base = tree(a_ref, a_roots)
    head = tree(b_ref, b_roots)
    both = base.keys() & head.keys()
    return Result(
        name, a_roots, b_roots,
        verbatim=sorted(p for p in both if base[p] == head[p]),
        adapted=sorted(p for p in both if base[p] != head[p]),
        new=sorted(head.keys() - base.keys()),
        dropped=sorted(base.keys() - head.keys()),
    )


def describe(roots: tuple[Root, ...]) -> str:
    parts = []
    for r in roots:
        s = f"`{r.base}`"
        if r.include:
            s += " (" + ", ".join(f"`{p}`" for p in r.include) + ")"
        if r.exclude:
            s += " minus " + ", ".join(f"`{p}`" for p in r.exclude)
        parts.append(s)
    return "; ".join(parts)


def manifest(results: list[Result], a_ref, a_layout, b_ref, b_layout) -> str:
    a_desc = f"`{a_ref}`" + (f" ({a_layout} layout)" if a_layout else "")
    b_desc = f"`{b_ref}`" + (f" ({b_layout} layout)" if b_layout else "")
    lines = [
        "# Provenance manifest",
        "",
        f"- baseline: {a_desc}",
        f"- compared: {b_desc}",
        "",
        "| scope | verbatim | adapted | new | dropped |",
        "|---|---|---|---|---|",
    ]
    for r in results:
        lines.append(f"| {r.name} | {len(r.verbatim)} | {len(r.adapted)} | "
                     f"{len(r.new)} | {len(r.dropped)} |")
    lines.append("")
    for r in results:
        lines += [
            f"## Scope `{r.name}`",
            "",
            f"- baseline roots: {describe(r.a_roots)}",
            f"- compared roots: {describe(r.b_roots)}",
            "",
        ]
        for title, items in (("Adapted", r.adapted), ("New", r.new), ("Dropped", r.dropped)):
            lines += [f"### {title} ({len(items)})", ""]
            lines += [f"- `{p}`" for p in items]
            lines += [""]
    return "\n".join(lines)


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--scope", choices=(*SCOPES, "all"),
                    help="audit a named scope (omit for legacy single-root mode)")
    ap.add_argument("--a-ref", default=BASELINE)
    ap.add_argument("--a-layout", choices=tuple(LAYOUTS), default="angelica")
    ap.add_argument("--b-ref", default="HEAD", help=f"a ref, or {INDEX} for staged files")
    ap.add_argument("--b-layout", choices=tuple(LAYOUTS), default="demonica")
    ap.add_argument("--a-prefix", default="src/main/java", help="legacy mode only")
    ap.add_argument("--b-prefix", default="src/main/java", help="legacy mode only")
    ap.add_argument("--paths", nargs="*", default=list(LEGACY_PATHS), help="legacy mode only")
    ap.add_argument("--expect-identical", action="store_true",
                    help="exit 1 if any scope has an adapted, new or dropped file")
    ap.add_argument("--write", help="write the markdown manifest to this file")
    args = ap.parse_args()

    cache: dict[str, dict[str, dict[str, str]]] = {}
    results = []
    if args.scope is None:
        a_layout = b_layout = None
        results.append(compare(
            "legacy",
            args.a_ref, (Root(args.a_prefix, tuple(args.paths)),),
            args.b_ref, (Root(args.b_prefix, tuple(args.paths)),),
            cache))
    else:
        a_layout, b_layout = args.a_layout, args.b_layout
        a_scopes, b_scopes = LAYOUTS[a_layout], LAYOUTS[b_layout]
        if args.scope == "all":
            names = [s for s in SCOPES if s in a_scopes and s in b_scopes]
            skipped = [s for s in SCOPES if s not in names]
            if skipped:
                print(f"note: scopes not defined in both layouts: {', '.join(skipped)}")
        else:
            names = [args.scope]
            for layout in (a_layout, b_layout):
                if args.scope not in LAYOUTS[layout]:
                    ap.error(f"scope {args.scope} is not defined for layout {layout}")
        for name in names:
            results.append(compare(name, args.a_ref, a_scopes[name],
                                   args.b_ref, b_scopes[name], cache))

    for r in results:
        print(f"{r.name + ':':18} {r.counts()}  ({args.a_ref} vs {args.b_ref})")

    text = manifest(results, args.a_ref, a_layout, args.b_ref, b_layout)
    if args.write:
        with open(args.write, "w") as f:
            f.write(text)
        print(f"wrote {args.write}")
    elif not args.expect_identical:
        sys.stdout.write("\n" + text)

    if args.expect_identical:
        bad = [r for r in results if not r.identical]
        for r in bad:
            for title, items in (("adapted", r.adapted), ("new", r.new), ("dropped", r.dropped)):
                for p in items:
                    print(f"  {r.name}: {title}: {p}", file=sys.stderr)
        if bad:
            print(f"FAIL: not identical: {', '.join(r.name for r in bad)}", file=sys.stderr)
            return 1
        print("OK: all audited scopes are identical")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
