"""Class diagrams for the report, drawn from the domain model in docs/uml/generator/build_domain.py.

* package_overview(): package diagram of the 8 bounded contexts and their «use» dependencies.
* context_diagrams(): one class diagram per bounded context. Classes show attributes and the
  operations that the use cases / state machines require; classes owned by another context are
  drawn collapsed with their qualified name (Package::Class). A cross-context association is
  drawn in the diagram of the downstream context only. Enumerations and value objects are used as
  attribute types, so they are listed without connecting lines (UML typed attributes).
"""

from __future__ import annotations

import copy
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[2] / "uml" / "generator"))
import build_domain as BD  # noqa: E402
from vpxml import Diagram, Elem, class_size  # noqa: E402

P = BD.p
SHORT = {"Identity & Access": "Identity", "Partner": "Partner", "Network": "Network", "Pricing": "Pricing",
         "Shipment": "Shipment", "Exception Handling": "Exception", "Workforce & Operations": "Workforce",
         "Shared Kernel": "SharedKernel"}
# upstream -> downstream order; a cross-context association belongs to the downstream package
LAYER = {"Shared Kernel": 0, "Identity & Access": 1, "Partner": 2, "Network": 2, "Pricing": 3,
         "Shipment": 4, "Workforce & Operations": 5, "Exception Handling": 5}

# Operations derived from the use cases and the state enumerations of each aggregate.
OPS = {
    "UserAccount": ["+register(email : String, password : String)", "+verifyEmail()", "+changeStatus(status : AccountStatus)",
                    "+assignRoles(roles : Role[1..*])", "+hasPermission(code : String) : Boolean"],
    "UserProfile": ["+update(firstName, lastName, gender, dateOfBirth)", "+isComplete() : Boolean"],
    "UserAddress": ["+markDefault()"],
    "Role": ["+grant(permission : Permission)", "+revoke(permission : Permission)"],
    "Merchant": ["+verify(adminId : UUID)", "+reject(reason : MerchantStatusReason, detail : String)",
                 "+suspend(reason : MerchantStatusReason)", "+reactivate()", "+close()", "+canCreateShipment() : Boolean"],
    "MerchantPickupAddress": ["+markDefault()"],
    "ServiceArea": ["+covers(provinceCode, wardCode) : Boolean", "+tierOf(wardCode : String) : CoverageTier"],
    "Hub": ["+activate()", "+deactivate()", "+close()"],
    "HubLane": ["+addSchedule(schedule : LaneSchedule)", "+activate()", "+deactivate()"],
    "LaneSchedule": ["+runsOn(date : LocalDate) : Boolean", "+remainingCapacityKg(date : LocalDate) : BigDecimal"],
    "RouteTemplate": ["+activate()", "+archive()", "+newRevision() : RouteTemplate"],
    "LaneCapacityReservation": ["+consume()", "+release()"],
    "RatePlan": ["+newRevision() : RatePlan", "+activate(adminId : UUID)", "+retire(adminId : UUID)",
                 "+findRule(level, zone) : RateRule"],
    "RateRule": ["+calculateFee(weight, tier, cod, declaredValue) : FeeBreakdown"],
    "QuoteRequest": ["+isExpired(now : Instant) : Boolean", "+option(level : ServiceLevel) : QuoteOption"],
    "Shipment": ["+create(quote, sender, recipient, parcels)", "+markRouted(batchId : UUID)",
                 "+markRoutingFailed()", "+placeOnHold(reason : String)", "+releaseHold()", "+changeEndpoint(dest : ShipmentEndpoint)",
                 "+recordAttempt(attempt : DeliveryAttempt)", "+markDelivered(at : Instant)", "+cancel(reason : String)"],
    "Parcel": ["+updateCustody(type, refId, hub, stage)",
               "+reweigh(weight : Weight)", "+markLost()", "+markDamaged()"],
    "ShipmentExceptionRequest": ["+approve(reviewerId : UUID, note : String)", "+reject(reviewerId : UUID, note : String)",
                                 "+complete(actorId : UUID)", "+cancel()"],
    "ShipmentCase": ["+assign(userId : UUID)", "+startProgress()", "+resolve(resolution : String)", "+close()"],
    "ShipmentWeightAdjustment": ["+approve(reviewerId : UUID)", "+reject(reviewerId : UUID, note : String)"],
    "WorkforceMember": ["+joinHub(hub : Hub, primary : Boolean)", "+leaveHub(hub : Hub)",
                        "+setAvailability(status : WorkforceAvailabilityStatus)", "+canTakeAssignment() : Boolean"],
    "WorkShift": ["+assign(member : WorkforceMember)", "+start()", "+complete()", "+cancel()"],
    "WorkforceShiftAssignment": ["+checkIn(at : Instant)", "+checkOut(at : Instant)", "+markAbsent()", "+cancel(reason : String)"],
    "OperationalAssignment": ["+assign(member : WorkforceMember, by : UUID)", "+accept()", "+start()", "+complete()",
                              "+fail(note : String)", "+cancel(reason : String)"],
}


def pkg_of(e: Elem) -> str:
    while e.parent is not None and e.parent.kind != "Package":
        e = e.parent
    return e.parent.name


def view(name: str, external: bool) -> Elem:
    """Shallow copy of a model class for one diagram (same id, so associations still connect)."""
    e = copy.copy(P.by_name[name])
    if external:
        e.name = f"{SHORT[pkg_of(e)]}::{e.name}"
        e.features = []
        e.external = True
    else:
        e.ops = OPS.get(name, [])
    return e


def build(pkg: str, columns: list[list[str]], extras: list[str], hgap=150, vgap=90, wrap=1250):
    """columns: stacks of class names placed left to right ("~Name" = class of another package);
    extras: value objects / enumerations listed in rows below ("~Name" = owned by another package)."""
    d = Diagram(P, "FIG", "ClassDiagram", pkg, "")
    d.frame = f"class {pkg}"
    d.lane_gap = 34  # parallel associations between the same two classes keep their labels apart
    shown = set()
    x = 40
    bottom = 40
    for col in columns:
        y = 50
        width = 0
        for key in col:
            ext = key.startswith("~")
            name = key.lstrip("~")
            e = view(name, ext)
            w, h = class_size(e)
            d.place(e, x, y, w, h)
            shown.add(name)
            y += h + vgap
            width = max(width, w)
        bottom = max(bottom, y)
        x += width + hgap
    right = max(x - hgap, wrap)
    y, x, row_h = bottom + 10, 40, 0
    for key in extras:
        e = view(key.lstrip("~"), key.startswith("~"))
        w, h = class_size(e)
        if x + w > right and x > 40:
            x, y, row_h = 40, y + row_h + 30, 0
        d.place(e, x, y, w, h)
        x += w + 30
        row_h = max(row_h, h)
    # associations: inside the package, or crossing into it from an upstream package
    own = lambda n: pkg_of(P.by_name[n]) == pkg  # noqa: E731
    keep = set()
    for r in P.rels:
        if r.kind != "Association" or r.src.name not in shown or r.dst.name not in shown:
            continue
        a, b = pkg_of(r.src), pkg_of(r.dst)
        if a == b == pkg:
            keep.add(r.id)
        elif pkg in (a, b) and LAYER[pkg] >= LAYER[b if a == pkg else a] and (own(r.src.name) or own(r.dst.name)):
            keep.add(r.id)
    d.only_rels = keep
    return d


def enums_of(pkg):
    return [c.name for c in BD.PKGS[pkg_key(pkg)].children if c.stereotypes[0].name == "enumeration"]


def vos_of(pkg):
    return [c.name for c in BD.PKGS[pkg_key(pkg)].children if c.stereotypes[0].name == "value object"]


def foreign_types(pkg):
    """Value objects / enumerations of other packages used as attribute types here (drawn collapsed)."""
    used = []
    for c in BD.PKGS[pkg_key(pkg)].children:
        for f in c.features:
            t = f[3]
            if f[0] == "Attribute" and t in P.by_name and P.by_name[t].kind == "Class" \
                    and pkg_of(P.by_name[t]) != pkg and t not in used:
                used.append(t)
    return ["~" + t for t in used]


def pkg_key(pkg):
    return next(k for k, v in BD.PKGS.items() if v.name == pkg)


CONTEXTS = [
    ("identity", "Identity & Access",
     [["UserProfile"], ["UserAccount", "UserAddress"], ["Role"], ["Permission"]]),
    ("partner", "Partner", [["~UserAccount"], ["Merchant"], ["MerchantPickupAddress"]]),
    ("network", "Network",
     [["NetworkRegion", "Hub", "RouteTemplate"], ["ServiceArea", "HubLane", "RouteTemplateLeg"],
      ["ServiceAreaCoverage", "LaneSchedule", "LaneCapacityReservation"]]),
    ("pricing", "Pricing", [["RateRule"], ["RatePlan"], ["QuoteRequest", "QuoteOption"], ["QuoteParcel"]]),
    ("shipment", "Shipment",
     [["~Merchant", "~RatePlan", "~QuoteRequest", "~LaneCapacityReservation"], ["Shipment"],
      ["Parcel", "DeliveryAttempt"], ["ShipmentEvent", "~Hub"]]),
    ("exception", "Exception Handling",
     [["ShipmentExceptionRequest"], ["~Shipment", "~Parcel", "~Hub"], ["ShipmentCase", "ShipmentWeightAdjustment"]]),
    ("workforce", "Workforce & Operations",
     [["~UserAccount", "WorkforceAvailability", "~Shipment", "~Parcel"], ["WorkforceMember", "OperationalAssignment"],
      ["HubMembership", "~Hub", "WorkforceShiftAssignment"], ["WorkShift"]]),
]


def context_diagrams():
    out = []
    for slug, pkg, cols in CONTEXTS:
        types = vos_of(pkg) + enums_of(pkg) + foreign_types(pkg)
        if len(types) > 12:  # too many for one readable figure: classes first, then their data types
            out.append((slug, pkg, build(pkg, cols, [])))
            out.append((f"{slug}-types", pkg, build(pkg, [], types, wrap=1100)))
        else:
            out.append((slug, pkg, build(pkg, cols, types)))
    sk = build("Shared Kernel", [], vos_of("Shared Kernel") + enums_of("Shared Kernel"), wrap=900)
    out.append(("shared", "Shared Kernel", sk))
    return out


def package_svg():
    """Package diagram: bounded contexts with their aggregate roots and the «use» dependencies
    (downstream -> upstream, derived from the cross-context associations). Hand-routed."""
    from html import escape

    contents = {}
    for pk in BD.PKGS.values():
        roots = [c.name for c in pk.children if c.stereotypes[0].name == "aggregate root"]
        contents[pk.name] = roots or [c.name for c in pk.children if c.stereotypes[0].name == "value object"]
    deps = set()
    for r in P.rels:
        if r.kind == "Association" and pkg_of(r.src) != pkg_of(r.dst):
            lo, hi = sorted((pkg_of(r.src), pkg_of(r.dst)), key=lambda n: LAYER[n])
            deps.add((hi, lo))
    W, PW = 1010, 270
    pos = {"Exception Handling": (40, 60), "Workforce & Operations": (690, 60), "Shipment": (365, 230),
           "Partner": (40, 420), "Pricing": (365, 420), "Network": (690, 420),
           "Shared Kernel": (40, 640), "Identity & Access": (365, 640)}
    box = {}
    for name, (x, y) in pos.items():
        box[name] = (x, y, PW, 46 + 15 * len(contents[name]))
    L = lambda n: box[n][0]; R = lambda n: box[n][0] + box[n][2]  # noqa: E731
    T = lambda n: box[n][1]; B = lambda n: box[n][1] + box[n][3]  # noqa: E731
    CY = lambda n: box[n][1] + box[n][3] / 2  # noqa: E731
    ex, wf, sh, pa, pr, nw, idn = ("Exception Handling", "Workforce & Operations", "Shipment", "Partner", "Pricing",
                                   "Network", "Identity & Access")
    routes = {
        (ex, sh): [(R(ex), CY(ex)), (430, CY(ex)), (430, T(sh))],
        (wf, sh): [(L(wf), CY(wf)), (570, CY(wf)), (570, T(sh))],
        (wf, nw): [(860, B(wf)), (860, T(nw))],
        (ex, nw): [(150, T(ex)), (150, 40), (985, 40), (985, CY(nw)), (R(nw), CY(nw))],
        (wf, idn): [(R(wf), CY(wf) + 12), (970, CY(wf) + 12), (970, CY(idn)), (R(idn), CY(idn))],
        (sh, pa): [(L(sh), CY(sh)), (175, CY(sh)), (175, T(pa))],
        (sh, pr): [(500, B(sh)), (500, T(pr))],
        (sh, nw): [(R(sh), CY(sh)), (760, CY(sh)), (760, T(nw))],
        (pa, idn): [(175, B(pa)), (175, 600), (430, 600), (430, T(idn))],
    }
    missing = deps - set(routes)
    assert not missing, missing
    H = max(B(n) for n in box) + 150
    o = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}" '
         'font-family="DejaVu Sans, Arial, sans-serif" font-size="11">',
         '<defs><marker id="open" markerWidth="12" markerHeight="12" refX="11" refY="6" orient="auto">'
         '<path d="M0,0 L11,6 L0,12" fill="none" stroke="#333"/></marker></defs>',
         f'<rect width="{W}" height="{H}" fill="#fff"/>',
         f'<rect x="1" y="1" width="{W - 2}" height="{H - 2}" fill="none" stroke="#333" stroke-width="1.3"/>',
         '<path d="M1,1 L196,1 L196,15 L186,25 L1,25 Z" fill="#fff" stroke="#333" stroke-width="1.3"/>',
         '<text x="8" y="18" font-size="12"><tspan font-weight="bold">pkg</tspan> PAVEX Domain Model</text>']
    for name, (x, y, w, h) in box.items():
        tab = len(name) * 7.4 + 20
        o.append(f'<rect x="{x}" y="{y}" width="{tab}" height="20" fill="#ede7f6" stroke="#333"/>')
        o.append(f'<rect x="{x}" y="{y + 20}" width="{w}" height="{h - 20}" fill="#ede7f6" stroke="#333"/>')
        o.append(f'<text x="{x + 8}" y="{y + 14}" font-weight="bold">{escape(name)}</text>')
        for i, t in enumerate(contents[name]):
            o.append(f'<text x="{x + 12}" y="{y + 40 + i * 15}" font-size="10.5">{escape(t)}</text>')
    for key in sorted(deps):
        pts = routes[key]
        o.append(f'<polyline points="{" ".join(f"{a:.0f},{b:.0f}" for a, b in pts)}" fill="none" stroke="#333" '
                 'stroke-dasharray="6,4" marker-end="url(#open)"/>')
        segs = sorted(zip(pts, pts[1:]), key=lambda q: -abs(q[1][0] - q[0][0]) - abs(q[1][1] - q[0][1]))
        (x1, y1), (x2, y2) = segs[0]
        mx, my = (x1 + x2) / 2, (y1 + y2) / 2
        near_edge = x1 == x2 and x1 > W - 80
        dx, dy = (0, -5) if y1 == y2 else ((-6, 4) if near_edge else (6, 4))
        anchor = "middle" if y1 == y2 else ("end" if near_edge else "start")
        o.append(f'<text x="{mx + dx:.0f}" y="{my + dy:.0f}" font-size="10" fill="#444" text-anchor="{anchor}" '
                 'paint-order="stroke" stroke="#fff" stroke-width="3">«use»</text>')
    # UML comment attached to the Shared Kernel
    nx, ny, nw_, nh = 40, B("Shared Kernel") + 40, 330, 62
    text = ["Mọi package dùng chung các value object", "và enumeration của Shared Kernel", "(Weight, Dimensions, FeeBreakdown, ...)."]
    o.append(f'<path d="M{nx},{ny} L{nx + nw_ - 14},{ny} L{nx + nw_},{ny + 14} L{nx + nw_},{ny + nh} L{nx},{ny + nh} Z" fill="#fffde7" stroke="#333"/>')
    o.append(f'<path d="M{nx + nw_ - 14},{ny} L{nx + nw_ - 14},{ny + 14} L{nx + nw_},{ny + 14}" fill="none" stroke="#333"/>')
    o += [f'<text x="{nx + 10}" y="{ny + 20 + i * 15}" font-size="10.5">{escape(t)}</text>' for i, t in enumerate(text)]
    o.append(f'<line x1="{nx + 60}" y1="{ny}" x2="{nx + 60}" y2="{B("Shared Kernel")}" stroke="#333" stroke-dasharray="4,3"/>')
    o.append("</svg>")
    return "\n".join(o)
