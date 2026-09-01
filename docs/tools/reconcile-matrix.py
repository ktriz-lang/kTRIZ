#!/usr/bin/env python3
"""kTRIZ classical 39x39 contradiction matrix -- 4-source reconciliation.

DOCUMENTATION ARTEFACT, NOT PART OF THE BUILD. This script has no Gradle
task, is not invoked by any CI workflow, and is not a build-time dependency
of `ktriz-core`. It exists purely so the provenance of
`ktriz-core/src/main/resources/dev/ktriz/core/contradiction-matrix-1971.csv`
is independently reproducible -- see docs/matrix-provenance.adoc for the
full account, including why this reconciliation approach (rather than
shipping any single source 1:1) was chosen.

Reads four independently transcribed copies of Altshuller's classical matrix
and emits kTRIZ's own reconciled CSV (majority rule: a cell value is
accepted when at least two of the four sources agree on the exact ordered
principle list). Run manually, from a scratch directory containing the four
raw source files fetched as below -- never automatically, and never against
network access implied by CI.

Fetch the four raw sources (adjust output filenames to match this script's
expectations: a.json, s.md, w.json, and an XLS-to-CSV conversion of
triz_matrix.xls under xls_out/triz_matrix-CM4.csv):

    gh api repos/Antropocosmist/triz-engineering-solver/contents/resources/contradiction_matrix.json \
        --jq .content | base64 -d > a.json
    gh api repos/sorunokoe/TRIZ-Skills/contents/matrix.md \
        --jq .content | base64 -d > s.md
    gh api repos/FreeFallingSnow/TRIZ-Altshuller/contents/way.json \
        --jq .content | base64 -d > w.json
    gh api repos/kamil-szczepanik/TRIZ-Agents/contents/data/tools_sources/triz_matrix.xls \
        --jq .content | base64 -d > triz_matrix.xls
    libreoffice --headless --convert-to csv --outdir xls_out triz_matrix.xls

Then:

    python3 reconcile-matrix.py .

Expected output on the bundled dataset (2026-09-02): accepted=1248,
conflicts=0, single-source=0. One accepted cell (19x9) contains a duplicate
principle id in its source data (a transcription error in the 1997 XLS
original, "8 35 35"); this script does not deduplicate it -- that
correction was made by hand when the CSV was copied into
contradiction-matrix-1971.csv, and is documented in
docs/matrix-provenance.adoc, not silently reproduced here so the raw
reconciliation output stays independently checkable against the sources.
"""
import csv, json, re, collections, sys, pathlib
S = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else ".")

def load_ant(p):
    return {tuple(int(x) for x in k.split(',')): tuple(v)
            for k, v in json.load(open(p))["cells"].items()}

def load_ffs(p):
    out = {}
    for e in json.load(open(p)):
        v = e["way"].strip()
        if v in ("+", "-", ""):
            continue
        try:
            out[(int(e["worsen"]), int(e["better"]))] = tuple(int(x) for x in v.split(','))
        except ValueError:
            pass  # two OCR-corrupt cells ("3.5", "1.26.12") -- dropped, majority covers them
    return out

def load_sor(p):
    out, row = {}, None
    for line in open(p):
        m = re.match(r'### R(\d+)', line)
        if m:
            row = int(m.group(1)); continue
        m = re.match(r'\|\s*(\d+)\s*\|[^|]*\|\s*([0-9,\s]+)\|', line)
        if m and row:
            out[(row, int(m.group(1)))] = tuple(
                int(x) for x in m.group(2).replace(' ', '').strip(',').split(',') if x)
    return out

def load_xls_csv(p):
    out = {}
    for r in csv.reader(open(p)):
        if len(r) > 2 and r[0].strip().isdigit():
            i = int(r[0])
            for j in range(1, 40):
                if 1 + j < len(r):
                    v = r[1 + j].strip()
                    if v and v not in ('+', '-'):
                        out[(i, j)] = tuple(int(x) for x in v.replace(' ', '').strip(',').split(',') if x)
    return out

srcs = {
    "XLS": load_xls_csv(S / "xls_out/triz_matrix-CM4.csv"),
    "ANT": load_ant(S / "a.json"),
    "SOR": load_sor(S / "s.md"),
    "FFS": load_ffs(S / "w.json"),
}
rows, conflicts, singles = {}, [], []
for k in sorted(set().union(*[set(d) for d in srcs.values()])):
    vals = [(n, d[k]) for n, d in srcs.items() if k in d]
    top, n = collections.Counter(v for _, v in vals).most_common(1)[0]
    if n >= 2:
        rows[k] = (list(top), n, sorted(n_ for n_, v in vals if v == top))
    elif len(vals) >= 2:
        conflicts.append((k, vals))
    else:
        singles.append((k, vals))
print(f"accepted={len(rows)} conflicts={len(conflicts)} single-source={len(singles)}")
for k, v in conflicts + singles:
    print("  UNRESOLVED", k, v)
dupes = [(k, v[0]) for k, v in rows.items() if len(set(v[0])) != len(v[0])]
print("cells with duplicate principle ids:", dupes)
with open(S / "contradiction-matrix.csv", "w", newline="") as fh:
    w = csv.writer(fh, lineterminator="\n")
    w.writerow(["improving", "worsening", "principles", "sources"])
    for (i, j), (v, n, who) in sorted(rows.items()):
        w.writerow([i, j, " ".join(str(x) for x in v), "+".join(who)])
