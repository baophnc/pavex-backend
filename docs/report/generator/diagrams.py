"""SVG renderers for the report figures, following UML 2.5 notation.

* activity(): activity diagram in a frame ("act <name>") with two activity
  partitions (actor | system). Initial node, actions, call behavior actions
  (included use cases), decision nodes with a guard on every outgoing edge,
  merge nodes wherever flows come together (no action ever has two incoming
  edges), fork / join bars for parallel work, activity final nodes.
* sequence(): interaction in a frame ("sd <name>"). Lifelines with
  actor / boundary / control / entity heads (robustness stereotypes, as in
  Visual Paradigm), execution specifications (activation bars, nested for self
  calls), synchronous calls (filled arrowhead), asynchronous signals to
  external systems (open arrowhead), replies (dashed, open arrowhead) and
  combined fragments alt / opt / loop / break / ref with guarded operands.
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
# Step format (see activities.py):
#   ("A" | "S", text[, label])            action in the actor / system partition
#   ("C", lane, text[, label])            call behavior action (calls another use case)
#   ("?", lane, main_guard, branches[, label])
#        branches = [(guard, action | None, target)], action = (lane, text) or ("C", lane, text);
#        target = label of the node the branch flows to (a merge node is put in front of it) or "end"
#   ("F", [(lane, text), ...][, label])   fork -> parallel actions -> join
# The main flow runs top to bottom; every branch leaves its decision to the right.

LA, LS = 380, 570            # widths of the actor / system partitions
AW, BW, D = 220, 172, 30     # main action width, branch action width, diamond size
GAP = 30


def _label(st):
    n = {"A": 2, "S": 2, "C": 3, "?": 4, "F": 2}[st[0]]
    return st[n] if len(st) > n else None


def activity(title: str, lanes: tuple[str, str], steps: list, graph: dict | None = None) -> str:
    X0 = 8
    x1, x2, x3 = X0, X0 + LA, X0 + LA + LS
    W = x3 + 8
    main_x = {"A": x1 + LA / 2, "S": x2 + 165}
    branch_x = {"A": x1 + 95, "S": x2 + 405}
    targets = {br[2] for st in steps if st[0] == "?" for br in st[3] if br[2] != "end"}
    labels = {_label(st) for st in steps if _label(st)}
    missing = targets - labels
    assert not missing, f"{title}: unknown targets {missing}"

    nodes, y = [], 108
    for st in steps:
        kind, lab = st[0], _label(st)
        lane = st[1] if kind in ("C", "?") else ("S" if kind == "F" else kind)
        if lab in targets:  # merge node in front of every node a branch flows into
            nodes.append(dict(kind="merge", lane=lane, x=main_x[lane], y=y + D / 2, h=D, label=lab))
            y += D + GAP - 6
        if kind in ("A", "S", "C"):
            text = st[1] if kind != "C" else st[2]
            lines = _wrap(text, AW - 26)
            h = max(40, 18 + 14 * len(lines))
            nodes.append(dict(kind="act", call=kind == "C", lane=lane, x=main_x[lane], y=y + h / 2, w=AW, h=h, lines=lines))
            y += h + GAP
        elif kind == "?":
            brs, by = [], y
            for k, (guard, act, target) in enumerate(st[3]):
                if act is None:
                    bh, blines, bcall, blane = 0, [], False, "S"
                else:
                    bcall = act[0] == "C"
                    blane, btext = (act[1], act[2]) if bcall else act
                    blines = _wrap(btext, BW - 22, 6.0)
                    bh = max(40, 16 + 13 * len(blines))
                cy = (y + D / 2) if k == 0 else by + max(bh, 24) / 2
                if k == 0:
                    by = y + D / 2 - max(bh, 24) / 2
                brs.append(dict(guard=guard, h=bh, lines=blines, call=bcall, x=branch_x[blane], y=cy, target=target))
                by = cy + max(bh, 24) / 2 + (40 if target == "end" else 0) + 16
            hh = max(D, by - y - 16)
            nodes.append(dict(kind="dec", lane=lane, x=main_x[lane], y=y + D / 2, h=D, main=st[2], brs=brs, span=hh))
            y += hh + GAP + 4
        else:  # fork / join
            items = st[1]
            per_lane = {}
            for ln, _t in items:
                per_lane.setdefault(ln, 0)
                per_lane[ln] += 1
            used, boxes = {}, []
            for ln, text in items:
                n_l = per_lane[ln]
                left, right = (x1 + 20, x2 - 20) if ln == "A" else (x2 + 20, x3 - 70)
                i = used.get(ln, 0)
                used[ln] = i + 1
                cx = left + (right - left) * (i + 0.5) / n_l
                lines = _wrap(text, min(AW, (right - left) / n_l - 20) - 22)
                boxes.append(dict(x=cx, lines=lines, h=max(40, 18 + 14 * len(lines)), w=min(AW, (right - left) / n_l - 20)))
            bh = max(b["h"] for b in boxes)
            fy = y + 4
            for b_ in boxes:
                b_["y"] = fy + 26 + b_["h"] / 2
            jy = fy + 26 + bh + 26
            nodes.append(dict(kind="fork", lane="S", x=main_x["S"], fy=fy, jy=jy, boxes=boxes, y=(fy + jy) / 2, h=jy - fy + 5))
            y = jy + 5 + GAP
    end_y = y + 8
    H = math.ceil(end_y + 44)

    # ---- graph: every node and edge with its geometry (drawn as SVG below, exported to VP XML) ----
    G = dict(title=title, lanes=lanes, W=W, H=H, x1=x1, x2=x2, x3=x3, nodes=[], edges=[])

    def node(kind, x, y, w, h, text="", lane="S", lines=None, size=11):
        G["nodes"].append(dict(id=f"N{len(G['nodes'])}", kind=kind, x=x, y=y, w=w, h=h, text=text, lane=lane,
                               lines=lines or [], size=size))
        return G["nodes"][-1]["id"]

    def edge(src, dst, pts, guard=None, gpos=None):
        G["edges"].append(dict(src=src, dst=dst, pts=pts, guard=guard, gpos=gpos))

    def lane_of(x):
        return "A" if x < x2 else "S"

    first = nodes[0]
    start = node("initial", first["x"], 78, 20, 20, lane=first["lane"])
    loops = []
    for i, n in enumerate(nodes):
        x, yy = n["x"], n["y"]
        if n["kind"] == "act":
            n["in"] = n["out"] = node("call" if n["call"] else "action", x, yy, n["w"], n["h"], " ".join(n["lines"]), n["lane"], n["lines"])
        elif n["kind"] == "merge":
            n["in"] = n["out"] = node("merge", x, yy, D, D, lane=n["lane"])
        elif n["kind"] == "fork":
            prev_x = nodes[i - 1]["x"] if i else x
            xs = [b_["x"] for b_ in n["boxes"]] + [prev_x, x]  # bars span the parallel branches only
            bl, br = min(xs) - 30, max(xs) + 30
            fk = node("fork", (bl + br) / 2, n["fy"] + 2.5, br - bl, 5)
            jn = node("join", (bl + br) / 2, n["jy"] + 2.5, br - bl, 5)
            for b_ in n["boxes"]:
                bid = node("action", b_["x"], b_["y"], b_["w"], b_["h"], " ".join(b_["lines"]), lane_of(b_["x"]), b_["lines"])
                edge(fk, bid, [(b_["x"], n["fy"] + 5), (b_["x"], b_["y"] - b_["h"] / 2)])
                edge(bid, jn, [(b_["x"], b_["y"] + b_["h"] / 2), (b_["x"], n["jy"])])
            n["in"], n["out"] = fk, jn
        else:  # decision
            d = D / 2
            dec = node("decision", x, yy, D, D, lane=n["lane"])
            n["in"] = n["out"] = dec
            trunk = x + d + 22
            for k, br_ in enumerate(n["brs"]):
                bx, by, bh = br_["x"], br_["y"], br_["h"]
                left = bx - BW / 2 if bh else bx - 11
                lead = [(x + d, yy), (left, yy)] if k == 0 else [(trunk, yy), (trunk, by), (left, by)]
                gx = x + d + 5 if k == 0 else trunk + 5
                gpos = dict(x=gx, y=by - 6, width=left - gx - 8, anchor="start")
                if bh:
                    box = node("call" if br_["call"] else "action", bx, by, BW, bh, " ".join(br_["lines"]), lane_of(bx), br_["lines"], 10)
                    edge(dec, box, lead, br_["guard"], gpos)
                    if br_["target"] == "end":
                        fin = node("final", bx, by + bh / 2 + 27, 22, 22, lane=lane_of(bx))
                        edge(box, fin, [(bx, by + bh / 2), (bx, by + bh / 2 + 16)])
                    else:
                        loops.append((box, [(bx + BW / 2, by)], by, br_["target"], None, None))
                elif br_["target"] == "end":
                    fin = node("final", bx, by, 22, 22, lane=lane_of(bx))
                    edge(dec, fin, lead, br_["guard"], gpos)
                else:  # no action: one continuous edge from the decision to the merge
                    loops.append((dec, lead, by, br_["target"], br_["guard"], gpos))
    order = [n for n in nodes]
    for i, n in enumerate(order[:-1]):
        nx = order[i + 1]
        sx, sy = n["x"], (n["jy"] + 5 if n["kind"] == "fork" else n["y"] + n["h"] / 2)
        ex, ey = (sx if nx["kind"] == "fork" else nx["x"]), (nx["fy"] if nx["kind"] == "fork" else nx["y"] - nx["h"] / 2)
        pts = [(sx, sy), (ex, ey)] if abs(sx - ex) < 1 else [(sx, sy), (sx, ey - 14), (ex, ey - 14), (ex, ey)]
        if n["kind"] == "dec":  # guard of the main flow sits left of the edge, branches go right
            edge(n["out"], nx["in"], pts, n["main"], dict(x=n["x"] - 6, y=sy + 13, width=150, anchor="end"))
        else:
            edge(n["out"], nx["in"], pts)
    first_top = first["fy"] if first["kind"] == "fork" else first["y"] - first["h"] / 2
    edge(start, first["in"], [(first["x"], 88), (first["x"], first_top)])
    merge_by_label = {n["label"]: n for n in nodes if n["kind"] == "merge"}
    # the loop spanning more of the diagram takes the outer channel, so loops nest instead of crossing
    for k, (src, lead, by, target, guard, gpos) in enumerate(sorted(loops, key=lambda q: -abs(q[2] - merge_by_label[q[3]]["y"]))):
        m = merge_by_label[target]
        rx = x3 - 10 - 9 * k
        edge(src, m["in"], lead + [(rx, by), (rx, m["y"]), (m["x"] + D / 2, m["y"])], guard, gpos)
    last = nodes[-1]
    fin = node("final", last["x"], end_y + 14, 22, 22, lane=last["lane"])
    edge(last["out"], fin, [(last["x"], last["jy"] + 5 if last["kind"] == "fork" else last["y"] + last["h"] / 2), (last["x"], end_y + 3)])
    if graph is not None:
        graph.update(G)
    return _activity_svg(G)


def _activity_svg(G):
    W, H, x1, x2, x3 = G["W"], G["H"], G["x1"], G["x2"], G["x3"]
    o = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}" font-family="{FONT}">',
         _defs(), f'<rect width="{W}" height="{H}" fill="#fff"/>', _frame(W, H, f"act {G['title']}")]
    top, bottom = 32, H - 8
    o.append(f'<rect x="{x1}" y="{top}" width="{x3 - x1}" height="{bottom - top}" fill="none" stroke="{STROKE}"/>')
    o.append(f'<line x1="{x2}" y1="{top}" x2="{x2}" y2="{bottom}" stroke="{STROKE}"/>')
    o.append(f'<line x1="{x1}" y1="{top + 26}" x2="{x3}" y2="{top + 26}" stroke="{STROKE}"/>')
    o.append(_text_block((x1 + x2) / 2, top + 13, [G["lanes"][0]], 11.5, "bold"))
    o.append(_text_block((x2 + x3) / 2, top + 13, [G["lanes"][1]], 11.5, "bold"))
    for e in G["edges"]:
        path = "M" + " L".join(f"{px:.1f},{py:.1f}" for px, py in e["pts"])
        o.append(f'<path d="{path}" fill="none" stroke="{STROKE}" marker-end="url(#arr)"/>')
    for e in G["edges"]:
        if e["guard"]:
            g = e["gpos"]
            lines = _wrap(f"[{e['guard']}]", g["width"], 5.6)
            o += [f'<text x="{g["x"]:.1f}" y="{g["y"] - (len(lines) - 1 - i) * 12 if g["anchor"] == "start" else g["y"] + i * 12:.1f}" '
                  f'font-size="10" text-anchor="{g["anchor"]}" fill="#222" paint-order="stroke" stroke="#fff" stroke-width="3">{escape(t)}</text>'
                  for i, t in enumerate(lines)]
    for n in G["nodes"]:
        x, yy, w, h = n["x"], n["y"], n["w"], n["h"]
        k = n["kind"]
        if k == "initial":
            o.append(f'<circle cx="{x}" cy="{yy}" r="10" fill="{STROKE}"/>')
        elif k == "final":
            o.append(f'<circle cx="{x}" cy="{yy}" r="11" fill="#fff" stroke="{STROKE}" stroke-width="1.5"/><circle cx="{x}" cy="{yy}" r="6.5" fill="{STROKE}"/>')
        elif k in ("decision", "merge"):
            d = D / 2
            o.append(f'<path d="M{x},{yy - d} L{x + d},{yy} L{x},{yy + d} L{x - d},{yy} Z" fill="#fff" stroke="{STROKE}" stroke-width="1.2"/>')
        elif k in ("fork", "join"):
            o.append(f'<rect x="{x - w / 2}" y="{yy - h / 2}" width="{w}" height="{h}" fill="{STROKE}"/>')
        else:
            call = k == "call"
            o.append(f'<rect x="{x - w / 2}" y="{yy - h / 2}" width="{w}" height="{h}" rx="13" fill="{FILL}" stroke="{STROKE}"/>')
            o.append(_text_block(x, yy - (4 if call else 0), n["lines"], n["size"]))
            if call:  # call behavior action: rake symbol in the lower right corner
                rx, ry = x + w / 2 - 16, yy + h / 2 - 13
                o.append(f'<path d="M{rx},{ry} L{rx},{ry + 9} M{rx - 6},{ry + 4} L{rx + 6},{ry + 4} M{rx - 6},{ry + 4} L{rx - 6},{ry + 9} '
                         f'M{rx + 6},{ry + 4} L{rx + 6},{ry + 9}" fill="none" stroke="{STROKE}" stroke-width="1.3"/>')
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


def _frags(frags):
    """Normalise fragments to dicts: a, b (first / last message), op, cond, elses [(message, guard)]."""
    out = []
    for fr in frags or []:
        if isinstance(fr, dict):
            out.append(fr)
        else:
            elses = [(fr[4], fr[5])] if len(fr) > 4 else []
            out.append(dict(a=fr[0], b=fr[1], op=fr[2], cond=fr[3], elses=elses))
    return out


def _is_ret(m):
    return len(m) > 3 and m[3] == "ret"


def _activations(parts, msgs, frags):
    """Execution specifications: (lifeline, first_msg, last_msg, self_call)."""
    n = len(msgs)
    out = []

    def involved(k, t):
        return msgs[k][0] == t or msgs[k][1] == t

    for i, m in enumerate(msgs):
        f, t = m[0], m[1]
        if _is_ret(m) or parts[t][0] == "actor":
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
            if tj == t and fj == f and not _is_ret(msgs[j]):
                cand = [k for k in range(i + 1, j) if involved(k, t)]
                end = cand[-1] if cand else i  # caller starts a new call: previous one is over
                break
        if end is None:
            cand = [k for k in range(i + 1, n) if involved(k, t)]
            end = cand[-1] if cand and parts[t][0] in ("boundary", "control") else i
        # a reply inside a break / alt / opt operand does not end a call that began before the fragment:
        # the call lasts until its reply after the fragment, or else until its last reply in the fragment
        for fr in sorted(frags, key=lambda q: q["b"] - q["a"]):
            if i < fr["a"] <= end <= fr["b"]:
                after = None
                for j in range(fr["b"] + 1, n):
                    if msgs[j][0] == f and msgs[j][1] == t and not _is_ret(msgs[j]):
                        break
                    if msgs[j][0] == t and msgs[j][1] == f:
                        after = j
                        break
                inside = [j for j in range(fr["a"], fr["b"] + 1) if msgs[j][0] == t and msgs[j][1] == f]
                end = after if after is not None else max(inside + [end])
        out.append((t, i, end, False))
    return out


def sequence(title: str, parts: list, msgs: list, frags: list | None = None) -> str:
    """parts: [(kind, name)], kind in actor|boundary|control|entity|system.
    msgs: (from, to, text) or (from, to, text, "ret"); from == to is a self call;
    a call to a «system» participant is an asynchronous signal.
    frags: dicts (a, b, op, cond, elses) or tuples (a, b, op, cond[, else_msg, guard]);
    op in alt / opt / loop / break / ref."""
    frags = _frags(frags)
    COL = 190
    W = COL * len(parts) + 50
    xs = [30 + COL / 2 + i * COL for i in range(len(parts))]

    def inner(g, fr):  # g lies strictly inside fr
        return g is not fr and fr["a"] <= g["a"] and g["b"] <= fr["b"] and (g["b"] - g["a"]) < (fr["b"] - fr["a"])

    top = 112
    y = top + 22
    rows = []
    num = 0
    for k, m in enumerate(msgs):
        f, t, text = m[0], m[1], m[2]
        ret = _is_ret(m)
        y += 30 * sum(1 for fr in frags for e, _g in fr["elses"] if e == k)
        y += 32 * sum(1 for fr in frags if fr["a"] == k)
        prefix = "" if ret else f"{(num := num + 1)}: "
        span = COL * abs(t - f) - 26 if f != t else COL - 40
        lines = _wrap(text, span - len(prefix) * 6.1, 6.1)  # keep the number on the first line
        lines[0] = prefix + lines[0]
        ly = y + 14 * (len(lines) - 1) + 5
        h = 14 * (len(lines) - 1) + (30 if f == t else 12)
        rows.append(dict(y=y, ly=ly, f=f, t=t, lines=lines, ret=ret, bottom=y + h))
        y += h + 12
        y += 10 * sum(1 for fr in frags if fr["b"] == k)
    H = math.ceil(y + 30)
    o = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}" font-family="{FONT}">',
         _defs(), f'<rect width="{W}" height="{H}" fill="#fff"/>', _frame(W, H, f"sd {title}")]
    for (kind, name), x in zip(parts, xs):
        o.append(_icon(kind, x, 66, name))
        o.append(_text_block(x, 66 if kind == "system" else 102, [name], 11))
        o.append(f'<line x1="{x}" y1="{top}" x2="{x}" y2="{H - 16}" stroke="{STROKE}" stroke-dasharray="5,4"/>')
    # execution specifications
    placed = []
    for t, a, b, selfc in sorted(_activations(parts, msgs, frags), key=lambda q: (q[1], q[3])):
        if selfc:
            y1, y2 = rows[a]["ly"] + 16, rows[a]["ly"] + 30
        else:
            y1, y2 = rows[a]["ly"] - 2, max(rows[b]["ly"] + 2, rows[a]["ly"] + 18)
        depth = sum(1 for (tt, yy1, yy2) in placed if tt == t and yy1 <= y1 < yy2)
        placed.append((t, y1, y2))
        x = xs[t] - 5 + 6 * depth
        o.append(f'<rect x="{x}" y="{y1}" width="10" height="{y2 - y1}" fill="#fff" stroke="{STROKE}"/>')
    halo = 'paint-order="stroke" stroke="#fff" stroke-width="4" stroke-linejoin="round"'
    for r in rows:
        f, t = r["f"], r["t"]
        x1, x2 = xs[f], xs[t]
        if f == t:
            ty = r["y"] + 4
            o.append("".join(f'<text x="{x1 + 14}" y="{ty + i * 14}" font-size="10.5" {halo}>{escape(s)}</text>' for i, s in enumerate(r["lines"])))
            ly = r["ly"] + 6
            o.append(f'<path d="M{x1 + 5},{ly} L{x1 + 42},{ly} L{x1 + 42},{ly + 14} L{x1 + 12},{ly + 14}" fill="none" stroke="{STROKE}" marker-end="url(#arr)"/>')
            continue
        ly = r["ly"]
        mid = (x1 + x2) / 2
        o.append("".join(f'<text x="{mid}" y="{r["y"] + i * 14}" font-size="10.5" text-anchor="middle" {halo}>{escape(s)}</text>' for i, s in enumerate(r["lines"])))
        right = x2 > x1
        sx = x1 + ((5 if right else -5) if parts[f][0] != "actor" else 0)
        ex = x2 - ((5 if right else -5) if parts[t][0] != "actor" else 0)
        if r["ret"]:
            o.append(f'<line x1="{sx}" y1="{ly}" x2="{ex}" y2="{ly}" stroke="{STROKE}" stroke-dasharray="6,4" marker-end="url(#open)"/>')
        elif parts[t][0] == "system":
            o.append(f'<line x1="{sx}" y1="{ly}" x2="{ex}" y2="{ly}" stroke="{STROKE}" marker-end="url(#open)"/>')
        else:
            o.append(f'<line x1="{sx}" y1="{ly}" x2="{ex}" y2="{ly}" stroke="{STROKE}" marker-end="url(#arr)"/>')
    # combined fragments: span only the lifelines they cover; nested ones are inset
    for fr in frags:
        a, b, op, cond = fr["a"], fr["b"], fr["op"], fr["cond"]
        lo = min(min(msgs[k][0], msgs[k][1]) for k in range(a, b + 1))
        hi = max(max(msgs[k][0], msgs[k][1]) for k in range(a, b + 1))
        selfhi = any(msgs[k][0] == msgs[k][1] == hi for k in range(a, b + 1))
        depth = sum(1 for g in frags if inner(fr, g))
        fx1 = xs[lo] - 52 + 9 * depth
        fx2 = min(W - 10, xs[hi] + (150 if selfhi else 52)) - 9 * depth
        fx2 = max(fx2, fx1 + len(cond) * 6 + 80)
        y1 = rows[a]["y"] - 30 - 32 * sum(1 for g in frags if g["a"] == a and inner(g, fr))
        y2 = rows[b]["bottom"] + 6 + 10 * sum(1 for g in frags if g["b"] == b and inner(g, fr))
        tw = max(40, len(op) * 7 + 12)
        o.append(f'<rect x="{fx1}" y="{y1}" width="{fx2 - fx1}" height="{y2 - y1}" fill="none" stroke="#444"/>')
        o.append(f'<path d="M{fx1},{y1} L{fx1 + tw},{y1} L{fx1 + tw},{y1 + 11} L{fx1 + tw - 7},{y1 + 18} L{fx1},{y1 + 18} Z" fill="#fff" stroke="#444"/>')
        o.append(f'<text x="{fx1 + 6}" y="{y1 + 13}" font-size="10.5" font-weight="bold">{op}</text>')
        o.append(f'<text x="{fx1 + tw + 8}" y="{y1 + 13}" font-size="10.5" {halo}>{escape(cond)}</text>')
        for e, guard in fr["elses"]:
            ye = rows[e]["y"] - 30 - 32 * sum(1 for g in frags if g["a"] == e and inner(g, fr))
            o.append(f'<line x1="{fx1}" y1="{ye}" x2="{fx2}" y2="{ye}" stroke="#444" stroke-dasharray="6,4"/>')
            o.append(f'<text x="{fx1 + 8}" y="{ye + 13}" font-size="10.5" {halo}>{escape(guard)}</text>')
    o.append("</svg>")
    return "".join(o)
