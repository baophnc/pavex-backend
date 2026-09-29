#!/usr/bin/env python3
"""
Generates Pavex staff sized like a real nationwide carrier, on top of the network already loaded
(hubs, lanes). No shops, customers or shipments: those are created by hand when testing.

- Post offices are staffed by how busy their ward is: city wards (Hà Nội, TP.HCM, Hải Phòng, Đà Nẵng,
  Cần Thơ) get a larger team than town wards, rural communes, remote communes and islands.
- Provincial warehouses and regional centres get sorting teams sized by the hubs they serve.
- Line-haul drivers belong to the provincial warehouse fleet. Each route truck serves a run of
  neighbouring post offices: the driver's primary hub is the warehouse and they are also a member
  of every post office on the route. Trunk drivers run warehouse <-> regional centre; regional
  centres have their own inter-region drivers.
- Emails follow <given>.<surname+middle>.<role tag>@pavex.com, e.g. hung.phancong.tbc@pavex.com.
- A small share of accounts is offboarded, suspended, invited but not activated, or on leave.
  The sample accounts listed in the project guide are always active.
- Shifts are not written here: Operations keeps a rolling week of shifts from the standing
  rotation (6 days on, 1 day off). The weekday off is computed the same way and put in the output.

Everything is deterministic. Output (under --out):
  sql/identity.sql, sql/auth.sql, sql/operations.sql
  staff.json, staff.csv
"""
import argparse
import csv
import datetime as dt
import json
import os
import random
import uuid

from generate import ADMIN_ID, EPOCH, GIVEN, MIDDLE, PASSWORD, PROVINCES, REGIONS, SURNAMES, Copy, psql, slug, uid

DOMAIN = "pavex.com"
BIG_CITIES = {"HN", "HCM", "DN", "HP", "CT"}
TODAY = dt.date(2026, 9, 29)
WEEKDAYS = ["Thứ Hai", "Thứ Ba", "Thứ Tư", "Thứ Năm", "Thứ Sáu", "Thứ Bảy", "Chủ nhật"]

# Share of each carrier's prefixes among Vietnamese mobile numbers (roughly).
CARRIERS = [
    (["032", "033", "034", "035", "036", "037", "038", "039", "086", "096", "097", "098"], 50),  # Viettel
    (["081", "082", "083", "084", "085", "088", "091", "094"], 25),  # Vinaphone
    (["070", "076", "077", "078", "079", "089", "090", "093"], 20),  # Mobifone
    (["052", "056", "058", "092"], 5),  # Vietnamobile
]
# Surnames more common in one region than nationwide.
REGIONAL_SURNAMES = {
    "NORTH": {"Hoàng": 1.6, "Vũ": 1.6, "Đỗ": 1.4, "Nguyễn": 1.0, "Huỳnh": 0.2, "Võ": 0.4},
    "CENTRAL": {"Hồ": 2.0, "Phan": 1.4, "Võ": 1.5, "Lê": 1.2},
    "SOUTH": {"Huỳnh": 3.0, "Võ": 2.5, "Trương": 2.0, "Lâm": 2.0, "Hoàng": 0.3, "Vũ": 0.4},
}
EXTRA_SURNAMES = [("Trương", 0.6)]

# kind -> (male share, youngest age, oldest age)
PROFILE = {
    "COURIER": (0.85, 20, 40), "LINE_HAUL_DRIVER": (0.98, 25, 55), "WAREHOUSE_OPERATOR": (0.55, 20, 45),
    "HUB_MANAGER": (0.6, 28, 52), "OFFICE": (0.45, 23, 50), "FINANCE": (0.2, 24, 50), "CARE": (0.3, 22, 40),
}
CAPACITY = {"COURIER": 20, "WAREHOUSE_OPERATOR": 100, "HUB_MANAGER": 50, "LINE_HAUL_DRIVER": 100}

# Post office class -> (office staff, couriers range); the manager is always one.
POST_OFFICE_STAFF = {"CITY": (2, (6, 10)), "TOWN": (1, (3, 5)), "RURAL": (None, (2, 3)), "REMOTE": (0, (1, 2)),
                     "ISLAND": (0, (1, 2))}


class People:
    """Fictional Vietnamese people with regional surnames, age by job and unique phone numbers."""

    def __init__(self, rng):
        self.rng = rng
        self.phones = set()
        self.emails = set()

    def person(self, profile, region):
        male_share, youngest, oldest = PROFILE[profile]
        gender = "MALE" if self.rng.random() < male_share else "FEMALE"
        boost = REGIONAL_SURNAMES.get(region, {})
        names = SURNAMES + EXTRA_SURNAMES
        last = self.rng.choices([name for name, _ in names], [weight * boost.get(name, 1.0) for name, weight in names])[0]
        middle = self.rng.choice(MIDDLE[gender])
        given = self.rng.choice(GIVEN[gender])
        age = self.rng.randint(youngest, oldest)
        birth = TODAY - dt.timedelta(days=age * 365 + self.rng.randrange(0, 365))
        return {
            "gender": gender, "lastName": last, "firstName": f"{middle} {given}",
            "displayName": f"{last} {middle} {given}", "dateOfBirth": birth.isoformat(), "phone": self.phone(),
            "given": given, "surnameMiddle": f"{last} {middle}",
        }

    def phone(self):
        while True:
            prefixes = self.rng.choices([items for items, _ in CARRIERS], [weight for _, weight in CARRIERS])[0]
            number = self.rng.choice(prefixes) + f"{self.rng.randrange(0, 10_000_000):07d}"
            if number not in self.phones:
                self.phones.add(number)
                return number

    def email(self, person, tag):
        local = f"{slug(person['given']).lower()}.{slug(person['surnameMiddle']).lower().replace(' ', '')}.{tag}"
        candidate, suffix = local, 2
        while candidate in self.emails:
            candidate = f"{local}{suffix}"
            suffix += 1
        self.emails.add(candidate)
        return f"{candidate}@{DOMAIN}"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", required=True)
    args = parser.parse_args()
    rng = random.Random(20260929)
    people = People(rng)

    wards = {row[0]: row[1] for row in psql("pavex-backend-partners-db-1", "SELECT code, full_name FROM partners.wards")}
    provinces = {row[0]: row[1] for row in psql("pavex-backend-partners-db-1", "SELECT code, name FROM partners.provinces")}
    hubs = [
        {"id": row[0], "code": row[1], "name": row[2], "type": row[3], "active": row[4] == "ACTIVE",
         "province": row[5], "ward": row[6], "parentId": row[7] or None}
        for row in psql("pavex-backend-network-db-1",
                        "SELECT id, code, name, type, status, province_code, ward_code, parent_hub_id FROM network.hubs ORDER BY code")
    ]
    tiers = {row[0]: row[1] for row in psql(
        "pavex-backend-network-db-1",
        "SELECT a.primary_hub_id, c.tier FROM network.service_areas a JOIN network.service_area_coverages c ON c.service_area_id = a.id")}
    role_ids = {row[0]: row[1] for row in psql("pavex-backend-identity-db-1", "SELECT code, id FROM identity.role")}
    password_hash = psql("pavex-backend-auth-db-1",
                         "SELECT password_hash FROM auth.user_account WHERE email = 'admin@pavex.com'")[0][0]

    gateways = {hub["id"]: hub for hub in hubs if hub["type"] == "REGIONAL_GATEWAY" and hub["active"]}
    warehouses = {hub["id"]: hub for hub in hubs if hub["type"] == "PROVINCIAL" and hub["active"]}
    offices = [hub for hub in hubs if hub["type"] == "LOCAL" and hub["active"]]
    offices_by_warehouse = {}
    for hub in offices:
        offices_by_warehouse.setdefault(hub["parentId"], []).append(hub)
    region_of_gateway = {hub["id"]: next(key for key, value in REGIONS.items() if hub["code"] == f"TT-{value[2]}") for hub in gateways.values()}

    def region(hub):
        return PROVINCES[hub["province"]][1]

    def office_class(hub):
        full = wards.get(hub["ward"], "")
        tier = tiers.get(hub["id"], "STANDARD")
        if full.startswith("Đặc khu") or tier == "ISLAND":
            return "ISLAND"
        if full.startswith("Phường"):
            return "CITY" if PROVINCES[hub["province"]][0] in BIG_CITIES else "TOWN"
        return "REMOTE" if tier == "REMOTE" else "RURAL"

    identity, auth, operations = Copy(), Copy(), Copy()
    account_columns = ["id", "email", "status", "profile_status", "email_verified", "authorization_version",
                       "created_at", "updated_at", "version"]
    profile_columns = ["user_id", "first_name", "last_name", "display_name", "gender", "date_of_birth", "avatar_url",
                       "created_at", "updated_at"]
    role_columns = ["user_id", "role_id", "assigned_at", "assigned_by"]
    credential_columns = ["id", "email", "password_hash", "email_verified", "failed_login_attempts", "lockout_end_at",
                          "last_login_at", "created_at", "updated_at", "version", "identity_provisioned"]
    member_columns = ["id", "user_id", "employee_code", "kind", "status", "created_at", "updated_at", "version"]
    membership_columns = ["id", "workforce_member_id", "hub_id", "is_primary", "is_active", "assigned_at",
                          "unassigned_at", "updated_at", "version"]
    availability_columns = ["workforce_member_id", "status", "max_concurrent_assignments", "updated_at", "version"]

    staff = []  # everyone, in generation order

    def add_person(role, profile, tag, title, region_code, hub=None, kind=None, route=None):
        person = people.person(profile, region_code)
        email = people.email(person, tag)
        record = {
            "userId": uid("user", email), "email": email, "person": person, "role": role, "title": title,
            "kind": kind, "hub": hub, "route": route or [], "status": "ACTIVE", "sample": False,
        }
        staff.append(record)
        return record

    # ---- Head office (Hà Nội, with one operations pair per region) ------------------------------
    for _ in range(2):
        add_person("ADMIN", "OFFICE", "qtht", "Quản trị hệ thống", "NORTH")
    for code, (_, label, _, _) in REGIONS.items():
        for _ in range(2):
            add_person("OPERATIONS_MANAGER", "OFFICE", "qlvh", f"Quản lý vận hành {label[0].lower()}{label[1:]}", code)
    for _ in range(10):
        add_person("COMMERCIAL_MANAGER", "CARE", "cskh", "Kinh doanh & CSKH", "NORTH")
    for _ in range(6):
        add_person("FINANCE_ACCOUNTANT", "FINANCE", "kt", "Kế toán", "NORTH")

    def worker(kind, tag, title, hub, route=None, profile=None, region_code=None):
        return add_person(kind, profile or kind, tag, title, region_code or region(hub), hub, kind, route)

    # ---- Regional centres ------------------------------------------------------------------------
    for gateway in sorted(gateways.values(), key=lambda hub: hub["code"]):
        area = region_of_gateway[gateway["id"]]
        served = sum(1 for hub in warehouses.values() if hub["parentId"] == gateway["id"])
        worker("HUB_MANAGER", "gdtt", "Giám đốc trung tâm", gateway, region_code=area)
        for _ in range(2):
            worker("HUB_MANAGER", "pgdtt", "Phó giám đốc trung tâm", gateway, region_code=area)
        for _ in range(50 + 2 * served):
            worker("WAREHOUSE_OPERATOR", "nvkt", "Nhân viên khai thác", gateway, region_code=area)
        for _ in range(15 + served // 2):
            worker("LINE_HAUL_DRIVER", "tx", "Tài xế liên vùng", gateway, region_code=area)

    # ---- Provincial warehouses and their fleets -----------------------------------------------
    routes = []
    for warehouse in sorted(warehouses.values(), key=lambda hub: hub["code"]):
        served = sorted(offices_by_warehouse.get(warehouse["id"], []), key=lambda hub: hub["ward"])
        big_city = PROVINCES[warehouse["province"]][0] in BIG_CITIES
        worker("HUB_MANAGER", "tk", "Trưởng kho", warehouse)
        worker("HUB_MANAGER", "pk", "Phó kho", warehouse)
        for _ in range(max(10, min(40, round(len(served) / 5)))):
            worker("WAREHOUSE_OPERATOR", "nvkt", "Nhân viên khai thác", warehouse)
        # Route trucks: a run of neighbouring post offices (ward codes of one area are consecutive).
        size = 6 if big_city else 8
        for start in range(0, len(served), size):
            stops = served[start:start + size]
            route = {"code": f"{warehouse['code']}-T{start // size + 1:02d}", "warehouse": warehouse, "stops": stops, "drivers": []}
            for _ in range(2):
                route["drivers"].append(worker("LINE_HAUL_DRIVER", "tx", "Tài xế tuyến bưu cục", warehouse, route=stops))
            routes.append(route)
        gateway = gateways.get(warehouse["parentId"])
        for _ in range(max(3, min(8, len(served) // 25))):
            worker("LINE_HAUL_DRIVER", "tx", "Tài xế trục", warehouse, route=[gateway] if gateway else [])

    # ---- Post offices --------------------------------------------------------------------------
    for office in sorted(offices, key=lambda hub: hub["code"]):
        kind = office_class(office)
        office["class"] = kind
        clerks, couriers = POST_OFFICE_STAFF[kind]
        if clerks is None:
            clerks = 1 if rng.random() < 0.5 else 0
        worker("HUB_MANAGER", "tbc", "Trưởng bưu cục", office)
        for _ in range(clerks):
            worker("WAREHOUSE_OPERATOR", "nvbc", "Nhân viên bưu cục", office)
        for _ in range(rng.randint(*couriers)):
            worker("COURIER", "bt", "Bưu tá", office)

    # ---- Sample accounts for the guide: always active ------------------------------------------
    def first(predicate):
        return next(record for record in staff if predicate(record))

    samples = []
    for role in ("ADMIN", "COMMERCIAL_MANAGER", "FINANCE_ACCOUNTANT"):
        samples.append(first(lambda r, role=role: r["role"] == role))
    for label in ("miền Bắc", "miền Nam"):
        samples.append(first(lambda r, label=label: r["role"] == "OPERATIONS_MANAGER" and r["title"].endswith(label)))
    for short, preferred in (("HN", "Ba Đình"), ("HCM", "Sài Gòn")):
        city_offices = sorted((hub for hub in offices if PROVINCES[hub["province"]][0] == short and hub.get("class") == "CITY"),
                              key=lambda hub: (wards.get(hub["ward"]) != f"Phường {preferred}", hub["ward"]))
        office = city_offices[0]
        route = next(item for item in routes if office in item["stops"])
        warehouse = route["warehouse"]
        gateway = gateways[warehouse["parentId"]]
        samples += [first(lambda r, h=gateway: r["hub"] is h and r["title"] == "Giám đốc trung tâm"),
                    first(lambda r, h=warehouse: r["hub"] is h and r["title"] == "Trưởng kho"),
                    first(lambda r, h=warehouse: r["hub"] is h and r["title"] == "Nhân viên khai thác"),
                    first(lambda r, h=warehouse: r["hub"] is h and r["title"] == "Tài xế trục")]
        samples += route["drivers"]
        samples += [record for record in staff if record["hub"] is office]
    for record in samples:
        record["sample"] = True

    # ---- Realistic account states --------------------------------------------------------------
    for record in staff:
        if record["sample"] or record["role"] == "ADMIN":
            continue
        roll = rng.random()
        if roll < 0.02:
            record["status"] = "OFFBOARDED"
        elif roll < 0.03:
            record["status"] = "SUSPENDED"
        elif roll < 0.04:
            record["status"] = "INVITED"
        elif roll < 0.09 and record["kind"]:
            record["status"] = "ON_LEAVE"

    # ---- Rows --------------------------------------------------------------------------------
    employee = 0
    for record in staff:
        person, user_id, email, status = record["person"], record["userId"], record["email"], record["status"]
        account_status = {"OFFBOARDED": "DISABLED", "SUSPENDED": "SUSPENDED"}.get(status, "ACTIVE")
        invited = status == "INVITED"
        identity.add("identity.user_account", account_columns, [
            user_id, email, account_status, "INCOMPLETE" if invited else "COMPLETED", not invited, 1, EPOCH, EPOCH, 0])
        identity.add("identity.user_profile", profile_columns, [
            user_id, person["firstName"], person["lastName"], person["displayName"], person["gender"],
            person["dateOfBirth"], None, EPOCH, EPOCH])
        identity.add("identity.user_role", role_columns, [user_id, role_ids[record["role"]], EPOCH, ADMIN_ID])
        # Invited staff cannot sign in until they accept the invitation and choose a password.
        auth.add("auth.user_account", credential_columns, [
            user_id, email, password_hash, not invited, 0, None, None, EPOCH, EPOCH, 0, True])
        if not record["kind"]:
            continue
        employee += 1
        record["employeeCode"] = f"PVX{employee:06d}"
        member_id = uid("member", email)
        record["memberId"] = member_id
        member_status = {"OFFBOARDED": "OFFBOARDED", "SUSPENDED": "SUSPENDED"}.get(status, "ACTIVE")
        operations.add("operations.workforce_members", member_columns, [
            member_id, user_id, record["employeeCode"], record["kind"], member_status, EPOCH, EPOCH, 0])
        active = status != "OFFBOARDED"
        for position, hub in enumerate([record["hub"]] + [stop for stop in record["route"] if stop]):
            operations.add("operations.hub_memberships", membership_columns, [
                uid("membership", email, hub["id"]), member_id, hub["id"], position == 0, active, EPOCH,
                None if active else EPOCH, EPOCH, 0])
        operations.add("operations.workforce_availabilities", availability_columns, [
            member_id, "UNAVAILABLE" if status == "ON_LEAVE" else "OFF_DUTY", CAPACITY[record["kind"]], EPOCH, 0])

    # ---- Weekday off, as Operations' rotation computes it ------------------------------------------
    rosters = {}
    for record in staff:
        if record.get("memberId") and record["status"] not in ("OFFBOARDED", "SUSPENDED"):
            key = (record["hub"]["id"], record["kind"] == "LINE_HAUL_DRIVER")
            rosters.setdefault(key, []).append(record)
    for members in rosters.values():
        for position, record in enumerate(sorted(members, key=lambda item: str(uuid.UUID(item["memberId"])))):
            record["dayOff"] = WEEKDAYS[position % 7]

    # ---- Write --------------------------------------------------------------------------------
    seeded = ("email LIKE '%@demo.pavex.local' OR email LIKE '%@khachhang.pavex.com' "
              f"OR (email LIKE '%@{DOMAIN}' AND email <> 'admin@pavex.com')")
    wipe = {
        "identity": f"DELETE FROM identity.user_account WHERE {seeded};\n"
                    "-- Shops are recreated by hand: nobody keeps the MERCHANT role, and tokens are refreshed.\n"
                    "UPDATE identity.user_account SET authorization_version = authorization_version + 1, updated_at = NOW()\n"
                    "WHERE id IN (SELECT ur.user_id FROM identity.user_role ur JOIN identity.role r ON r.id = ur.role_id WHERE r.code = 'MERCHANT');\n"
                    "DELETE FROM identity.user_role WHERE role_id = (SELECT id FROM identity.role WHERE code = 'MERCHANT');",
        "auth": f"DELETE FROM auth.user_account WHERE {seeded};",
        "operations": "DO $$ DECLARE t text; BEGIN FOR t IN SELECT tablename FROM pg_tables WHERE schemaname = 'operations' "
                      "LOOP EXECUTE format('TRUNCATE operations.%I CASCADE', t); END LOOP; END $$;",
    }
    sql_dir = os.path.join(args.out, "sql")
    os.makedirs(sql_dir, exist_ok=True)
    for name, copy in (("identity", identity), ("auth", auth), ("operations", operations)):
        with open(os.path.join(sql_dir, f"{name}.sql"), "w", encoding="utf-8") as handle:
            handle.write("\\set ON_ERROR_STOP on\nBEGIN;\n" + wipe[name] + "\n" + copy.render() + "\nCOMMIT;\n")

    status_labels = {"ACTIVE": "Đang làm", "OFFBOARDED": "Đã nghỉ việc", "SUSPENDED": "Tạm khoá",
                     "INVITED": "Mới mời, chưa kích hoạt", "ON_LEAVE": "Nghỉ phép"}
    rows = []
    for record in staff:
        hub = record["hub"]
        rows.append({
            "email": record["email"], "name": record["person"]["displayName"], "title": record["title"],
            "role": record["role"], "employeeCode": record.get("employeeCode"), "phone": record["person"]["phone"],
            "gender": record["person"]["gender"], "dateOfBirth": record["person"]["dateOfBirth"],
            "status": status_labels[record["status"]], "hubCode": hub["code"] if hub else None,
            "hubName": hub["name"] if hub else None, "province": provinces.get(hub["province"]) if hub else None,
            "dayOff": record.get("dayOff"), "routeStops": [stop["code"] for stop in record["route"] if stop],
            "sample": record["sample"],
        })
    counts = {}
    for record in staff:
        counts[record["title"]] = counts.get(record["title"], 0) + 1
    by_status = {}
    for record in staff:
        by_status[status_labels[record["status"]]] = by_status.get(status_labels[record["status"]], 0) + 1
    classes = {}
    for office in offices:
        classes[office["class"]] = classes.get(office["class"], 0) + 1
    summary = {"accounts": len(staff), "workforce": employee, "routes": len(routes), "byTitle": counts,
               "byStatus": by_status, "postOfficeClasses": classes}
    with open(os.path.join(args.out, "staff.json"), "w", encoding="utf-8") as handle:
        json.dump({"password": PASSWORD, "summary": summary, "accounts": rows}, handle, ensure_ascii=False, indent=1)
    with open(os.path.join(args.out, "staff.csv"), "w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.writer(handle)
        writer.writerow(["Email", "Họ tên", "Chức vụ", "Vai trò", "Mã NV", "SĐT", "Giới tính", "Ngày sinh",
                         "Trạng thái", "Nơi làm chính", "Mã hub", "Tỉnh/thành", "Ngày nghỉ trong tuần",
                         "Bưu cục trên tuyến", "Tài khoản thử"])
        for row in rows:
            writer.writerow([row["email"], row["name"], row["title"], row["role"], row["employeeCode"] or "",
                             row["phone"], "Nam" if row["gender"] == "MALE" else "Nữ", row["dateOfBirth"],
                             row["status"], row["hubName"] or "Văn phòng", row["hubCode"] or "", row["province"] or "",
                             row["dayOff"] or "", " ".join(row["routeStops"]), "x" if row["sample"] else ""])
    print(json.dumps(summary, ensure_ascii=False))


if __name__ == "__main__":
    main()
