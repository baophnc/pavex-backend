# Pavex backend manual test flow

This flow is intentionally seed-light. The local stack keeps only:

- the fixed bootstrap platform administrator (`admin@pavex.com`),
- the Network demo geography/topology used to exercise routing,
- the Pricing demo catalog used by local quote tests.

It does **not** create demo Identity users, Auth credentials, workforce members,
shifts, merchants, or merchant addresses. Create those records through the public
admin/user APIs so each feature is tested through its real lifecycle.

## 1. Start from a clean local database

If an older checkout has already run the removed demo seeders, deleting the Kotlin
seed classes is not enough to remove their rows. For the cleanest test run:

```bash
./scripts/dev-down.sh
docker compose down -v
./scripts/dev-up.sh
```

`docker compose down -v` deletes all local database/RabbitMQ volumes. Network and
Pricing demo catalogs are recreated on startup because their seeders are still kept.

Expected account state after startup:

- Auth/Identity contain only the bootstrap administrator.
- Operations contains no workforce members or shifts.
- Partners contains no demo merchant.

## 2. Login as the bootstrap administrator

```bash
BASE=http://localhost:8080

ADMIN_TOKEN="$(curl -fsS -X POST "$BASE/api/v1/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@pavex.com","password":"1205@Phuc"}' \
  | jq -r '.accessToken')"

test -n "$ADMIN_TOKEN" && test "$ADMIN_TOKEN" != null
```

Verify the principal:

```bash
curl -fsS "$BASE/api/v1/auth/me" \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq
```

The response must contain `PLATFORM_SUPER_ADMIN`.

## 3. Choose a real Network hub

The Network demo topology remains available. Find the hub you want to manage:

```bash
curl -fsS "$BASE/api/v1/admin/network/hubs?search=HN-HOANKIEM&page=1&pageSize=20" \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq
```

Copy the `id` of `HN-HOANKIEM` into:

```bash
HUB_A_ID='<HN-HOANKIEM UUID>'
```

Choose a second hub for negative scope tests, for example `HN-TULIEM`:

```bash
HUB_B_ID='<HN-TULIEM UUID>'
```

## 4. Onboard one HUB_MANAGER through Operations

A hub manager is represented as workforce kind `DISPATCHER`, but receives the
Identity role `HUB_MANAGER` through `dispatcherAccessLevel=HUB_MANAGER`.

```bash
MANAGER_JSON="$(curl -fsS -X POST "$BASE/api/v1/admin/operations/workforce/onboard" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{
    \"email\":\"manager.hn@example.com\",
    \"firstName\":\"Ha\",
    \"lastName\":\"Noi Manager\",
    \"displayName\":\"HN Hub Manager\",
    \"gender\":\"OTHER\",
    \"dateOfBirth\":\"1995-01-01\",
    \"kind\":\"DISPATCHER\",
    \"dispatcherAccessLevel\":\"HUB_MANAGER\",
    \"hubId\":\"$HUB_A_ID\",
    \"maxConcurrentAssignments\":10
  }")"

echo "$MANAGER_JSON" | jq
MANAGER_USER_ID="$(echo "$MANAGER_JSON" | jq -r '.userId')"
MANAGER_MEMBER_ID="$(echo "$MANAGER_JSON" | jq -r '.id')"
```

Expected result:

- Identity user exists and has role `HUB_MANAGER`.
- Auth has an invitation credential record, but no chosen password yet.
- Operations has one active workforce member with `kind=DISPATCHER`.
- The member has one active **primary** membership for `HUB_A_ID`.

## 5. Send and accept the invitation

Send the invitation as the platform admin:

```bash
curl -fsS -o /dev/null -w '%{http_code}\n' -X POST \
  "$BASE/api/v1/users/$MANAGER_USER_ID/invite" \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

Expected HTTP status: `204`.

Open Mailpit at `http://localhost:8025`, open the invitation email and copy the
`token` query parameter from its accept-invitation link. Then set the password:

```bash
INVITE_TOKEN='<token copied from Mailpit>'

curl -fsS -o /dev/null -w '%{http_code}\n' -X POST \
  "$BASE/api/v1/auth/accept-invite" \
  -H 'Content-Type: application/json' \
  -d "{\"token\":\"$INVITE_TOKEN\",\"password\":\"HubManager@123\"}"
```

Expected HTTP status: `204`.

Login as the new manager:

```bash
MANAGER_TOKEN="$(curl -fsS -X POST "$BASE/api/v1/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"email":"manager.hn@example.com","password":"HubManager@123"}' \
  | jq -r '.accessToken')"
```

Verify role/profile:

```bash
curl -fsS "$BASE/api/v1/auth/me" \
  -H "Authorization: Bearer $MANAGER_TOKEN" | jq
```

The role must be `HUB_MANAGER`.

## 6. Create workers in two hubs as platform admin

Use the same onboarding endpoint for operational roles.

### Courier at Hub A

```bash
COURIER_A_JSON="$(curl -fsS -X POST "$BASE/api/v1/admin/operations/workforce/onboard" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{
    \"email\":\"courier.hn.a@example.com\",
    \"firstName\":\"Courier\",
    \"lastName\":\"Hub A\",
    \"gender\":\"OTHER\",
    \"dateOfBirth\":\"1998-01-01\",
    \"kind\":\"COURIER\",
    \"hubId\":\"$HUB_A_ID\"
  }")"

echo "$COURIER_A_JSON" | jq
COURIER_A_MEMBER_ID="$(echo "$COURIER_A_JSON" | jq -r '.id')"
```

### Courier at Hub B

```bash
curl -fsS -X POST "$BASE/api/v1/admin/operations/workforce/onboard" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{
    \"email\":\"courier.hn.b@example.com\",
    \"firstName\":\"Courier\",
    \"lastName\":\"Hub B\",
    \"gender\":\"OTHER\",
    \"dateOfBirth\":\"1998-01-01\",
    \"kind\":\"COURIER\",
    \"hubId\":\"$HUB_B_ID\"
  }" | jq
```

Other role mappings for onboarding:

| Workforce kind | `dispatcherAccessLevel` | Identity role | Hub required |
|---|---|---|---|
| `COURIER` | omit | `COURIER` | yes |
| `WAREHOUSE_OPERATOR` | omit | `WAREHOUSE_OPERATOR` | yes |
| `LINE_HAUL_DRIVER` | omit | `LINE_HAUL_DRIVER` | yes |
| `DISPATCHER` | `HUB` | `HUB_DISPATCHER` | yes |
| `DISPATCHER` | `HUB_MANAGER` | `HUB_MANAGER` | yes |
| `DISPATCHER` | `GLOBAL` | `OPERATIONS_MANAGER` | no |

## 7. Verify HUB_MANAGER scope

The manager must see Hub A workforce:

```bash
curl -fsS "$BASE/api/v1/admin/operations/workforce?hubId=$HUB_A_ID&page=1&pageSize=50" \
  -H "Authorization: Bearer $MANAGER_TOKEN" | jq
```

The same manager must **not** be able to query Hub B explicitly:

```bash
curl -sS -o /tmp/pavex-scope-denied.json -w '%{http_code}\n' \
  "$BASE/api/v1/admin/operations/workforce?hubId=$HUB_B_ID&page=1&pageSize=50" \
  -H "Authorization: Bearer $MANAGER_TOKEN"
cat /tmp/pavex-scope-denied.json | jq
```

Expected HTTP status: `403`.

A list without `hubId` must be filtered to the manager's active hub memberships:

```bash
curl -fsS "$BASE/api/v1/admin/operations/workforce?page=1&pageSize=50" \
  -H "Authorization: Bearer $MANAGER_TOKEN" | jq
```

It must not expose the Hub B courier.

## 8. Verify global-only onboarding is protected

`HUB_MANAGER` intentionally does not create new Identity/workforce accounts.
Account onboarding remains a global operation for `PLATFORM_SUPER_ADMIN` or a
properly privileged `OPERATIONS_MANAGER`.

```bash
curl -sS -o /tmp/pavex-onboard-denied.json -w '%{http_code}\n' -X POST \
  "$BASE/api/v1/admin/operations/workforce/onboard" \
  -H "Authorization: Bearer $MANAGER_TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{
    \"email\":\"should.fail@example.com\",
    \"firstName\":\"Should\",
    \"lastName\":\"Fail\",
    \"gender\":\"OTHER\",
    \"dateOfBirth\":\"1998-01-01\",
    \"kind\":\"COURIER\",
    \"hubId\":\"$HUB_A_ID\"
  }"
cat /tmp/pavex-onboard-denied.json | jq
```

Expected HTTP status: `403`.

## 9. Verify a hub-scoped management function

A `HUB_MANAGER` can create/manage shifts only inside its hub scope. Use future
UTC timestamps when running the test:

```bash
SHIFT_JSON="$(curl -fsS -X POST "$BASE/api/v1/admin/operations/shifts" \
  -H "Authorization: Bearer $MANAGER_TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{
    \"hubId\":\"$HUB_A_ID\",
    \"name\":\"Hub A test shift\",
    \"startsAt\":\"2026-09-08T01:00:00Z\",
    \"endsAt\":\"2026-09-08T09:00:00Z\"
  }")"

echo "$SHIFT_JSON" | jq
SHIFT_ID="$(echo "$SHIFT_JSON" | jq -r '.id')"
```

If those sample timestamps are already in the past when you run the test,
replace them with future values.

Assign the Hub A courier to the shift:

```bash
curl -fsS -X POST "$BASE/api/v1/admin/operations/shifts/$SHIFT_ID/members" \
  -H "Authorization: Bearer $MANAGER_TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"workforceMemberId\":\"$COURIER_A_MEMBER_ID\"}" | jq
```

Creating a shift for `HUB_B_ID` with `MANAGER_TOKEN` must return `403`.

## Recommended order for testing the rest of the backend

Do not create all test data at once. Build state in this order so failures are
traceable to one bounded context:

1. **Auth + Identity** — bootstrap login, invitation, accept invite, login, role.
2. **Network** — hub lookup, lanes, schedules, route resolution.
3. **Operations workforce** — onboard each role and verify hub scope.
4. **Operations shifts** — create shift, roster, worker check-in/check-out.
5. **Partners** — create/approve a merchant and pickup address through APIs.
6. **Pricing** — quote against the retained local rate catalog.
7. **Shipment** — create one shipment only after merchant + pricing are valid.
8. **Operations assignments** — verify generated work, assignment scope and worker actions.
9. **Parcel scans/delivery** — advance one assignment at a time and verify the shipment state after every event.

Keep one account per role and one or two hubs at first. Once those tests pass,
add more accounts/hubs to test pagination, reassignment, capacity, concurrency,
and cross-region routing.
