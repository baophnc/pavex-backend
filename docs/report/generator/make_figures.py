"""Render activity / sequence figures for the core use cases to ../figures (SVG + PNG)."""
import pathlib
import subprocess
import sys

from diagrams import activity, sequence
from specs import CORE

OUT = pathlib.Path(__file__).resolve().parent.parent / "figures"
OUT.mkdir(exist_ok=True)


def slug(uc):
    return uc["code"].split(" ")[0].lower()


def domain_entities():
    """Compact domain model: entities / aggregate roots only, names without attributes."""
    sys.path.insert(0, str(OUT.parent.parent / "uml" / "generator"))
    import build_domain as BD
    from vpxml import Diagram

    p = BD.p
    ents = [e for e in p.by_name.values() if e.kind == "Class" and e.stereotypes[0].name in ("entity", "aggregate root")]
    saved = {e.id: e.features for e in ents}
    for e in ents:
        e.features = []
    d = Diagram(p, "TMP", "ClassDiagram", "PAVEX Domain Model – entities", "")
    d.rows([["UserProfile", "UserAccount", "UserAddress", "Role", "Permission"],
            ["MerchantPickupAddress", "Merchant", "WorkforceMember", "WorkforceAvailability", "HubMembership"],
            ["QuoteParcel", "QuoteRequest", "Shipment", "Parcel", "OperationalAssignment", "WorkforceShiftAssignment", "WorkShift"],
            ["QuoteOption", "RatePlan", "RateRule", "ShipmentEvent", "DeliveryAttempt", "Hub", "HubLane", "LaneSchedule"],
            ["ShipmentExceptionRequest", "ShipmentCase", "ShipmentWeightAdjustment", "LaneCapacityReservation", "RouteTemplate", "RouteTemplateLeg"],
            ["NetworkRegion", "ServiceArea", "ServiceAreaCoverage"]], hgap=46, vgap=95)
    d.only_rels = {r.id for r in p.rels if r.kind == "Association"}
    d.labels = False
    (OUT / "domain-entities.svg").write_text(d.to_svg(), encoding="utf-8")
    for e in ents:
        e.features = saved[e.id]


def main():
    domain_entities()
    svgs = []
    for uc in CORE:
        lane_actor = uc["actor"].split(" (")[0].split(",")[0]
        a = activity(uc["name"], (lane_actor, "Hệ thống"), uc["activity"])
        s = sequence(uc["name"], uc["seq"]["parts"], uc["seq"]["msgs"], uc["seq"].get("frags"))
        for kind, svg in (("act", a), ("seq", s)):
            p = OUT / f"{slug(uc)}-{kind}.svg"
            p.write_text(svg, encoding="utf-8")
            svgs.append(str(p))
    if len(sys.argv) > 1:  # path to the svg->png renderer (node + playwright)
        subprocess.run(["node", sys.argv[1], str(OUT)], check=True)


if __name__ == "__main__":
    main()
