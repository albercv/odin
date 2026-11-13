.PHONY: setup test check up down demo answer-battery tagger-model tagging-battery mcp-extension

setup:
	cd api && mvn -q dependency:go-offline
	cd mcp && npm ci --silent
	cd panel && npm ci --silent

test:
	cd api && mvn -q test
	cd mcp && npm ci --silent && npm test
	cd panel && npm ci --silent && npm test

check:
	cd api && mvn -q verify
	cd mcp && npm ci --silent && npm run check:types && npm run lint && npm run typecheck && npm run build && npm test && npm run extension:validate
	cd panel && npm ci --silent && npm run check:types && npm run lint && npm run typecheck && npm test && npm run build

mcp-extension:
	cd mcp && npm ci --silent && npm run build && bash scripts/pack-extension.sh

COMPOSE = docker compose -f deploy/compose.yaml --env-file deploy/.env

up:
	@test -f deploy/.env || { echo "Falta deploy/.env: cópialo de deploy/.env.example (cp deploy/.env.example deploy/.env) y cambia las claves y el token."; exit 1; }
	$(COMPOSE) up -d --build --wait

down:
	$(COMPOSE) down

demo:
	@deploy/demo/run-demo.sh

# Batería de respuestas (experimento del encargo 12): usa la cuenta de Claude, no entra en check ni en demo.
answer-battery:
	@MODELO="$(MODELO)" MUESTRA="$(MUESTRA)" experimentos/2026-11-12-archivo-ficticio/medir/medir.sh

# Descarga el modelo del etiquetado con el Ollama del equipo. Es la única orden del etiquetado que usa la red.
tagger-model:
	ollama pull $(or $(MODEL),qwen3.5:9b)

# Batería del etiquetado con el modelo real (el Ollama de ODIN_TAGGER_URL). Fuera de make check; escribe api/odin-adapter-tagger-ollama/target/tagging-battery.json.
tagging-battery:
	cd api && mvn -q -am -pl odin-adapter-tagger-ollama verify -Dgroups=model -DexcludedGroups=
