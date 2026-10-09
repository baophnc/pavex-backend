"""Build the report: figures -> content.json -> docx, then measure heading pages in a PDF render
and rebuild with a static table of contents until page numbers are stable.

usage: python3 make_report.py <svg-to-png renderer.cjs>
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


def measure(pdf, headings):
    pages = page_texts(pdf)
    toc, start = [], 3  # skip cover, thanks and the TOC itself
    for lvl, text in headings:
        key = norm(text)[:60]
        page = None
        for i in range(start, len(pages)):
            if key in pages[i]:
                page = i + 1
                start = i
                break
        toc.append({"level": lvl, "text": text, "page": page})
    return toc


def main():
    renderer = sys.argv[1]
    WORK.mkdir(parents=True, exist_ok=True)
    subprocess.run([sys.executable, str(HERE / "make_figures.py"), renderer], check=True)
    subprocess.run([sys.executable, str(HERE / "content.py")], check=True)
    blocks = json.loads((HERE / "content.json").read_text(encoding="utf-8"))
    headings = [({"h1": 1, "h2": 2, "h3": 3}[b["t"]], b["text"]) for b in blocks if b["t"] in ("h1", "h2", "h3")]
    toc_file = HERE / "toc.json"
    # pass 0: placeholder TOC of the right length so pagination matches the final document
    toc_file.write_text(json.dumps([{"level": l, "text": t, "page": 0} for l, t in headings], ensure_ascii=False), encoding="utf-8")
    docx = WORK / "PAVEX-bao-cao.docx"
    prev = None
    for _ in range(4):
        build_docx(docx)
        toc = measure(to_pdf(docx), headings)
        if toc == prev:
            break
        toc_file.write_text(json.dumps(toc, ensure_ascii=False, indent=1), encoding="utf-8")
        prev = toc
    build_docx(docx)
    pdf = to_pdf(docx)
    missing = [e["text"] for e in prev if e["page"] is None]
    if missing:
        print("WARNING headings not found:", missing)
    shutil.copy(docx, OUT / "PAVEX-bao-cao.docx")
    shutil.copy(pdf, OUT / "PAVEX-bao-cao.pdf")
    print("done", OUT / "PAVEX-bao-cao.docx")


if __name__ == "__main__":
    main()
