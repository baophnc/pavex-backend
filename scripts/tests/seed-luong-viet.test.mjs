import test from "node:test";
import assert from "node:assert/strict";
import { mkdtemp, mkdir, copyFile, writeFile, readFile, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { fileURLToPath } from "node:url";
import { promisify } from "node:util";
import { execFile } from "node:child_process";
import { scenario, staff } from "../fixtures/luong-giao-hang-viet.mjs";
const exec = promisify(execFile);
const script = fileURLToPath(new URL("../seed-luong-giao-hang-viet.mjs", import.meta.url));

test("fixture covers every route hub with realistic Vietnamese names and unique local emails", () => {
  assert.equal(staff.length, 14);
  assert.equal(new Set(staff.map(p => p.email)).size, 14);
  for (const p of staff) {
    assert.match(p.email, /@demo\.pavex\.local$/);
    assert.match(p.displayName, /[^\x00-\x7F]/);
  }
  for (const hub of scenario.hubs) assert.ok(staff.some(p => p.hubCode === hub && p.kind === "WAREHOUSE_OPERATOR"));
  for (const hub of scenario.hubs.slice(0, -1)) assert.ok(staff.some(p => p.hubCode === hub && p.kind === "LINE_HAUL_DRIVER"));
});

test("preview is read-only and external API URLs are rejected before requests", async () => {
  const { stdout } = await exec(process.execPath, [script], { env: { ...process.env, PAVEX_API_URL: "http://localhost:1" } });
  assert.match(stdout, /XEM TRƯỚC/);
  await assert.rejects(exec(process.execPath, [script, "--apply"], {
    env: { ...process.env, PAVEX_API_URL: "https://pavex.example" },
  }), error => { assert.match(error.stderr, /chỉ cho phép/); return true; });
});

test("API contract simulation prepares one shipment and reruns without resetting accounts or duplicating data", async () => {
  const temp = await mkdtemp(join(tmpdir(), "pavex-seed-test-"));
  try {
    await mkdir(join(temp, "scripts", "fixtures"), { recursive: true });
    const target = join(temp, "scripts", "seed-luong-giao-hang-viet.mjs");
    await copyFile(script, target);
    await copyFile(new URL("../fixtures/luong-giao-hang-viet.mjs", import.meta.url), join(temp, "scripts", "fixtures", "luong-giao-hang-viet.mjs"));
    const dbPath = join(temp, "test-db.json");
    await writeFile(dbPath, JSON.stringify({ accounts: {}, members: [], messages: [], shifts: {}, shipments: [], pickups: [], calls: [] }));
    const args = ["--import", fileURLToPath(new URL("./seed-api-mock.mjs", import.meta.url)), target, "--apply"];
    const options = { env: { ...process.env, PAVEX_API_URL: "http://localhost:8080", PAVEX_MAILPIT_URL: "http://localhost:8025", PAVEX_SEED_TEST_DB: dbPath }, timeout: 45000 };
    await exec(process.execPath, args, options);
    let db = JSON.parse(await readFile(dbPath, "utf8"));
    assert.equal(db.members.length, 14);
    assert.equal(Object.keys(db.accounts).length, 15);
    assert.equal(Object.keys(db.shifts).length, 4);
    assert.equal(db.shipments.length, 1);
    assert.equal(db.calls.filter(c => c === "POST /auth/reset-password").length, 15);
    const firstRunCalls = db.calls.length;
    await exec(process.execPath, args, options);
    db = JSON.parse(await readFile(dbPath, "utf8"));
    assert.equal(db.shipments.length, 1);
    assert.equal(db.members.length, 14);
    assert.equal(db.calls.filter(c => c === "POST /auth/reset-password").length, 15);
    assert.ok(!db.calls.slice(firstRunCalls).some(c => /onboard|reset-password|register/.test(c)));
    assert.equal(db.shipments[0].status, "AWAITING_PICKUP");
    const journal = await readFile(join(temp, ".local", "seed-luong-viet", "state.json"), "utf8");
    assert.ok(!journal.includes("1205@Phuc"));
    assert.ok(!journal.includes("accessToken"));
  } finally {
    // Exact newly allocated temporary test directory only; never project/user data.
    await rm(temp, { recursive: true, force: true });
  }
});
