// Render every .svg in a folder to a .png next to it (2x for print quality).
// usage: NODE_PATH=/opt/node22/lib/node_modules node render_svg.cjs <dir>
const { chromium } = require("playwright");
const fs = require("fs"), path = require("path");
(async () => {
  const dir = path.resolve(process.argv[2]);
  const browser = await chromium.launch();
  const page = await browser.newPage({ deviceScaleFactor: 2 });
  for (const f of fs.readdirSync(dir).filter((f) => f.endsWith(".svg"))) {
    const svg = fs.readFileSync(path.join(dir, f), "utf8");
    const [, w, h] = svg.match(/width="([\d.]+)" height="([\d.]+)"/);
    await page.setViewportSize({ width: Math.ceil(+w), height: Math.ceil(+h) });
    await page.setContent(`<html><body style="margin:0">${svg}</body></html>`);
    await page.screenshot({ path: path.join(dir, f.replace(".svg", ".png")) });
  }
  await browser.close();
})();
