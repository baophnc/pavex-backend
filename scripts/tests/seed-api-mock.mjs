// Test-only in-memory API contract double. Never imported by the real seed CLI.
import assert from "node:assert/strict";
import { readFileSync, writeFileSync } from "node:fs";
import { scenario, staff } from "../fixtures/luong-giao-hang-viet.mjs";
const path = process.env.PAVEX_SEED_TEST_DB;
assert.ok(path, "A test database file is required");
const db = JSON.parse(readFileSync(path, "utf8"));
const reply = (value, status = 200) => new Response(JSON.stringify(value), { status, headers: { "Content-Type": "application/json" } });
const page = items => ({ items, totalPages: 1 });
const hubList = scenario.hubs.map(code => ({ id: code, code, name: code, status: "ACTIVE" }));
const legs = scenario.hubs.slice(0, -1).map((code, i) => ({ laneId: `lane-${i}`, fromHubId: code, toHubId: scenario.hubs[i + 1], transportMode: "ROAD" }));
process.on("exit", () => writeFileSync(path, JSON.stringify(db)));
globalThis.fetch = async (input, init = {}) => {
  const url = new URL(input);
  const path = url.pathname.replace(/^\/api\/v1/, "");
  const method = init.method ?? "GET";
  const body = init.body ? JSON.parse(init.body) : null;
  const actor = init.headers?.Authorization?.replace("Bearer ", "");
  db.calls.push(`${method} ${path}`);
  if (path === "/messages") return reply({ messages: db.messages });
  if (path.startsWith("/message/")) {
    const msg = db.messages.find(m => m.ID === path.split("/").at(-1));
    return reply({ HTML: `<a href="http://localhost:${msg.portal === "MANAGEMENT" ? 3001 : 3000}/reset-password?token=${encodeURIComponent(msg.To[0].Address)}">Đặt mật khẩu</a>` });
  }
  if (path === "/auth/login") {
    if (body.email === "admin@pavex.com") return reply({ accessToken: body.email });
    assert.ok(db.accounts[body.email]?.verified);
    assert.equal(db.accounts[body.email].password, body.password);
    return reply({ accessToken: body.email });
  }
  if (path === "/auth/register") {
    assert.equal(body.email, scenario.merchant.email);
    assert.ok(!db.accounts[body.email]);
    db.accounts[body.email] = { id: body.email, verified: false, password: body.password };
    return reply({ id: body.email }, 201);
  }
  if (path === "/auth/forgot-password") {
    assert.ok(db.accounts[body.email]);
    const worker = staff.some(p => p.email === body.email);
    assert.equal(body.portal, worker ? "MANAGEMENT" : "CLIENT");
    db.messages.unshift({ ID: `mail-${db.messages.length}`, To: [{ Address: body.email }], portal: body.portal });
    return reply({});
  }
  if (path === "/auth/reset-password") {
    assert.ok(db.accounts[body.token]);
    db.accounts[body.token].verified = true;
    db.accounts[body.token].password = body.newPassword;
    return reply({});
  }
  if (path === "/auth/me/profile") return reply({});
  if (path === "/auth/me") return reply({ roles: db.merchant?.status === "ACTIVE" ? ["MERCHANT"] : ["CUSTOMER"] });
  if (path === "/admin/network/hubs") return reply(page(hubList.filter(h => h.code === url.searchParams.get("search"))));
  if (path === "/admin/operations/workforce") return reply(page(db.members.filter(m => m.identity.email === url.searchParams.get("search"))));
  if (path === "/admin/operations/workforce/onboard") {
    const p = staff.find(p => p.email === body.email);
    assert.ok(p);
    assert.equal(body.hubId, p.hubCode);
    assert.equal(body.kind, p.kind);
    assert.ok(!db.accounts[body.email]);
    db.accounts[body.email] = { id: body.email, verified: false };
    const member = { id: body.email, userId: body.email, kind: p.kind, status: "ACTIVE",
      identity: { email: body.email }, hubMemberships: p.hubCode ? [{ hubId: p.hubCode, isActive: true }] : [] };
    db.members.push(member);
    return reply(member, 201);
  }
  if (path === "/merchants/me/profile") {
    if (method === "POST") { assert.equal(body.businessName, scenario.merchant.businessName); db.merchant = { id: "merchant", status: "PENDING_VERIFICATION" }; }
    return reply(db.merchant ?? {}, db.merchant ? 200 : 404);
  }
  if (path === "/admin/merchants/merchant/status") { db.merchant.status = body.status; return reply(db.merchant); }
  if (path === "/merchants/me/pickup-addresses") {
    if (method === "POST") { assert.equal(body.isDefault, true); db.pickups.push(body); }
    return reply(db.pickups);
  }
  if (path === "/admin/operations/shifts" && method === "POST") {
    assert.ok(new Date(body.startsAt) > new Date());
    const shift = { ...body, id: body.hubId, status: "SCHEDULED" };
    db.shifts[shift.id] = { shift, roster: [] };
    return reply(shift);
  }
  const shiftMatch = path.match(/^\/admin\/operations\/shifts\/([^/]+)(\/members)?$/);
  if (shiftMatch) {
    const detail = db.shifts[shiftMatch[1]];
    assert.ok(detail);
    if (shiftMatch[2]) {
      assert.ok(new Date(detail.shift.startsAt) > new Date(), "roster must be assigned before shift starts");
      const member = db.members.find(m => m.id === body.workforceMemberId);
      assert.ok(member.hubMemberships.some(h => h.hubId === detail.shift.hubId));
      detail.roster.push({ workforceMemberId: member.id, status: "SCHEDULED" });
      return reply(detail.roster.at(-1));
    }
    if (method === "PUT") { assert.ok(new Date(body.startsAt) > new Date()); Object.assign(detail.shift, body); return reply(detail.shift); }
    return reply(detail);
  }
  const checkin = path.match(/^\/operations\/shifts\/([^/]+)\/check-in$/);
  if (checkin) {
    const detail = db.shifts[checkin[1]];
    assert.ok(new Date(detail.shift.startsAt) <= new Date());
    const own = detail.roster.find(r => r.workforceMemberId === actor);
    assert.ok(own, "check-in must use the worker's own account");
    own.status = "CHECKED_IN";
    return reply(own);
  }
  if (path === "/admin/network/route-templates") {
    if (method === "POST") {
      assert.equal(body.priority, 1);
      assert.deepEqual(body.laneIds, legs.map(l => l.laneId));
      db.route = { ...body, status: "ACTIVE", id: "route", legs };
      return reply(db.route);
    }
    return reply(page([{ code: "HN-HCM-STANDARD", legs }, ...(db.route ? [db.route] : [])]));
  }
  if (path === "/admin/network/route-templates/journey") return reply({ routeTemplateCode: db.route.code });
  if (path === "/pricing/quotes") return reply({ options: [{ quoteId: "quote", serviceLevel: "STANDARD" }] });
  if (path === "/shipments") {
    assert.equal(actor, scenario.merchant.email);
    if (method === "POST") {
      assert.equal(db.merchant.status, "ACTIVE");
      assert.equal(db.pickups.length, 1);
      assert.equal(init.headers["Idempotency-Key"], scenario.code);
      assert.equal(body.merchantReference, scenario.code);
      assert.equal(body.parcels.length, 1);
      assert.equal(body.recipient.contactName, "Phạm Thảo Vy");
      assert.equal(db.shipments.length, 0, "only one shipment allowed");
      db.shipments.push({ ...body, trackingCode: "PVX-TEST-001", status: "AWAITING_PICKUP" });
      return reply(db.shipments[0], 201);
    }
    return reply({ items: db.shipments, nextCursor: null });
  }
  if (path === "/admin/shipments/PVX-TEST-001") return reply({ id: "shipment", shipment: db.shipments[0] });
  if (path === "/admin/operations/assignments") return reply(Array.from({ length: 9 }, (_, i) => ({ sequence: i + 1 })));
  throw new Error(`Unexpected mock request: ${method} ${path}`);
};
