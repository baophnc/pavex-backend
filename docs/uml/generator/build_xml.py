"""One Visual Paradigm XML file per diagram -> docs/uml/xml/.

Every class-type file carries the whole PAVEX domain model (same element ids in every file) plus
exactly one diagram, so importing several files into one VP project merges them into a single
model with one diagram per file:

  01-usecase.xml            use case diagram (same as ../pavex-usecase-model.xml)
  02-domain-packages.xml    package diagram of the bounded contexts with «use» dependencies
  03-domain-overview.xml    overall domain model (all entities / aggregate roots)
  04..13-class-*.xml        one class diagram per bounded context (attributes + operations)

The layouts follow the report figures (docs/report/generator/classdiag.py); operations come from
classdiag.OPS and are written as UML operations with parameters and return types.
"""

from __future__ import annotations

import os
import pathlib
import re
import shutil
import sys

HERE = pathlib.Path(__file__).resolve().parent
OUT = HERE.parent / "xml"
sys.path.insert(0, str(HERE.parent.parent / "report" / "generator"))

import classdiag as C  # noqa: E402
from vpxml import Diagram, class_size  # noqa: E402

P = C.P


def parse_op(spec: str):
    """'+findRule(level : ServiceLevel, zone) : RateRule' -> (name, [(param, type)], return type)."""
    m = re.match(r"\+(\w+)\((.*)\)\s*(?::\s*(\w+))?", spec)
    name, args, ret = m.group(1), m.group(2), m.group(3)
    params = []
    for a in filter(None, (x.strip() for x in args.split(","))):
        pname, _, ptype = (x.strip() for x in a.partition(":"))
        params.append((pname, ptype.split("[")[0] or None))
    return name, params, ret


def attach_operations():
    for cname, specs in C.OPS.items():
        e = P.by_name[cname]
        e.ops = specs  # shown in the class shape (sizes)
        e.ops_model = [(P.nid("OPR"), *parse_op(sp)) for sp in specs]


def add_package_dependencies():
    deps = set()
    for r in list(P.rels):
        if r.kind == "Association" and C.pkg_of(r.src) != C.pkg_of(r.dst):
            lo, hi = sorted((C.pkg_of(r.src), C.pkg_of(r.dst)), key=lambda n: C.LAYER[n])
            deps.add((hi, lo))
    pk = {v.name: v for v in C.BD.PKGS.values()}
    for name in pk:
        if name != "Shared Kernel":
            deps.add((name, "Shared Kernel"))
    return [P.rel("Dependency", pk[hi], pk[lo], name="use") for hi, lo in sorted(deps)]


def new_diagram(name, doc):
    return Diagram(P, P.nid("DIA"), "ClassDiagram", name, doc)


def columns_layout(d, columns, extras, hgap=110, vgap=70, wrap=1500):
    x, bottom = 40, 40
    for col in columns:
        y, width = 40, 0
        for key in col:
            s = d.place(P.by_name[key.lstrip("~")], x, y)
            y += s.h + vgap
            width = max(width, s.w)
        bottom = max(bottom, y)
        x += width + hgap
    right = max(x - hgap, wrap)
    y, x, row_h = bottom + 20, 40, 0
    for key in extras:
        e = P.by_name[key.lstrip("~")]
        w, h = class_size(e)
        if x + w > right and x > 40:
            x, y, row_h = 40, y + row_h + 40, 0
        d.place(e, x, y, w, h)
        x += w + 40
        row_h = max(row_h, h)


def context_rels(pkg, shown):
    keep = set()
    for r in P.rels:
        if r.kind != "Association" or r.src.name not in shown or r.dst.name not in shown:
            continue
        a, b = C.pkg_of(r.src), C.pkg_of(r.dst)
        if a == b == pkg or (pkg in (a, b) and C.LAYER[pkg] >= C.LAYER[b if a == pkg else a]):
            keep.add(r.id)
    return keep


def write(fname, d):
    P.diagrams = [d]
    (OUT / fname).write_bytes(P.to_xml())
    preview = os.environ.get("XML_PREVIEW_DIR")  # optional: SVG of exactly what the XML lays out
    if preview:
        (pathlib.Path(preview) / fname.replace(".xml", ".svg")).write_text(d.to_svg(), encoding="utf-8")
    print(f"{fname}: {len(d.shapes)} shapes, {len(d.connectors())} connectors")


def main():
    OUT.mkdir(exist_ok=True)
    attach_operations()
    deps = add_package_dependencies()
    shutil.copy(HERE.parent / "pavex-usecase-model.xml", OUT / "01-usecase.xml")
    print("01-usecase.xml: copied")

    # package diagram (same placement as the report figure)
    d = new_diagram("PAVEX – Sơ đồ package", "Bounded context và phụ thuộc «use» (downstream -> upstream).")
    pos = {"Exception Handling": (40, 40), "Workforce & Operations": (720, 40), "Shipment": (380, 230),
           "Partner": (40, 420), "Pricing": (380, 420), "Network": (720, 420),
           "Shared Kernel": (40, 640), "Identity & Access": (380, 640)}
    sh = {}
    for pk in C.BD.PKGS.values():
        x, y = pos[pk.name]
        sh[pk.name] = d.place(pk, x, y, 280, 120)
    # hand-routed like the report figure; the «use» on Shared Kernel stay in the model only
    L = lambda n: sh[n].x; R = lambda n: sh[n].x + sh[n].w  # noqa: E731
    T = lambda n: sh[n].y; B = lambda n: sh[n].y + sh[n].h; CY = lambda n: sh[n].cy  # noqa: E731
    ex, wf, sp, pa, pr, nw, idn = ("Exception Handling", "Workforce & Operations", "Shipment", "Partner", "Pricing",
                                   "Network", "Identity & Access")
    routes = {
        (ex, sp): [(R(ex), CY(ex)), (450, CY(ex)), (450, T(sp))],
        (wf, sp): [(L(wf), CY(wf)), (590, CY(wf)), (590, T(sp))],
        (wf, nw): [(860, B(wf)), (860, T(nw))],
        (ex, nw): [(150, T(ex)), (150, 15), (1030, 15), (1030, CY(nw)), (R(nw), CY(nw))],
        (wf, idn): [(R(wf), CY(wf) + 12), (1015, CY(wf) + 12), (1015, CY(idn)), (R(idn), CY(idn))],
        (sp, pa): [(L(sp), CY(sp)), (180, CY(sp)), (180, T(pa))],
        (sp, pr): [(520, B(sp)), (520, T(pr))],
        (sp, nw): [(R(sp), CY(sp)), (780, CY(sp)), (780, T(nw))],
        (pa, idn): [(180, B(pa)), (180, 600), (450, 600), (450, T(idn))],
    }
    d._routed, d._conn_ids = [], {}
    for r in deps:
        key = (r.src.name, r.dst.name)
        if key in routes:
            d._routed.append((r, sh[key[0]], sh[key[1]], routes[key]))
            d._conn_ids[r.id] = P.nid("CON")
    assert len(d._routed) == len(routes)
    write("02-domain-packages.xml", d)

    # overall domain model: grid of the report figure, full class shapes
    d = new_diagram("PAVEX – Sơ đồ domain tổng thể", "Toàn bộ aggregate root và entity cùng các association.")
    cols, rows = {}, {}
    for name, (c, r) in C.OVERVIEW.items():
        w, h = class_size(P.by_name[name])
        cols[c] = max(cols.get(c, 0), w)
        rows[r] = max(rows.get(r, 0), h)
    xs, ys = {}, {}
    acc = 40
    for c in sorted(cols):
        xs[c] = acc
        acc += cols[c] + 90
    acc = 40
    for r in sorted(rows):
        ys[r] = acc
        acc += rows[r] + 90
    for name, (c, r) in C.OVERVIEW.items():
        d.place(P.by_name[name], xs[c], ys[r])
    d.only_rels = {r.id for r in P.rels if r.kind == "Association"}
    d.lane_gap = 22
    write("03-domain-overview.xml", d)

    n = 4
    for slug, pkg, cols_ in C.CONTEXTS:
        types = C.vos_of(pkg) + C.enums_of(pkg) + C.foreign_types(pkg)
        split = len(types) > 12
        d = new_diagram(f"Class – {pkg}", f"Sơ đồ lớp của bounded context {pkg}.")
        d.lane_gap = 34
        columns_layout(d, cols_, [] if split else types)
        d.only_rels = context_rels(pkg, {s.elem.name for s in d.shapes})
        write(f"{n:02d}-class-{slug}.xml", d)
        n += 1
        if split:
            d = new_diagram(f"Class – {pkg} – value object và enumeration", f"Kiểu dữ liệu của bounded context {pkg}.")
            columns_layout(d, [], types, wrap=1300)
            d.only_rels = set()
            write(f"{n:02d}-class-{slug}-types.xml", d)
            n += 1
    d = new_diagram("Class – Shared Kernel", "Value object dùng chung.")
    columns_layout(d, [], C.vos_of("Shared Kernel") + C.enums_of("Shared Kernel"), wrap=1000)
    d.only_rels = set()
    write(f"{n:02d}-class-shared.xml", d)


if __name__ == "__main__":
    main()
