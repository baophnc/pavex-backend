"""SVG renderers for the report figures, following UML 2.5 notation.

* activity(): activity diagram in a frame ("act <name>") with two activity
  partitions (actor | system). Initial node, actions, decision nodes with a
  guard on every outgoing edge, merge nodes where a flow loops back (no action
  ever has two incoming edges), activity final nodes.
* sequence(): interaction in a frame ("sd <name>"). Lifelines with
  actor / boundary / control / entity heads (robustness stereotypes, as in
  Visual Paradigm), execution specifications (activation bars, nested for self
  calls), synchronous calls (filled arrowhead), asynchronous signals to
  external systems (open arrowhead), replies (dashed, open arrowhead) and
  combined fragments alt / opt / loop with guarded operands.
"""

from __future__ import annotations

import math
from html import escape

FILL = "#cfe8f7"
STROKE = "#1f2d3d"
FONT = "DejaVu Sans, Arial, sans-serif"


def _wrap(text: str, width_px: float, char_px: float = 6.3):
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
        f'<marker id="open" markerWidth="11" markerHeight="11" refX="10" refY="5.5" orient="auto"><path d="M0,0 L10,5.5 L0,11" fill="none" stroke="{STROKE}" stroke-width="1.2"/></marker>'
        "</defs>"
    )


def _frame(W, H, label):
    """UML diagram frame with the name compartment (pentagon) in the top-left corner."""
    tw = len(label) * 6.6 + 22
    return (f'<rect x="1" y="1" width="{W - 2}" height="{H - 2}" fill="none" stroke="{STROKE}" stroke-width="1.3"/>'
            f'<path d="M1,1 L{tw},1 L{tw},14 L{tw - 10},24 L1,24 Z" fill="#fff" stroke="{STROKE}" stroke-width="1.3"/>'
            f'<text x="8" y="17" font-size="11.5"><tspan font-weight="bold">{escape(label.split(" ", 1)[0])}</tspan> {escape(label.split(" ", 1)[1])}</text>')


# ---------------------------------------------------------------------------
# activity
# ---------------------------------------------------------------------------


def _guards(question, guards):
    if guards and question in guards:
        return guards[question]
    q = question.rstrip("?").strip()
    q = q[0].lower() + q[1:]
    return q, f"không {q}"


def activity(title: str, lanes: tuple[str, str], steps: list, guards: dict | None = None) -> str:
    """steps: ("A"|"S", text) actions in the actor / system partition, or
    ("?", question, (lane, text_of_else_action), target) where target is the
    index (in steps) of the node the else flow returns to, or "end".
    The question becomes the pair of guards [yes] / [no] (see `guards`)."""
    LW = 420
    W = LW * 2 + 56
    X0 = 8                                   # left edge of the partitions
    AW, GAP = 210, 30
    main_x = {"A": X0 + 240, "S": X0 + LW + 138}
    branch_x = {"A": X0 + 80, "S": X0 + LW + 350}
    D = 30                                   # decision / merge diamond size
    targets = {st[3] for st in steps if st[0] == "?" and st[3] != "end"}
    y = 104
    nodes = []
    for i, st in enumerate(steps):
        lane = st[0] if st[0] in ("A", "S") else nodes[-1]["lane"]
        if i in targets:  # merge node in front of the node a loop returns to
            nodes.append(dict(kind="merge", lane=lane, x=main_x[lane], y=y + D / 2, w=D, h=D, step=i))
            y += D + GAP - 8
        if st[0] in ("A", "S"):
            lines = _wrap(st[1], AW - 24)
            h = max(40, 16 + 14 * len(lines))
            nodes.append(dict(kind="act", lane=lane, x=main_x[lane], y=y + h / 2, w=AW, h=h, lines=lines, step=i))
            y += h + GAP
        else:
            nl, ntext = st[2]
            lines = _wrap(ntext, 140, 6.0)
            bh = max(40, 14 + 13 * len(lines))
            hh = max(D, bh)
            yes, no = _guards(st[1], guards)
            nodes.append(dict(kind="dec", lane=lane, x=main_x[lane], y=y + hh / 2, w=D, h=D, step=i,
                              bx=branch_x[nl], bh=bh, blines=lines, target=st[3], yes=yes, no=no))
            y += hh + GAP + 6
    end_y = y + 8
    H = math.ceil(end_y + 44)
    o = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}" font-family="{FONT}">',
         _defs(), f'<rect width="{W}" height="{H}" fill="#fff"/>', _frame(W, H, f"act {title}")]
    # activity partitions (swimlanes)
    top, bottom, x1, x2, x3 = 32, H - 8, X0, X0 + LW, W - 8
    o.append(f'<rect x="{x1}" y="{top}" width="{x3 - x1}" height="{bottom - top}" fill="none" stroke="{STROKE}"/>')
    o.append(f'<line x1="{x2}" y1="{top}" x2="{x2}" y2="{bottom}" stroke="{STROKE}"/>')
    o.append(f'<line x1="{x1}" y1="{top + 26}" x2="{x3}" y2="{top + 26}" stroke="{STROKE}"/>')
    o.append(_text_block((x1 + x2) / 2, top + 13, [lanes[0]], 11.5, "bold"))
    o.append(_text_block((x2 + x3) / 2, top + 13, [lanes[1]], 11.5, "bold"))

    def final(cx, cy):
        return (f'<circle cx="{cx}" cy="{cy}" r="11" fill="#fff" stroke="{STROKE}" stroke-width="1.5"/>'
                f'<circle cx="{cx}" cy="{cy}" r="6.5" fill="{STROKE}"/>')

    def arrow(path):
        return f'<path d="{path}" fill="none" stroke="{STROKE}" marker-end="url(#arr)"/>'

    def guard(x, y, text, anchor="start"):
        return f'<text x="{x:.1f}" y="{y:.1f}" font-size="10" text-anchor="{anchor}" fill="#222">[{escape(text)}]</text>'

    def diamond(x, yy):
        d = D / 2
        return f'<path d="M{x},{yy - d} L{x + d},{yy} L{x},{yy + d} L{x - d},{yy} Z" fill="#fff" stroke="{STROKE}" stroke-width="1.2"/>'

    first = nodes[0]
    o.append(f'<circle cx="{first["x"]}" cy="{78}" r="10" fill="{STROKE}"/>')
    o.append(arrow(f'M{first["x"]},88 L{first["x"]},{first["y"] - first["h"] / 2}'))
    merge_of = {n["step"]: n for n in nodes if n["kind"] == "merge"}
    loops = 0
    for i, n in enumerate(nodes):
        x, yy = n["x"], n["y"]
        if n["kind"] == "act":
            o.append(f'<rect x="{x - n["w"] / 2}" y="{yy - n["h"] / 2}" width="{n["w"]}" height="{n["h"]}" rx="13" fill="{FILL}" stroke="{STROKE}"/>')
            o.append(_text_block(x, yy, n["lines"]))
        elif n["kind"] == "merge":
            o.append(diamond(x, yy))
        else:
            d = D / 2
            o.append(diamond(x, yy))
            bx, bw, bh = n["bx"], 150, n["bh"]
            o.append(f'<rect x="{bx - bw / 2}" y="{yy - bh / 2}" width="{bw}" height="{bh}" rx="12" fill="{FILL}" stroke="{STROKE}"/>')
            o.append(_text_block(bx, yy, n["blines"], 10))
            sgn = 1 if bx > x else -1
            o.append(arrow(f'M{x + sgn * d},{yy} L{bx - sgn * bw / 2},{yy}'))
            o.append(guard(x + sgn * (d + 4), yy - 6, n["no"], "start" if sgn > 0 else "end"))
            if n["target"] == "end":
                o.append(arrow(f'M{bx},{yy + bh / 2} L{bx},{yy + bh / 2 + 16}'))
                o.append(final(bx, yy + bh / 2 + 27))
            else:
                m = merge_of[n["target"]]
                loops += 1
                rx = W - 16 - 9 * ((loops - 1) % 3)
                o.append(arrow(f'M{bx + bw / 2},{yy} L{rx},{yy} L{rx},{m["y"]} L{m["x"] + D / 2},{m["y"]}'))
        if i + 1 < len(nodes):
            nx = nodes[i + 1]
            x2_, y2 = nx["x"], nx["y"] - nx["h"] / 2
            y1 = yy + n["h"] / 2
            if abs(x - x2_) < 1:
                o.append(arrow(f'M{x},{y1} L{x2_},{y2}'))
            else:
                ym = y2 - 14
                o.append(arrow(f'M{x},{y1} L{x},{ym} L{x2_},{ym} L{x2_},{y2}'))
            if n["kind"] == "dec":
                o.append(guard(x + 6, y1 + 12, n["yes"]))
    last = nodes[-1]
    ex, ey = last["x"], end_y + 14
    o.append(arrow(f'M{ex},{last["y"] + last["h"] / 2} L{ex},{ey - 11}'))
    o.append(final(ex, ey))
    o.append("</svg>")
    return "".join(o)


# ---------------------------------------------------------------------------
# sequence
# ---------------------------------------------------------------------------


def _icon(kind, cx, cy, name):
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
    w = max(90, len(name) * 6.6 + 16)  # external system: classifier rectangle «system»
    return (f'<rect x="{cx - w / 2}" y="{cy - 20}" width="{w}" height="40" fill="#fff" stroke="{s}"/>'
            f'<text x="{cx}" y="{cy - 5}" font-size="10" text-anchor="middle">«system»</text>')


def _activations(parts, msgs, frags):
    """Execution specifications: (lifeline, first_msg, last_msg, self_call)."""
    n = len(msgs)
    out = []

    def involved(k, t):
        return msgs[k][0] == t or msgs[k][1] == t

    for i, m in enumerate(msgs):
        f, t = m[0], m[1]
        ret = len(m) > 3 and m[3] == "ret"
        if ret or parts[t][0] == "actor":
            continue
        if f == t:
            out.append((t, i, i, True))
            continue
        end = None
        for j in range(i + 1, n):
            fj, tj = msgs[j][0], msgs[j][1]
            if fj == t and tj == f:            # reply (or answer message) to the caller
                end = j
                break
            if tj == t and fj == f and not (len(msgs[j]) > 3 and msgs[j][3] == "ret"):
                cand = [k for k in range(i + 1, j) if involved(k, t)]
                end = cand[-1] if cand else i  # caller starts a new call: previous one is over
                break
        if end is None:
            cand = [k for k in range(i + 1, n) if involved(k, t)]
            end = cand[-1] if cand and parts[t][0] in ("boundary", "control") else i
        # alt: the activation also covers the reply sent in the other operand
        for fr in frags or []:
            if len(fr) > 4 and i < fr[0] and fr[0] <= end < fr[4]:
                for j in range(fr[4], fr[1] + 1):
                    if msgs[j][0] == t and msgs[j][1] == f:
                        end = max(end, j)
        out.append((t, i, end, False))
    return out


def sequence(title: str, parts: list, msgs: list, frags: list | None = None) -> str:
    """parts: [(kind, name)], kind in actor|boundary|control|entity|system.
    msgs: (from, to, text) or (from, to, text, "ret"); from == to is a self call;
    a call to a «system» participant is an asynchronous signal.
    frags: [(first_msg, last_msg, "alt"|"opt"|"loop", "[guard]"[, else_msg, "[guard]"])]."""
    frags = frags or []
    COL = 190
    W = COL * len(parts) + 50
    xs = [30 + COL / 2 + i * COL for i in range(len(parts))]
    top = 112
    y = top + 22
    rows = []
    num = 0
    for k, m in enumerate(msgs):
        f, t, text = m[0], m[1], m[2]
        ret = len(m) > 3 and m[3] == "ret"
        y += 32 * sum(1 for fr in frags if fr[0] == k)
        y += 30 * sum(1 for fr in frags if len(fr) > 4 and fr[4] == k)
        prefix = "" if ret else f"{(num := num + 1)}: "
        span = COL * abs(t - f) - 26 if f != t else COL - 40
        lines = _wrap(text, span - len(prefix) * 6.1, 6.1)  # keep the number on the first line
        lines[0] = prefix + lines[0]
        ly = y + 14 * (len(lines) - 1) + 5
        h = 14 * (len(lines) - 1) + (30 if f == t else 12)
        rows.append(dict(y=y, ly=ly, f=f, t=t, lines=lines, ret=ret, bottom=y + h))
        y += h + 12
        y += 10 * sum(1 for fr in frags if fr[1] == k)
    H = math.ceil(y + 30)
    o = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}" font-family="{FONT}">',
         _defs(), f'<rect width="{W}" height="{H}" fill="#fff"/>', _frame(W, H, f"sd {title}")]
    for (kind, name), x in zip(parts, xs):
        o.append(_icon(kind, x, 66, name))
        o.append(_text_block(x, 66 if kind == "system" else 102, [name], 11))
        o.append(f'<line x1="{x}" y1="{top}" x2="{x}" y2="{H - 16}" stroke="{STROKE}" stroke-dasharray="5,4"/>')
    # execution specifications
    acts = _activations(parts, msgs, frags)
    active_at = {}
    for t, a, b, selfc in sorted(acts, key=lambda q: (q[1], q[3])):
        if selfc:
            y1, y2 = rows[a]["ly"] + 16, rows[a]["ly"] + 30
        else:
            y1, y2 = rows[a]["ly"] - 2, max(rows[b]["ly"] + 2, rows[a]["ly"] + 18)
        depth = sum(1 for (tt, yy1, yy2) in active_at.get("list", []) if tt == t and yy1 <= y1 < yy2)
        active_at.setdefault("list", []).append((t, y1, y2))
        x = xs[t] - 5 + 6 * depth
        o.append(f'<rect x="{x}" y="{y1}" width="10" height="{y2 - y1}" fill="#fff" stroke="{STROKE}"/>')
    for r in rows:
        f, t = r["f"], r["t"]
        x1, x2 = xs[f], xs[t]
        if f == t:
            ty = r["y"] + 4
            o.append("".join(f'<text x="{x1 + 14}" y="{ty + i * 14}" font-size="10.5" paint-order="stroke" stroke="#fff" stroke-width="4" stroke-linejoin="round">{escape(s)}</text>' for i, s in enumerate(r["lines"])))
            ly = r["ly"] + 6
            o.append(f'<path d="M{x1 + 5},{ly} L{x1 + 42},{ly} L{x1 + 42},{ly + 14} L{x1 + 12},{ly + 14}" fill="none" stroke="{STROKE}" marker-end="url(#arr)"/>')
            continue
        ly = r["ly"]
        mid = (x1 + x2) / 2
        o.append("".join(f'<text x="{mid}" y="{r["y"] + i * 14}" font-size="10.5" text-anchor="middle" paint-order="stroke" stroke="#fff" stroke-width="4" stroke-linejoin="round">{escape(s)}</text>' for i, s in enumerate(r["lines"])))
        right = x2 > x1
        sx = x1 + ((5 if right else -5) if parts[f][0] != "actor" else 0)
        ex = x2 - ((5 if right else -5) if parts[t][0] != "actor" else 0)
        if r["ret"]:
            o.append(f'<line x1="{sx}" y1="{ly}" x2="{ex}" y2="{ly}" stroke="{STROKE}" stroke-dasharray="6,4" marker-end="url(#open)"/>')
        elif parts[t][0] == "system":
            o.append(f'<line x1="{sx}" y1="{ly}" x2="{ex}" y2="{ly}" stroke="{STROKE}" marker-end="url(#open)"/>')
        else:
            o.append(f'<line x1="{sx}" y1="{ly}" x2="{ex}" y2="{ly}" stroke="{STROKE}" marker-end="url(#arr)"/>')
    # combined fragments: span only the lifelines they cover
    for fr in frags:
        a, b, op, cond = fr[0], fr[1], fr[2], fr[3]
        lo = min(min(msgs[k][0], msgs[k][1]) for k in range(a, b + 1))
        hi = max(max(msgs[k][0], msgs[k][1]) for k in range(a, b + 1))
        selfhi = any(msgs[k][0] == msgs[k][1] == hi for k in range(a, b + 1))
        inset = 8 * sum(1 for g in frags if g is not fr and g[0] <= a and b <= g[1] and (g[0], g[1]) != (a, b))
        fx1 = xs[lo] - 46 + inset
        fx2 = min(W - 10, xs[hi] + (150 if selfhi else 46)) - inset
        fx2 = max(fx2, fx1 + len(cond) * 6 + 80)
        y1 = rows[a]["y"] - 30 - 32 * sum(1 for g in frags if g[0] == a and g is not fr and g[1] < b)
        y2 = rows[b]["bottom"] + 6
        o.append(f'<rect x="{fx1}" y="{y1}" width="{fx2 - fx1}" height="{y2 - y1}" fill="none" stroke="#444"/>')
        o.append(f'<path d="M{fx1},{y1} L{fx1 + 40},{y1} L{fx1 + 40},{y1 + 11} L{fx1 + 33},{y1 + 18} L{fx1},{y1 + 18} Z" fill="#fff" stroke="#444"/>')
        o.append(f'<text x="{fx1 + 6}" y="{y1 + 13}" font-size="10.5" font-weight="bold">{op}</text>')
        o.append(f'<text x="{fx1 + 48}" y="{y1 + 13}" font-size="10.5" paint-order="stroke" stroke="#fff" stroke-width="4" stroke-linejoin="round">{escape(cond)}</text>')
        if len(fr) > 4:
            ye = rows[fr[4]]["y"] - 30
            o.append(f'<line x1="{fx1}" y1="{ye}" x2="{fx2}" y2="{ye}" stroke="#444" stroke-dasharray="6,4"/>')
            o.append(f'<text x="{fx1 + 8}" y="{ye + 13}" font-size="10.5" paint-order="stroke" stroke="#fff" stroke-width="4" stroke-linejoin="round">{escape(fr[5])}</text>')
    o.append("</svg>")
    return "".join(o)
