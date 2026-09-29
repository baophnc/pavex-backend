#!/usr/bin/env python3
"""
Generates the nationwide Pavex dataset: one post office per ward, a sorting warehouse per
province and a gateway per region, the staff every hub needs, transport lanes and daily
schedules, head-office staff, shops and customers.

People are fictional (realistic Vietnamese names and phone numbers, not real persons);
places come from the official administrative catalogue already loaded in Partners.

Everything is deterministic: the same catalogue and seed day produce the same ids, names and
emails, so the seed can be re-applied safely.

Output (under --out):
  sql/<database>.sql   wipe + COPY blocks, one file per service database
  manifest.json        what was generated, for the spreadsheet and the demo-shipment script
"""
import argparse
import datetime as dt
import json
import os
import random
import subprocess
import unicodedata
import uuid

NAMESPACE = uuid.UUID("6f1d2c1e-8a8f-4d0e-9f61-5a7e0c2b9a10")
EPOCH = "2026-09-25 00:00:00+07"
PASSWORD = "1205@Phuc"
ADMIN_ID = "00000000-0000-0000-0000-000000000001"

STAFF_DOMAIN = "pavex.com"
CUSTOMER_DOMAIN = "khachhang.pavex.com"

REGIONS = {
    "NORTH": ("30000000-0000-0000-0000-000000000001", "Miền Bắc", "MIENBAC", "01"),
    "CENTRAL": ("30000000-0000-0000-0000-000000000002", "Miền Trung", "MIENTRUNG", "48"),
    "SOUTH": ("30000000-0000-0000-0000-000000000003", "Miền Nam", "MIENNAM", "79"),
}

# province code -> (short code, region, road hours to the regional gateway)
PROVINCES = {
    "01": ("HN", "NORTH", 1), "04": ("CB", "NORTH", 6), "08": ("TQ", "NORTH", 3), "11": ("DB", "NORTH", 10),
    "12": ("LCH", "NORTH", 9), "14": ("SL", "NORTH", 6), "15": ("LCA", "NORTH", 5), "19": ("TN", "NORTH", 2),
    "20": ("LS", "NORTH", 3), "22": ("QNI", "NORTH", 3), "24": ("BN", "NORTH", 1), "25": ("PT", "NORTH", 2),
    "31": ("HP", "NORTH", 2), "33": ("HY", "NORTH", 2), "37": ("NB", "NORTH", 2),
    "38": ("TH", "CENTRAL", 12), "40": ("NA", "CENTRAL", 10), "42": ("HT", "CENTRAL", 8), "44": ("QT", "CENTRAL", 4),
    "46": ("HUE", "CENTRAL", 2), "48": ("DN", "CENTRAL", 1), "51": ("QNG", "CENTRAL", 3), "52": ("GL", "CENTRAL", 6),
    "56": ("KH", "CENTRAL", 10), "66": ("DLK", "CENTRAL", 10), "68": ("LD", "CENTRAL", 14),
    "75": ("DNA", "SOUTH", 2), "79": ("HCM", "SOUTH", 1), "80": ("TNI", "SOUTH", 2), "82": ("DT", "SOUTH", 4),
    "86": ("VL", "SOUTH", 3), "91": ("AG", "SOUTH", 6), "92": ("CT", "SOUTH", 4), "96": ("CM", "SOUTH", 7),
}
GATEWAY_HOURS = {("NORTH", "CENTRAL"): 14, ("CENTRAL", "SOUTH"): 16, ("NORTH", "SOUTH"): 30}
# Northern highland communes carry the remote-area surcharge.
REMOTE_PROVINCES = {"04", "08", "11", "12", "14", "15", "20"}
UNSERVED_WARDS = {"Đặc khu Hoàng Sa"}

SURNAMES = [
    ("Nguyễn", 38), ("Trần", 11), ("Lê", 9.5), ("Phạm", 7), ("Hoàng", 5), ("Phan", 4.5), ("Vũ", 4), ("Huỳnh", 2),
    ("Võ", 2), ("Đặng", 2), ("Bùi", 2), ("Đỗ", 1.4), ("Hồ", 1.3), ("Ngô", 1.3), ("Dương", 1), ("Lý", 0.5),
    ("Trịnh", 0.5), ("Đinh", 0.5), ("Mai", 0.3), ("Lâm", 0.3), ("Cao", 0.3), ("Đoàn", 0.3), ("Lương", 0.3),
    ("Hà", 0.3), ("Tạ", 0.2), ("Tô", 0.2), ("Châu", 0.2), ("Lưu", 0.2), ("Thái", 0.2), ("Quách", 0.1),
]
MIDDLE = {
    "MALE": ["Văn", "Hữu", "Đức", "Minh", "Quốc", "Thanh", "Công", "Xuân", "Ngọc", "Hoàng", "Gia", "Tuấn",
             "Đình", "Trung", "Anh", "Bảo", "Nhật", "Tiến", "Thành", "Quang"],
    "FEMALE": ["Thị", "Ngọc", "Thu", "Thanh", "Minh", "Kim", "Mai", "Phương", "Bảo", "Khánh", "Hồng", "Diệu",
               "Mỹ", "Hoài", "Thùy", "Tuyết"],
}
GIVEN = {
    "MALE": ["An", "Bình", "Cường", "Dũng", "Duy", "Đạt", "Hải", "Hiếu", "Hòa", "Hùng", "Huy", "Khang", "Khánh",
             "Khoa", "Kiên", "Lâm", "Long", "Luân", "Minh", "Nam", "Nghĩa", "Nhân", "Phong", "Phúc", "Quân", "Quang",
             "Sơn", "Tài", "Tâm", "Thắng", "Thành", "Thịnh", "Tín", "Toàn", "Trí", "Trung", "Tuấn", "Tùng", "Vinh", "Vũ"],
    "FEMALE": ["Anh", "Châu", "Chi", "Dung", "Duyên", "Giang", "Hà", "Hạnh", "Hằng", "Hiền", "Hoa", "Hương",
               "Huyền", "Lan", "Linh", "Loan", "Ly", "Mai", "My", "Nga", "Ngân", "Ngọc", "Nhi", "Nhung", "Oanh",
               "Phương", "Quỳnh", "Tâm", "Thảo", "Thư", "Trang", "Trinh", "Uyên", "Vân", "Vy", "Yến"],
}
MOBILE_PREFIXES = ["032", "033", "034", "035", "036", "037", "038", "039", "086", "096", "097", "098",
                   "081", "082", "083", "084", "085", "088", "091", "094", "070", "076", "077", "078", "079",
                   "089", "090", "093"]
STREETS = ["Lê Lợi", "Trần Hưng Đạo", "Nguyễn Huệ", "Hai Bà Trưng", "Lý Thường Kiệt", "Quang Trung", "Lê Duẩn",
           "Nguyễn Trãi", "Phan Chu Trinh", "Hùng Vương", "Điện Biên Phủ", "Võ Thị Sáu", "Nguyễn Du", "Bà Triệu",
           "Trần Phú", "Lạc Long Quân", "Nguyễn Văn Linh", "Tôn Đức Thắng", "Hoàng Diệu", "Phạm Văn Đồng"]

# (business name, contact kind, goods) — fictional shops.
SHOPS = [
    ("Thời trang Mộc Lan", "79"), ("Gốm sứ Minh Khai", "01"), ("Trà Thái Nguyên Hồng Hà", "19"),
    ("Cà phê Buôn Hồ", "66"), ("Nước mắm Cô Tô", "22"), ("Đặc sản Huế Mệ Thu", "46"),
    ("Bánh pía Sóc Trăng Tân Huê", "92"), ("Mỹ phẩm Thiên Hương", "79"), ("Sách cũ Phố Đinh", "01"),
    ("Đồ gỗ Đồng Kỵ Xuân Trường", "24"), ("Hải sản khô Nha Trang Biển Xanh", "56"), ("Điện thoại Hòa Phát Tech", "79"),
    ("Tơ lụa Hà Đông Vạn Phúc", "01"), ("Nông sản Đà Lạt Xanh", "68"), ("Mắm Châu Đốc Ba Hiền", "91"),
    ("Thời trang trẻ em Bông Mai", "48"), ("Phụ kiện xe máy Tuấn Phát", "75"), ("Gạo sạch Đồng Tháp Mười", "82"),
    ("Nón lá Chuông Hồng Nhung", "01"), ("Kẹo dừa Bến Tre Sáu Hòa", "86"), ("Đồ handmade Mây Tre Việt", "25"),
    ("Mật ong Tây Bắc Sơn Tuyền", "14"), ("Giày da Hiệp Thành", "79"), ("Văn phòng phẩm An Khang", "31"),
    ("Mỹ nghệ Non Nước Ngũ Hành", "48"), ("Thực phẩm chay Tâm An", "92"), ("Đồ chơi gỗ Bé Xinh", "37"),
    ("Yến sào Khánh Hòa Hải Âu", "56"), ("Rượu cần Tây Nguyên Y Moan", "52"), ("Linh kiện điện tử Minh Quân", "01"),
]


def uid(*parts):
    return str(uuid.uuid5(NAMESPACE, ":".join(str(part) for part in parts)))


def slug(text):
    text = text.replace("đ", "d").replace("Đ", "D")
    text = unicodedata.normalize("NFD", text)
    return "".join(ch for ch in text if unicodedata.category(ch) != "Mn")


def psql(container, sql):
    command = ["docker", "exec", container, "sh", "-c",
               'psql -U "${POSTGRES_USER:-postgres}" -d "$POSTGRES_DB" -At -F "\t" -c "$0"', sql]
    return [line.split("\t") for line in subprocess.check_output(command, text=True).splitlines() if line]


class People:
    """Realistic but fictional Vietnamese people, never repeating a phone number."""

    def __init__(self, rng):
        self.rng = rng
        self.phones = set()
        self.surnames = [name for name, _ in SURNAMES]
        self.weights = [weight for _, weight in SURNAMES]

    def person(self, male_share):
        gender = "MALE" if self.rng.random() < male_share else "FEMALE"
        last = self.rng.choices(self.surnames, self.weights)[0]
        middle = self.rng.choice(MIDDLE[gender])
        given = self.rng.choice(GIVEN[gender])
        birth = dt.date(1975, 1, 1) + dt.timedelta(days=self.rng.randrange(0, 27 * 365))
        return {
            "gender": gender,
            "lastName": last,
            "firstName": f"{middle} {given}",
            "displayName": f"{last} {middle} {given}",
            "dateOfBirth": birth.isoformat(),
            "phone": self.phone(),
        }

    def phone(self):
        while True:
            number = self.rng.choice(MOBILE_PREFIXES) + f"{self.rng.randrange(0, 10_000_000):07d}"
            if number not in self.phones:
                self.phones.add(number)
                return number


class Copy:
    """Accumulates rows and renders PostgreSQL COPY blocks."""

    def __init__(self):
        self.tables = {}

    def add(self, table, columns, row):
        entry = self.tables.setdefault(table, (columns, []))
        entry[1].append(row)

    @staticmethod
    def _cell(value):
        if value is None:
            return r"\N"
        if isinstance(value, bool):
            return "t" if value else "f"
        text = str(value)
        return text.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n").replace("\r", "\\r")

    def render(self):
        out = []
        for table, (columns, rows) in self.tables.items():
            out.append(f"COPY {table} ({', '.join(columns)}) FROM stdin;")
            out.extend("\t".join(self._cell(value) for value in row) for row in rows)
            out.append("\\.")
        return "\n".join(out)

    def count(self, table):
        return len(self.tables.get(table, (None, []))[1])


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", required=True)
    parser.add_argument("--day", default=dt.date.today().isoformat(), help="first day with shifts (Vietnam time)")
    args = parser.parse_args()
    rng = random.Random(20260925)
    people = People(rng)
    day = dt.date.fromisoformat(args.day)

    provinces = {row[0]: {"name": row[1], "fullName": row[2]} for row in psql(
        "pavex-backend-partners-db-1", "SELECT code, name, full_name FROM partners.provinces ORDER BY code")}
    wards = [
        {"code": row[0], "province": row[1], "name": row[2], "fullName": row[3], "codeName": row[4]}
        for row in psql(
            "pavex-backend-partners-db-1",
            "SELECT code, province_code, name, full_name, code_name FROM partners.wards ORDER BY province_code, code",
        )
    ]
    role_ids = {row[0]: row[1] for row in psql("pavex-backend-identity-db-1", "SELECT code, id FROM identity.role")}
    password_hash = psql("pavex-backend-auth-db-1",
                         "SELECT password_hash FROM auth.user_account WHERE email = 'admin@pavex.com'")[0][0]
    missing = set(provinces) - set(PROVINCES)
    if missing:
        raise SystemExit(f"Provinces without a region mapping: {sorted(missing)}")

    network, identity, auth, operations, partners = Copy(), Copy(), Copy(), Copy(), Copy()
    manifest = {"day": day.isoformat(), "password": PASSWORD, "hubs": [], "lanes": [], "accounts": [], "shops": [],
                "customers": []}

    # ---- Hubs ---------------------------------------------------------------------------------
    hub_columns = ["id", "code", "name", "type", "status", "address_line", "province_code", "ward_code",
                   "parent_hub_id", "public_visible", "public_name", "created_at", "updated_at", "version"]
    wards_by_province = {}
    for ward in wards:
        wards_by_province.setdefault(ward["province"], []).append(ward)

    def address(ward):
        return f"Số {rng.randrange(1, 399)} {rng.choice(STREETS)}, {ward['fullName']}"

    def add_hub(code, name, kind, ward, parent, active=True):
        hub_id = uid("hub", code)
        network.add("network.hubs", hub_columns, [
            hub_id, code, name, kind, "ACTIVE" if active else "INACTIVE", address(ward), ward["province"], ward["code"],
            parent, active, name, EPOCH, EPOCH, 0])
        hub = {"id": hub_id, "code": code, "name": name, "type": kind, "provinceCode": ward["province"],
               "province": provinces[ward["province"]]["name"], "wardCode": ward["code"], "ward": ward["fullName"],
               "active": active, "parentId": parent, "staff": []}
        manifest["hubs"].append(hub)
        return hub

    def first_urban_ward(province):
        items = wards_by_province[province]
        return next((ward for ward in items if ward["fullName"].startswith("Phường")), items[0])

    gateways = {}
    for region, (_, label, short, province) in REGIONS.items():
        gateways[region] = add_hub(f"TT-{short}", f"Trung tâm khai thác {label.lower()}", "REGIONAL_GATEWAY",
                                   first_urban_ward(province), None)

    warehouses = {}
    for province in sorted(provinces):
        short, region, _ = PROVINCES[province]
        warehouses[province] = add_hub(f"KHO-{short}", f"Kho trung chuyển {provinces[province]['name']}", "PROVINCIAL",
                                       first_urban_ward(province), gateways[region]["id"])

    offices = []
    used_codes = set()
    for ward in wards:
        short = PROVINCES[ward["province"]][0]
        base = f"BC-{short}-{ward['codeName'].replace('_', '').upper()}"[:44]
        code, suffix = base, 2
        while code in used_codes:
            code = f"{base[:41]}-{suffix}"
            suffix += 1
        used_codes.add(code)
        active = ward["fullName"] not in UNSERVED_WARDS
        hub = add_hub(code, f"Bưu cục {ward['name']}, {provinces[ward['province']]['name']}", "LOCAL", ward,
                      warehouses[ward["province"]]["id"], active)
        hub["wardKind"] = "ISLAND" if ward["fullName"].startswith("Đặc khu") else (
            "URBAN" if ward["fullName"].startswith("Phường") else "RURAL")
        # From the de-duplicated code: two wards of one province can share a name.
        hub["slug"] = f"{short.lower()}.{code.split('-', 2)[2].replace('-', '').lower()}"
        offices.append(hub)

    # ---- Service areas: one per post office, priced by the ward's province and region ----------
    area_columns = ["id", "code", "name", "region_id", "primary_hub_id", "status", "created_at", "updated_at", "version"]
    coverage_columns = ["id", "service_area_id", "province_code", "ward_code", "tier", "is_active", "created_at",
                        "updated_at"]
    for hub in offices:
        region = PROVINCES[hub["provinceCode"]][1]
        area_id = uid("area", hub["code"])
        tier = "ISLAND" if hub["wardKind"] == "ISLAND" else (
            "REMOTE" if hub["wardKind"] == "RURAL" and hub["provinceCode"] in REMOTE_PROVINCES else "STANDARD")
        hub["tier"] = tier
        status = "ACTIVE" if hub["active"] else "INACTIVE"
        network.add("network.service_areas", area_columns, [
            area_id, f"SA-{hub['code'][3:]}"[:50], hub["ward"], REGIONS[region][0], hub["id"], status, EPOCH, EPOCH, 0])
        network.add("network.service_area_coverages", coverage_columns, [
            uid("coverage", hub["code"]), area_id, hub["provinceCode"], hub["wardCode"], tier, hub["active"], EPOCH, EPOCH])

    # ---- Lanes and daily schedules ------------------------------------------------------------
    lane_columns = ["id", "code", "from_hub_id", "to_hub_id", "transport_mode", "estimated_transit_minutes",
                    "distance_km", "max_shipment_weight_kg", "status", "created_at", "updated_at", "version"]
    schedule_columns = ["id", "lane_id", "departure_time", "monday", "tuesday", "wednesday", "thursday", "friday",
                        "saturday", "sunday", "effective_from", "effective_to", "status", "created_at", "updated_at",
                        "version", "capacity_kg"]
    schedule_start = (day - dt.timedelta(days=1)).isoformat()

    def add_lane(code, source, target, mode, minutes, km, max_kg, departures, capacity):
        lane_id = uid("lane", code)
        network.add("network.hub_lanes", lane_columns, [
            lane_id, code, source["id"], target["id"], mode, minutes, km, max_kg, "ACTIVE", EPOCH, EPOCH, 0])
        for time in departures:
            network.add("network.lane_schedules", schedule_columns, [
                uid("schedule", code, time), lane_id, time, True, True, True, True, True, True, True,
                schedule_start, None, "ACTIVE", EPOCH, EPOCH, 0, capacity])
        manifest["lanes"].append({"code": code, "from": source["name"], "to": target["name"], "mode": mode,
                                  "minutes": minutes, "departures": ", ".join(departures), "capacityKg": capacity})

    for hub in offices:
        if not hub["active"]:
            continue
        warehouse = warehouses[hub["provinceCode"]]
        island = hub["wardKind"] == "ISLAND"
        minutes = 720 if island else (90 if hub["wardKind"] == "URBAN" else 180)
        mode = "SEA" if island else "ROAD"
        km = 250 if island else (15 if hub["wardKind"] == "URBAN" else 45)
        add_lane(f"{hub['code']}-DI", hub, warehouse, mode, minutes, km, 100, ["08:00"] if island else ["11:00", "17:00"], 800)
        add_lane(f"{hub['code']}-VE", warehouse, hub, mode, minutes, km, 100, ["14:00"] if island else ["06:00", "13:00"], 800)

    for province, warehouse in warehouses.items():
        short, region, hours = PROVINCES[province]
        gateway = gateways[region]
        add_lane(f"KHO-{short}-DI", warehouse, gateway, "ROAD", hours * 60, hours * 50, 1000, ["20:00"], 8000)
        add_lane(f"KHO-{short}-VE", gateway, warehouse, "ROAD", hours * 60, hours * 50, 1000, ["04:00", "14:00"], 8000)

    for (left, right), hours in GATEWAY_HOURS.items():
        for source, target in ((left, right), (right, left)):
            add_lane(f"TT-{REGIONS[source][2]}-{REGIONS[target][2]}", gateways[source], gateways[target], "ROAD",
                     hours * 60, hours * 55, 1000, ["22:00"], 30000)

    # ---- Accounts ------------------------------------------------------------------------------
    account_columns = ["id", "email", "status", "profile_status", "email_verified", "authorization_version",
                       "created_at", "updated_at", "version"]
    profile_columns = ["user_id", "first_name", "last_name", "display_name", "gender", "date_of_birth", "avatar_url",
                       "created_at", "updated_at"]
    role_columns = ["user_id", "role_id", "assigned_at", "assigned_by"]
    credential_columns = ["id", "email", "password_hash", "email_verified", "failed_login_attempts", "lockout_end_at",
                          "last_login_at", "created_at", "updated_at", "version", "identity_provisioned"]

    def add_account(email, person, roles, title, hub=None):
        user_id = uid("user", email)
        identity.add("identity.user_account", account_columns,
                     [user_id, email, "ACTIVE", "COMPLETED", True, 1, EPOCH, EPOCH, 0])
        identity.add("identity.user_profile", profile_columns, [
            user_id, person["firstName"], person["lastName"], person["displayName"], person["gender"],
            person["dateOfBirth"], None, EPOCH, EPOCH])
        for role in roles:
            identity.add("identity.user_role", role_columns, [user_id, role_ids[role], EPOCH, ADMIN_ID])
        auth.add("auth.user_account", credential_columns, [
            user_id, email, password_hash, True, 0, None, None, EPOCH, EPOCH, 0, True])
        manifest["accounts"].append({
            "email": email, "name": person["displayName"], "phone": person["phone"], "gender": person["gender"],
            "dateOfBirth": person["dateOfBirth"], "roles": roles, "title": title,
            "hubCode": hub["code"] if hub else None, "hubName": hub["name"] if hub else None,
            "province": hub["province"] if hub else None,
        })
        return user_id

    # ---- Workforce: members, hub memberships, availability, shifts -----------------------------
    member_columns = ["id", "user_id", "employee_code", "kind", "status", "created_at", "updated_at", "version"]
    membership_columns = ["id", "workforce_member_id", "hub_id", "is_primary", "is_active", "assigned_at",
                          "unassigned_at", "updated_at", "version"]
    availability_columns = ["workforce_member_id", "status", "max_concurrent_assignments", "updated_at", "version"]
    shift_columns = ["id", "hub_id", "code", "name", "starts_at", "ends_at", "status", "created_by_user_id",
                     "cancelled_by_user_id", "cancel_reason", "cancelled_at", "created_at", "updated_at", "version"]
    shift_member_columns = ["id", "shift_id", "workforce_member_id", "status", "assigned_by_user_id",
                            "status_changed_by_user_id", "assigned_at", "checked_in_at", "checked_out_at",
                            "marked_absent_at", "cancelled_at", "note", "updated_at", "version"]
    capacity = {"COURIER": 20, "WAREHOUSE_OPERATOR": 100, "HUB_MANAGER": 50, "LINE_HAUL_DRIVER": 100}
    titles = {"COURIER": "Bưu tá", "WAREHOUSE_OPERATOR": "Nhân viên bưu cục", "HUB_MANAGER": "Trưởng bưu cục",
              "LINE_HAUL_DRIVER": "Tài xế trung chuyển"}
    male_share = {"COURIER": 0.8, "WAREHOUSE_OPERATOR": 0.5, "HUB_MANAGER": 0.6, "LINE_HAUL_DRIVER": 0.95}
    shift_days = [day + dt.timedelta(days=offset) for offset in range(2)]

    def add_worker(hub, kind, email, title=None):
        person = people.person(male_share[kind])
        user_id = add_account(email, person, [kind], title or titles[kind], hub)
        member_id = uid("member", email)
        operations.add("operations.workforce_members", member_columns, [
            member_id, user_id, f"PAVEX-WF-{member_id.replace('-', '')[:10].upper()}", kind, "ACTIVE", EPOCH, EPOCH, 0])
        operations.add("operations.hub_memberships", membership_columns, [
            uid("membership", email), member_id, hub["id"], True, True, EPOCH, None, EPOCH, 0])
        operations.add("operations.workforce_availabilities", availability_columns, [
            member_id, "AVAILABLE", capacity[kind], EPOCH, 0])
        hub["staff"].append(member_id)

    # Every post office runs its own shuttle to the provincial warehouse: a line-haul job is
    # dispatched to drivers checked in at the hub the truck leaves from.
    staffing = {
        "URBAN": [("HUB_MANAGER", "truongbc", 1), ("WAREHOUSE_OPERATOR", "nhanvien", 1), ("COURIER", "buuta", 3),
                  ("LINE_HAUL_DRIVER", "taixe", 1)],
        "RURAL": [("HUB_MANAGER", "truongbc", 1), ("WAREHOUSE_OPERATOR", "nhanvien", 1), ("COURIER", "buuta", 2),
                  ("LINE_HAUL_DRIVER", "taixe", 1)],
        "ISLAND": [("HUB_MANAGER", "truongbc", 1), ("WAREHOUSE_OPERATOR", "nhanvien", 1), ("COURIER", "buuta", 1),
                   ("LINE_HAUL_DRIVER", "taixe", 1)],
    }
    for hub in offices:
        if not hub["active"]:
            continue
        for kind, prefix, count in staffing[hub["wardKind"]]:
            for index in range(1, count + 1):
                number = str(index) if count > 1 or kind == "COURIER" else ""
                add_worker(hub, kind, f"{prefix}{number}.{hub['slug']}@{STAFF_DOMAIN}")

    for province, hub in warehouses.items():
        short = PROVINCES[province][0].lower()
        add_worker(hub, "HUB_MANAGER", f"truongkho.{short}@{STAFF_DOMAIN}", "Trưởng kho")
        for index in range(1, 4):
            add_worker(hub, "WAREHOUSE_OPERATOR", f"kho{index}.{short}@{STAFF_DOMAIN}", "Nhân viên kho")
        for index in range(1, 3):
            add_worker(hub, "LINE_HAUL_DRIVER", f"taixe{index}.{short}@{STAFF_DOMAIN}")

    for region, hub in gateways.items():
        short = REGIONS[region][2].lower()
        add_worker(hub, "HUB_MANAGER", f"giamdoc.{short}@{STAFF_DOMAIN}", "Giám đốc trung tâm")
        for index in range(1, 7):
            add_worker(hub, "WAREHOUSE_OPERATOR", f"khaithac{index}.{short}@{STAFF_DOMAIN}", "Nhân viên khai thác")
        for index in range(1, 5):
            add_worker(hub, "LINE_HAUL_DRIVER", f"taixelienvung{index}.{short}@{STAFF_DOMAIN}", "Tài xế liên vùng")

    # One long day shift (06:00–22:00 Vietnam time) per hub for the seed day and the next.
    for hub in manifest["hubs"]:
        if not hub["staff"]:
            continue
        for shift_day in shift_days:
            shift_id = uid("shift", hub["code"], shift_day)
            operations.add("operations.work_shifts", shift_columns, [
                shift_id, hub["id"], f"PAVEX-SH-{shift_id.replace('-', '')[:12].upper()}",
                f"Ca ngày {shift_day.strftime('%d/%m')}", f"{shift_day} 06:00:00+07", f"{shift_day} 22:00:00+07",
                "SCHEDULED", ADMIN_ID, None, None, None, EPOCH, EPOCH, 0])
            for member_id in hub["staff"]:
                operations.add("operations.workforce_shift_assignments", shift_member_columns, [
                    uid("shift-member", shift_id, member_id), shift_id, member_id, "SCHEDULED", ADMIN_ID, ADMIN_ID,
                    EPOCH, None, None, None, None, None, EPOCH, 0])

    # ---- Head office -------------------------------------------------------------------------
    head_office = [("vanhanh.mienbac", "OPERATIONS_MANAGER", "Quản lý vận hành miền Bắc"),
                   ("vanhanh.mientrung", "OPERATIONS_MANAGER", "Quản lý vận hành miền Trung"),
                   ("vanhanh.miennam", "OPERATIONS_MANAGER", "Quản lý vận hành miền Nam"),
                   ("kinhdoanh1", "COMMERCIAL_MANAGER", "Kinh doanh & CSKH"),
                   ("kinhdoanh2", "COMMERCIAL_MANAGER", "Kinh doanh & CSKH"),
                   ("ketoan1", "FINANCE_ACCOUNTANT", "Kế toán"),
                   ("ketoan2", "FINANCE_ACCOUNTANT", "Kế toán")]
    for local, role, title in head_office:
        add_account(f"{local}@{STAFF_DOMAIN}", people.person(0.5), [role], title)

    # ---- Shops and customers ---------------------------------------------------------------------
    merchant_columns = ["id", "merchant_code", "user_id", "business_name", "contact_name", "contact_phone",
                        "contact_email", "tax_code", "status", "status_reason_code", "status_reason_detail",
                        "status_changed_at", "status_changed_by_user_id", "verified_at", "verified_by_user_id",
                        "version", "created_at", "updated_at"]
    history_columns = ["id", "merchant_id", "from_status", "to_status", "reason_code", "reason_detail",
                       "changed_by_user_id", "changed_at"]
    pickup_columns = ["id", "merchant_id", "name", "contact_name", "contact_phone", "address_line", "province_code",
                      "ward_code", "is_default", "created_at", "updated_at"]
    address_columns = ["id", "user_id", "label", "contact_name", "contact_phone", "address_line", "province_code",
                       "ward_code", "is_default", "created_at", "updated_at"]
    served = [hub for hub in offices if hub["active"]]
    served_by_province = {}
    for hub in served:
        served_by_province.setdefault(hub["provinceCode"], []).append(hub)

    for index, (business, province) in enumerate(SHOPS, start=2):
        owner = people.person(0.45)
        email = f"shop{index - 1:02d}.{slug(business).lower().replace(' ', '')[:24]}@{CUSTOMER_DOMAIN}"
        user_id = add_account(email, owner, ["CUSTOMER", "MERCHANT"], "Chủ shop")
        merchant_id = uid("merchant", email)
        code = f"PVM{index:06d}"
        partners.add("partners.merchants", merchant_columns, [
            merchant_id, code, user_id, business, owner["displayName"], owner["phone"], email,
            f"{rng.randrange(10**9, 10**10)}", "ACTIVE", None, None, EPOCH, ADMIN_ID, EPOCH, ADMIN_ID, 1, EPOCH, EPOCH])
        partners.add("partners.merchant_status_history", history_columns, [
            uid("merchant-history", email, 1), merchant_id, None, "PENDING_VERIFICATION", None, None, user_id, EPOCH])
        partners.add("partners.merchant_status_history", history_columns, [
            uid("merchant-history", email, 2), merchant_id, "PENDING_VERIFICATION", "ACTIVE", None, None, ADMIN_ID, EPOCH])
        pickups = []
        for position, hub in enumerate(rng.sample(served_by_province[province], k=min(2, len(served_by_province[province])))):
            pickup_id = uid("pickup", email, position)
            name = "Kho chính" if position == 0 else "Cửa hàng"
            line = f"Số {rng.randrange(1, 399)} {rng.choice(STREETS)}"
            partners.add("partners.merchant_pickup_addresses", pickup_columns, [
                pickup_id, merchant_id, name, owner["displayName"], owner["phone"], line, hub["provinceCode"],
                hub["wardCode"], position == 0, EPOCH, EPOCH])
            pickups.append({"id": pickup_id, "name": name, "address": f"{line}, {hub['ward']}, {hub['province']}",
                            "hubCode": hub["code"], "provinceCode": hub["provinceCode"], "wardCode": hub["wardCode"],
                            "region": PROVINCES[hub["provinceCode"]][1]})
        manifest["shops"].append({"code": code, "name": business, "email": email, "owner": owner["displayName"],
                                  "phone": owner["phone"], "province": provinces[province]["name"], "pickups": pickups})

    for index in range(1, 11):
        person = people.person(0.5)
        email = f"khach{index:02d}@{CUSTOMER_DOMAIN}"
        user_id = add_account(email, person, ["CUSTOMER"], "Khách cá nhân")
        hub = rng.choice(served)
        line = f"Số {rng.randrange(1, 399)} {rng.choice(STREETS)}"
        partners.add("partners.user_addresses", address_columns, [
            uid("address", email), user_id, "Nhà", person["displayName"], person["phone"], line, hub["provinceCode"],
            hub["wardCode"], True, EPOCH, EPOCH])
        manifest["customers"].append({"email": email, "name": person["displayName"], "phone": person["phone"],
                                      "address": f"{line}, {hub['ward']}, {hub['province']}"})

    # Recipients for demo shipments: fictional people spread over the served wards.
    manifest["recipients"] = []
    for _ in range(400):
        person = people.person(0.5)
        hub = rng.choice(served)
        manifest["recipients"].append({
            "name": person["displayName"], "phone": person["phone"],
            "addressLine": f"Số {rng.randrange(1, 399)} {rng.choice(STREETS)}",
            "provinceCode": hub["provinceCode"], "wardCode": hub["wardCode"], "hubCode": hub["code"],
            "region": PROVINCES[hub["provinceCode"]][1]})

    # ---- Write --------------------------------------------------------------------------------
    seeded_users = (f"email LIKE '%@demo.pavex.local' OR email LIKE '%@{CUSTOMER_DOMAIN}' "
                    f"OR (email LIKE '%@{STAFF_DOMAIN}' AND email <> 'admin@pavex.com')")
    wipe = {
        "network": "TRUNCATE network.lane_capacity_reservations, network.route_template_legs, network.route_templates, "
                   "network.lane_schedules, network.hub_lanes, network.service_area_coverages, network.service_areas, "
                   "network.hubs, network.audit_logs CASCADE;",
        "operations": "TRUNCATE operations.operational_assignment_event_logs, operations.outbox_messages, "
                      "operations.operational_assignments, operations.workforce_audit_logs, operations.hub_memberships, "
                      "operations.workforce_availabilities, operations.workforce_shift_assignments, "
                      "operations.work_shifts, operations.workforce_members CASCADE;",
        "identity": f"DELETE FROM identity.user_account WHERE {seeded_users};",
        "auth": f"DELETE FROM auth.user_account WHERE {seeded_users};",
        # Partners cannot see Identity, so the seeded user ids (old demo seeds and this one) are passed in.
        "partners": "CREATE TEMP TABLE seeded_users (id uuid PRIMARY KEY) ON COMMIT DROP;\n"
                    "COPY seeded_users (id) FROM stdin;\n{seeded_ids}\n\\.\n"
                    "DELETE FROM partners.merchant_role_outbox WHERE user_id IN (SELECT id FROM seeded_users);\n"
                    "DELETE FROM partners.merchants WHERE user_id IN (SELECT id FROM seeded_users);\n"
                    "DELETE FROM partners.user_addresses WHERE user_id IN (SELECT id FROM seeded_users);",
    }
    previous = {row[0] for row in psql("pavex-backend-identity-db-1", f"SELECT id FROM identity.user_account WHERE {seeded_users}")}
    current = {uid("user", item["email"]) for item in manifest["accounts"]}
    wipe["partners"] = wipe["partners"].replace("{seeded_ids}", "\n".join(sorted(previous | current)))
    tails = {
        "partners": f"SELECT setval('partners.merchant_code_seq', {len(SHOPS) + 1});",
    }
    sql_dir = os.path.join(args.out, "sql")
    os.makedirs(sql_dir, exist_ok=True)
    for name, copy in (("network", network), ("identity", identity), ("auth", auth), ("operations", operations),
                       ("partners", partners)):
        with open(os.path.join(sql_dir, f"{name}.sql"), "w", encoding="utf-8") as handle:
            handle.write("\\set ON_ERROR_STOP on\nBEGIN;\n")
            handle.write(wipe[name] + "\n")
            handle.write(copy.render() + "\n")
            if name in tails:
                handle.write(tails[name] + "\n")
            handle.write("COMMIT;\n")

    counts = {
        "hubs": len(manifest["hubs"]), "postOffices": len(offices), "servedPostOffices": len(served),
        "warehouses": len(warehouses), "gateways": len(gateways), "lanes": network.count("network.hub_lanes"),
        "schedules": network.count("network.lane_schedules"), "accounts": len(manifest["accounts"]),
        "workforce": operations.count("operations.workforce_members"),
        "shifts": operations.count("operations.work_shifts"), "shops": len(SHOPS), "customers": 10,
    }
    manifest["counts"] = counts
    with open(os.path.join(args.out, "manifest.json"), "w", encoding="utf-8") as handle:
        json.dump(manifest, handle, ensure_ascii=False)
    print(json.dumps(counts, ensure_ascii=False))


if __name__ == "__main__":
    main()
