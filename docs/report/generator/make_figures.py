"""Render the report figures to ../figures (SVG + PNG): activity and sequence diagrams of the core
use cases, the package diagram of the domain model and one class diagram per bounded context."""
import os
import pathlib
import subprocess

from diagrams import activity, sequence
from specs import CORE, GUARDS

OUT = pathlib.Path(__file__).resolve().parent.parent / "figures"
OUT.mkdir(exist_ok=True)


def slug(uc):
    return uc["code"].split(" ")[0].lower()


def class_figures():
    """Package diagram of the bounded contexts + one class diagram per context."""
    import classdiag

    (OUT / "domain-packages.svg").write_text(classdiag.package_svg(), encoding="utf-8")
    for slug, _pkg, d in classdiag.context_diagrams():
        (OUT / f"class-{slug}.svg").write_text(d.to_svg(), encoding="utf-8")


def main():
    class_figures()
    svgs = []
    for uc in CORE:
        lane_actor = uc["seq"]["parts"][0][1]  # same actor name as the sequence diagram
        a = activity(uc["name"], (lane_actor, "Hệ thống PAVEX"), uc["activity"], GUARDS)
        s = sequence(uc["name"], uc["seq"]["parts"], uc["seq"]["msgs"], uc["seq"].get("frags"))
        for kind, svg in (("act", a), ("seq", s)):
            p = OUT / f"{slug(uc)}-{kind}.svg"
            p.write_text(svg, encoding="utf-8")
            svgs.append(str(p))
    env = dict(os.environ, NODE_PATH="/opt/node22/lib/node_modules")
    subprocess.run(["node", str(pathlib.Path(__file__).with_name("render_svg.cjs")), str(OUT)], check=True, env=env)


if __name__ == "__main__":
    main()
