"""Minimal writer for Visual Paradigm "simple" XML projects (File > Import > XML).

Produces model elements (packages, classes, data types, stereotypes, actors,
use cases, systems, relationships) and diagrams (shapes + connectors), plus an
SVG preview of each diagram computed from the same layout.
"""

from __future__ import annotations

import math
import xml.etree.ElementTree as ET
from dataclasses import dataclass, field
from html import escape

AUTHOR = "baophuc"
STAMP = "1790931600000"  # epoch millis (VP format)

# ---------------------------------------------------------------------------
# Model
# ---------------------------------------------------------------------------


@dataclass
class Elem:
    id: str
    kind: str  # Package, Class, DataType, Stereotype, Actor, UseCase, System
    name: str
    doc: str = ""
    parent: "Elem | None" = None
    children: list = field(default_factory=list)
    attrs: dict = field(default_factory=dict)  # extra XML attributes
    stereotypes: list = field(default_factory=list)  # list[Elem]
    features: list = field(default_factory=list)  # (kind, id, name, type, mult, doc)


@dataclass
class Rel:
    id: str
    kind: str  # Association, Generalization, Include, Extend, Dependency
    src: Elem
    dst: Elem
    name: str = ""
    doc: str = ""
    # association specifics
    src_mult: str = ""
    dst_mult: str = ""
    src_role: str = ""
    dst_role: str = ""
    src_agg: str = "None"  # None | Shared | Composited (diamond drawn at src)


class Project:
    def __init__(self, name: str, prefix: str):
        self.name = name
        self.prefix = prefix
        self.roots: list[Elem] = []
        self.rels: list[Rel] = []
        self.diagrams: list[Diagram] = []
        self.by_name: dict[str, Elem] = {}
        self._seq: dict[str, int] = {}

    def nid(self, tag: str) -> str:
        n = self._seq.get(tag, 0) + 1
        self._seq[tag] = n
        return f"{self.prefix}_{tag}_{n:04d}"

    def add(self, kind, name, parent=None, doc="", tag=None, key=None, **attrs):
        e = Elem(self.nid(tag or kind[:3].upper()), kind, name, doc, parent, attrs=attrs)
        (parent.children if parent else self.roots).append(e)
        k = key or name
        if k in self.by_name:
            raise ValueError(f"duplicate element key {k}")
        self.by_name[k] = e
        return e

    def get(self, key) -> Elem:
        return self.by_name[key]

    def rel(self, kind, src, dst, **kw) -> Rel:
        src = self.get(src) if isinstance(src, str) else src
        dst = self.get(dst) if isinstance(dst, str) else dst
        r = Rel(self.nid(kind[:3].upper() + "R"), kind, src, dst, **kw)
        self.rels.append(r)
        return r

    def diagram(self, kind, name, doc="") -> "Diagram":
        d = Diagram(self, self.nid("DIA"), kind, name, doc)
        self.diagrams.append(d)
        return d

    # ------------------------------------------------------------------ XML
    def to_xml(self) -> bytes:
        root = ET.Element(
            "Project",
            {
                "Author": AUTHOR,
                "CommentTableSortAscending": "false",
                "CommentTableSortColumn": "Date Time",
                "DocumentationType": "html",
                "ExportedFromDifferentName": "false",
                "ExporterVersion": "17.2",
                "Name": self.name,
                "TextualAnalysisHighlightOptionCaseSensitive": "false",
                "UmlVersion": "2.x",
                "Xml_structure": "simple",
            },
        )
        models = ET.SubElement(root, "Models")
        for e in self.roots:
            self._elem_xml(models, e)
        self._rels_xml(models)
        dias = ET.SubElement(root, "Diagrams")
        for d in self.diagrams:
            d.to_xml(dias)
        ET.indent(root, space="  ")
        return b'<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n' + ET.tostring(
            root, encoding="utf-8"
        )

    @staticmethod
    def _common(e_id, name, doc):
        return {
            "Documentation_plain": doc,
            "Id": e_id,
            "Name": name,
            "PmAuthor": AUTHOR,
            "PmCreateDateTime": STAMP,
            "PmLastModified": STAMP,
            "QualityReason_IsNull": "true",
            "QualityScore": "-1",
        }

    def _type_ref(self, parent, type_name):
        t = self.by_name[type_name]
        holder = ET.SubElement(parent, "Type")
        ET.SubElement(holder, t.kind, {"Idref": t.id, "Name": t.name})

    def _elem_xml(self, parent, e: Elem):
        a = self._common(e.id, e.name, e.doc)
        if e.kind in ("Class", "Actor", "UseCase"):
            a.update({"Abstract": "false", "BusinessModel": "false", "Leaf": "false", "Root": "false", "Visibility": "public"})
        if e.kind == "Class":
            a.update({"Active": "false", "BusinessKeyMutable": "false"})
        if e.kind == "UseCase":
            a.update({"Rank": "Unspecified"})
        a.update(e.attrs)
        x = ET.SubElement(parent, e.kind, a)
        if e.stereotypes:
            st = ET.SubElement(x, "Stereotypes")
            for s in e.stereotypes:
                ET.SubElement(st, "Stereotype", {"Idref": s.id, "Name": s.name})
        if e.children or e.features:
            mc = ET.SubElement(x, "ModelChildren")
            for kind, fid, fname, ftype, mult, fdoc in e.features:
                if kind == "Attribute":
                    fa = self._common(fid, fname, fdoc)
                    fa.update(
                        {
                            "Abstract": "false",
                            "Aggregation": "none",
                            "AllowEmptyName": "false",
                            "ConnectToCodeModel": "1",
                            "Derived": "false",
                            "HasGetter": "false",
                            "HasSetter": "false",
                            "Leaf": "false",
                            "Multiplicity": mult,
                            "ReadOnly": "false",
                            "Scope": "instance",
                            "Unique": "false",
                            "Visibility": "private",
                        }
                    )
                    ax = ET.SubElement(mc, "Attribute", fa)
                    self._type_ref(ax, ftype)
                else:  # EnumerationLiteral
                    ET.SubElement(mc, "EnumerationLiteral", self._common(fid, fname, fdoc))
            for c in e.children:
                self._elem_xml(mc, c)

    def _rels_xml(self, models):
        if not self.rels:
            return
        cont = ET.SubElement(models, "ModelRelationshipContainer", self._common(self.nid("REL"), "relationships", ""))
        cmc = ET.SubElement(cont, "ModelChildren")
        kinds = []
        for r in self.rels:
            if r.kind not in kinds:
                kinds.append(r.kind)
        for k in kinds:
            sub = ET.SubElement(cmc, "ModelRelationshipContainer", self._common(self.nid("REL"), k, ""))
            smc = ET.SubElement(sub, "ModelChildren")
            for r in (r for r in self.rels if r.kind == k):
                a = self._common(r.id, r.name, r.doc)
                if k == "Association":
                    a.update({"Abstract": "false", "Derived": "false", "Direction": "From To", "Leaf": "false", "OrderingInProfile": "-1", "Visibility": "public"})
                    x = ET.SubElement(smc, "Association", a)
                    for side, el, mult, role, agg in (
                        ("FromEnd", r.src, r.src_mult, r.src_role, r.src_agg),
                        ("ToEnd", r.dst, r.dst_mult, r.dst_role, "None"),
                    ):
                        end_id = f"{r.id}_{side[0]}"
                        ea = self._common(end_id, role, "")
                        ea.update(
                            {
                                "AggregationKind": agg,
                                "ConnectToCodeModel": "1",
                                "Derived": "false",
                                "DerivedUnion": "false",
                                "JavaDistinct": "0",
                                "Leaf": "false",
                                "Multiplicity": mult,
                                "Navigable": "Unspecified",
                                "ProvidePropertyGetterMethod": "false",
                                "ProvidePropertySetterMethod": "false",
                                "ReadOnly": "false",
                                "Static": "false",
                                "Visibility": "Unspecified",
                            }
                        )
                        ex = ET.SubElement(ET.SubElement(x, side), "AssociationEnd", ea)
                        ET.SubElement(ET.SubElement(ex, "Qualifier"), "Qualifier", self._common(end_id + "Q", "", ""))
                        holder = ET.SubElement(ex, "Type")
                        ET.SubElement(holder, el.kind, {"Idref": el.id, "Name": el.name})
                else:
                    a.update({"From": r.src.id, "To": r.dst.id})
                    if k == "Generalization":
                        a["Substitutable"] = "false"
                    ET.SubElement(smc, k, a)


# ---------------------------------------------------------------------------
# Diagram + layout
# ---------------------------------------------------------------------------

CHAR_W = 6.6
ROW_H = 17


def text_w(s: str) -> float:
    return len(s) * CHAR_W


@dataclass
class Shape:
    id: str
    elem: Elem
    x: float
    y: float
    w: float
    h: float

    @property
    def cx(self):
        return self.x + self.w / 2

    @property
    def cy(self):
        return self.y + self.h / 2

    def border_point(self, tx, ty, ellipse=False):
        dx, dy = tx - self.cx, ty - self.cy
        if dx == 0 and dy == 0:
            return self.cx, self.cy
        if ellipse:
            a, b = self.w / 2, self.h / 2
            t = 1 / math.sqrt((dx / a) ** 2 + (dy / b) ** 2)
            return self.cx + dx * t, self.cy + dy * t
        sx = (self.w / 2) / abs(dx) if dx else math.inf
        sy = (self.h / 2) / abs(dy) if dy else math.inf
        s = min(sx, sy)
        return self.cx + dx * s, self.cy + dy * s


def class_lines(e: Elem):
    """Return (header lines, body lines) as displayed in a class shape."""
    head = [f"«{s.name}»" for s in e.stereotypes] + [e.name]
    body = []
    for kind, _id, fname, ftype, mult, _doc in e.features:
        if kind == "Attribute":
            m = f" [{mult}]" if mult not in ("", "1") else ""
            body.append(f"-{fname} : {ftype}{m}")
        else:
            body.append(fname)
    return head, body


def class_size(e: Elem):
    head, body = class_lines(e)
    w = max([text_w(h) + 34 for h in head] + [text_w(b) + 26 for b in body] + [150])
    h = 12 + ROW_H * len(head) + 8 + ROW_H * len(body) + (8 if body else 4)
    return math.ceil(w), math.ceil(h)


def usecase_size(e: Elem):
    words = e.name
    w = min(max(text_w(words) * 0.62 + 70, 150), 230)
    lines = math.ceil(text_w(words) / (w - 40))
    return math.ceil(w), 46 + 14 * max(0, lines - 2)


class Diagram:
    def __init__(self, project: Project, did, kind, name, doc):
        self.p = project
        self.id = did
        self.kind = kind  # ClassDiagram | UseCaseDiagram
        self.name = name
        self.doc = doc
        self.shapes: list[Shape] = []
        self.by_elem: dict[str, Shape] = {}
        self.extra_rels: list[Rel] | None = None
        self.only_rels: set[str] | None = None

    def place(self, key, x, y, w=None, h=None):
        e = self.p.get(key) if isinstance(key, str) else key
        if w is None or h is None:
            if e.kind == "Class":
                w, h = class_size(e)
            elif e.kind == "UseCase":
                w, h = usecase_size(e)
            elif e.kind == "Actor":
                w, h = 40, 80
            elif e.kind == "Package":
                w, h = max(160, text_w(e.name) + 40), 90
        s = Shape(self.p.nid("SHP"), e, x, y, w, h)
        self.shapes.append(s)
        self.by_elem[e.id] = s
        return s

    def rows(self, rows, x0=40, y0=40, hgap=60, vgap=80, align="top"):
        """Place rows of element keys; returns bottom y."""
        y = y0
        for row in rows:
            x = x0
            placed = []
            for key in row:
                if key is None:
                    x += 120
                    continue
                if isinstance(key, tuple):  # (key, dx_extra)
                    key, extra = key
                    x += extra
                s = self.place(key, x, y)
                placed.append(s)
                x += s.w + hgap
            if placed:
                y += max(s.h for s in placed) + vgap
        return y

    def connectors(self):
        """[(rel, from_shape, to_shape, points)] with simple obstacle-avoiding routes."""
        if getattr(self, "_routed", None) is not None:
            return self._routed
        pairs: dict[frozenset, int] = {}
        items = []
        for n, r in enumerate(self.p.rels):
            if self.only_rels is not None and r.id not in self.only_rels:
                continue
            a, b = self.by_elem.get(r.src.id), self.by_elem.get(r.dst.id)
            if a and b:
                items.append((n, r, a, b))
        # spread connection ports along each shape, ordered by the position of the other end
        self._px, self._py = {}, {}
        for s in self.shapes:
            mine = [(n, b if a is s else a) for n, r, a, b in items if (a is s) != (b is s)]
            for attr, store, key in (("cx", self._px, lambda o: o[1].cx), ("cy", self._py, lambda o: o[1].cy)):
                ranked = sorted(mine, key=key)
                size = s.w if attr == "cx" else s.h
                start = s.x if attr == "cx" else s.y
                for i, (n, _o) in enumerate(ranked):
                    store[(n, s.id)] = start + size * (i + 1) / (len(ranked) + 1)
        out = []
        for n, r, a, b in items:
            key = frozenset((a.id, b.id))
            k = pairs.get(key, 0)
            pairs[key] = k + 1
            lane = (k + 1) // 2 * (1 if k % 2 else -1)  # 0, 1, -1, 2, -2 ...
            lanes = getattr(self, "lanes", {})
            if r.id in lanes:  # route via a vertical lane beside the actor column
                lx = lanes[r.id]
                ya, yb = a.cy - 10, b.cy - 10
                xa = a.x if lx < a.x else a.x + a.w
                xb = b.x if lx < b.x else b.x + b.w
                out.append((r, a, b, [(xa, ya), (lx, ya), (lx, yb), (xb, yb)]))
                continue
            out.append((r, a, b, self._route(a, b, lane, n)))
        self._routed = out
        return out

    def _hits(self, pts, a, b):
        others = [s for s in self.shapes if s is not a and s is not b and s.elem.kind != "System"]
        hit = set()
        for (x1, y1), (x2, y2) in zip(pts, pts[1:]):
            L = math.hypot(x2 - x1, y2 - y1)
            for i in range(int(L // 6) + 1):
                t = i * 6 / L if L else 0
                px, py = x1 + (x2 - x1) * t, y1 + (y2 - y1) * t
                for s in others:
                    if s.x + 2 < px < s.x + s.w - 2 and s.y + 2 < py < s.y + s.h - 2:
                        hit.add(s.id)
        return len(hit)

    def _route(self, a: Shape, b: Shape, lane: int, n: int):
        if a is b:  # self association: loop over the top-right corner
            x, y, w = a.x, a.y, a.w
            return [(x + w, y + 30), (x + w + 40, y + 30), (x + w + 40, y - 30), (x + w - 50, y - 30), (x + w - 50, y)]
        ell_a, ell_b = a.elem.kind == "UseCase", b.elem.kind == "UseCase"
        straight = [a.border_point(b.cx, b.cy, ell_a), b.border_point(a.cx, a.cy, ell_b)]
        if lane:
            dx, dy = b.cx - a.cx, b.cy - a.cy
            L = math.hypot(dx, dy) or 1
            ox, oy = -dy / L * 14 * lane, dx / L * 14 * lane
            straight = [(straight[0][0] + ox, straight[0][1] + oy), (straight[1][0] + ox, straight[1][1] + oy)]
        if self.kind != "ClassDiagram":
            return straight
        off = 14 * lane
        jog = ((n % 5) - 2) * 7  # spread parallel channel segments

        def clampx(s, _x):
            return self._px.get((n, s.id), s.cx)

        def clampy(s, _y):
            return self._py.get((n, s.id), s.cy)

        cands = []
        # vertical gap -> Z route through the gap
        if a.y + a.h < b.y or b.y + b.h < a.y:
            top, bot = (a, b) if a.y < b.y else (b, a)
            ym = (top.y + top.h + bot.y) / 2 + jog
            lo, hi = max(a.x, b.x) + 12, min(a.x + a.w, b.x + b.w) - 12
            ea = a.y + a.h if a is top else a.y
            eb = b.y if a is top else b.y + b.h
            if lo < hi:
                xm = (lo + hi) / 2 + off
                cands.append([(xm, ea), (xm, eb)])
            xa, xb = clampx(a, a.cx + off), clampx(b, b.cx + off)
            lo_y, hi_y = top.y + top.h + 8, bot.y - 8
            yms = [ym] + [v + jog / 2 for o in self.shapes for v in (o.y + o.h + 16, o.y - 16) if lo_y < v < hi_y]
            for y_ in yms:
                cands.append([(xa, ea), (xa, y_), (xb, y_), (xb, eb)])
        # horizontal gap -> Z route
        if a.x + a.w < b.x or b.x + b.w < a.x:
            left, right = (a, b) if a.x < b.x else (b, a)
            xm = (left.x + left.w + right.x) / 2 + jog
            ea = a.x + a.w if a is left else a.x
            eb = b.x if a is left else b.x + b.w
            lo, hi = max(a.y, b.y) + 12, min(a.y + a.h, b.y + b.h) - 12
            if lo < hi:
                ym = min(lo + 30, (lo + hi) / 2) + off
                cands.append([(ea, ym), (eb, ym)])
            ya, yb = clampy(a, a.cy + off), clampy(b, b.cy + off)
            lo_x, hi_x = left.x + left.w + 8, right.x - 8
            xms = [xm] + [v + jog / 2 for o in self.shapes for v in (o.x + o.w + 16, o.x - 16) if lo_x < v < hi_x]
            for x_ in xms:
                cands.append([(ea, ya), (x_, ya), (x_, yb), (eb, yb)])
            # L routes
            xb = clampx(b, b.cx + off)
            if not (b.y < ya < b.y + b.h):
                cands.append([(ea, ya), (xb, ya), (xb, b.y if ya < b.y else b.y + b.h)])
            xa = clampx(a, a.cx + off)
            if not (a.y < yb < a.y + a.h):
                cands.append([(xa, a.y if yb < a.y else a.y + a.h), (xa, yb), (eb, yb)])
        # U routes below / above everything in between
        xa, xb = clampx(a, a.cx + off + jog), clampx(b, b.cx + off - jog)
        yd = max(a.y + a.h, b.y + b.h) + 26 + abs(jog)
        cands.append([(xa, a.y + a.h), (xa, yd), (xb, yd), (xb, b.y + b.h)])
        yu = min(a.y, b.y) - 26 - abs(jog)
        cands.append([(xa, a.y), (xa, yu), (xb, yu), (xb, b.y)])
        cands.append(straight)

        def score(pts):
            length = sum(math.hypot(q[0] - p[0], q[1] - p[1]) for p, q in zip(pts, pts[1:]))
            diag = any(abs(p[0] - q[0]) > 1 and abs(p[1] - q[1]) > 1 for p, q in zip(pts, pts[1:]))
            return self._hits(pts, a, b) * 10000 + length + 40 * (len(pts) - 2) + 1000 * diag

        return min(cands, key=score)

    # ------------------------------------------------------------------ XML
    def to_xml(self, parent):
        d = ET.SubElement(
            parent,
            self.kind,
            {
                "Author": AUTHOR,
                "ConnectionPointType": "2",
                "ConnectorStyle": "Rectilinear" if self.kind == "ClassDiagram" else "Straight",
                "CreateDateTime": STAMP,
                "Documentation_plain": self.doc,
                "Id": self.id,
                "Name": self.name,
                "ShowActivityStateNodeCaption": "true",
            },
        )
        shapes = ET.SubElement(d, "Shapes")
        for s in self.shapes:
            ET.SubElement(
                shapes,
                s.elem.kind,
                {
                    "Height": str(int(s.h)),
                    "Id": s.id,
                    "MetaModelElement": s.elem.id,
                    "Model": s.elem.id,
                    "Name": s.elem.name,
                    "Width": str(int(s.w)),
                    "X": str(int(s.x)),
                    "Y": str(int(s.y)),
                },
            )
        conns = ET.SubElement(d, "Connectors")
        for r, a, b, route in self.connectors():
            c = ET.SubElement(
                conns,
                r.kind,
                {
                    "From": a.id,
                    "Id": self.p.nid("CON"),
                    "MetaModelElement": r.id,
                    "Model": r.id,
                    "Name": r.name,
                    "To": b.id,
                },
            )
            pts = ET.SubElement(c, "Points")
            for px, py in route:
                ET.SubElement(pts, "Point", {"X": str(int(px)), "Y": str(int(py))})

    # ------------------------------------------------------------------ SVG
    def to_svg(self) -> str:
        pts = [pt for *_x, route in self.connectors() for pt in route]
        W = int(max([s.x + s.w for s in self.shapes] + [x for x, _ in pts]) + 60)
        H = int(max([s.y + s.h + (24 if s.elem.kind == "Actor" else 0) for s in self.shapes] + [y for _, y in pts]) + 60)
        o = [
            f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}" '
            'font-family="DejaVu Sans, Arial, sans-serif" font-size="11">',
            '<defs><marker id="open" markerWidth="12" markerHeight="12" refX="11" refY="6" orient="auto">'
            '<path d="M0,0 L11,6 L0,12" fill="none" stroke="#333"/></marker>'
            '<marker id="tri" markerWidth="16" markerHeight="16" refX="15" refY="8" orient="auto">'
            '<path d="M0,0 L15,8 L0,16 Z" fill="#fff" stroke="#333"/></marker>'
            '<marker id="dia" markerWidth="20" markerHeight="12" refX="1" refY="6" orient="auto">'
            '<path d="M1,6 L10,1 L19,6 L10,11 Z" fill="#333" stroke="#333"/></marker>'
            '<marker id="odia" markerWidth="20" markerHeight="12" refX="1" refY="6" orient="auto">'
            '<path d="M1,6 L10,1 L19,6 L10,11 Z" fill="#fff" stroke="#333"/></marker></defs>',
            f'<rect width="{W}" height="{H}" fill="#fff"/>',
            f'<text x="20" y="22" font-size="14" font-weight="bold">{escape(self.name)}</text>',
        ]
        # boundaries / packages first
        for s in self.shapes:
            if s.elem.kind == "System":
                o.append(f'<rect x="{s.x}" y="{s.y}" width="{s.w}" height="{s.h}" fill="#f4fbff" stroke="#333"/>')
                o.append(f'<text x="{s.cx}" y="{s.y + 18}" text-anchor="middle" font-weight="bold">{escape(s.elem.name)}</text>')
        for r, a, b, route in self.connectors():
            pts = " ".join(f"{x:.0f},{y:.0f}" for x, y in route)
            p1, p2 = route[0], route[-1]
            if r.kind == "Generalization":  # src = general, arrow head at the general end
                pts = " ".join(f"{x:.0f},{y:.0f}" for x, y in reversed(route))
                o.append(f'<polyline points="{pts}" fill="none" stroke="#333" marker-end="url(#tri)"/>')
            elif r.kind in ("Include", "Extend", "Dependency"):
                o.append(f'<polyline points="{pts}" fill="none" stroke="#333" stroke-dasharray="6,4" marker-end="url(#open)"/>')
                label = {"Include": "«include»", "Extend": "«extend»", "Dependency": "«use»"}[r.kind]
                mx, my = (p1[0] + p2[0]) / 2, (p1[1] + p2[1]) / 2
                o.append(f'<text x="{mx:.0f}" y="{my - 3:.0f}" text-anchor="middle" fill="#555" font-size="10">{label}</text>')
            else:
                mk = {"Composited": ' marker-start="url(#dia)"', "Shared": ' marker-start="url(#odia)"'}.get(r.src_agg, "")
                o.append(f'<polyline points="{pts}" fill="none" stroke="#333"{mk}/>')
                ends = ((route[0], route[1], r.src_mult, r.src_role), (route[-1], route[-2], r.dst_mult, r.dst_role))
                for (px, py), (qx, qy), mult, role in ends:
                    if not (mult or role):
                        continue
                    L = math.hypot(qx - px, qy - py) or 1
                    ux, uy = (qx - px) / L, (qy - py) / L
                    tx, ty = px + ux * 14, py + uy * 14
                    nx, ny = -uy, ux  # put role and multiplicity on opposite sides of the line
                    if role:
                        o.append(f'<text x="{tx + nx * 9:.0f}" y="{ty + ny * 9 + 4:.0f}" fill="#a33" font-size="9" text-anchor="{"start" if ux >= -0.1 else "end"}">{escape(role)}</text>')
                    if mult:
                        o.append(f'<text x="{tx - nx * 9:.0f}" y="{ty - ny * 9 + 4:.0f}" fill="#1a5" font-size="9" text-anchor="{"start" if ux >= -0.1 else "end"}">{escape(mult)}</text>')
        for s in self.shapes:
            k = s.elem.kind
            if k == "Class":
                head, body = class_lines(s.elem)
                is_enum = any(st.name == "enumeration" for st in s.elem.stereotypes)
                is_vo = any(st.name == "value object" for st in s.elem.stereotypes)
                fill = "#fff6d5" if is_enum else ("#e8f5e9" if is_vo else "#e3f2fd")
                hh = 12 + ROW_H * len(head)
                o.append(f'<rect x="{s.x}" y="{s.y}" width="{s.w}" height="{s.h}" fill="{fill}" stroke="#333"/>')
                o.append(f'<line x1="{s.x}" y1="{s.y + hh}" x2="{s.x + s.w}" y2="{s.y + hh}" stroke="#333"/>')
                for i, t in enumerate(head):
                    bold = ' font-weight="bold"' if i == len(head) - 1 else ""
                    o.append(f'<text x="{s.cx}" y="{s.y + 18 + i * ROW_H}" text-anchor="middle"{bold}>{escape(t)}</text>')
                for i, t in enumerate(body):
                    o.append(f'<text x="{s.x + 8}" y="{s.y + hh + 18 + i * ROW_H}">{escape(t)}</text>')
            elif k == "UseCase":
                o.append(f'<ellipse cx="{s.cx}" cy="{s.cy}" rx="{s.w / 2}" ry="{s.h / 2}" fill="#e3f2fd" stroke="#333"/>')
                words, lines, cur = s.elem.name.split(), [], ""
                for wd in words:
                    if cur and text_w(cur + " " + wd) > s.w - 36:
                        lines.append(cur)
                        cur = wd
                    else:
                        cur = (cur + " " + wd).strip()
                lines.append(cur)
                y0 = s.cy - (len(lines) - 1) * 7 + 4
                for i, t in enumerate(lines):
                    o.append(f'<text x="{s.cx}" y="{y0 + i * 14}" text-anchor="middle">{escape(t)}</text>')
            elif k == "Actor":
                cx, y = s.cx, s.y
                o.append(f'<circle cx="{cx}" cy="{y + 10}" r="10" fill="#fff" stroke="#333"/>')
                o.append(f'<path d="M{cx},{y + 20} L{cx},{y + 50} M{cx - 18},{y + 30} L{cx + 18},{y + 30} M{cx},{y + 50} L{cx - 15},{y + 78} M{cx},{y + 50} L{cx + 15},{y + 78}" stroke="#333" fill="none"/>')
                style = ' font-style="italic"' if s.elem.attrs.get("Abstract") == "true" else ""
                o.append(f'<text x="{cx}" y="{y + 94}" text-anchor="middle"{style}>{escape(s.elem.name)}</text>')
            elif k == "Package":
                o.append(f'<rect x="{s.x}" y="{s.y}" width="{min(90, s.w / 2)}" height="18" fill="#ede7f6" stroke="#333"/>')
                o.append(f'<rect x="{s.x}" y="{s.y + 18}" width="{s.w}" height="{s.h - 18}" fill="#ede7f6" stroke="#333"/>')
                o.append(f'<text x="{s.cx}" y="{s.y + 18 + (s.h - 18) / 2 + 4}" text-anchor="middle" font-weight="bold">{escape(s.elem.name)}</text>')
        o.append("</svg>")
        return "\n".join(o)


# ---------------------------------------------------------------------------
# helpers for class specs
# ---------------------------------------------------------------------------


def parse_attr(spec: str):
    """'name:Type[0..1] // doc' -> (name, type, mult, doc)."""
    doc = ""
    if "//" in spec:
        spec, doc = (x.strip() for x in spec.split("//", 1))
    name, typ = (x.strip() for x in spec.split(":", 1))
    mult = "1"
    if "[" in typ:
        typ, mult = typ[:-1].split("[")
    return name, typ.strip(), mult, doc
