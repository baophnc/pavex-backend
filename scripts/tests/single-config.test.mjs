import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync, readdirSync, existsSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { join } from "node:path";

const root = fileURLToPath(new URL("../../", import.meta.url));
const modules = ["auth", "identity", "partners", "network", "pricing", "shipment", "operations", "billing", "fleet", "platform/api-gateway", "platform/discovery-server"];

test("each service has exactly one runtime application YAML without environment overlays", () => {
  for (const module of modules) {
    const dir = join(root, module, "src/main/resources");
    assert.deepEqual(readdirSync(dir).filter(name => /^application(?:[-.]|$)/.test(name)), ["application.yaml"], module);
    const yaml = readFileSync(join(dir, "application.yaml"), "utf8");
    assert.doesNotMatch(yaml, /on-profile:|profiles:\s*\n\s*(active|default|include):|bootstrap-admin\.properties|\.local\/dev\.env/);
  }
});

test("shared IntelliJ configurations use real Kotlin main classes and no profile or environment overrides", () => {
  const files = readdirSync(join(root, ".run")).filter(name => name.startsWith("PAVEX-") && name.endsWith(".run.xml"));
  assert.equal(files.length, 9);
  for (const file of files) {
    const xml = readFileSync(join(root, ".run", file), "utf8");
    assert.doesNotMatch(xml, /ACTIVE_PROFILES|SPRING_PROFILES|SPRING_CONFIG|<env\b|VM_PARAMETERS|PROGRAM_PARAMETERS/);
    const main = xml.match(/name="MAIN_CLASS_NAME" value="([^"]+)Kt"/)[1];
    const relative = `${main.replaceAll(".", "/")}.kt`;
    assert.ok(modules.some(module => existsSync(join(root, module, "src/main/kotlin", relative))), main);
    assert.match(xml, /WORKING_DIRECTORY" value="\$PROJECT_DIR\$"/);
  }
});

test("Eureka clients advertise explicit loopback IP, never a machine-specific local hostname", () => {
  for (const module of modules.filter(module => module !== "platform/discovery-server")) {
    const yaml = readFileSync(join(root, module, "src/main/resources/application.yaml"), "utf8");
    const eureka = yaml.split("\neureka:\n")[1];
    assert.ok(eureka, module);
    assert.match(eureka, /prefer-ip-address: true/);
    assert.match(eureka, /ip-address: 127\.0\.0\.1/);
    assert.match(eureka, /hostname: 127\.0\.0\.1/);
    assert.doesNotMatch(eureka, /EUREKA_PREFER_IP_ADDRESS|hostname:.*\.local/);
  }
});

test("setup no longer requires a local-specific script or imported environment file", () => {
  assert.ok(existsSync(join(root, "scripts/security-bootstrap.sh")));
  assert.ok(!existsSync(join(root, "scripts/security-bootstrap-local.sh")));
  const up = readFileSync(join(root, "scripts/dev-up.sh"), "utf8");
  assert.match(up, /bash "\$script_dir\/security-bootstrap\.sh"/);
  assert.doesNotMatch(up, /source |--spring.profiles/);
});
