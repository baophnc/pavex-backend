#!/usr/bin/env node
import { mkdir, open, readFile, rename, unlink, writeFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import { scenario, staff, roleLabel } from "./fixtures/seed-baofuc-e2e.mjs";

const base = new URL(process.env.PAVEX_API_URL ?? "http://localhost:8080");
const mailpit = new URL(process.env.PAVEX_MAILPIT_URL ?? "http://localhost:8025");
const password = process.env.PAVEX_DEMO_PASSWORD ?? "1205@Phuc";
const directory = fileURLToPath(new URL("../.local/seed-baofuc-e2e/", import.meta.url));
const statePath = `${directory}/state.json`;
const reportPath = `${directory}/ket-qua.md`;
const lockPath = `${directory}/running.lock`;
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));

let state = { version: 1, base: base.origin, accounts: {}, shifts: {}, shipments: {} };
let admin;
const tokens = new Map();
const hubs = new Map();
const members = new Map();

function ensureLocal(url) {
  if (url.protocol !== "http:" || !["localhost", "127.0.0.1", "[::1]"].includes(url.hostname)
    || url.username || url.password || url.pathname !== "/" || url.search || url.hash) {
    throw new Error("Seed chỉ cho phép HTTP localhost; không chạy seed này vào môi trường bên ngoài.");
  }
}

async function save() {
  await writeFile(`${statePath}.tmp`, JSON.stringify(state, null, 2), { mode: 0o600 });
  await rename(`${statePath}.tmp`, statePath);
}

async function api(path, { method = "GET", body, token = admin, headers = {}, allow = [] } = {}) {
  const response = await fetch(new URL(`/api/v1${path}`, base), {
    method,
    redirect: "error",
    signal: AbortSignal.timeout(30000),
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...headers,
    },
    ...(body === undefined ? {} : { body: JSON.stringify(body) }),
  });
  const value = await response.json().catch(() => null);
  if (!response.ok && !allow.includes(response.status)) {
    throw new Error(`${method} ${path.split("?")[0]}: HTTP ${response.status} (${value?.code ?? value?.error ?? "không thành công"}).`);
  }
  return { status: response.status, value };
}

async function list(path) {
  const items = [];
  for (let page = 1; page <= 100; page++) {
    const { value } = await api(`${path}${path.includes("?") ? "&" : "?"}page=${page}&pageSize=100`);
    const batch = value.items ?? value.content;
    if (!Array.isArray(batch)) throw new Error(`Phản hồi danh sách không hợp lệ: ${path}`);
    items.push(...batch);
    if (page >= value.totalPages || batch.length < 100) return items;
  }
  throw new Error("Danh sách vượt giới hạn an toàn của seed local.");
}

async function loginResult(email, loginPassword = password) {
  return api("/auth/login", {
    method: "POST",
    token: null,
    body: { email, password: loginPassword },
    allow: [401, 403, 423, 428, 429],
  });
}

async function login(email, loginPassword = password) {
  const result = await loginResult(email, loginPassword);
  if (result.status !== 200 || !result.value?.accessToken) {
    throw new Error(`Không đăng nhập được ${email}: ${result.value?.code ?? `HTTP ${result.status}`}.`);
  }
  return result.value.accessToken;
}

async function mail(path) {
  const response = await fetch(new URL(path, mailpit), {
    redirect: "error",
    signal: AbortSignal.timeout(10000),
  });
  if (!response.ok) throw new Error(`Không đọc được Mailpit: HTTP ${response.status}.`);
  return response.json();
}

async function waitForMailToken(email, pathname, { timeoutMs = 60000 } = {}) {
  const target = email.trim().toLowerCase();
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const messages = (await mail("/api/v1/messages?limit=100")).messages ?? [];
    for (const item of messages) {
      if (!item.To?.some(to => to.Address?.toLowerCase() === target)) continue;
      const message = await mail(`/api/v1/message/${encodeURIComponent(item.ID)}`);
      const content = `${message.HTML ?? ""}\n${message.Text ?? ""}`.replaceAll("&amp;", "&");
      for (const match of content.matchAll(/https?:\/\/[^\s<>"']+/g)) {
        const link = new URL(match[0]);
        if (link.pathname === pathname && link.searchParams.has("token")) {
          return link.searchParams.get("token");
        }
      }
    }
    await sleep(1000);
  }
  throw new Error(`Không tìm thấy mail ${pathname} cho ${email} trong Mailpit.`);
}

async function activateMerchantCredential() {
  const merchant = scenario.merchant;
  let account = state.accounts[merchant.email];
  if (!account) {
    const { value } = await api("/auth/register", {
      method: "POST",
      token: null,
      body: { email: merchant.email, password },
    });
    account = state.accounts[merchant.email] = {
      userId: value.id,
      credentialType: "registration",
      activated: false,
    };
    await save();
  }

  if (!account.activated) {
    const probe = await loginResult(merchant.email);
    if (probe.status === 200 && probe.value?.accessToken) {
      tokens.set(merchant.email, probe.value.accessToken);
      account.activated = true;
      await save();
    } else {
      if (probe.value?.code !== "email_not_verified") {
        throw new Error(`Tài khoản ${merchant.email} tồn tại trong state nhưng không thể xác nhận credential (${probe.value?.code ?? probe.status}).`);
      }
      await api("/auth/resend-verification", {
        method: "POST",
        token: null,
        body: { email: merchant.email },
      });
      const rawToken = await waitForMailToken(merchant.email, "/verify-email");
      await api("/auth/verify-email", {
        method: "POST",
        token: null,
        body: { token: rawToken },
      });
      account.activated = true;
      await save();
    }
  }

  if (!tokens.has(merchant.email)) tokens.set(merchant.email, await login(merchant.email));
}

async function activateWorkforceCredential(person) {
  const account = state.accounts[person.email];
  if (!account) throw new Error(`Thiếu state account cho workforce ${person.email}.`);

  if (!account.activated) {
    const invite = await api(`/users/${account.userId}/invite`, {
      method: "POST",
      allow: [409],
    });
    if (invite.status === 409) {
      const probe = await loginResult(person.email);
      if (probe.status !== 200 || !probe.value?.accessToken) {
        throw new Error(`Workforce ${person.email} đã được kích hoạt trước đó nhưng mật khẩu hiện tại không khớp PAVEX_DEMO_PASSWORD.`);
      }
      tokens.set(person.email, probe.value.accessToken);
    } else {
      const rawToken = await waitForMailToken(person.email, "/accept-invite");
      await api("/auth/accept-invite", {
        method: "POST",
        token: null,
        body: { token: rawToken, password },
      });
    }
    account.activated = true;
    await save();
  }

  if (!tokens.has(person.email)) tokens.set(person.email, await login(person.email));
}

async function prepareHubs() {
  const codes = new Set(scenario.orders.flatMap(order => order.hubCodes));
  for (const code of codes) {
    const matches = (await list(`/admin/network/hubs?search=${encodeURIComponent(code)}`)).filter(h => h.code === code);
    if (matches.length !== 1 || matches[0].status !== "ACTIVE") {
      throw new Error(`Thiếu bưu cục ACTIVE ${code}; hãy bật Network demo geography trước.`);
    }
    hubs.set(code, matches[0]);
  }
}

async function prepareWorkforce() {
  for (const person of staff) {
    let member = (await list(`/admin/operations/workforce?search=${encodeURIComponent(person.email)}`))
      .find(item => item.identity?.email?.toLowerCase() === person.email.toLowerCase());

    if (member && !state.accounts[person.email]) {
      throw new Error(`Email workforce ${person.email} đã tồn tại nhưng không thuộc state của seed; dừng để không chiếm tài khoản có sẵn.`);
    }
    if (!member && state.accounts[person.email]) {
      throw new Error(`State có ${person.email} nhưng Operations không còn workforce member tương ứng.`);
    }

    if (!member) {
      const { hubCode, ...profile } = person;
      member = (await api("/admin/operations/workforce/onboard", {
        method: "POST",
        body: {
          ...profile,
          hubId: hubCode ? hubs.get(hubCode).id : null,
          maxConcurrentAssignments: 10,
        },
      })).value;
      state.accounts[person.email] = {
        userId: member.userId,
        memberId: member.id,
        credentialType: "invitation",
        activated: false,
      };
      await save();
    }

    if (member.kind !== person.kind || member.status !== "ACTIVE") {
      throw new Error(`Workforce ${person.email} không còn đúng kind/status mà seed mong đợi.`);
    }
    if (person.hubCode && !member.hubMemberships?.some(m => m.hubId === hubs.get(person.hubCode).id && m.isActive)) {
      throw new Error(`Workforce ${person.email} không còn membership ACTIVE tại ${person.hubCode}.`);
    }

    await activateWorkforceCredential(person);
    members.set(person.email, member);
    console.log(`Workforce sẵn sàng: ${person.displayName} — ${roleLabel(person)}${person.hubCode ? ` — ${person.hubCode}` : ""}`);
  }
}

async function prepareMerchant() {
  await activateMerchantCredential();
  let token = tokens.get(scenario.merchant.email);
  const merchant = scenario.merchant;

  const me = (await api("/auth/me", { token })).value;
  if (me.email?.toLowerCase() !== merchant.email || me.emailVerified !== true) {
    throw new Error("Email merchant chưa được verify đúng trạng thái Identity.");
  }

  await api("/auth/me/profile", {
    method: "PATCH",
    token,
    body: {
      firstName: merchant.firstName,
      lastName: merchant.lastName,
      displayName: merchant.displayName,
      gender: merchant.gender,
      dateOfBirth: merchant.dateOfBirth,
      avatarUrl: null,
    },
  });

  const completed = (await api("/auth/me", { token })).value;
  if (completed.profileStatus !== "COMPLETED" || completed.emailVerified !== true) {
    throw new Error(`Merchant Identity chưa hoàn tất: profile=${completed.profileStatus}, emailVerified=${completed.emailVerified}.`);
  }

  let profile = await api("/merchants/me/profile", { token, allow: [404] });
  if (profile.status === 404) {
    profile = await api("/merchants/me/profile", {
      method: "POST",
      token,
      body: {
        businessName: merchant.businessName,
        contactName: merchant.displayName,
        contactPhone: merchant.contactPhone,
        contactEmail: merchant.email,
      },
    });
  }

  if (profile.value.status === "PENDING_VERIFICATION") {
    profile = await api(`/admin/merchants/${profile.value.id}/status`, {
      method: "PATCH",
      body: {
        status: "ACTIVE",
        reasonDetail: "Seed E2E: hồ sơ merchant đã được xác minh để kiểm thử luồng tạo đơn và vận hành.",
      },
    });
    profile.value = profile.value.merchant ?? profile.value;
  }
  if (profile.value.status !== "ACTIVE") {
    throw new Error(`Merchant ${merchant.email} không ở trạng thái ACTIVE (${profile.value.status}).`);
  }

  const deadline = Date.now() + 60000;
  let merchantReady = false;
  while (Date.now() < deadline) {
    const current = (await api("/auth/me", { token })).value;
    if (current.roles?.includes("MERCHANT") && current.profileStatus === "COMPLETED" && current.emailVerified === true) {
      merchantReady = true;
      break;
    }
    await sleep(1000);
  }
  if (!merchantReady) throw new Error("Role MERCHANT chưa đồng bộ sang Identity sau khi duyệt Partners.");

  token = await login(merchant.email);
  tokens.set(merchant.email, token);

  const { value: pickupAddresses } = await api("/merchants/me/pickup-addresses", { token });
  for (const [index, order] of scenario.orders.entries()) {
    const exists = pickupAddresses.some(address =>
      address.provinceCode === order.sender.provinceCode && address.wardCode === order.sender.wardCode,
    );
    if (!exists) {
      await api("/merchants/me/pickup-addresses", {
        method: "POST",
        token,
        body: {
          ...order.sender,
          name: order.pickupName,
          isDefault: index === 0 && !pickupAddresses.some(address => address.isDefault),
        },
      });
    }
  }

  console.log(`Merchant sẵn sàng: ${merchant.email} — verified email — profile COMPLETED — MERCHANT ACTIVE.`);
}

async function prepareShifts() {
  const byHub = new Map();
  for (const person of staff.filter(item => item.kind !== "DISPATCHER")) {
    const listForHub = byHub.get(person.hubCode) ?? [];
    listForHub.push(person);
    byHub.set(person.hubCode, listForHub);
  }

  for (const [hubCode, workers] of byHub) {
    const hub = hubs.get(hubCode);
    let shift;
    if (state.shifts[hubCode]) {
      const existing = await api(`/admin/operations/shifts/${state.shifts[hubCode]}`, { allow: [404] });
      shift = existing.status === 200 ? existing.value.shift : null;
    }
    if (shift && (shift.status !== "SCHEDULED" || new Date(shift.endsAt).getTime() <= Date.now())) {
      shift = null;
      delete state.shifts[hubCode];
      await save();
    }
    if (!shift) {
      shift = (await api("/admin/operations/shifts", {
        method: "POST",
        body: {
          hubId: hub.id,
          name: `Ca seed E2E — ${hub.name}`,
          startsAt: new Date(Date.now() + 3000).toISOString(),
          endsAt: new Date(Date.now() + 8 * 3600000).toISOString(),
        },
      })).value;
      state.shifts[hubCode] = shift.id;
      await save();
    }

    const roster = (await api(`/admin/operations/shifts/${shift.id}`)).value.roster;
    for (const worker of workers) {
      const member = members.get(worker.email);
      if (!roster.some(r => r.workforceMemberId === member.id && ["SCHEDULED", "CHECKED_IN"].includes(r.status))) {
        await api(`/admin/operations/shifts/${shift.id}/members`, {
          method: "POST",
          body: { workforceMemberId: member.id },
        });
      }
    }

    if (new Date(shift.startsAt).getTime() > Date.now()) {
      await sleep(Math.max(0, new Date(shift.startsAt).getTime() - Date.now()) + 300);
    }
    for (const worker of workers) {
      await api(`/operations/shifts/${shift.id}/check-in`, {
        method: "POST",
        token: tokens.get(worker.email),
        allow: [409],
      });
      await api("/operations/availability", {
        method: "PUT",
        token: tokens.get(worker.email),
        body: { status: "AVAILABLE" },
      });
    }
    console.log(`Ca vận hành sẵn sàng: ${hubCode} (${workers.length} execution workers).`);
  }
}

async function ensureRoadTemplate(order) {
  const templates = await list(`/admin/network/route-templates?search=${encodeURIComponent(order.standardRouteCode)}`);
  const standard = templates.find(item => item.code === order.standardRouteCode);
  if (!standard || standard.legs.length !== 3 || standard.legs.some(leg => leg.transportMode !== "ROAD")) {
    throw new Error(`Thiếu route demo ${order.standardRouteCode} gồm đúng 3 chặng ROAD.`);
  }

  const customSearch = await list(`/admin/network/route-templates?search=${encodeURIComponent(order.routeCode)}`);
  let route = customSearch.find(item => item.code === order.routeCode);
  if (!route) {
    route = (await api("/admin/network/route-templates", {
      method: "POST",
      body: {
        code: order.routeCode,
        name: `${order.hubCodes[0]} → HCM-SAIGON · E2E road`,
        description: "Route priority 1 dành riêng cho seed E2E để assignment đi đúng local hub → gateway → HCM gateway → Sài Gòn.",
        originHubId: hubs.get(order.hubCodes[0]).id,
        destinationHubId: hubs.get(order.hubCodes.at(-1)).id,
        priority: 1,
        laneIds: standard.legs.map(leg => leg.laneId),
      },
    })).value;
  }

  if (route.priority !== 1 || route.legs.length !== 3
      || route.legs.some((leg, index) => leg.laneId !== standard.legs[index]?.laneId)) {
    throw new Error(`Route ${order.routeCode} đã bị chỉnh sửa; seed không tự ghi đè route có sẵn.`);
  }

  if (route.status !== "ACTIVE") {
    route = (await api(`/admin/network/route-templates/${route.id}/status`, {
      method: "PATCH",
      body: {
        active: true,
        reason: "Kích hoạt route dành riêng cho seed E2E.",
      },
    })).value;
  }

  const { value: journey } = await api(
    `/admin/network/route-templates/journey?originHubId=${route.originHubId}&destinationHubId=${route.destinationHubId}&shipmentWeightKg=1`,
  );
  if (journey.routeTemplateCode !== order.routeCode) {
    throw new Error(`Network đang chọn ${journey.routeTemplateCode ?? "graph fallback"} thay vì ${order.routeCode}; kiểm tra route priority 1 khác cùng origin/destination.`);
  }
  return route;
}

async function findMerchantShipment(token, merchantReference) {
  let cursor;
  do {
    const { value } = await api(`/shipments?limit=100${cursor ? `&cursor=${encodeURIComponent(cursor)}` : ""}`, { token });
    const found = value.items.find(item => item.merchantReference === merchantReference);
    if (found) return found;
    cursor = value.nextCursor;
  } while (cursor);
  return null;
}

async function createShipment(order) {
  const token = tokens.get(scenario.merchant.email);
  await ensureRoadTemplate(order);

  let shipment = await findMerchantShipment(token, order.merchantReference);
  if (!shipment) {
    const { value: quote } = await api("/pricing/quotes", {
      method: "POST",
      token,
      body: {
        originProvinceCode: order.sender.provinceCode,
        originWardCode: order.sender.wardCode,
        destinationProvinceCode: scenario.destination.provinceCode,
        destinationWardCode: scenario.destination.wardCode,
        parcels: [{ weightKg: 1, lengthCm: 20, widthCm: 15, heightCm: 10 }],
        codAmount: order.codAmount,
        declaredValue: order.declaredValue,
      },
    });
    const option = quote.options.find(
      item => String(item.serviceLevel ?? "").trim().toUpperCase() === "STANDARD",
    );
    if (!option) {
      const returnedLevels = quote.options.map(item => item.serviceLevel).filter(Boolean).join(", ") || "<none>";
      throw new Error(
        `Pricing không trả Standard quote cho ${order.merchantReference}; service levels nhận được: ${returnedLevels}.`,
      );
    }

    shipment = (await api("/shipments", {
      method: "POST",
      token,
      headers: { "Idempotency-Key": order.merchantReference },
      body: {
        quoteId: option.quoteId,
        merchantReference: order.merchantReference,
        sender: order.sender,
        recipient: scenario.destination,
        shippingFeePayer: "SENDER",
        parcels: [order.parcel],
        note: `Seed E2E ${order.merchantReference}; COD ${order.codAmount.toLocaleString("vi-VN")}đ.`,
      },
    })).value;
  }

  const { value: detail } = await api(`/admin/shipments/${shipment.trackingCode}`);
  let { value: assignments } = await api(`/admin/operations/assignments?shipmentId=${detail.id}`);
  if (!Array.isArray(assignments)) {
    throw new Error(`Shipment ${shipment.trackingCode} không trả về danh sách operational assignments hợp lệ.`);
  }

  const expected = [
    ["PICKUP", "COURIER", order.hubCodes[0]],
    ["ORIGIN_HUB_HANDLING", "WAREHOUSE_OPERATOR", order.hubCodes[0]],
    ["LINE_HAUL", "LINE_HAUL_DRIVER", order.hubCodes[0]],
    ["TRANSIT_HUB_HANDLING", "WAREHOUSE_OPERATOR", order.hubCodes[1]],
    ["LINE_HAUL", "LINE_HAUL_DRIVER", order.hubCodes[1]],
    ["TRANSIT_HUB_HANDLING", "WAREHOUSE_OPERATOR", order.hubCodes[2]],
    ["LINE_HAUL", "LINE_HAUL_DRIVER", order.hubCodes[2]],
    ["DESTINATION_HUB_HANDLING", "WAREHOUSE_OPERATOR", order.hubCodes[3]],
    ["LAST_MILE_DELIVERY", "COURIER", order.hubCodes[3]],
  ];

  // For a 3-leg route Operations creates 9 assignments: pickup + origin handling +
  // 3 line-hauls + 2 transit handling + destination handling + last mile.
  if (assignments.length !== expected.length) {
    throw new Error(`Shipment ${shipment.trackingCode}: expected ${expected.length} assignments, got ${assignments.length}.`);
  }

  const hubCodeById = new Map([...hubs.entries()].map(([code, hub]) => [hub.id, code]));
  assignments.sort((a, b) => a.sequence - b.sequence);
  assignments.forEach((assignment, index) => {
    const [type, kind, hubCode] = expected[index];
    if (assignment.type !== type || assignment.requiredKind !== kind || hubCodeById.get(assignment.hubId) !== hubCode) {
      throw new Error(
        `Assignment #${assignment.sequence} của ${shipment.trackingCode} sai plan: ` +
        `${assignment.type}/${assignment.requiredKind}/${hubCodeById.get(assignment.hubId)}; expected ${type}/${kind}/${hubCode}.`,
      );
    }
  });

  const pickup = assignments[0];
  const targetCourier = members.get(order.pickupCourierEmail);
  if (pickup.assigneeWorkforceMemberId !== targetCourier.id) {
    if (["PENDING", "READY", "ASSIGNED", "REJECTED"].includes(pickup.status)) {
      await api(`/admin/operations/assignments/${pickup.id}/assign`, {
        method: "POST",
        body: { workforceMemberId: targetCourier.id },
      });
      assignments = (await api(`/admin/operations/assignments?shipmentId=${detail.id}`)).value.sort((a, b) => a.sequence - b.sequence);
    } else {
      console.log(`Giữ nguyên pickup ${pickup.code} vì đơn đã tiến tới trạng thái ${pickup.status}; seed không tua ngược tiến độ.`);
    }
  }

  state.shipments[order.key] = {
    shipmentId: detail.id,
    trackingCode: shipment.trackingCode,
    merchantReference: order.merchantReference,
    codAmount: order.codAmount,
  };
  await save();

  console.log(`${shipment.trackingCode}: ${order.hubCodes.join(" → ")} — COD ${order.codAmount.toLocaleString("vi-VN")}đ — pickup assigned ${order.pickupCourierEmail}`);
  return { order, shipment, detail, assignments };
}

function expectedWorkerEmail(kind, hubCode) {
  return staff.find(item => item.kind === kind && item.hubCode === hubCode)?.email ?? "—";
}

async function writeReport(results) {
  const lines = [
    "# Seed E2E Bảo Phúc",
    "",
    `Merchant: **${scenario.merchant.email}**`,
    "",
    "- Email: verified bằng `/auth/verify-email` từ token email Mailpit.",
    "- Identity profile: `COMPLETED`.",
    "- Partners merchant: `ACTIVE`, role `MERCHANT` đã đồng bộ.",
    "- Mật khẩu không ghi vào report; dùng giá trị `PAVEX_DEMO_PASSWORD` của môi trường seed.",
    "",
    "## Đơn hàng",
    "",
    "| Tracking | Merchant reference | Tuyến | COD | Pickup courier |",
    "|---|---|---|---:|---|",
    ...results.map(({ order, shipment, assignments }) => {
      const pickup = assignments.find(item => item.type === "PICKUP");
      const assigned = staff.find(person => members.get(person.email)?.id === pickup?.assigneeWorkforceMemberId)?.email ?? "—";
      return `| ${shipment.trackingCode} | ${order.merchantReference} | ${order.hubCodes.join(" → ")} | ${order.codAmount.toLocaleString("vi-VN")}đ | ${assigned} |`;
    }),
    "",
    "## Operational plan",
    "",
    "Các assignment sau pickup vẫn `BLOCKED` theo domain và chỉ được activate/auto-assign sau khi assignment trước hoàn tất. Seed không gán người vào `BLOCKED` để tránh phá state machine.",
    "",
  ];

  for (const { order, shipment, assignments } of results) {
    lines.push(`### ${shipment.trackingCode} — ${order.merchantReference}`, "", "| # | Type | Required kind | Hub | Current status | Current assignee | Target seeded worker |", "|---:|---|---|---|---|---|---|");
    for (const assignment of assignments) {
      const hubCode = [...hubs.entries()].find(([, hub]) => hub.id === assignment.hubId)?.[0] ?? assignment.hubId;
      const currentAssignee = staff.find(person => members.get(person.email)?.id === assignment.assigneeWorkforceMemberId)?.email ?? "—";
      lines.push(`| ${assignment.sequence} | ${assignment.type} | ${assignment.requiredKind} | ${hubCode} | ${assignment.status} | ${currentAssignee} | ${expectedWorkerEmail(assignment.requiredKind, hubCode)} |`);
    }
    lines.push("");
  }

  lines.push("## Workforce accounts", "", "| Email | Role | Hub |", "|---|---|---|", ...staff.map(person => `| ${person.email} | ${roleLabel(person)} | ${person.hubCode ?? "GLOBAL"} |`), "");
  await writeFile(reportPath, lines.join("\n"), { mode: 0o600 });
  console.log(`Báo cáo seed: ${reportPath}`);
}

async function main() {
  ensureLocal(base);
  ensureLocal(mailpit);

  if (!process.argv.includes("--apply")) {
    console.log("XEM TRƯỚC — không kết nối hoặc sửa dữ liệu. Dùng --apply khi backend + Mailpit đang chạy.");
    console.log(`Merchant: ${scenario.merchant.email}`);
    for (const order of scenario.orders) {
      console.log(`${order.merchantReference}: ${order.hubCodes.join(" → ")} — COD ${order.codAmount.toLocaleString("vi-VN")}đ`);
    }
    console.log(`Workforce: ${staff.length} accounts (${[...new Set(staff.map(roleLabel))].join(", ")}).`);
    return;
  }

  if (password.length < 8 || password.length > 128 || !/[a-z]/.test(password) || !/[A-Z]/.test(password)
      || !/\d/.test(password) || !/[^A-Za-z0-9]/.test(password)) {
    throw new Error("PAVEX_DEMO_PASSWORD phải 8–128 ký tự và có chữ thường, chữ hoa, số, ký tự đặc biệt.");
  }

  await mkdir(directory, { recursive: true, mode: 0o700 });
  const lock = await open(lockPath, "wx", 0o600).catch(() => {
    throw new Error(`Seed đang chạy hoặc còn lock cũ: ${lockPath}`);
  });

  try {
    try {
      state = JSON.parse(await readFile(statePath, "utf8"));
    } catch (error) {
      if (error.code !== "ENOENT") throw error;
    }
    if (state.version !== 1 || state.base !== base.origin) throw new Error("State seed không thuộc môi trường hiện tại.");

    admin = await login(
      process.env.PAVEX_ADMIN_EMAIL ?? "admin@pavex.com",
      process.env.PAVEX_ADMIN_PASSWORD ?? "1205@Phuc",
    );
    await mail("/api/v1/messages?limit=1");

    await prepareHubs();
    await prepareWorkforce();
    await prepareMerchant();
    await prepareShifts();

    const results = [];
    for (const order of scenario.orders) results.push(await createShipment(order));
    await writeReport(results);
  } finally {
    await lock.close();
    await unlink(lockPath).catch(() => {});
  }
}

main().catch(error => {
  console.error(`Dừng seed: ${error.message}`);
  process.exitCode = 1;
});
