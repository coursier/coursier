#!/usr/bin/env python3
"""Generate index.html for the JVM diagnostics artifact."""
import os
import glob
import pathlib

PATTERNS = [
    "hotspot-pid-*.jfr",
    "gc_pid*.log",
    "out/mill-heap.hprof",
    "out/hs_err_pid*.log",
    "out/**/*.hprof",
    "out/**/*.jfr",
    "out/**/*.log",
]

files: list[str] = []
seen: set[str] = set()
for pattern in PATTERNS:
    for f in sorted(glob.glob(pattern, recursive=True)):
        if os.path.isfile(f) and f not in seen:
            files.append(f)
            seen.add(f)

if not files:
    print("No diagnostic files found, skipping index generation")
    raise SystemExit(0)


def insert(tree: dict, parts: tuple, full_path: str) -> None:
    if len(parts) == 1:
        tree.setdefault("__files__", []).append((parts[0], full_path))
    else:
        insert(tree.setdefault(parts[0], {}), parts[1:], full_path)


tree: dict = {}
for f in files:
    insert(tree, pathlib.PurePosixPath(f).parts, f)


def fmt_size(path: str) -> str:
    n = os.path.getsize(path)
    if n >= 1024 * 1024:
        return f"{n / 1024 / 1024:.1f} MB"
    return f"{n / 1024:.1f} KB"


def render(node: dict) -> list[str]:
    out: list[str] = []
    for name, path in sorted(node.get("__files__", [])):
        out.append(
            f'<li class="f"><a href="{path}">{name}</a>'
            f' <small>({fmt_size(path)})</small></li>'
        )
    for key in sorted(k for k in node if k != "__files__"):
        out.append(f'<li class="d"><details open><summary>{key}/</summary><ul>')
        out.extend(render(node[key]))
        out.append("</ul></details></li>")
    return out


CSS = """
  body { font-family: monospace; margin: 2em; color: #222; }
  h1 { font-size: 1.2em; }
  p  { color: #666; font-size: .9em; margin: .4em 0 1em; }
  ul { list-style: none; padding-left: 1.4em; margin: .15em 0; }
  ul:first-of-type { padding-left: 0; }
  .d > details > summary { cursor: pointer; font-weight: bold; color: #444; }
  .d > details > summary:hover { color: #000; }
  .f { margin: .1em 0; }
  .f a { color: #1558d6; text-decoration: none; }
  .f a:hover { text-decoration: underline; }
  small { color: #888; }
"""

rows = "\n".join(render(tree))
html = f"""<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <title>JVM Diagnostics</title>
  <style>{CSS}</style>
</head>
<body>
  <h1>JVM Diagnostics</h1>
  <p>{len(files)} file(s)</p>
  <ul>
{rows}
  </ul>
</body>
</html>
"""

with open("index.html", "w") as fh:
    fh.write(html)

print(f"Generated index.html listing {len(files)} diagnostic file(s)")
