#!/usr/bin/env bash
set -euo pipefail
diagram_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_dir="$(cd "$diagram_dir/../.." && pwd)"
plantuml_jar="${PLANTUML_JAR:-$repo_dir/.local/tools/plantuml-1.2026.8.jar}"
if [[ ! -f "$plantuml_jar" ]]; then
  echo "Set PLANTUML_JAR to a local PlantUML 1.2026.8 JAR." >&2
  exit 1
fi
for format in svg png; do
  java -Djava.awt.headless=true -jar "$plantuml_jar" \
    -charset UTF-8 "-t$format" -failfast2 "$diagram_dir/[0-9]*.puml"
done
echo "Rendered 10 UML diagrams as SVG and PNG in $diagram_dir"
