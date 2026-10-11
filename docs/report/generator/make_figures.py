"""Render the report figures to ../figures (SVG + PNG): activity and sequence diagrams of the core
use cases, the package diagram and the overall diagram of the domain model."""
import os
import pathlib
import subprocess

from diagrams import activity, sequence
from activities import ACTIVITIES, LANE
from sequences import SEQ
from specs import CORE

OUT = pathlib.Path(__file__).resolve().parent.parent / "figures"
OUT.mkdir(exist_ok=True)


def slug(uc):
    return uc["code"].split(" ")[0].lower()


def class_figures():
    """Package diagram and overall domain model."""
    import classdiag

    (OUT / "domain-packages.svg").write_text(classdiag.package_svg(), encoding="utf-8")
    (OUT / "domain-overview.svg").write_text(classdiag.overview_svg(), encoding="utf-8")


def main():
    class_figures()
    names = {u["code"]: u["name"] for u in CORE}
    for code, steps in ACTIVITIES.items():  # AD-UCxx
        svg = activity(f"AD-{code} {names[code]}", (LANE[code], "Hệ thống PAVEX"), steps)
        (OUT / f"ad-{code.lower()}.svg").write_text(svg, encoding="utf-8")
    for code, sd in SEQ.items():  # SD-UCxx
        svg = sequence(f"SD-{code} {names[code]}", sd["parts"], sd["msgs"], sd["frags"])
        (OUT / f"sd-{code.lower()}.svg").write_text(svg, encoding="utf-8")
    env = dict(os.environ, NODE_PATH="/opt/node22/lib/node_modules")
    subprocess.run(["node", str(pathlib.Path(__file__).with_name("render_svg.cjs")), str(OUT)], check=True, env=env)


if __name__ == "__main__":
    main()
