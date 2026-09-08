#!/usr/bin/env node
import { mkdir, open, readFile, rename, writeFile, unlink } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import { scenario, staff, roleLabel } from "./fixtures/luong-giao-hang-viet.mjs";

const base = new URL(process.env.PAVEX_API_URL ?? "http://localhost:8080");
const mailpit = new URL(process.env.PAVEX_MAILPIT_URL ?? "http://localhost:8025");
const password = process.env.PAVEX_DEMO_PASSWORD ?? "1205@Phuc";
const directory = fileURLToPath(new URL("../.local/seed-luong-viet/", import.meta.url));
const statePath = `${directory}/state.json`;
const reportPath = `${directory}/ket-qua.md`;
const lockPath = `${directory}/running.lock`;
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
let state = { version: 1, base: base.origin, accounts: {}, shifts: {} };
let admin;
const tokens = new Map();
const hubs = new Map();
const members = new Map();

function ensureLocal(url) {
  if (url.protocol !== "http:" || !["localhost", "127.0.0.1", "[::1]"].includes(url.hostname)
    || url.username || url.password || url.pathname !== "/" || url.search || url.hash) {
    throw new Error("Seed chỉ cho phép địa chỉ HTTP localhost, không chấp nhận URL máy chủ bên ngoài.");
  }
}

async function save() {
  // No passwords, access tokens or email tokens in the journal.
  await writeFile(`${statePath}.tmp`, JSON.stringify(state, null, 2), { mode: 0o600 });
  await rename(`${statePath}.tmp`, statePath);
}

async function api(path, { method = "GET", body, token = admin, headers = {}, allow = [] } = {}) {
  const response = await fetch(new URL(`/api/v1${path}`, base), {
    method, redirect: "error", signal: AbortSignal.timeout(30000),
    headers: { "Content-Type": "application/json", ...(token ? { Authorization: `Bearer ${token}` } : {}), ...headers },
    ...(body === undefined ? {} : { body: JSON.stringify(body) }),
  });
  const value = await response.json().catch(() => null);
  if (!response.ok && !allow.includes(response.status)) {
    // Report only stable error codes: server errors must not accidentally print credentials.
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

async function login(email, loginPassword = password) {
  const { value } = await api("/auth/login", { method: "POST", token: null, body: { email, password: loginPassword } });
  if (!value?.accessToken) throw new Error("Đăng nhập không trả về access token.");
  return value.accessToken;
}

async function mail(path) {
  const response = await fetch(new URL(path, mailpit), { redirect: "error", signal: AbortSignal.timeout(10000) });
  if (!response.ok) throw new Error(`Không đọc được Mailpit: HTTP ${response.status}.`);
  return response.json();
}

async function setInitialPassword(p, portal) {
  // Only accounts explicitly created/recorded by this run are eligible for automatic recovery.
  if (!state.accounts[p.email]) throw new Error(`Không được đặt lại mật khẩu tài khoản ngoài bộ mẫu: ${p.email}`);
  if (!state.accounts[p.email].activated) {
    const before = new Set((await mail("/api/v1/messages?limit=100")).messages.map(item => item.ID));
    await api("/auth/forgot-password", { method: "POST", token: null, body: { email: p.email, portal } });
    let rawToken;
    const deadline = Date.now() + 60000;
    while (!rawToken && Date.now() < deadline) {
      const messages = (await mail("/api/v1/messages?limit=100")).messages;
      for (const item of messages.filter(m => !before.has(m.ID) && m.To?.some(to => to.Address.toLowerCase() === p.email))) {
        const message = await mail(`/api/v1/message/${encodeURIComponent(item.ID)}`);
        const content = `${message.HTML ?? ""}\n${message.Text ?? ""}`.replaceAll("&amp;", "&");
        for (const match of content.matchAll(/https?:\/\/[^\s<>"']+/g)) {
          const link = new URL(match[0]);
          if (link.pathname === "/reset-password" && link.searchParams.has("token")) rawToken = link.searchParams.get("token");
        }
      }
      if (!rawToken) await sleep(1000);
    }
    if (!rawToken) throw new Error(`Chưa nhận được thư đặt mật khẩu cho ${p.email}. Kiểm tra Auth, RabbitMQ và Mailpit rồi chạy lại.`);
    await api("/auth/reset-password", { method: "POST", token: null, body: { token: rawToken, newPassword: password } });
    // Mark before login: never repeatedly overwrite a password if Identity is temporarily unavailable.
    state.accounts[p.email].activated = true;
    await save();
  }
  try {
    tokens.set(p.email, await login(p.email));
  } catch {
    throw new Error(`Không đăng nhập được ${p.email}. Hãy kiểm tra đã restart Auth bản mới; nếu đã tự đổi mật khẩu, dùng PAVEX_DEMO_PASSWORD phù hợp. Seed không ghi đè mật khẩu đã kích hoạt.`);
  }
}

async function preparePeople() {
  for (const code of scenario.hubs) {
    const matches = (await list(`/admin/network/hubs?search=${code}`)).filter(h => h.code === code);
    if (matches.length !== 1 || matches[0].status !== "ACTIVE") throw new Error(`Thiếu bưu cục hoạt động ${code}; kiểm tra seed Network.`);
    hubs.set(code, matches[0]);
  }
  for (const p of staff) {
    let member = (await list(`/admin/operations/workforce?search=${encodeURIComponent(p.email)}`))
      .find(m => m.identity?.email === p.email);
    if (member && !state.accounts[p.email]) {
      throw new Error(`Email mẫu đã tồn tại nhưng không thuộc nhật ký seed: ${p.email}. Không tự nhận quyền sở hữu hay đổi mật khẩu.`);
    }
    if (!member && state.accounts[p.email]) throw new Error("Dữ liệu database và nhật ký seed không khớp; xem hướng dẫn khôi phục.");
    if (!member) {
      const { hubCode, ...profile } = p;
      member = (await api("/admin/operations/workforce/onboard", { method: "POST", body: {
        ...profile, hubId: hubCode ? hubs.get(hubCode).id : null, maxConcurrentAssignments: 10,
      } })).value;
      state.accounts[p.email] = { userId: member.userId, memberId: member.id, activated: false };
      await save();
    }
    if (member.kind !== p.kind || member.status !== "ACTIVE" || (p.hubCode && !member.hubMemberships.some(m => m.hubId === hubs.get(p.hubCode).id && m.isActive))) {
      throw new Error(`Nhân sự ${p.displayName} đã đổi trạng thái hoặc bưu cục. Seed không tự sửa lại.`);
    }
    await setInitialPassword(p, "MANAGEMENT");
    members.set(p.email, member);
    console.log(`Sẵn sàng: ${p.displayName} — ${roleLabel(p)}`);
  }
  const p = scenario.merchant;
  if (!state.accounts[p.email]) {
    const { value } = await api("/auth/register", { method: "POST", token: null, body: { email: p.email, password } });
    state.accounts[p.email] = { userId: value.id, activated: false };
    await save();
  }
  await setInitialPassword(p, "CLIENT");
  let token = tokens.get(p.email);
  await api("/auth/me/profile", { method: "PATCH", token, body: {
    firstName: p.firstName, lastName: p.lastName, displayName: p.displayName,
    gender: p.gender, dateOfBirth: p.dateOfBirth, avatarUrl: null,
  } });
  let profile = await api("/merchants/me/profile", { token, allow: [404] });
  if (profile.status === 404) profile = await api("/merchants/me/profile", { method: "POST", token, body: {
    businessName: p.businessName, contactName: p.displayName, contactPhone: p.contactPhone, contactEmail: p.email,
  } });
  if (profile.value.status === "PENDING_VERIFICATION") {
    await api(`/admin/merchants/${profile.value.id}/status`, { method: "PATCH", body: {
      status: "ACTIVE", reasonDetail: "Đã kiểm tra hồ sơ cửa hàng mẫu để kiểm thử luồng giao hàng nội bộ.",
    } });
  } else if (profile.value.status !== "ACTIVE") throw new Error("Đối tác mẫu đã bị từ chối/đình chỉ; seed không tự mở khóa.");
  // Approval uses an outbox; wait for Identity, not repeated logins that hit the rate limit.
  const deadline = Date.now() + 60000;
  let merchantReady = false;
  while (Date.now() < deadline) {
    const me = (await api("/auth/me", { token })).value;
    if (me.roles?.includes("MERCHANT")) { merchantReady = true; break; }
    await sleep(1000);
  }
  if (!merchantReady) throw new Error("Chưa đồng bộ quyền đối tác sau duyệt. Kiểm tra Partners role outbox và Identity, rồi chạy lại.");
  // Obtain a new JWT rather than reuse CUSTOMER claims.
  token = await login(p.email);
  tokens.set(p.email, token);
  const { value: pickups } = await api("/merchants/me/pickup-addresses", { token });
  if (!pickups.some(address => address.isDefault)) {
    await api("/merchants/me/pickup-addresses", { method: "POST", token, body: {
      ...scenario.sender, name: "Điểm lấy hàng Cửa hàng May Mộc", isDefault: true,
    } });
  }
}

async function prepareShifts() {
  for (const [code, hub] of hubs) {
    const workers = staff.filter(p => p.hubCode === code && p.kind !== "DISPATCHER");
    let shift;
    if (state.shifts[code]) shift = (await api(`/admin/operations/shifts/${state.shifts[code]}`)).value.shift;
    if (shift && (shift.status !== "SCHEDULED" || new Date(shift.endsAt).getTime() <= Date.now())) {
      throw new Error(`Ca mẫu tại ${hub.name} đã kết thúc/hủy. Tạo ca và chấm công trên giao diện; seed không mở lại ca cũ.`);
    }
    if (!shift) {
      shift = (await api("/admin/operations/shifts", { method: "POST", body: {
        hubId: hub.id, name: `Ca kiểm thử giao hàng — ${hub.name}`,
        startsAt: new Date(Date.now() + 300000).toISOString(), endsAt: new Date(Date.now() + 8 * 3600000).toISOString(),
      } })).value;
      state.shifts[code] = shift.id;
      await save();
    }
    const roster = (await api(`/admin/operations/shifts/${shift.id}`)).value.roster;
    for (const p of workers) {
      if (!roster.some(r => r.workforceMemberId === members.get(p.email).id && ["SCHEDULED", "CHECKED_IN"].includes(r.status))) {
        await api(`/admin/operations/shifts/${shift.id}/members`, { method: "POST", body: { workforceMemberId: members.get(p.email).id } });
      }
    }
    if (new Date(shift.startsAt).getTime() > Date.now()) {
      shift = (await api(`/admin/operations/shifts/${shift.id}`, { method: "PUT", body: {
        name: shift.name, startsAt: new Date(Date.now() + 3000).toISOString(), endsAt: shift.endsAt,
      } })).value;
      await sleep(Math.max(0, new Date(shift.startsAt).getTime() - Date.now()) + 300);
    }
    for (const p of workers) {
      await api(`/operations/shifts/${shift.id}/check-in`, { method: "POST", token: tokens.get(p.email) });
    }
    console.log(`Đã vào ca, sẵn sàng nhận việc: ${hub.name}`);
  }
}

async function prepareShipment() {
  const token = tokens.get(scenario.merchant.email);
  let shipment;
  let cursor;
  do {
    const { value } = await api(`/shipments?limit=100${cursor ? `&cursor=${encodeURIComponent(cursor)}` : ""}`, { token });
    shipment = value.items.find(s => s.merchantReference === scenario.code);
    cursor = value.nextCursor;
  } while (!shipment && cursor);
  if (!shipment) {
    const templates = await list("/admin/network/route-templates?search=HN-HCM");
    const standard = templates.find(t => t.code === "HN-HCM-STANDARD");
    if (!standard || standard.legs.length !== 3 || standard.legs.some(l => l.transportMode !== "ROAD")) {
      throw new Error("Thiếu tuyến mẫu đường bộ 3 chặng Hà Nội → TP.HCM của Network.");
    }
    let route = templates.find(t => t.code === scenario.routeCode);
    if (!route) route = (await api("/admin/network/route-templates", { method: "POST", body: {
      code: scenario.routeCode, name: "Tuyến mẫu Hà Nội → TP.HCM — đường bộ",
      description: "Tuyến kiểm thử lấy hàng, gom hàng, trung chuyển hai tổng kho và giao chặng cuối.",
      originHubId: hubs.get(scenario.hubs[0]).id, destinationHubId: hubs.get(scenario.hubs[3]).id,
      priority: 1, laneIds: standard.legs.map(l => l.laneId),
    } })).value;
    if (route.status !== "ACTIVE" || route.priority !== 1 || route.legs.some((l, i) => l.laneId !== standard.legs[i]?.laneId)) {
      throw new Error("Tuyến mẫu đã được chỉnh sửa; kiểm tra trên giao diện Network trước khi chạy lại.");
    }
    const { value: journey } = await api(`/admin/network/route-templates/journey?originHubId=${route.originHubId}&destinationHubId=${route.destinationHubId}&shipmentWeightKg=1`);
    if (journey.routeTemplateCode !== scenario.routeCode) throw new Error("Tuyến mẫu không được chọn hoặc không còn sức chứa. Seed không tạo đơn trên tuyến khác.");
    const { value: quote } = await api("/pricing/quotes", { method: "POST", token, body: {
      originProvinceCode: "01", originWardCode: "00070", destinationProvinceCode: "79", destinationWardCode: "26740",
      parcels: [{ weightKg: 1, lengthCm: 20, widthCm: 15, heightCm: 10 }], codAmount: 0, declaredValue: 250000,
    } });
    const option = quote.options.find(o => o.serviceLevel === "STANDARD");
    if (!option) throw new Error("Pricing chưa có báo giá tiêu chuẩn cho tuyến mẫu.");
    shipment = (await api("/shipments", { method: "POST", token,
      headers: { "Idempotency-Key": scenario.code }, body: {
        quoteId: option.quoteId, merchantReference: scenario.code, sender: scenario.sender, recipient: scenario.recipient,
        shippingFeePayer: "SENDER", parcels: [{ description: "Áo sơ mi vải lanh màu trắng", quantity: 2, category: "APPAREL", isFragile: false }],
        note: "Đơn hàng kiểm thử. Người gửi trả phí; không thu hộ. Gọi người nhận trước khi giao.",
      } })).value;
  }
  const { value: detail } = await api(`/admin/shipments/${shipment.trackingCode}`);
  const { value: assignments } = await api(`/admin/operations/assignments?shipmentId=${detail.id}`);
  if (assignments.some(work => work.assigneeWorkforceMemberId)) {
    console.log("Lưu ý: đơn đã có người phụ trách (do tự phân công hoặc thao tác trước đó). Script giữ nguyên. Muốn test tay từ đầu, tắt OPERATIONS_AUTO_ASSIGN_ENABLED trước khi tạo đơn.");
  }
  state.trackingCode = shipment.trackingCode;
  state.shipmentId = detail.id;
  await save();
  const lines = ["# Bộ mẫu kiểm thử giao hàng tiếng Việt", "", `Mã đơn: **${shipment.trackingCode}**`,
    `Mã tham chiếu: ${scenario.code}`, "", "Không có mật khẩu/token trong báo cáo. Mật khẩu mẫu do PAVEX_DEMO_PASSWORD cấu hình (mặc định xem tài liệu).",
    "", "| Họ tên | Email | Vai trò | Bưu cục |", "|---|---|---|---|",
    `| ${scenario.merchant.displayName} | ${scenario.merchant.email} | Chủ cửa hàng May Mộc | — |`,
    ...staff.map(p => `| ${p.displayName} | ${p.email} | ${roleLabel(p)} | ${p.hubCode ? hubs.get(p.hubCode).name : "Toàn mạng"} |`),
    "", `Có ${assignments.length} công việc trong kế hoạch vận hành. Không tự phân công hoặc đẩy trạng thái.`,
    "", "Ca làm việc có hiệu lực 8 giờ kể từ lúc tạo; sau đó cần xếp ca mới.",
    "Hướng dẫn từng bước: docs/kiem-thu-luong-tieng-viet.md.", ""];
  await writeFile(reportPath, lines.join("\n"), { mode: 0o600 });
  console.log(`Đã chuẩn bị đơn ${shipment.trackingCode}; giữ nguyên tiến độ nếu chạy lại. Báo cáo: ${reportPath}`);
}

async function main() {
  ensureLocal(base); ensureLocal(mailpit);
  if (!process.argv.includes("--apply")) {
    console.log("XEM TRƯỚC — không kết nối hay sửa dữ liệu. Dùng --apply sau khi backend và Mailpit đã chạy.");
    console.log(`${scenario.code}: ${scenario.hubs.join(" → ")}. Một đơn hàng, một đối tác, ${staff.length} nhân viên.`);
    for (const p of staff) console.log(`${p.displayName} | ${p.email} | ${roleLabel(p)} | ${p.hubCode ?? "Toàn mạng"}`);
    return;
  }
  if (password.length < 8 || password.length > 128) throw new Error("Mật khẩu mẫu cần 8–128 ký tự.");
  await mkdir(directory, { recursive: true, mode: 0o700 });
  const lock = await open(lockPath, "wx", 0o600).catch(() => { throw new Error(`Seed đang chạy hoặc còn khóa từ lần bị ngắt: ${lockPath}. Không chạy song song.`); });
  try {
    try { state = JSON.parse(await readFile(statePath, "utf8")); } catch (error) { if (error.code !== "ENOENT") throw error; }
    if (state.version !== 1 || state.base !== base.origin) throw new Error("Nhật ký không thuộc môi trường này.");
    admin = await login(process.env.PAVEX_ADMIN_EMAIL ?? "admin@pavex.com", process.env.PAVEX_ADMIN_PASSWORD ?? "1205@Phuc");
    await mail("/api/v1/messages?limit=1");
    await preparePeople();
    await prepareShifts();
    await prepareShipment();
  } finally {
    await lock.close();
    await unlink(lockPath);
  }
}

main().catch(error => { console.error(`Dừng seed: ${error.message}`); process.exitCode = 1; });
