#!/usr/bin/env python3
"""Cheap internal link checker for the built site: every local href/src in the HTML must
resolve to a file in the output directory. Usage: check-links.py site/public"""
import re, sys
from pathlib import Path
from urllib.parse import urlparse, unquote

root = Path(sys.argv[1]).resolve()
base = urlparse(re.search(r'<link rel="?canonical"? href="?([^" >]+)', (root / "index.html").read_text()).group(1))
prefix = base.path.rstrip("/")  # e.g. "/panelo" when canonical includes subpath
pat = re.compile(r'(?:href|src)="([^"#][^"]*)"')
bad = []
for page in root.rglob("*.html"):
    for ref in pat.findall(page.read_text(errors="ignore")):
        u = urlparse(ref)
        if u.scheme or u.netloc or ref.startswith(("mailto:", "data:", "javascript:")):
            # absolute links to our own origin are checked too
            if u.netloc != base.netloc:
                continue
        path = unquote(u.path)
        if prefix and path.startswith(prefix + "/"):
            path = path[len(prefix):]
        if not path.startswith("/"):
            path = "/" + str(((page.parent.relative_to(root) / path)).as_posix())
        target = root / path.lstrip("/")
        if not (target.is_file() or (target / "index.html").is_file()):
            bad.append(f"{page.relative_to(root)}: {ref}")
if bad:
    print("Broken internal links:", *sorted(set(bad)), sep="\n  ")
    sys.exit(1)
print("Link check OK")
