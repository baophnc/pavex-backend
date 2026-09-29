#!/usr/bin/env node
// Creates demo shipments for the nationwide seed through the public APIs, then walks part of
// them through the field-app flow (pickup, hub receipt, shuttle, delivery) so every screen and
// report has data. Run after scripts/seed-nationwide/apply.sh:
//
//   node scripts/seed-nationwide/demo-shipments.mjs [--per-shop 8] [--progress 24]
//
// Uses only the endpoints the web portal and the Pavex Bưu cục app use.
import { randomUUID } from "node:crypto";
import { readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const repo = join(dirname(fileURLToPath(import.meta.url)), "..", "..");
const outDir = join(repo, ".local", "seed", "nationwide");
const manifest = JSON.parse(readFileSync(join(outDir, "manifest.json"), "utf8"));
const gateway = (process.env.PAVEX_GATEWAY_URL ?? "http://localhost:8080").replace(/\/$/, "");
const args = process.argv.slice(2);
const option = (name, fallback) => {
  const index = args.indexOf(name);
  return index >= 0 ? Number(args[index + 1]) : fallback;
};
const perShop = option("--per-shop", 8);
const progressCount = option("--progress", 24);

let seed = 20260925;
const random = () => {
  seed = (seed * 1664525 + 1013904223) % 4294967296;
  return seed / 4294967296;
};
const pick = (items) => items[Math.floor(random() * items.length)];
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

class ApiError extends Error {
  constructor(status, body) {
    super(`${status} ${body?.code ?? ""} ${body?.message ?? ""}`.trim());
    this.status = status;
    this.code = body?.code;
  }
}

async function api(path, { token, method = "GET", body, headers = {}, form } = {}) {
  for (let attempt = 0; ; attempt += 1) {
    const response = await fetch(`${gateway}${path}`, {
      method,
      headers: {
        accept: "application/json",
        ...(body !== undefined ? { "content-type": "application/json" } : {}),
        ...(token ? { authorization: `Bearer ${token}` } : {}),
        ...headers,
      },
      body: form ?? (body !== undefined ? JSON.stringify(body) : undefined),
    });
    if (response.status === 429 && attempt < 8) {
      await sleep(1000 * (attempt + 1));
      continue;
    }
    const text = await response.text();
    const data = text ? JSON.parse(text) : null;
    if (!response.ok) throw new ApiError(response.status, data?.error ?? data);
    return data;
  }
}

const tokens = new Map();
async function login(email) {
  if (!tokens.has(email)) {
    const result = await api("/api/v1/auth/login", { method: "POST", body: { email, password: manifest.password } });
    tokens.set(email, result.accessToken);
  }
  return tokens.get(email);
}

// ---- 1. Shops create shipments ---------------------------------------------------------------
const goods = [
  ["Áo thun cotton", "APPAREL"], ["Váy công sở", "APPAREL"], ["Tai nghe bluetooth", "ELECTRONICS"],
  ["Sạc dự phòng", "ELECTRONICS"], ["Trà xanh 500g", "DRY_FOOD"], ["Cà phê hạt 1kg", "DRY_FOOD"],
  ["Son dưỡng", "COSMETICS"], ["Bộ chén gốm", "FRAGILE_GOODS"], ["Hồ sơ hợp đồng", "DOCUMENTS"],
  ["Khăn lụa", "APPAREL"], ["Nồi inox", "HOUSEHOLD"], ["Mật ong 1 lít", "DRY_FOOD"],
];
const codChoices = [0, 0, 120000, 185000, 250000, 349000, 420000, 599000, 890000];

function recipientFor(pickup) {
  const roll = random();
  const pool = roll < 0.4
    ? manifest.recipients.filter((item) => item.provinceCode === pickup.provinceCode)
    : roll < 0.7
      ? manifest.recipients.filter((item) => item.region === pickup.region && item.provinceCode !== pickup.provinceCode)
      : manifest.recipients.filter((item) => item.region !== pickup.region);
  return pick(pool.length ? pool : manifest.recipients);
}

async function createShipments() {
  const created = [];
  for (const shop of manifest.shops) {
    const token = await login(shop.email);
    for (let index = 0; index < perShop; index += 1) {
      const pickup = shop.pickups[index % shop.pickups.length];
      const recipient = recipientFor(pickup);
      const parcelCount = random() < 0.15 ? 2 : 1;
      const parcels = Array.from({ length: parcelCount }, () => ({
        weightKg: Math.round((0.3 + random() * 4) * 10) / 10,
        lengthCm: 20 + Math.floor(random() * 20),
        widthCm: 15 + Math.floor(random() * 15),
        heightCm: 5 + Math.floor(random() * 15),
      }));
      const codAmount = pick(codChoices);
      try {
        const quote = await api("/api/v1/pricing/quotes", {
          method: "POST",
          body: {
            originProvinceCode: pickup.provinceCode,
            originWardCode: pickup.wardCode,
            destinationProvinceCode: recipient.provinceCode,
            destinationWardCode: recipient.wardCode,
            parcels,
            codAmount,
            declaredValue: codAmount,
          },
        });
        const option = quote.options.find((item) => item.serviceLevel === (random() < 0.75 ? "Standard" : "Express"))
          ?? quote.options[0];
        const shipment = await api("/api/v1/shipments", {
          token,
          method: "POST",
          headers: { "idempotency-key": `demo-${shop.code}-${index}` },
          body: {
            quoteId: option.quoteId,
            merchantReference: `${shop.code}-DH${String(index + 1).padStart(3, "0")}`,
            pickupAddressId: pickup.id,
            recipient: {
              contactName: recipient.name,
              contactPhone: recipient.phone,
              addressLine: recipient.addressLine,
              provinceCode: recipient.provinceCode,
              wardCode: recipient.wardCode,
            },
            shippingFeePayer: random() < 0.7 ? "SENDER" : "RECIPIENT",
            parcels: parcels.map(() => {
              const [description, category] = pick(goods);
              return { description, quantity: 1 + Math.floor(random() * 3), category, isFragile: category === "FRAGILE_GOODS" };
            }),
            note: random() < 0.3 ? "Gọi trước khi giao" : null,
          },
        });
        created.push({
          trackingCode: shipment.trackingCode,
          shop: shop.name,
          shopEmail: shop.email,
          originHubCode: pickup.hubCode,
          destinationHubCode: recipient.hubCode,
          sameProvince: pickup.provinceCode === recipient.provinceCode,
          recipient: recipient.name,
          codAmount,
          total: option.total,
        });
      } catch (error) {
        console.warn(`  ! ${shop.code} #${index + 1}: ${error.message}`);
      }
    }
    process.stdout.write(`\r  ${created.length} shipments created`);
  }
  process.stdout.write("\n");
  return created;
}

// ---- 2. Staff walk shipments through the field flow -----------------------------------------
const hubsByCode = new Map(manifest.hubs.map((hub) => [hub.code, hub]));
const hubsById = new Map(manifest.hubs.map((hub) => [hub.id, hub]));
const staffOf = (hubCode, prefix) => manifest.accounts.filter((item) => item.hubCode === hubCode && item.email.startsWith(prefix));
const checkedIn = new Set();

async function onDuty(email) {
  const token = await login(email);
  if (!checkedIn.has(email)) {
    const now = new Date();
    const shifts = await api(
      `/api/v1/operations/shifts/mine?from=${new Date(now - 864e5).toISOString()}&to=${new Date(+now + 864e5).toISOString()}`,
      { token },
    );
    const current = shifts.find((shift) => new Date(shift.startsAt) <= now && now < new Date(shift.endsAt));
    if (current?.assignmentStatus === "SCHEDULED") {
      await api(`/api/v1/operations/shifts/${current.shiftId}/check-in`, { token, method: "POST" });
    }
    await api("/api/v1/operations/availability", { token, method: "PUT", body: { status: "AVAILABLE" } });
    checkedIn.add(email);
  }
  return token;
}

/** Waits for the dispatcher to hand this worker a job of `type` for the shipment, then starts it. */
async function takeJob(email, trackingCode, type, { start = true } = {}) {
  const token = await onDuty(email);
  for (let tries = 0; tries < 40; tries += 1) {
    const tasks = await api("/api/v1/workforce/tasks", { token });
    const task = tasks.find((item) => item.type === type && item.shipment.trackingCode === trackingCode);
    if (task) {
      if (task.status === "ASSIGNED") await api(`/api/v1/operations/assignments/${task.assignmentId}/accept`, { token, method: "POST" });
      if (start && task.status !== "IN_PROGRESS") await api(`/api/v1/operations/assignments/${task.assignmentId}/start`, { token, method: "POST" });
      return { token, task };
    }
    await sleep(1500);
  }
  throw new Error(`${email} never received ${type} for ${trackingCode}`);
}

async function scan(token, trackingCode, action, hubId) {
  const result = await api("/api/v1/workforce/scans", { token, method: "POST", body: { code: trackingCode, action, hubId } });
  const failed = result.results.filter((item) => !item.ok);
  if (failed.length) throw new Error(`${action} ${trackingCode}: ${failed.map((item) => item.message).join("; ")}`);
  return result;
}

const ONE_PIXEL_PNG = Buffer.from(
  "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=",
  "base64",
);

async function deliver(token, task, delivered) {
  let evidence = null;
  if (delivered) {
    const form = new FormData();
    form.append("file", new Blob([ONE_PIXEL_PNG], { type: "image/png" }), "chu-ky.png");
    evidence = (await api("/api/v1/workforce/evidence", { token, method: "POST", form })).reference;
  }
  const held = await api("/api/v1/deliveries/mine", { token });
  const shipment = held.find((item) => item.trackingCode === task.shipment.trackingCode);
  let collect = delivered && shipment.collectionStatus === "PENDING" && shipment.amountToCollect > 0;
  for (const parcel of shipment.parcels) {
    await api("/api/v1/deliveries/attempts", {
      token,
      method: "POST",
      body: {
        assignmentId: parcel.assignmentId,
        parcelCode: parcel.parcelCode,
        idempotencyKey: randomUUID(),
        outcome: delivered ? "DELIVERED" : "FAILED",
        failureReason: delivered ? null : pick(["RECIPIENT_UNAVAILABLE", "UNABLE_TO_CONTACT", "RECIPIENT_REFUSED"]),
        proofMethod: delivered ? "SIGNATURE" : null,
        recipientName: delivered ? shipment.recipient.contactName : null,
        evidenceReference: evidence,
        collectedAmount: collect ? shipment.amountToCollect : null,
      },
    });
    collect = false;
  }
}

/** Moves one shipment as far as `stop` ("picked", "received", "transit", "delivered", "failed"). */
async function walk(shipment, stop) {
  const origin = hubsByCode.get(shipment.originHubCode);
  const destination = hubsByCode.get(shipment.destinationHubCode);
  const code = shipment.trackingCode;

  const courier = staffOf(origin.code, "buuta")[0].email;
  const pickup = await takeJob(courier, code, "PICKUP");
  await scan(pickup.token, code, "PICK_UP");
  if (stop === "picked") return "Đã lấy hàng";

  const clerk = staffOf(origin.code, "nhanvien")[0].email;
  await takeJob(clerk, code, "ORIGIN_HUB_HANDLING", { start: false });
  await scan(await onDuty(clerk), code, "RECEIVE", origin.id);
  if (stop === "received") return "Đã nhập bưu cục gửi";

  // Each leg: the driver at the departure hub loads it, staff at the next hub unload it.
  let hub = origin;
  for (let leg = 0; hub.id !== destination.id && leg < 6; leg += 1) {
    const driverEmail = (staffOf(hub.code, "taixe")[0] ?? staffOf(hub.code, "taixelienvung")[0]).email;
    const lineHaul = await takeJob(driverEmail, code, "LINE_HAUL");
    await scan(lineHaul.token, code, "DEPART");
    const next = await nextHub(lineHaul.token, code, hub);
    const receiver = (staffOf(next.code, "nhanvien")[0] ?? staffOf(next.code, "kho")[0] ?? staffOf(next.code, "khaithac")[0]).email;
    const receiverToken = await onDuty(receiver);
    await scan(receiverToken, code, "RECEIVE", next.id);
    if (next.id !== destination.id) {
      await takeJob(receiver, code, "TRANSIT_HUB_HANDLING", { start: false });
      await scan(receiverToken, code, "CHECK", next.id);
      if (stop === "transit") return `Đang ở ${next.name}`;
    }
    hub = next;
  }

  const clerkAtDestination = (staffOf(destination.code, "nhanvien")[0]).email;
  await takeJob(clerkAtDestination, code, "DESTINATION_HUB_HANDLING", { start: false });
  await scan(await onDuty(clerkAtDestination), code, "CHECK", destination.id);

  const deliverer = staffOf(destination.code, "buuta")[0].email;
  const delivery = await takeJob(deliverer, code, "LAST_MILE_DELIVERY");
  await scan(delivery.token, code, "OUT_FOR_DELIVERY");
  await deliver(delivery.token, delivery.task, stop !== "failed");
  return stop === "failed" ? "Giao thất bại" : "Đã giao";
}

/** Where the truck just carrying the parcel is heading: the lane leaving `hub` on the shipment's path. */
async function nextHub(token, code, hub) {
  const lookup = await api(`/api/v1/workforce/lookup/${code}`, { token });
  const destination = [...hubsByCode.values()].find((item) => item.name === lookup.shipment.destinationHubName);
  // The shuttle from a post office goes to its warehouse; a warehouse goes to its gateway or down to the post office.
  if (hub.type === "LOCAL") return hubsById.get(hub.parentId);
  if (hub.type === "PROVINCIAL") {
    return destination.parentId === hub.id ? destination : hubsById.get(hub.parentId);
  }
  const destinationWarehouse = hubsById.get(destination.parentId);
  return destinationWarehouse.parentId === hub.id ? destinationWarehouse : hubsById.get(destinationWarehouse.parentId);
}

async function progress(shipments) {
  const local = shipments.filter((item) => item.originHubCode === item.destinationHubCode);
  const province = shipments.filter((item) => item.sameProvince && item.originHubCode !== item.destinationHubCode);
  const far = shipments.filter((item) => !item.sameProvince);
  const plan = [
    ...local.slice(0, 4).map((item) => [item, "delivered"]),
    ...province.slice(0, Math.ceil(progressCount * 0.4)).map((item, index) => [item, index % 5 === 4 ? "failed" : "delivered"]),
    ...far.slice(0, Math.ceil(progressCount * 0.2)).map((item) => [item, "transit"]),
    ...shipments.filter((item) => !item.sameProvince).slice(-4).map((item) => [item, "received"]),
    ...shipments.slice(-6, -3).map((item) => [item, "picked"]),
  ];
  const outcomes = [];
  for (const [shipment, stop] of plan) {
    try {
      shipment.progress = await walk(shipment, stop);
    } catch (error) {
      shipment.progress = `Dừng: ${error.message}`;
      console.warn(`  ! ${shipment.trackingCode}: ${error.message}`);
    }
    outcomes.push(shipment);
    process.stdout.write(`\r  ${outcomes.length}/${plan.length} walked`);
  }
  process.stdout.write("\n");

  // A few shops cancel orders that were not picked up yet.
  const untouched = shipments.filter((item) => !item.progress);
  for (const shipment of untouched.slice(0, 5)) {
    try {
      await api(`/api/v1/shipments/${shipment.trackingCode}/cancel`, {
        token: await login(shipment.shopEmail),
        method: "POST",
        body: { reason: "Khách đổi ý, shop hủy đơn" },
      });
      shipment.progress = "Shop đã hủy";
    } catch (error) {
      console.warn(`  ! cancel ${shipment.trackingCode}: ${error.message}`);
    }
  }
}

console.log(`==> Creating ${perShop} shipments for each of ${manifest.shops.length} shops via ${gateway}`);
const shipments = await createShipments();
console.log(`==> Walking ${progressCount}+ shipments through pickup, hubs and delivery`);
await progress(shipments);
writeFileSync(join(outDir, "shipments.json"), JSON.stringify(shipments, null, 2));
const summary = shipments.reduce((counts, item) => {
  const key = item.progress?.startsWith("Dừng") ? "Dừng giữa chừng" : item.progress ?? "Chờ lấy hàng";
  counts[key] = (counts[key] ?? 0) + 1;
  return counts;
}, {});
console.log(summary);
