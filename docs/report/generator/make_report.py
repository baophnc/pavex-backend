"""Build the report: figures -> content.json -> docx, then measure heading pages in a PDF render
and rebuild with a static table of contents until page numbers are stable.

usage: python3 make_report.py   (figures are rendered with render_svg.cjs)
"""
import json
import os
import pathlib
import re
import shutil
import subprocess
import sys

HERE = pathlib.Path(__file__).resolve().parent
OUT = HERE.parent
SOFFICE = os.environ.get("SOFFICE_PY", "")
WORK = pathlib.Path(os.environ.get("REPORT_WORK", "/tmp/pavex-report"))


def norm(s):
    return re.sub(r"\s+", " ", s).strip().lower()


def build_docx(target):
    env = dict(os.environ, NODE_PATH="/opt/node22/lib/node_modules")
    subprocess.run(["node", str(HERE / "build_docx.cjs"), str(target)], check=True, env=env)


def to_pdf(docx):
    cmd = [sys.executable, SOFFICE] if SOFFICE else []
    subprocess.run(cmd + ["--headless", "--convert-to", "pdf", "--outdir", str(WORK), str(docx)] if cmd else
                   ["soffice", "--headless", "--convert-to", "pdf", "--outdir", str(WORK), str(docx)], check=True, capture_output=True)
    return WORK / (docx.stem + ".pdf")


def page_texts(pdf):
    txt = subprocess.run(["pdftotext", "-layout", str(pdf), "-"], check=True, capture_output=True, text=True).stdout
    return [norm(p) for p in txt.split("\f")]


def measure(pdf, headings, figures, tables):
    """Page of every heading / caption, searched in order from the first body page (after the lists)."""
    pages = page_texts(pdf)
    raw = subprocess.run(["pdftotext", "-layout", str(pdf), "-"], check=True, capture_output=True, text=True).stdout.split("\f")
    first = norm(headings[0][1])
    body = next((i for i, pg in enumerate(raw) if any(norm(line) == first for line in pg.splitlines())), 3)

    def find(items, keylen):
        out, start = [], body
        for lvl, text in items:
            key = norm(text)[:keylen]
            page = None
            for i in range(start, len(pages)):
                if key in pages[i]:
                    page, start = i + 1, i
                    break
            out.append({"level": lvl, "text": text, "page": page})
        return out

    return {"headings": find(headings, 60), "figures": find(figures, 50), "tables": find(tables, 50)}


def main():
    WORK.mkdir(parents=True, exist_ok=True)
    subprocess.run([sys.executable, str(HERE / "make_figures.py")], check=True)
    subprocess.run([sys.executable, str(HERE / "content.py")], check=True)
    blocks = json.loads((HERE / "content.json").read_text(encoding="utf-8"))
    headings = [({"h1": 1, "h2": 2, "h3": 3}[b["t"]], b["text"]) for b in blocks if b["t"] in ("h1", "h2", "h3")]
    figures = [(2, b["caption"]) for b in blocks if b["t"] in ("img", "placeholder")]
    tables = [(2, b["text"]) for b in blocks if b["t"] == "caption" and b["text"].startswith("Bảng")]
    toc_file = HERE / "toc.json"
    # pass 0: placeholder lists of the right length so pagination matches the final document
    blank = lambda items: [{"level": l, "text": t, "page": 0} for l, t in items]  # noqa: E731
    toc_file.write_text(json.dumps({"headings": blank(headings), "figures": blank(figures), "tables": blank(tables)},
                                   ensure_ascii=False), encoding="utf-8")
    docx = WORK / "PAVEX-bao-cao.docx"
    prev = None
    for _ in range(4):
        build_docx(docx)
        toc = measure(to_pdf(docx), headings, figures, tables)
        if toc == prev:
            break
        toc_file.write_text(json.dumps(toc, ensure_ascii=False, indent=1), encoding="utf-8")
        prev = toc
    build_docx(docx)
    pdf = to_pdf(docx)
    missing = [e["text"] for k in prev for e in prev[k] if e["page"] is None]
    if missing:
        print("WARNING headings not found:", missing)
    shutil.copy(docx, OUT / "PAVEX-bao-cao.docx")
    shutil.copy(pdf, OUT / "PAVEX-bao-cao.pdf")
    print("done", OUT / "PAVEX-bao-cao.docx")


if __name__ == "__main__":
    main()
