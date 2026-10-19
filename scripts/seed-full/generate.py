#!/usr/bin/env python3
"""
Generates the nationwide Pavex network and its staff from the administrative catalogue:

- one post office (LOCAL) per ward, one provincial post office (PROVINCIAL, "bưu cục tổng") per province and one
  regional centre (REGIONAL_GATEWAY) for each of the north, centre and south;
- a service area per post office, priced by the ward (remote highland communes, islands);
- lanes with daily schedules: post office <-> provincial post office (two runs a day), provincial post office <->
  regional centre, regional centre <-> regional centre;
- staff, exactly one manager per hub:
    post office          1 manager + 2 couriers
    provincial office    1 manager + 4 warehouse staff + 1 driver per route of about 8 neighbouring post offices
                         + 1 reserve driver per 6 routes + 2 trunk drivers to the regional centre
    regional centre      1 manager + 6 warehouse staff, and 2 drivers for each pair of regions
  plus admin@pavex.test and vanhanh@pavex.test. No shops, customers or shipments: those are made by hand.

Emails follow the hub code so any account can be guessed: truongbc.hn-badinh@pavex.test, buuta1.hn-badinh@...,
quanly.kho-hn@..., kho1.kho-hn@..., taixe.kho-hn-t01@..., taixe.kho-hn-dp1@..., taixe.kho-hn-truc1@...,
quanly.tt-mienbac@..., taixe.mienbac-mientrung-1@... People's names are fictional.

Everything is deterministic: the same catalogue gives the same ids, codes, names and emails. The generator checks
the rules above (one post office per served ward, one manager per hub, ...) and stops if one is broken.

Input:  --catalog DIR with provinces.tsv (code, name) and wards.tsv (code, province_code, name, full_name, code_name)
Output: --out DIR with network.sql, identity.sql, operations.sql, staff.csv and summary.json
"""
import argparse
import csv
import datetime as dt
import hashlib
import json
import math
import os
import random
import unicodedata
import uuid

NAMESPACE = uuid.UUID("6f1d2c1e-8a8f-4d0e-9f61-5a7e0c2b9a10")
ADMIN_ID = "00000000-0000-0000-0000-000000000001"
DOMAIN = "pavex.test"
TODAY = dt.date(2026, 10, 1)

# region -> (id, label, code suffix, province of the regional centre); ids match scripts/seed-mini.
REGIONS = {
    "NORTH": ("30000000-0000-0000-0000-000000000001", "Miền Bắc", "MIENBAC", "01"),
    "CENTRAL": ("30000000-0000-0000-0000-000000000002", "Miền Trung", "MIENTRUNG", "48"),
    "SOUTH": ("30000000-0000-0000-0000-000000000003", "Miền Nam", "MIENNAM", "79"),
}
REGION_PAIRS = {("NORTH", "CENTRAL"): 14, ("CENTRAL", "SOUTH"): 16, ("NORTH", "SOUTH"): 30}  # road hours

# province -> (short code, region, road hours to the regional centre, latitude, longitude of the provincial seat).
# Coordinates are approximate: hubs are spread around the seat so the fleet map has somewhere to draw them.
PROVINCES = {
    "01": ("HN", "NORTH", 1, 21.028, 105.854), "04": ("CB", "NORTH", 6, 22.666, 106.258),
    "08": ("TQ", "NORTH", 3, 21.818, 105.211), "11": ("DB", "NORTH", 10, 21.386, 103.023),
    "12": ("LCH", "NORTH", 9, 22.396, 103.458), "14": ("SL", "NORTH", 6, 21.327, 103.914),
    "15": ("LCA", "NORTH", 5, 21.705, 104.875), "19": ("TN", "NORTH", 2, 21.594, 105.848),
    "20": ("LS", "NORTH", 3, 21.853, 106.761), "22": ("QNI", "NORTH", 3, 20.951, 107.080),
    "24": ("BN", "NORTH", 1, 21.273, 106.194), "25": ("PT", "NORTH", 2, 21.322, 105.402),
    "31": ("HP", "NORTH", 2, 20.845, 106.688), "33": ("HY", "NORTH", 2, 20.646, 106.051),
    "37": ("NB", "NORTH", 2, 20.251, 105.975),
    "38": ("TH", "CENTRAL", 12, 19.807, 105.776), "40": ("NA", "CENTRAL", 10, 18.679, 105.681),
    "42": ("HT", "CENTRAL", 8, 18.342, 105.906), "44": ("QT", "CENTRAL", 4, 17.468, 106.622),
    "46": ("HUE", "CENTRAL", 2, 16.463, 107.590), "48": ("DN", "CENTRAL", 1, 16.054, 108.202),
    "51": ("QNG", "CENTRAL", 3, 15.120, 108.792), "52": ("GL", "CENTRAL", 6, 13.782, 109.219),
    "56": ("KH", "CENTRAL", 10, 12.238, 109.197), "66": ("DLK", "CENTRAL", 10, 12.667, 108.038),
    "68": ("LD", "CENTRAL", 14, 11.940, 108.458),
    "75": ("DNA", "SOUTH", 2, 10.957, 106.843), "79": ("HCM", "SOUTH", 1, 10.776, 106.700),
    "80": ("TNI", "SOUTH", 2, 10.535, 106.413), "82": ("DT", "SOUTH", 4, 10.360, 106.360),
    "86": ("VL", "SOUTH", 3, 10.253, 105.972), "91": ("AG", "SOUTH", 6, 10.012, 105.081),
    "92": ("CT", "SOUTH", 4, 10.045, 105.747), "96": ("CM", "SOUTH", 7, 9.177, 105.152),
}
BIG_CITIES = {"01", "79", "48", "31", "92"}
REMOTE_PROVINCES = {"04", "08", "11", "12", "14", "15", "20"}  # northern highlands: remote-area surcharge
UNSERVED_WARDS = {"Đặc khu Hoàng Sa"}

ROUTE_SIZE = 8  # post offices per route truck
ROUTES_PER_RESERVE = 6  # one reserve driver covers the weekly day off of this many route drivers
CAPACITY = {"COURIER": 20, "WAREHOUSE_OPERATOR": 100, "HUB_MANAGER": 50, "LINE_HAUL_DRIVER": 100}
PROFILE = {"COURIER": (0.85, 20, 40), "LINE_HAUL_DRIVER": (0.98, 25, 55), "WAREHOUSE_OPERATOR": (0.55, 20, 45),
           "HUB_MANAGER": (0.6, 28, 52)}

SURNAMES = [
    ("Nguyễn", 38), ("Trần", 11), ("Lê", 9.5), ("Phạm", 7), ("Hoàng", 5), ("Phan", 4.5), ("Vũ", 4), ("Huỳnh", 2),
    ("Võ", 2), ("Đặng", 2), ("Bùi", 2), ("Đỗ", 1.4), ("Hồ", 1.3), ("Ngô", 1.3), ("Dương", 1), ("Lý", 0.5),
    ("Trịnh", 0.5), ("Đinh", 0.5), ("Mai", 0.3), ("Lâm", 0.3), ("Cao", 0.3), ("Đoàn", 0.3), ("Lương", 0.3),
    ("Hà", 0.3), ("Tạ", 0.2), ("Tô", 0.2), ("Châu", 0.2), ("Lưu", 0.2), ("Thái", 0.2), ("Trương", 0.6),
]
REGIONAL_SURNAMES = {
    "NORTH": {"Hoàng": 1.6, "Vũ": 1.6, "Đỗ": 1.4, "Huỳnh": 0.2, "Võ": 0.4},
    "CENTRAL": {"Hồ": 2.0, "Phan": 1.4, "Võ": 1.5, "Lê": 1.2},
    "SOUTH": {"Huỳnh": 3.0, "Võ": 2.5, "Trương": 2.0, "Lâm": 2.0, "Hoàng": 0.3, "Vũ": 0.4},
}
MIDDLE = {
    "MALE": ["Văn", "Hữu", "Đức", "Minh", "Quốc", "Thanh", "Công", "Xuân", "Ngọc", "Hoàng", "Gia", "Tuấn", "Đình",
             "Trung", "Anh", "Bảo", "Nhật", "Tiến", "Thành", "Quang"],
    "FEMALE": ["Thị", "Ngọc", "Thu", "Thanh", "Minh", "Kim", "Mai", "Phương", "Bảo", "Khánh", "Hồng", "Diệu", "Mỹ",
               "Hoài", "Thùy", "Tuyết"],
}
GIVEN = {
    "MALE": ["An", "Bình", "Cường", "Dũng", "Duy", "Đạt", "Hải", "Hiếu", "Hòa", "Hùng", "Huy", "Khang", "Khánh", "Khoa",
             "Kiên", "Lâm", "Long", "Luân", "Minh", "Nam", "Nghĩa", "Nhân", "Phong", "Phúc", "Quân", "Quang", "Sơn",
             "Tài", "Tâm", "Thắng", "Thành", "Thịnh", "Tín", "Toàn", "Trí", "Trung", "Tuấn", "Tùng", "Vinh", "Vũ"],
    "FEMALE": ["Anh", "Châu", "Chi", "Dung", "Duyên", "Giang", "Hà", "Hạnh", "Hằng", "Hiền", "Hoa", "Hương", "Huyền",
               "Lan", "Linh", "Loan", "Ly", "Mai", "My", "Nga", "Ngân", "Ngọc", "Nhi", "Nhung", "Oanh", "Phương",
               "Quỳnh", "Tâm", "Thảo", "Thư", "Trang", "Trinh", "Uyên", "Vân", "Vy", "Yến"],
}
STREETS = ["Lê Lợi", "Trần Hưng Đạo", "Nguyễn Huệ", "Hai Bà Trưng", "Lý Thường Kiệt", "Quang Trung", "Lê Duẩn",
           "Nguyễn Trãi", "Phan Chu Trinh", "Hùng Vương", "Điện Biên Phủ", "Võ Thị Sáu", "Nguyễn Du", "Bà Triệu",
           "Trần Phú", "Lạc Long Quân", "Nguyễn Văn Linh", "Tôn Đức Thắng", "Hoàng Diệu", "Phạm Văn Đồng"]


def uid(*parts):
    return str(uuid.uuid5(NAMESPACE, ":".join(str(part) for part in parts)))


def ascii_slug(text):
    text = unicodedata.normalize("NFD", text.replace("đ", "d").replace("Đ", "D"))
    return "".join(ch for ch in text if unicodedata.category(ch) != "Mn")


def spread(key, lat, lon, radius):
    """A fixed point within `radius` degrees of (lat, lon), derived from `key`."""
    digest = hashlib.sha256(key.encode()).digest()
    angle = int.from_bytes(digest[:4], "big") / 2**32 * 2 * math.pi
    distance = math.sqrt(int.from_bytes(digest[4:8], "big") / 2**32) * radius
    return round(lat + distance * math.sin(angle), 6), round(lon + distance * math.cos(angle), 6)


class Copy:
    """Accumulates rows and renders PostgreSQL COPY blocks."""

    def __init__(self):
        self.tables = {}

    def add(self, table, columns, row):
        self.tables.setdefault(table, (columns, []))[1].append(row)

    @staticmethod
    def cell(value):
        if value is None:
            return r"\N"
        if isinstance(value, bool):
            return "t" if value else "f"
        if isinstance(value, list):
            value = "{" + ",".join(value) + "}"
        return str(value).replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n").replace("\r", "\\r")

    def render(self, table=None):
        out = []
        for name, (columns, rows) in self.tables.items():
            if table and name != table:
                continue
            out.append(f"COPY {name} ({', '.join(columns)}) FROM stdin;")
            out.extend("\t".join(self.cell(value) for value in row) for row in rows)
            out.append("\\.")
        return "\n".join(out)

    def count(self, table):
        return len(self.tables.get(table, (None, []))[1])


class People:
    """Fictional Vietnamese people: regional surnames, an age that fits the job, unique phone numbers."""

    CARRIERS = [(["032", "033", "034", "035", "036", "037", "038", "039", "086", "096", "097", "098"], 50),
                (["081", "082", "083", "084", "085", "088", "091", "094"], 25),
                (["070", "076", "077", "078", "079", "089", "090", "093"], 20),
                (["052", "056", "058", "092"], 5)]

    def __init__(self, rng):
        self.rng = rng
        self.phones = set()

    def person(self, kind, region):
        male_share, youngest, oldest = PROFILE[kind]
        gender = "MALE" if self.rng.random() < male_share else "FEMALE"
        boost = REGIONAL_SURNAMES.get(region, {})
        last = self.rng.choices([name for name, _ in SURNAMES], [w * boost.get(name, 1.0) for name, w in SURNAMES])[0]
        first = f"{self.rng.choice(MIDDLE[gender])} {self.rng.choice(GIVEN[gender])}"
        age = self.rng.randint(youngest, oldest)
        birth = TODAY - dt.timedelta(days=age * 365 + self.rng.randrange(0, 365))
        return {"gender": gender, "firstName": first, "lastName": last, "dateOfBirth": birth.isoformat(),
                "phone": self.phone()}

    def phone(self):
        while True:
            prefixes = self.rng.choices([items for items, _ in self.CARRIERS], [w for _, w in self.CARRIERS])[0]
            number = self.rng.choice(prefixes) + f"{self.rng.randrange(0, 10_000_000):07d}"
            if number not in self.phones:
                self.phones.add(number)
                return number


def read_tsv(path):
    with open(path, encoding="utf-8") as handle:
        return [line.rstrip("\n").split("\t") for line in handle if line.strip()]


def build(provinces, wards):
    rng = random.Random(20261001)
    missing = set(provinces) - set(PROVINCES)
    if missing:
        raise SystemExit(f"Provinces without a region mapping in generate.py: {sorted(missing)}")

    network = Copy()
    hubs = []
    wards_by_province = {}
    for ward in wards:
        wards_by_province.setdefault(ward["province"], []).append(ward)

    hub_columns = ["id", "code", "name", "type", "status", "address_line", "province_code", "ward_code",
                   "parent_hub_id", "public_visible", "public_name", "latitude", "longitude", "created_at",
                   "updated_at", "version"]

    def add_hub(code, name, kind, ward, parent, lat, lon, active=True):
        hub = {"id": uid("hub", code), "code": code, "name": name, "type": kind, "province": ward["province"],
               "ward": ward, "parent": parent, "active": active, "region": PROVINCES[ward["province"]][1]}
        address = f"Số {rng.randrange(1, 399)} {rng.choice(STREETS)}, {ward['fullName']}, {provinces[ward['province']]}"
        network.add("network.hubs", hub_columns, [
            hub["id"], code, name, kind, "ACTIVE" if active else "INACTIVE", address, ward["province"], ward["code"],
            parent["id"] if parent else None, active, name, lat, lon, "now", "now", 0])
        hubs.append(hub)
        return hub

    def seat_ward(province):
        items = wards_by_province[province]
        return next((ward for ward in items if ward["fullName"].startswith("Phường")), items[0])

    gateways = {}
    for region, (_, label, suffix, province) in REGIONS.items():
        _, _, _, lat, lon = PROVINCES[province]
        gateways[region] = add_hub(f"TT-{suffix}", f"Trung tâm khai thác {label.lower()}", "REGIONAL_GATEWAY",
                                   seat_ward(province), None, *spread(f"TT-{suffix}", lat, lon, 0.03))

    provincials = {}
    for province in sorted(provinces):
        short, region, _, lat, lon = PROVINCES[province]
        provincials[province] = add_hub(f"KHO-{short}", f"Bưu cục tổng {provinces[province]}", "PROVINCIAL",
                                        seat_ward(province), gateways[region], *spread(f"KHO-{short}", lat, lon, 0.02))

    offices = []
    used = set()
    for ward in wards:
        short, _, _, lat, lon = PROVINCES[ward["province"]]
        base = f"BC-{short}-{ward['codeName'].replace('_', '').upper()}"[:44]
        code, suffix = base, 2
        while code in used:
            code = f"{base[:41]}-{suffix}"
            suffix += 1
        used.add(code)
        island = ward["fullName"].startswith("Đặc khu")
        urban = ward["fullName"].startswith("Phường")
        radius = 0.06 if urban and ward["province"] in BIG_CITIES else (0.15 if urban else 0.4)
        hub = add_hub(code, f"Bưu cục {ward['name']}, {provinces[ward['province']]}", "LOCAL", ward,
                      provincials[ward["province"]], *spread(code, lat, lon, radius),
                      active=ward["fullName"] not in UNSERVED_WARDS)
        hub["wardKind"] = "ISLAND" if island else ("URBAN" if urban else "RURAL")
        hub["slug"] = code[3:].lower()
        offices.append(hub)
    for province, hub in provincials.items():
        hub["slug"] = hub["code"].lower()
    for hub in gateways.values():
        hub["slug"] = hub["code"].lower()

    # ---- Service areas: one per post office ------------------------------------------------------------
    area_columns = ["id", "code", "name", "region_id", "primary_hub_id", "status", "created_at", "updated_at", "version"]
    coverage_columns = ["id", "service_area_id", "province_code", "ward_code", "tier", "is_active", "created_at",
                        "updated_at"]
    for hub in offices:
        tier = "ISLAND" if hub["wardKind"] == "ISLAND" else (
            "REMOTE" if hub["wardKind"] == "RURAL" and hub["province"] in REMOTE_PROVINCES else "STANDARD")
        area_id = uid("area", hub["code"])
        network.add("network.service_areas", area_columns, [
            area_id, f"SA-{hub['code'][3:]}"[:50], hub["ward"]["fullName"], REGIONS[hub["region"]][0], hub["id"],
            "ACTIVE" if hub["active"] else "INACTIVE", "now", "now", 0])
        network.add("network.service_area_coverages", coverage_columns, [
            uid("coverage", hub["code"]), area_id, hub["province"], hub["ward"]["code"], tier, hub["active"], "now", "now"])

    # ---- Lanes and daily schedules ------------------------------------------------------------------
    lane_columns = ["id", "code", "from_hub_id", "to_hub_id", "transport_mode", "estimated_transit_minutes",
                    "distance_km", "max_shipment_weight_kg", "status", "created_at", "updated_at", "version"]
    schedule_columns = ["id", "lane_id", "departure_time", "monday", "tuesday", "wednesday", "thursday", "friday",
                        "saturday", "sunday", "effective_from", "effective_to", "status", "created_at", "updated_at",
                        "version", "capacity_kg"]
    lanes = []

    def add_lane(code, source, target, mode, minutes, km, max_kg, departures, capacity):
        lane_id = uid("lane", code)
        network.add("network.hub_lanes", lane_columns, [
            lane_id, code, source["id"], target["id"], mode, minutes, km, max_kg, "ACTIVE", "now", "now", 0])
        for time in departures:
            network.add("network.lane_schedules", schedule_columns, [
                uid("schedule", code, time), lane_id, time, True, True, True, True, True, True, True,
                "2026-01-01", None, "ACTIVE", "now", "now", 0, capacity])
        lanes.append({"code": code, "from": source, "to": target})

    for hub in offices:
        if not hub["active"]:
            continue
        provincial = provincials[hub["province"]]
        island = hub["wardKind"] == "ISLAND"
        minutes = 720 if island else (90 if hub["wardKind"] == "URBAN" else 180)
        km = 250 if island else (15 if hub["wardKind"] == "URBAN" else 45)
        mode = "SEA" if island else "ROAD"
        add_lane(f"{hub['code']}-DI", hub, provincial, mode, minutes, km, 100,
                 ["08:00"] if island else ["11:00", "17:00"], 800)
        add_lane(f"{hub['code']}-VE", provincial, hub, mode, minutes, km, 100,
                 ["14:00"] if island else ["06:00", "13:00"], 800)
    for province, provincial in provincials.items():
        short, region, hours, _, _ = PROVINCES[province]
        add_lane(f"KHO-{short}-DI", provincial, gateways[region], "ROAD", hours * 60, hours * 50, 1000,
                 ["12:00", "20:00"], 8000)
        add_lane(f"KHO-{short}-VE", gateways[region], provincial, "ROAD", hours * 60, hours * 50, 1000,
                 ["04:00", "14:00"], 8000)
    for (left, right), hours in REGION_PAIRS.items():
        for source, target in ((left, right), (right, left)):
            add_lane(f"TT-{REGIONS[source][2]}-{REGIONS[target][2]}", gateways[source], gateways[target], "ROAD",
                     hours * 60, hours * 55, 1000, ["10:00", "22:00"], 30000)

    # ---- Staff ------------------------------------------------------------------------------------
    people = People(rng)
    staff = []

    def person(email, role, title, hub=None, kind=None, also=()):
        region = hub["region"] if hub else "NORTH"
        record = {"userId": uid("user", email), "email": email, "role": role, "title": title, "kind": kind,
                  "hub": hub, "also": list(also), **people.person(kind or "HUB_MANAGER", region)}
        staff.append(record)
        return record

    office_staff = [
        person("admin@pavex.test", "ADMIN", "Quản trị hệ thống"),
        person("vanhanh@pavex.test", "OPERATIONS_MANAGER", "Quản lý vận hành"),
    ]

    routes = []
    for region, gateway in gateways.items():
        slug = gateway["slug"]
        person(f"quanly.{slug}@{DOMAIN}", "HUB_MANAGER", "Quản lý trung tâm", gateway, "HUB_MANAGER")
        for index in range(1, 7):
            person(f"kho{index}.{slug}@{DOMAIN}", "WAREHOUSE_OPERATOR", "Nhân viên khai thác", gateway,
                   "WAREHOUSE_OPERATOR")
    for left, right in REGION_PAIRS:
        pair = f"{REGIONS[left][2]}-{REGIONS[right][2]}".lower()
        person(f"taixe.{pair}-1@{DOMAIN}", "LINE_HAUL_DRIVER", "Tài xế liên miền", gateways[left], "LINE_HAUL_DRIVER",
               [gateways[right]])
        person(f"taixe.{pair}-2@{DOMAIN}", "LINE_HAUL_DRIVER", "Tài xế liên miền", gateways[right], "LINE_HAUL_DRIVER",
               [gateways[left]])

    for province, provincial in sorted(provincials.items()):
        slug = provincial["slug"]
        person(f"quanly.{slug}@{DOMAIN}", "HUB_MANAGER", "Quản lý bưu cục tổng", provincial, "HUB_MANAGER")
        for index in range(1, 5):
            person(f"kho{index}.{slug}@{DOMAIN}", "WAREHOUSE_OPERATOR", "Nhân viên kho", provincial, "WAREHOUSE_OPERATOR")
        # Ward codes of one area are consecutive, so a run of codes is a run of neighbouring post offices.
        served = sorted((hub for hub in offices if hub["province"] == province and hub["active"]),
                        key=lambda hub: hub["ward"]["code"])
        province_routes = []
        for start in range(0, len(served), ROUTE_SIZE):
            number = start // ROUTE_SIZE + 1
            stops = served[start:start + ROUTE_SIZE]
            code = f"{provincial['code']}-T{number:02d}"
            driver = person(f"taixe.{slug}-t{number:02d}@{DOMAIN}", "LINE_HAUL_DRIVER", f"Tài xế tuyến {code}",
                            provincial, "LINE_HAUL_DRIVER", stops)
            route = {"code": code, "provincial": provincial, "stops": stops, "driver": driver}
            for stop in stops:
                stop["route"] = code
            province_routes.append(route)
        for index in range(1, math.ceil(len(province_routes) / ROUTES_PER_RESERVE) + 1):
            person(f"taixe.{slug}-dp{index}@{DOMAIN}", "LINE_HAUL_DRIVER", "Tài xế dự phòng", provincial,
                   "LINE_HAUL_DRIVER", served)
        for index in (1, 2):
            person(f"taixe.{slug}-truc{index}@{DOMAIN}", "LINE_HAUL_DRIVER", "Tài xế trục", provincial,
                   "LINE_HAUL_DRIVER", [gateways[provincial["region"]]])
        routes += province_routes

    for hub in sorted(offices, key=lambda item: item["code"]):
        if not hub["active"]:
            continue
        person(f"truongbc.{hub['slug']}@{DOMAIN}", "HUB_MANAGER", "Trưởng bưu cục", hub, "HUB_MANAGER")
        for index in (1, 2):
            person(f"buuta{index}.{hub['slug']}@{DOMAIN}", "COURIER", "Bưu tá", hub, "COURIER")

    check(provinces, wards, hubs, offices, provincials, gateways, lanes, staff, routes)

    # ---- Rows -------------------------------------------------------------------------------------
    identity, operations = Copy(), Copy()
    user_columns = ["id", "email", "first_name", "last_name", "gender", "date_of_birth", "phone", "role"]
    member_columns = ["member_id", "user_id", "employee_code", "kind", "capacity", "hubs"]
    for number, record in enumerate(staff, start=1):
        identity.add("seed_user", user_columns, [
            record["userId"], record["email"], record["firstName"], record["lastName"], record["gender"],
            record["dateOfBirth"], record["phone"], record["role"]])
        if record["kind"]:
            record["employeeCode"] = f"PVX{number:06d}"
            hub_ids = [record["hub"]["id"]] + [hub["id"] for hub in record["also"] if hub["id"] != record["hub"]["id"]]
            operations.add("seed_member", member_columns, [
                uid("member", record["email"]), record["userId"], record["employeeCode"], record["kind"],
                CAPACITY[record["kind"]], hub_ids])

    summary = {
        "regionalCentres": len(gateways), "provincialOffices": len(provincials),
        "postOffices": sum(1 for hub in offices if hub["active"]),
        "unservedWards": sorted(hub["ward"]["fullName"] for hub in offices if not hub["active"]),
        "lanes": network.count("network.hub_lanes"), "schedules": network.count("network.lane_schedules"),
        "routes": len(routes), "accounts": len(staff), "workforce": operations.count("seed_member"),
        "byTitle": {},
    }
    for record in staff:
        title = record["title"] if not record["title"].startswith("Tài xế tuyến") else "Tài xế tuyến"
        summary["byTitle"][title] = summary["byTitle"].get(title, 0) + 1
    return network, identity, operations, staff, summary, office_staff


def check(provinces, wards, hubs, offices, provincials, gateways, lanes, staff, routes):
    """The rules the dataset promises; a broken one stops the generator."""
    problems = []
    served = [ward for ward in wards if ward["fullName"] not in UNSERVED_WARDS]
    office_by_ward = {}
    for hub in offices:
        office_by_ward.setdefault(hub["ward"]["code"], []).append(hub)
    for ward in served:
        if len([hub for hub in office_by_ward.get(ward["code"], []) if hub["active"]]) != 1:
            problems.append(f"ward {ward['code']} does not have exactly one post office")
    if sorted(provincials) != sorted(provinces):
        problems.append("not every province has exactly one provincial post office")
    if len(gateways) != 3:
        problems.append("there must be three regional centres")
    managers = {}
    for record in staff:
        if record["role"] == "HUB_MANAGER":
            managers[record["hub"]["id"]] = managers.get(record["hub"]["id"], 0) + 1
    for hub in hubs:
        if hub["active"] and managers.get(hub["id"], 0) != 1:
            problems.append(f"hub {hub['code']} has {managers.get(hub['id'], 0)} managers")
    couriers = {}
    for record in staff:
        if record["role"] == "COURIER":
            couriers[record["hub"]["id"]] = couriers.get(record["hub"]["id"], 0) + 1
    lane_codes = {lane["code"] for lane in lanes}
    for hub in offices:
        if not hub["active"]:
            continue
        if couriers.get(hub["id"], 0) != 2:
            problems.append(f"post office {hub['code']} has {couriers.get(hub['id'], 0)} couriers")
        if f"{hub['code']}-DI" not in lane_codes or f"{hub['code']}-VE" not in lane_codes:
            problems.append(f"post office {hub['code']} has no lane to and from its provincial post office")
        if not hub.get("route"):
            problems.append(f"post office {hub['code']} is on no route")
    stops = [stop["code"] for route in routes for stop in route["stops"]]
    if len(stops) != len(set(stops)):
        problems.append("a post office is on more than one route")
    emails = [record["email"] for record in staff]
    if len(emails) != len(set(emails)):
        problems.append("duplicate emails")
    if problems:
        raise SystemExit("Seed rules broken:\n  " + "\n  ".join(problems[:30]))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--catalog", required=True)
    parser.add_argument("--out", required=True)
    args = parser.parse_args()

    provinces = {code: name for code, name in read_tsv(os.path.join(args.catalog, "provinces.tsv"))}
    wards = [{"code": row[0], "province": row[1], "name": row[2], "fullName": row[3], "codeName": row[4]}
             for row in sorted(read_tsv(os.path.join(args.catalog, "wards.tsv")), key=lambda row: (row[1], row[0]))]
    network, identity, operations, staff, summary, _ = build(provinces, wards)
    os.makedirs(args.out, exist_ok=True)

    regions = ",\n".join(f"    ('{rid}', '{code}', '{label}', 'ACTIVE', NOW(), NOW(), 0)"
                         for code, (rid, label, _, _) in REGIONS.items())
    write(args.out, "network.sql", f"""-- Generated by scripts/seed-full/generate.py: the nationwide network.
\\set ON_ERROR_STOP on
BEGIN;
TRUNCATE network.lane_capacity_reservations, network.route_template_legs, network.route_templates,
    network.lane_schedules, network.hub_lanes, network.service_area_coverages, network.service_areas,
    network.hubs, network.audit_logs CASCADE;
INSERT INTO network.regions (id, code, name, status, created_at, updated_at, version) VALUES
{regions}
ON CONFLICT (id) DO UPDATE SET status = 'ACTIVE';
{network.render()}
COMMIT;
""")
    write(args.out, "identity.sql", f"""-- Generated by scripts/seed-full/generate.py: staff accounts (@{DOMAIN}).
-- Every earlier test account goes; admin@pavex.com and your own accounts (e.g. Gmail) stay without the MERCHANT
-- role, because shops are created again by hand.
\\set ON_ERROR_STOP on
BEGIN;
DELETE FROM identity.user_account
WHERE email LIKE '%@pavex.test' OR email LIKE '%@demo.pavex.local' OR email LIKE '%@khachhang.pavex.com'
   OR email LIKE 'deleted+%@pavex.invalid' OR (email LIKE '%@pavex.com' AND email <> 'admin@pavex.com');
UPDATE identity.user_account SET authorization_version = authorization_version + 1, updated_at = NOW()
WHERE id IN (SELECT ur.user_id FROM identity.user_role ur JOIN identity.role r ON r.id = ur.role_id WHERE r.code = 'MERCHANT');
DELETE FROM identity.user_role WHERE role_id = (SELECT id FROM identity.role WHERE code = 'MERCHANT');
CREATE TEMP TABLE seed_user (id UUID, email TEXT, first_name TEXT, last_name TEXT, gender TEXT, date_of_birth DATE,
    phone TEXT, role TEXT) ON COMMIT DROP;
{identity.render()}
INSERT INTO identity.user_account (id, email, status, profile_status, email_verified, authorization_version,
                                   created_at, updated_at, version)
SELECT id, email, 'ACTIVE', 'COMPLETED', TRUE, 1, NOW(), NOW(), 0 FROM seed_user;
INSERT INTO identity.user_profile (user_id, first_name, last_name, display_name, gender, date_of_birth, avatar_url,
                                   created_at, updated_at)
SELECT id, first_name, last_name, last_name || ' ' || first_name, gender, date_of_birth, NULL, NOW(), NOW() FROM seed_user;
INSERT INTO identity.user_role (user_id, role_id, assigned_at, assigned_by)
SELECT s.id, r.id, NOW(), '{ADMIN_ID}' FROM seed_user s JOIN identity.role r ON r.code = s.role;
COMMIT;
""")
    write(args.out, "operations.sql", f"""-- Generated by scripts/seed-full/generate.py: workforce and hub memberships.
-- Every operations table is emptied first; Operations rosters shifts from the standing rotation after the load.
\\set ON_ERROR_STOP on
BEGIN;
DO $$ DECLARE t text; BEGIN
  FOR t IN SELECT tablename FROM pg_tables WHERE schemaname = 'operations' AND tablename NOT LIKE 'flyway%' LOOP
    EXECUTE format('TRUNCATE operations.%I CASCADE', t);
  END LOOP;
END $$;
CREATE TEMP TABLE seed_member (member_id UUID, user_id UUID, employee_code TEXT, kind TEXT, capacity INT,
    hubs UUID[]) ON COMMIT DROP;
{operations.render()}
INSERT INTO operations.workforce_members (id, user_id, employee_code, kind, status, created_at, updated_at, version)
SELECT member_id, user_id, employee_code, kind, 'ACTIVE', NOW(), NOW(), 0 FROM seed_member;
INSERT INTO operations.hub_memberships (id, workforce_member_id, hub_id, is_primary, is_active, assigned_at,
                                        unassigned_at, updated_at, version)
SELECT md5(m.member_id::text || hub.id::text)::uuid, m.member_id, hub.id, hub.position = 1, TRUE, NOW(), NULL, NOW(), 0
FROM seed_member m CROSS JOIN LATERAL unnest(m.hubs) WITH ORDINALITY AS hub(id, position);
INSERT INTO operations.workforce_availabilities (workforce_member_id, status, max_concurrent_assignments, updated_at,
                                                 version)
SELECT member_id, 'OFF_DUTY', capacity, NOW(), 0 FROM seed_member;
COMMIT;
""")
    with open(os.path.join(args.out, "staff.csv"), "w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.writer(handle)
        writer.writerow(["Email", "Họ tên", "Chức vụ", "Vai trò", "Mã NV", "SĐT", "Mã bưu cục", "Bưu cục",
                         "Tỉnh/thành", "Bưu cục trên tuyến"])
        for record in staff:
            hub = record["hub"]
            writer.writerow([record["email"], f"{record['lastName']} {record['firstName']}", record["title"],
                             record["role"], record.get("employeeCode", ""), record["phone"],
                             hub["code"] if hub else "", hub["name"] if hub else "Văn phòng",
                             provinces.get(hub["province"], "") if hub else "",
                             " ".join(stop["code"] for stop in record["also"])])
    write(args.out, "summary.json", json.dumps(summary, ensure_ascii=False, indent=1))
    print(json.dumps({key: value for key, value in summary.items() if key != "byTitle"}, ensure_ascii=False))


def write(directory, name, text):
    with open(os.path.join(directory, name), "w", encoding="utf-8") as handle:
        handle.write(text)


if __name__ == "__main__":
    main()
