#!/usr/bin/env bash
# Empaqueta el MCP compilado (dist/) como extensión de Claude Desktop: dist-extension/<name>-<version>.mcpb.
# Lleva solo lo que hace falta en ejecución: dist, dependencias de producción, manifiesto e icono.
set -euo pipefail
cd "$(dirname "$0")/.."
[ -f dist/stdio.js ] || { echo "Falta dist/stdio.js: compila antes (npm run build)." >&2; exit 1; }
VERSION=$(node -p 'require("./package.json").version')
NOMBRE=$(node -p 'require("./extension/manifest.json").name')
PAQUETE="dist-extension/$NOMBRE-$VERSION.mcpb"
OBRA=dist-extension/obra
rm -rf "$OBRA" "$PAQUETE" && mkdir -p "$OBRA"
cp -R dist extension/manifest.json extension/icon.png package.json package-lock.json "$OBRA/"
(cd "$OBRA" && npm ci --omit=dev --ignore-scripts --silent && rm package-lock.json)
npx mcpb pack "$OBRA" "$PAQUETE" > /dev/null
rm -rf "$OBRA"
echo "Extensión de Claude Desktop: $(pwd)/$PAQUETE"
