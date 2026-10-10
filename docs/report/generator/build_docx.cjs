// Build the PAVEX report (.docx) from content.json, mirroring the layout of the earlier report:
// bordered cover page, IUH header, TOC, chapters, spec tables, figures and one table per test case.
const fs = require("fs");
const path = require("path");
const {
  Document, Packer, Paragraph, TextRun, ImageRun, Table, TableRow, TableCell, WidthType, AlignmentType,
  HeadingLevel, BorderStyle, ShadingType, PageBreak, Header, Footer, PageNumber, TableOfContents,
  LevelFormat, PageOrientation, VerticalAlign, SectionType, TabStopType,
} = require("docx");

const HERE = __dirname;
const FIG = path.join(HERE, "..", "figures");
const blocks = JSON.parse(fs.readFileSync(path.join(HERE, "content.json"), "utf8"));
const OUT = process.argv[2] || path.join(HERE, "..", "PAVEX-bao-cao.docx");

const FONT = "Times New Roman";
const SZ = 26; // 13pt
const A4 = { width: 11906, height: 16838 };
const MARGIN = { top: 1440, right: 1440, bottom: 1440, left: 1800, header: 600, footer: 600 };
const TEXT_W = A4.width - MARGIN.left - MARGIN.right; // 8666
const BLUE = "2E75B6";
const HEAD_FILL = "D9E2F3";
const LABEL_FILL = "EEF3FA";
const border = { style: BorderStyle.SINGLE, size: 4, color: "808080" };
const borders = { top: border, bottom: border, left: border, right: border };

function pngSize(file) {
  const b = fs.readFileSync(file);
  return { w: b.readUInt32BE(16), h: b.readUInt32BE(20), data: b };
}

function runs(text, opts = {}) {
  return [new TextRun({ text, font: FONT, size: opts.size || SZ, bold: opts.bold, italics: opts.italics, color: opts.color })];
}

function para(text, opts = {}) {
  return new Paragraph({
    alignment: opts.align || AlignmentType.JUSTIFIED,
    spacing: { before: opts.before ?? 0, after: opts.after ?? 120, line: opts.line ?? 312 },
    indent: opts.indent ? { firstLine: 567 } : undefined,
    children: runs(text, opts),
    keepNext: opts.keepNext,
    pageBreakBefore: opts.pageBreakBefore,
  });
}

function cell(text, width, opts = {}) {
  const lines = Array.isArray(text) ? text : [text];
  return new TableCell({
    borders,
    width: { size: width, type: WidthType.DXA },
    columnSpan: opts.span,
    verticalAlign: opts.vcenter ? VerticalAlign.CENTER : VerticalAlign.TOP,
    shading: opts.fill ? { fill: opts.fill, type: ShadingType.CLEAR, color: "auto" } : undefined,
    margins: { top: 60, bottom: 60, left: 100, right: 100 },
    children: lines.map((t) => new Paragraph({
      alignment: opts.align || AlignmentType.LEFT,
      spacing: { before: 0, after: 0, line: 276 },
      keepNext: opts.keepNext,
      children: runs(t, { size: opts.size || 24, bold: opts.bold, italics: opts.italics }),
    })),
  });
}

function caption(text) {
  return new Paragraph({
    alignment: AlignmentType.CENTER, spacing: { before: 80, after: 200 },
    children: runs(text, { size: 24, italics: true }),
  });
}

function image(file, wcm, maxhcm) {
  const { w, h, data } = pngSize(file);
  let wpx = wcm * 37.8;
  let hpx = wpx * h / w;
  const maxh = maxhcm * 37.8;
  if (hpx > maxh) { hpx = maxh; wpx = hpx * w / h; }
  return new Paragraph({
    alignment: AlignmentType.CENTER, spacing: { before: 120, after: 0 }, keepNext: true,
    children: [new ImageRun({ type: "png", data, transformation: { width: Math.round(wpx), height: Math.round(hpx) } })],
  });
}

function table(header, rows, widths, size, total) {
  const sum = widths.reduce((a, b) => a + b, 0);
  const scale = (total || TEXT_W) / sum;
  const ws = widths.map((x) => Math.floor(x * scale));
  ws[ws.length - 1] += (total || TEXT_W) - ws.reduce((a, b) => a + b, 0);
  const sz = (size || 11) * 2;
  return new Table({
    width: { size: total || TEXT_W, type: WidthType.DXA },
    columnWidths: ws,
    rows: [
      new TableRow({ tableHeader: true, children: header.map((t, i) => cell(t, ws[i], { bold: true, fill: HEAD_FILL, size: sz, align: AlignmentType.CENTER, vcenter: true })) }),
      ...rows.map((r) => new TableRow({ cantSplit: true, children: r.map((t, i) => cell(t, ws[i], { size: sz })) })),
    ],
  });
}

function specTable(uc) {
  const w1 = 2900, w2 = TEXT_W - 2900;
  const info = [
    ["Tên use case", uc.name], ["Mã use case", uc.code], ["Mô tả sơ lược", uc.desc], ["Actor chính", uc.actor],
    ["Actor phụ", uc.sub], ["Tiền điều kiện", uc.pre], ["Hậu điều kiện", uc.post],
  ];
  const rows = info.map(([k, v]) => new TableRow({ cantSplit: true, children: [cell(k, w1, { bold: true, fill: LABEL_FILL }), cell(v, w2)] }));
  const band = (t) => new TableRow({ cantSplit: true, children: [cell(t, TEXT_W, { span: 2, bold: true, fill: HEAD_FILL, align: AlignmentType.CENTER })] });
  rows.push(band("Luồng sự kiện chính"));
  rows.push(new TableRow({ cantSplit: true, children: [cell("Actor", w1, { bold: true, fill: LABEL_FILL, align: AlignmentType.CENTER }), cell("System", w2, { bold: true, fill: LABEL_FILL, align: AlignmentType.CENTER })] }));
  // each actor step starts a row; the system steps that follow share that row (as in the earlier report)
  let n = 0;
  const pairs = [];
  for (const [who, text] of uc.main) {
    n += 1;
    const t = `${n}. ${text}`;
    if (who === "A" || !pairs.length) pairs.push({ a: who === "A" ? [t] : [""], s: who === "A" ? [] : [t] });
    else pairs[pairs.length - 1].s.push(t);
  }
  for (const pr of pairs) rows.push(new TableRow({ cantSplit: true, children: [cell(pr.a, w1), cell(pr.s.length ? pr.s : [""], w2)] }));
  rows.push(band("Luồng sự kiện thay thế"));
  for (const [c, h] of uc.alt) rows.push(new TableRow({ cantSplit: true, children: [cell(c, w1), cell(h, w2)] }));
  return new Table({ width: { size: TEXT_W, type: WidthType.DXA }, columnWidths: [w1, w2], rows });
}

function placeholderBox(text) {
  return new Table({
    width: { size: TEXT_W, type: WidthType.DXA }, columnWidths: [TEXT_W],
    rows: [new TableRow({
      height: { value: 2600, rule: "atLeast" },
      children: [new TableCell({
        borders: { top: { style: BorderStyle.DASHED, size: 6, color: "999999" }, bottom: { style: BorderStyle.DASHED, size: 6, color: "999999" },
                   left: { style: BorderStyle.DASHED, size: 6, color: "999999" }, right: { style: BorderStyle.DASHED, size: 6, color: "999999" } },
        width: { size: TEXT_W, type: WidthType.DXA }, verticalAlign: VerticalAlign.CENTER,
        children: [new Paragraph({ alignment: AlignmentType.CENTER, children: runs(text, { italics: true, color: "888888", size: 24 }) })],
      })],
    })],
  });
}

// one vertical table per test case (ISO/IEC/IEEE 29119-3 fields)
function testcaseTable(tc) {
  const w1 = 2600, w2 = TEXT_W - 2600;
  const list = (xs, numbered) => xs.map((x, i) => (numbered ? `${i + 1}. ` : xs.length > 1 ? "- " : "") + x);
  const info = [
    ["Mã test case", tc.id], ["Tên test case", tc.name], ["Use case", tc.uc], ["Mục tiêu", tc.goal],
    ["Tiền điều kiện", list(tc.pre)], ["Dữ liệu vào", list(tc.data)], ["Các bước thực hiện", list(tc.steps, true)],
    ["Kết quả mong đợi", list(tc.expected)], ["Kết quả thực tế", tc.actual || ""], ["Trạng thái", tc.status || "☐ Đạt     ☐ Không đạt"],
  ];
  // keepNext on every row but the last keeps the whole test case on one page
  const rows = info.map(([k, v], i) => {
    const keepNext = i < info.length - 1;
    return new TableRow({ cantSplit: true, height: k === "Kết quả thực tế" ? { value: 700, rule: "atLeast" } : undefined, children: [
      cell(k, w1, { bold: true, fill: i === 0 ? HEAD_FILL : LABEL_FILL, keepNext }), cell(v, w2, { bold: i === 0, fill: i === 0 ? HEAD_FILL : undefined, keepNext })] });
  });
  return new Table({ width: { size: TEXT_W, type: WidthType.DXA }, columnWidths: [w1, w2], rows });
}

function coverChildren() {
  const c = (t, o = {}) => new Paragraph({ alignment: AlignmentType.CENTER, spacing: { before: o.before || 0, after: o.after ?? 80 }, children: runs(t, { bold: o.bold ?? true, size: o.size || 26, color: o.color }) });
  const logo = pngSize(path.join(FIG, "logo-cover.png"));
  const left = (label, value) => new Paragraph({ spacing: { after: 60 }, indent: { left: 600 }, children: [...runs(label, { bold: true, size: 26 }), ...runs(value, { bold: true, size: 26 })] });
  return [
    c("BỘ CÔNG THƯƠNG", { before: 300 }),
    c("TRƯỜNG ĐẠI HỌC CÔNG NGHIỆP THÀNH PHỐ HỒ CHÍ MINH"),
    c("KHOA CÔNG NGHỆ THÔNG TIN", { after: 600 }),
    new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 600 }, children: [new ImageRun({ type: "png", data: logo.data, transformation: { width: 300, height: Math.round(300 * logo.h / logo.w) } })] }),
    c("BÁO CÁO CUỐI KỲ", { size: 30, after: 160 }),
    c("MÔN HỌC: CÔNG NGHỆ MỚI TRONG PHÁT TRIỂN ỨNG DỤNG CNTT", { after: 600 }),
    c("ĐỀ TÀI:", { size: 28, after: 60 }),
    c("XÂY DỰNG HỆ THỐNG QUẢN LÝ VẬN CHUYỂN PAVEX", { size: 32, color: BLUE, after: 700 }),
    left("HỌ VÀ TÊN SINH VIÊN: ", "ÂN HIỀN BẢO PHÚC"),
    left("HỌ VÀ TÊN SINH VIÊN: ", "DƯƠNG THÁI BẢO"),
    left("GVHD: ", "VÕ NGỌC TẤN PHƯỚC"),
    left("LỚP: ", "DHHTTT18A"),
    left("MÃ HỌC PHẦN: ", "420300314702"),
    c("TP. Hồ Chí Minh, năm 2026", { before: 900, bold: false, size: 26 }),
  ];
}

function header() {
  const logo = pngSize(path.join(FIG, "logo-header.png"));
  return new Header({
    children: [new Paragraph({
      border: { bottom: { style: BorderStyle.SINGLE, size: 6, color: BLUE, space: 4 } },
      tabStops: [{ type: TabStopType.LEFT, position: 1600 }],
      children: [
        new ImageRun({ type: "png", data: logo.data, transformation: { width: 80, height: Math.round(80 * logo.h / logo.w) } }),
        new TextRun({ text: "\tKHOA CÔNG NGHỆ THÔNG TIN", font: FONT, size: 22, bold: true, color: BLUE }),
      ],
    })],
  });
}

function footer() {
  return new Footer({
    children: [new Paragraph({ alignment: AlignmentType.CENTER, children: [new TextRun({ children: [PageNumber.CURRENT], font: FONT, size: 22 })] })],
  });
}

// ---------------------------------------------------------------------------
const sections = [];
let cur = [];
const portrait = () => ({ page: { size: A4, margin: MARGIN } });
const landscape = () => ({ page: { size: { width: A4.width, height: A4.height, orientation: PageOrientation.LANDSCAPE }, margin: { top: 1300, bottom: 1300, left: 1440, right: 1440, header: 600, footer: 600 } } });
let curProps = portrait();
const flush = (type) => {
  if (cur.length) sections.push({ properties: { ...curProps, type: type || SectionType.NEXT_PAGE }, headers: { default: header() }, footers: { default: footer() }, children: cur });
  cur = [];
};

for (const b of blocks) {
  switch (b.t) {
    case "cover":
      sections.push({
        properties: {
          page: { size: A4, margin: { top: 1134, bottom: 1134, left: 1418, right: 1134 },
                  borders: { pageBorderTop: { style: BorderStyle.THICK_THIN_LARGE_GAP, size: 24, color: "1F4E9A", space: 24 },
                             pageBorderBottom: { style: BorderStyle.THIN_THICK_LARGE_GAP, size: 24, color: "1F4E9A", space: 24 },
                             pageBorderLeft: { style: BorderStyle.THICK_THIN_LARGE_GAP, size: 24, color: "1F4E9A", space: 24 },
                             pageBorderRight: { style: BorderStyle.THIN_THICK_LARGE_GAP, size: 24, color: "1F4E9A", space: 24 } } },
        },
        children: coverChildren(),
      });
      break;
    case "front":
      cur.push(new Paragraph({ alignment: AlignmentType.CENTER, pageBreakBefore: cur.length > 0, spacing: { before: 120, after: 240 }, children: runs(b.text, { bold: true, size: 30 }) }));
      break;
    case "toc": {
      const tocFile = path.join(HERE, "toc.json");
      if (!fs.existsSync(tocFile)) { cur.push(new TableOfContents("MỤC LỤC", { hyperlink: true, headingStyleRange: "1-3" })); break; }
      // static TOC with page numbers measured from a rendered pass (see make_report.py)
      for (const e of JSON.parse(fs.readFileSync(tocFile, "utf8"))) {
        cur.push(new Paragraph({
          tabStops: [{ type: TabStopType.RIGHT, position: TEXT_W, leader: "dot" }],
          indent: { left: (e.level - 1) * 400 },
          spacing: { before: e.level === 1 ? 120 : 0, after: 40, line: 276 },
          children: [new TextRun({ text: e.text, font: FONT, size: 26, bold: e.level === 1, italics: e.level === 3 }),
                     new TextRun({ text: `\t${e.page ?? ""}`, font: FONT, size: 26, bold: e.level === 1 })],
        }));
      }
      break;
    }
    case "h1":
      cur.push(new Paragraph({ heading: HeadingLevel.HEADING_1, pageBreakBefore: true, alignment: AlignmentType.CENTER, children: [new TextRun({ text: b.text })] }));
      break;
    case "h2":
      cur.push(new Paragraph({ heading: HeadingLevel.HEADING_2, children: [new TextRun({ text: b.text })] }));
      break;
    case "h3":
      cur.push(new Paragraph({ heading: HeadingLevel.HEADING_3, children: [new TextRun({ text: b.text })] }));
      break;
    case "h4":
      cur.push(new Paragraph({ heading: HeadingLevel.HEADING_4, children: [new TextRun({ text: b.text })] }));
      break;
    case "p":
      cur.push(para(b.text, { indent: true }));
      break;
    case "bullets":
      for (const it of b.items) cur.push(new Paragraph({ numbering: { reference: "bullets", level: 0 }, alignment: AlignmentType.JUSTIFIED, spacing: { after: 80, line: 300 }, children: runs(it) }));
      break;
    case "caption":
      cur.push(new Paragraph({ alignment: AlignmentType.CENTER, keepNext: true, spacing: { before: 160, after: 80 }, children: runs(b.text, { size: 24, italics: true }) }));
      break;
    case "table":
      cur.push(table(b.header, b.rows, b.widths, b.size));
      cur.push(para("", { after: 120 }));
      break;
    case "spec":
      cur.push(specTable(b.uc));
      cur.push(para("", { after: 60 }));
      break;
    case "img":
      cur.push(image(b.path, b.w, b.maxh));
      cur.push(caption(b.caption));
      break;
    case "placeholder":
      cur.push(placeholderBox(b.text));
      cur.push(caption(b.caption));
      break;
    case "landscape_start":  // a wide figure on its own landscape page
      flush();
      curProps = landscape();
      break;
    case "landscape_end":
      flush();
      curProps = portrait();
      break;
    case "testcase":
      cur.push(testcaseTable(b.tc));
      cur.push(para("", { after: 120 }));
      break;
    case "sign":
      cur.push(new Paragraph({ alignment: AlignmentType.CENTER, pageBreakBefore: true, spacing: { after: 400 }, children: runs(b.text, { bold: true, size: 28 }) }));
      for (let i = 0; i < 16; i++) cur.push(new Paragraph({ border: { bottom: { style: BorderStyle.DOTTED, size: 4, color: "999999", space: 6 } }, spacing: { after: 200 }, children: [] }));
      cur.push(new Paragraph({ alignment: AlignmentType.RIGHT, spacing: { before: 400 }, children: runs("TP. Hồ Chí Minh, ngày …. tháng …. năm …….", { italics: true }) }));
      cur.push(new Paragraph({ alignment: AlignmentType.RIGHT, spacing: { before: 120 }, indent: { right: 600 }, children: runs("CHỮ KÝ CỦA GIẢNG VIÊN", { bold: true }) }));
      break;
    default:
      throw new Error("unknown block " + b.t);
  }
}
flush();

const doc = new Document({
  creator: "PAVEX",
  title: "Báo cáo cuối kỳ – Hệ thống quản lý vận chuyển PAVEX",
  features: { updateFields: true },
  styles: {
    default: { document: { run: { font: FONT, size: SZ } } },
    paragraphStyles: [
      { id: "Heading1", name: "Heading 1", basedOn: "Normal", next: "Normal", quickFormat: true,
        run: { font: FONT, size: 30, bold: true, color: "1F3864" }, paragraph: { spacing: { before: 120, after: 360 }, outlineLevel: 0 } },
      { id: "Heading2", name: "Heading 2", basedOn: "Normal", next: "Normal", quickFormat: true,
        run: { font: FONT, size: 28, bold: true, color: "1F3864" }, paragraph: { spacing: { before: 300, after: 160 }, outlineLevel: 1, keepNext: true } },
      { id: "Heading3", name: "Heading 3", basedOn: "Normal", next: "Normal", quickFormat: true,
        run: { font: FONT, size: 26, bold: true, italics: true }, paragraph: { spacing: { before: 240, after: 120 }, outlineLevel: 2, keepNext: true } },
      { id: "Heading4", name: "Heading 4", basedOn: "Normal", next: "Normal", quickFormat: true,
        run: { font: FONT, size: 26, bold: true }, paragraph: { spacing: { before: 240, after: 120 }, outlineLevel: 3, keepNext: true } },
    ],
  },
  numbering: {
    config: [{ reference: "bullets", levels: [{ level: 0, format: LevelFormat.BULLET, text: "–", alignment: AlignmentType.LEFT,
      style: { paragraph: { indent: { left: 720, hanging: 360 } } } }] }],
  },
  sections,
});

Packer.toBuffer(doc).then((buf) => { fs.writeFileSync(OUT, buf); console.log("written", OUT, buf.length); });
