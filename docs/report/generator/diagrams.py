"""SVG renderers for the report figures, in the style of the earlier report:

* activity(): swimlane activity diagram (Actor | Hệ thống), rounded actions,
  decision diamonds with a "no" branch that loops back or ends.
* sequence(): sequence diagram with actor / boundary (GD_*) / control (CTRL_*)
  / entity lifelines, numbered messages, dashed returns and alt fragments.
"""

from __future__ import annotations

import math
from html import escape

FILL = "#7acff5"  # same light blue as Visual Paradigm defaults used in the old report
STROKE = "#1f2d3d"
FONT = "DejaVu Sans, Arial, sans-serif"


def _wrap(text: str, width_px: float, char_px: float = 6.4):
    words, lines, cur = text.split(), [], ""
    for w in words:
        if cur and len(cur + " " + w) * char_px > width_px:
            lines.append(cur)
            cur = w
        else:
            cur = (cur + " " + w).strip()
    if cur:
        lines.append(cur)
    return lines or [""]


def _text_block(cx, cy, lines, size=11, weight="normal", anchor="middle"):
    lh = size + 3
    y0 = cy - (len(lines) - 1) * lh / 2 + size / 3
    return "".join(
        f'<text x="{cx:.1f}" y="{y0 + i * lh:.1f}" font-size="{size}" font-weight="{weight}" text-anchor="{anchor}">{escape(t)}</text>'
        for i, t in enumerate(lines)
    )


def _defs():
    return (
        "<defs>"
        f'<marker id="arr" markerWidth="10" markerHeight="10" refX="9" refY="5" orient="auto"><path d="M0,0 L10,5 L0,10 Z" fill="{STROKE}"/></marker>'
        f'<marker id="open" markerWidth="10" markerHeight="10" refX="9" refY="5" orient="auto"><path d="M0,0 L10,5 L0,10" fill="none" stroke="{STROKE}"/></marker>'
        "</defs>"
    )


# ---------------------------------------------------------------------------
# activity
# ---------------------------------------------------------------------------


def activity(title: str, lanes: tuple[str, str], steps: list) -> str:
    """steps: ("A"|"S", text) actions in the actor / system lane, or
    ("?", question, (lane, text_of_no_action), target) where target is the
    index (in steps) of the action to loop back to, or "end"."""
    LW = 420
    W = LW * 2 + 40           # extra right margin = routing channel
    AW, GAP = 200, 30
    main_x = {"A": 245, "S": LW + 150}
    branch_x = {"A": 82, "S": LW + 335}
    y = 74
    nodes = []
    for st in steps:
        if st[0] in ("A", "S"):
            lines = _wrap(st[1], AW - 22)
            h = max(44, 16 + 14 * len(lines))
            nodes.append(dict(kind="act", lane=st[0], x=main_x[st[0]], y=y + h / 2, w=AW, h=h, lines=lines))
            y += h + GAP
        else:
            lane = nodes[-1]["lane"]
            nl, ntext = st[2]
            lines = _wrap(ntext, 130, 6.0)
            bh = max(40, 14 + 13 * len(lines))
            d = 34
            hh = max(d, bh)
            nodes.append(dict(kind="dec", lane=lane, x=main_x[lane], y=y + hh / 2, w=d, h=d, q=st[1],
                              bx=branch_x[nl], bh=bh, blines=lines, target=st[3]))
            y += hh + GAP
    end_y = y + 6
    H = end_y + 40
    o = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}" font-family="{FONT}">',
         _defs(), f'<rect width="{W}" height="{H}" fill="#fff"/>',
         f'<rect x="1" y="1" width="{W - 2}" height="{H - 2}" fill="none" stroke="{STROKE}" stroke-width="1.5"/>',
         f'<line x1="{LW}" y1="1" x2="{LW}" y2="{H - 1}" stroke="{STROKE}" stroke-width="1.5"/>',
         f'<line x1="1" y1="26" x2="{W - 1}" y2="26" stroke="{STROKE}" stroke-width="1.5"/>',
         _text_block(LW / 2, 14, [lanes[0].upper()], 11, "bold"),
         _text_block(LW + (W - LW) / 2, 14, [lanes[1].upper()], 11, "bold")]

    def final(cx, cy):
        return (f'<circle cx="{cx}" cy="{cy}" r="11" fill="#fff" stroke="{STROKE}" stroke-width="1.5"/>'
                f'<circle cx="{cx}" cy="{cy}" r="6" fill="{STROKE}"/>')

    def arrow(path):
        return f'<path d="{path}" fill="none" stroke="{STROKE}" marker-end="url(#arr)"/>'

    first = nodes[0]
    o.append(f'<circle cx="{first["x"]}" cy="46" r="10" fill="{STROKE}"/>')
    o.append(arrow(f'M{first["x"]},56 L{first["x"]},{first["y"] - first["h"] / 2}'))
    route = 0
    end_links = []
    for i, n in enumerate(nodes):
        x, yy = n["x"], n["y"]
        if n["kind"] == "act":
            o.append(f'<rect x="{x - n["w"] / 2}" y="{yy - n["h"] / 2}" width="{n["w"]}" height="{n["h"]}" rx="12" fill="{FILL}" stroke="{STROKE}"/>')
            o.append(_text_block(x, yy, n["lines"]))
        else:
            d = n["w"] / 2
            o.append(f'<path d="M{x},{yy - d} L{x + d},{yy} L{x},{yy + d} L{x - d},{yy} Z" fill="#fff" stroke="{STROKE}"/>')
            o.append(f'<text x="{x - d - 6}" y="{yy - 8}" font-size="10" text-anchor="end" fill="#333">{escape(n["q"])}</text>')
            bx, bw, bh = n["bx"], 150, n["bh"]
            o.append(f'<rect x="{bx - bw / 2}" y="{yy - bh / 2}" width="{bw}" height="{bh}" rx="10" fill="{FILL}" stroke="{STROKE}"/>')
            o.append(_text_block(bx, yy, n["blines"], 10))
            sgn = 1 if bx > x else -1
            o.append(arrow(f'M{x + sgn * d},{yy} L{bx - sgn * bw / 2},{yy}'))
            o.append(f'<text x="{x + sgn * (d + 5)}" y="{yy - 5}" font-size="10" text-anchor="{"start" if sgn > 0 else "end"}" fill="#333">[không]</text>')
            if n["target"] == "end":
                end_links.append((bx, yy + bh / 2))
            else:
                t = nodes[n["target"]]
                route += 1
                rx = W - 8 - 7 * (route % 4)
                o.append(arrow(f'M{bx + bw / 2},{yy} L{rx},{yy} L{rx},{t["y"]} L{t["x"] + t["w"] / 2},{t["y"]}')
                         if bx > x or True else "")
        if i + 1 < len(nodes):
            nx = nodes[i + 1]
            x2, y2 = nx["x"], nx["y"] - nx["h"] / 2
            y1 = yy + n["h"] / 2
            if abs(x - x2) < 1:
                o.append(arrow(f'M{x},{y1} L{x2},{y2}'))
            else:
                ym = y2 - GAP / 2
                o.append(arrow(f'M{x},{y1} L{x},{ym} L{x2},{ym} L{x2},{y2}'))
            if n["kind"] == "dec":
                o.append(f'<text x="{x + 6}" y="{y1 + 12}" font-size="10" fill="#333">[có]</text>')
    last = nodes[-1]
    ex, ey = last["x"], end_y + 12
    o.append(arrow(f'M{ex},{last["y"] + last["h"] / 2} L{ex},{ey - 11}'))
    o.append(final(ex, ey))
    for bx, by in end_links:  # "no" branches that end the use case: own final node under the branch box
        o.append(arrow(f'M{bx},{by} L{bx},{by + 18}'))
        o.append(final(bx, by + 30))
    o.append("</svg>")
    return "".join(o)


# ---------------------------------------------------------------------------
# sequence
# ---------------------------------------------------------------------------


def _icon(kind, cx, cy):
    s = STROKE
    if kind == "actor":
        return (f'<circle cx="{cx}" cy="{cy - 14}" r="7" fill="#fff" stroke="{s}"/>'
                f'<path d="M{cx},{cy - 7} L{cx},{cy + 8} M{cx - 11},{cy - 2} L{cx + 11},{cy - 2} M{cx},{cy + 8} L{cx - 9},{cy + 20} M{cx},{cy + 8} L{cx + 9},{cy + 20}" stroke="{s}" fill="none"/>')
    if kind == "boundary":
        return (f'<line x1="{cx - 22}" y1="{cy - 12}" x2="{cx - 22}" y2="{cy + 12}" stroke="{s}" stroke-width="1.5"/>'
                f'<line x1="{cx - 22}" y1="{cy}" x2="{cx - 12}" y2="{cy}" stroke="{s}" stroke-width="1.5"/>'
                f'<circle cx="{cx + 2}" cy="{cy}" r="13" fill="{FILL}" stroke="{s}"/>')
    if kind == "control":
        return (f'<circle cx="{cx}" cy="{cy}" r="13" fill="{FILL}" stroke="{s}"/>'
                f'<path d="M{cx - 4},{cy - 17} L{cx + 2},{cy - 13} L{cx - 4},{cy - 9}" fill="none" stroke="{s}" stroke-width="1.5"/>')
    if kind == "entity":
        return (f'<circle cx="{cx}" cy="{cy}" r="13" fill="{FILL}" stroke="{s}"/>'
                f'<line x1="{cx - 14}" y1="{cy + 15}" x2="{cx + 14}" y2="{cy + 15}" stroke="{s}" stroke-width="1.5"/>')
    return f'<rect x="{cx - 18}" y="{cy - 13}" width="36" height="26" fill="#fff" stroke="{s}"/>'  # external system


def sequence(title: str, parts: list, msgs: list, frags: list | None = None) -> str:
    """parts: [(kind, name)], kind in actor|boundary|control|entity|system.
    msgs: (from, to, text) or (from, to, text, "ret"); from == to is a self call.
    frags: [(first_msg, last_msg, "alt", "[điều kiện]", else_msg, "[ngược lại]")]."""
    COL = 175
    W = COL * len(parts) + 40
    xs = [40 + COL / 2 + i * COL - 20 for i in range(len(parts))]
    top = 78
    y = top + 30
    rows = []
    num = 0
    for k, m in enumerate(msgs):
        f, t, text = m[0], m[1], m[2]
        ret = len(m) > 3 and m[3] == "ret"
        if not ret:
            num += 1
        lines = _wrap(f"{text}" if ret else f"{num}: {text}", COL * max(1, abs(t - f)) - 30 if f != t else COL - 20, 6.0)
        h = 14 * len(lines) + (26 if f == t else 14)
        rows.append((y, f, t, lines, ret))
        y += h + 10
    H = y + 30
    o = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}" font-family="{FONT}">',
         _defs(), f'<rect width="{W}" height="{H}" fill="#fff"/>']
    for (kind, name), x in zip(parts, xs):
        o.append(_icon(kind, x, 34))
        o.append(_text_block(x, 66, [name], 11))
        o.append(f'<line x1="{x}" y1="{top}" x2="{x}" y2="{H - 14}" stroke="{STROKE}" stroke-dasharray="5,4"/>')
    # activation bar on actor + boundary for readability
    o.append(f'<rect x="{xs[0] - 5}" y="{top + 10}" width="10" height="{H - top - 34}" fill="{FILL}" stroke="{STROKE}"/>')
    for k, (yy, f, t, lines, ret) in enumerate(rows):
        x1, x2 = xs[f], xs[t]
        if f == t:
            ty = yy + 2
            o.append("".join(f'<text x="{x1 + 12}" y="{ty + i * 14}" font-size="10.5">{escape(s)}</text>' for i, s in enumerate(lines)))
            ly = ty + 14 * (len(lines) - 1) + 6
            o.append(f'<path d="M{x1 + 5},{ly} L{x1 + 40},{ly} L{x1 + 40},{ly + 16} L{x1 + 7},{ly + 16}" fill="none" stroke="{STROKE}" marker-end="url(#arr)"/>')
        else:
            ly = yy + 14 * (len(lines) - 1) + 6
            mid = (x1 + x2) / 2
            o.append("".join(f'<text x="{mid}" y="{yy + i * 14}" font-size="10.5" text-anchor="middle">{escape(s)}</text>' for i, s in enumerate(lines)))
            sx = x1 + (5 if x2 > x1 else -5)
            ex = x2 - (5 if x2 > x1 else -5)
            dash = ' stroke-dasharray="6,4"' if ret else ""
            mk = "open" if ret else "arr"
            o.append(f'<line x1="{sx}" y1="{ly}" x2="{ex}" y2="{ly}" stroke="{STROKE}"{dash} marker-end="url(#{mk})"/>')
    for fr in frags or []:
        a, b, op, cond = fr[0], fr[1], fr[2], fr[3]
        y1 = rows[a][0] - 18
        y2 = rows[b][0] + 14 * len(rows[b][3]) + 8
        o.append(f'<rect x="12" y="{y1}" width="{W - 24}" height="{y2 - y1}" fill="none" stroke="#555"/>')
        o.append(f'<path d="M12,{y1} L56,{y1} L56,{y1 + 10} L48,{y1 + 18} L12,{y1 + 18} Z" fill="#eef7fc" stroke="#555"/>')
        o.append(f'<text x="18" y="{y1 + 13}" font-size="10" font-weight="bold">{op}</text>')
        o.append(f'<text x="62" y="{y1 + 13}" font-size="10" fill="#333">{escape(cond)}</text>')
        if len(fr) > 4:
            ye = rows[fr[4]][0] - 16
            o.append(f'<line x1="12" y1="{ye}" x2="{W - 12}" y2="{ye}" stroke="#555" stroke-dasharray="6,4"/>')
            o.append(f'<text x="62" y="{ye + 12}" font-size="10" fill="#333">{escape(fr[5])}</text>')
    o.append("</svg>")
    return "".join(o)
