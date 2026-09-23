#!/usr/bin/env python3
"""Provenance audit for Demonica's Iris tree.

Classifies every file under the audited paths against a baseline ref as:
  verbatim  - byte-identical (same git blob SHA) to the baseline
  adapted   - exists in both, content differs
  new       - only exists in HEAD
  dropped   - only exists in the baseline

Because classification uses git blob SHAs, it works across different path
prefixes (e.g. Angelica's src/main/java vs Actinium's shader/src/main/java).

Examples:
  # Audit against the Angelica baseline:
  scripts/provenance_audit.py

  # Audit the incoming Actinium sync (different path prefix on that ref):
  scripts/provenance_audit.py --b-ref actinium/main --b-prefix shader/src/main/java
"""
import argparse
import subprocess
import sys

DEFAULT_PATHS = ("net/coderbot", "kroppeb", "net/irisshaders")


def ls_tree(ref: str, prefix: str) -> dict[str, str]:
    """Return {relative_path: blob_sha} for all files under prefix at ref."""
    out = subprocess.run(
        ["git", "ls-tree", "-r", ref, "--", prefix],
        capture_output=True, text=True, check=True,
    ).stdout
    files = {}
    for line in out.splitlines():
        meta, path = line.split("\t", 1)
        sha = meta.split()[2]
        rel = path[len(prefix):].lstrip("/")
        files[rel] = sha
    return files


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--a-ref", default="angelica-baseline/9fd02900ef")
    ap.add_argument("--a-prefix", default="src/main/java")
    ap.add_argument("--b-ref", default="HEAD")
    ap.add_argument("--b-prefix", default="src/main/java")
    ap.add_argument("--paths", nargs="*", default=list(DEFAULT_PATHS))
    ap.add_argument("--write", help="write markdown manifest to this file")
    args = ap.parse_args()

    base: dict[str, str] = {}
    head: dict[str, str] = {}
    for p in args.paths:
        base.update(ls_tree(args.a_ref, f"{args.a_prefix}/{p}"))
        head.update(ls_tree(args.b_ref, f"{args.b_prefix}/{p}"))

    verbatim = sorted(p for p in base.keys() & head.keys() if base[p] == head[p])
    adapted = sorted(p for p in base.keys() & head.keys() if base[p] != head[p])
    new = sorted(head.keys() - base.keys())
    dropped = sorted(base.keys() - head.keys())

    lines = [
        f"# Provenance manifest",
        "",
        f"- baseline: `{args.a_ref}` (prefix `{args.a_prefix}`)",
        f"- compared: `{args.b_ref}` (prefix `{args.b_prefix}`)",
        f"- paths: {', '.join(args.paths)}",
        "",
        f"| class | count |",
        f"|---|---|",
        f"| verbatim (byte-identical) | {len(verbatim)} |",
        f"| adapted (content differs) | {len(adapted)} |",
        f"| new (only in compared ref) | {len(new)} |",
        f"| dropped (only in baseline) | {len(dropped)} |",
        "",
    ]
    for title, items in (("Adapted", adapted), ("New", new), ("Dropped", dropped)):
        lines += [f"## {title} ({len(items)})", ""]
        lines += [f"- `{p}`" for p in items]
        lines += [""]
    manifest = "\n".join(lines)

    print(f"verbatim: {len(verbatim)}  adapted: {len(adapted)}  "
          f"new: {len(new)}  dropped: {len(dropped)}  "
          f"(baseline {args.a_ref} vs {args.b_ref})")
    if args.write:
        with open(args.write, "w") as f:
            f.write(manifest)
        print(f"wrote {args.write}")
    else:
        sys.stdout.write("\n" + manifest)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
