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

test("Eureka clients advertise loopback only through the local .env, never a machine-specific local hostname", () => {
  for (const module of modules.filter(module => module !== "platform/discovery-server")) {
    const yaml = readFileSync(join(root, module, "src/main/resources/application.yaml"), "utf8");
    const eureka = yaml.split("\neureka:\n")[1];
    assert.ok(eureka, module);
    assert.match(eureka, /prefer-ip-address: true/);
    assert.match(eureka, /ip-address: \$\{EUREKA_INSTANCE_IP_ADDRESS:\$\{spring\.cloud\.client\.ip-address\}\}/, module);
    assert.match(eureka, /hostname: \$\{EUREKA_INSTANCE_HOSTNAME:\$\{spring\.cloud\.client\.hostname\}\}/, module);
    assert.doesNotMatch(eureka, /^\s*(ip-address|hostname): 127\.0\.0\.1|EUREKA_PREFER_IP_ADDRESS|hostname:.*\.local/m, module);
  }
  const localEnv = readFileSync(join(root, "scripts/local-env.sh"), "utf8");
  assert.match(localEnv, /add EUREKA_INSTANCE_IP_ADDRESS 127\.0\.0\.1/);
  assert.match(localEnv, /add EUREKA_INSTANCE_HOSTNAME 127\.0\.0\.1/);
});

test("setup no longer requires a local-specific script or imported environment file", () => {
  assert.ok(existsSync(join(root, "scripts/security-bootstrap.sh")));
  assert.ok(!existsSync(join(root, "scripts/security-bootstrap-local.sh")));
  const up = readFileSync(join(root, "scripts/dev-up.sh"), "utf8");
  assert.match(up, /bash "\$script_dir\/security-bootstrap\.sh"/);
  assert.doesNotMatch(up, /source |--spring.profiles/);
});

const yamlOf = module => readFileSync(join(root, module, "src/main/resources/application.yaml"), "utf8");

test("no secret has a default and local-only features stay off unless enabled", () => {
  const secretName = /((?<!_RESET_)PASSWORD|SECRET|SECRET_KEY|SIGNING_KEY|ENCRYPTION_KEY|API_KEY|CHECKSUM_KEY)$/;
  for (const module of modules) {
    const yaml = yamlOf(module);
    for (const [, name, fallback] of yaml.matchAll(/\$\{([A-Z][A-Z0-9_]*):([^}]*)\}/g)) {
      if (secretName.test(name)) assert.equal(fallback, "", `${module}: ${name} must not have a default`);
    }
    assert.doesNotMatch(yaml, /^\s*[a-z-]*(password|secret|-key): ['"]?(?!\$\{|file:|classpath:)\S{6,}/im, `${module}: literal secret`);
    assert.doesNotMatch(yaml, /SPRINGDOC_[A-Z_]+_ENABLED:true|PRICING_DEMO_SEED_ENABLED:true|PAVEX_DEMO_RESET_ENABLED:true/, module);
  }
  for (const module of ["auth", "identity"]) {
    const bootstrap = yamlOf(module).split("bootstrap-admin:\n")[1].split(/\n  [a-z]/)[0];
    assert.match(bootstrap, /enabled: \$\{PAVEX_BOOTSTRAPADMIN_ENABLED:false\}/, module);
  }
  const auth = yamlOf("auth").split("bootstrap-admin:\n")[1].split(/\n  [a-z]/)[0];
  assert.match(auth, /password: \$\{PAVEX_BOOTSTRAPADMIN_PASSWORD:\}/);
  assert.match(auth, /reset-password: \$\{PAVEX_BOOTSTRAPADMIN_RESET_PASSWORD:false\}/);
});

test("dev-up gives every required setting a local value in .env", () => {
  const up = readFileSync(join(root, "scripts/dev-up.sh"), "utf8");
  assert.match(up, /bash "\$script_dir\/local-env\.sh"/);
  const localEnv = readFileSync(join(root, "scripts/local-env.sh"), "utf8");
  const dbServices = localEnv.match(/for service in ([A-Z ]+); do/)[1].split(" ");
  const provided = new Set([...localEnv.matchAll(/^add ([A-Z0-9_]+) /gm)].map(match => match[1]));
  dbServices.forEach(service => provided.add(`${service}_DB_PASSWORD`));
  for (const module of modules) {
    for (const [, name] of yamlOf(module).matchAll(/\$\{([A-Z][A-Z0-9_]*)\}/g)) {
      assert.ok(provided.has(name), `${module}: scripts/local-env.sh must add ${name}`);
    }
  }
  for (const name of ["PAVEX_BOOTSTRAPADMIN_ENABLED", "PAVEX_BOOTSTRAPADMIN_PASSWORD", "SPRINGDOC_API_DOCS_ENABLED", "SPRINGDOC_SWAGGER_UI_ENABLED"]) {
    assert.ok(provided.has(name), name);
  }
});

test("one Dockerfile builds any service as a non-root JRE 21 image without local settings", () => {
  const dockerfile = readFileSync(join(root, "Dockerfile"), "utf8");
  assert.match(dockerfile, /^ARG MODULE$/m);
  assert.match(dockerfile, /^FROM eclipse-temurin:21-jdk\S* AS build$/m);
  assert.match(dockerfile, /^FROM eclipse-temurin:21-jre\S*$/m);
  assert.match(dockerfile, /^USER pavex$/m);
  const ignored = readFileSync(join(root, ".dockerignore"), "utf8").split("\n");
  for (const entry of [".env", ".env.*", ".local", ".claude", "**/target"]) assert.ok(ignored.includes(entry), entry);
});

test("only the local stack runs full-day shifts with no day off; staging keeps the 6-on/1-off rotation", () => {
  assert.match(yamlOf("operations"), /full-day: \$\{OPERATIONS_SHIFT_FULL_DAY:false\}/);
  const operations = file => readFileSync(join(root, "deploy", file), "utf8").split("\n  operations-service:\n")[1].split(/\n  [a-z]/)[0];
  assert.match(operations("compose.yml"), /OPERATIONS_SHIFT_FULL_DAY: "false"/);
  assert.match(operations("compose.local.yml"), /OPERATIONS_SHIFT_FULL_DAY: \$\{OPERATIONS_SHIFT_FULL_DAY:-true\}/);
});
