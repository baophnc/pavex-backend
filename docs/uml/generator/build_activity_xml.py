"""Visual Paradigm XML for the activity diagrams AD-UCxx -> docs/uml/xml/2x-ad-ucxx.xml.

The geometry comes from the same graph that draws the report figures (docs/report/generator:
activities.py + diagrams.activity), so the imported diagram matches the PNG: same nodes, same
guards, same positions and edge bends. Each file holds one diagram with its own id prefix
(PAVEXADxx), so all files can be imported into one project.
"""

from __future__ import annotations

import pathlib
import sys

HERE = pathlib.Path(__file__).resolve().parent
OUT = HERE.parent / "xml"
sys.path.insert(0, str(HERE.parent.parent / "report" / "generator"))

from activities import ACTIVITIES, LANE  # noqa: E402
from diagrams import activity  # noqa: E402
from specs import CORE  # noqa: E402
from vpxml import Diagram, Project  # noqa: E402

KIND = {"initial": "InitialNode", "final": "ActivityFinalNode", "action": "ActivityAction", "call": "ActivityAction",
        "decision": "DecisionNode", "merge": "MergeNode", "fork": "ForkNode", "join": "JoinNode"}
NAME = {"initial": "Bắt đầu", "final": "Kết thúc", "decision": "", "merge": "", "fork": "", "join": ""}


def build(code: str, name: str):
    G = {}
    title = f"AD-{code} {name}"
    activity(title, (LANE[code], "Hệ thống PAVEX"), ACTIVITIES[code], G)
    p = Project(title, f"PAVEXAD{code[2:]}")
    lane_el = p.add("ActivitySwimlane2", f"Swimlane {code}", key="swimlane")
    parts = [p.add("ActivityPartition", lane, parent=lane_el, key=f"lane{i}") for i, lane in enumerate(G["lanes"])]
    elems = {}
    for n in G["nodes"]:
        doc = "Call behavior action: gọi use case " + n["text"].split(" ")[0] if n["kind"] == "call" else ""
        elems[n["id"]] = p.add(KIND[n["kind"]], NAME.get(n["kind"], n["text"]), doc=doc, key=n["id"])
    rels = []
    for e in G["edges"]:
        r = p.rel("ControlFlow", elems[e["src"]], elems[e["dst"]], name="")
        r.guard = e["guard"] or ""
        rels.append((r, e))
    d = p.diagram("ActivityDiagram", title, f"Sơ đồ hoạt động của use case {code} – {name}.")
    lane_shape = d.place(lane_el, G["x1"], 32, G["x3"] - G["x1"], G["H"] - 40)
    lane_shapes = [d.place(parts[0], G["x1"], 32, G["x2"] - G["x1"], G["H"] - 40),
                   d.place(parts[1], G["x2"], 32, G["x3"] - G["x2"], G["H"] - 40)]
    shapes = {}
    for n in G["nodes"]:
        shapes[n["id"]] = d.place(elems[n["id"]], n["x"] - n["w"] / 2, n["y"] - n["h"] / 2, n["w"], n["h"])
    d._routed, d._conn_ids = [], {}
    for r, e in rels:
        d._routed.append((r, shapes[e["src"]], shapes[e["dst"]], e["pts"]))
        d._conn_ids[r.id] = p.nid("CON")
    del lane_shape, lane_shapes
    return p


def main():
    OUT.mkdir(exist_ok=True)
    names = {u["code"]: u["name"] for u in CORE}
    for i, code in enumerate(ACTIVITIES, 20):
        p = build(code, names[code])
        fname = f"{i}-ad-{code.lower()}.xml"
        (OUT / fname).write_bytes(p.to_xml())
        d = p.diagrams[0]
        print(f"{fname}: {len(d.shapes)} shapes, {len(d._routed)} control flows")


if __name__ == "__main__":
    main()
