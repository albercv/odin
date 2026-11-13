.PHONY: setup test check up down demo answer-battery tagger-model tagging-battery banco-arriba banco-cargar banco-abajo banco banco-reanudar banco-barrido banco-informe banco-test

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
	cd mcp && npm ci --silent && npm run check:types && npm run lint && npm run typecheck && npm run build && npm test
	cd panel && npm ci --silent && npm run check:types && npm run lint && npm run typecheck && npm test && npm run build

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

# Banco de pruebas (archivo ficticio, fase 0): pila Docker propia en 48080/48088/48090 y estado en banco/estado (ignorado por git).
EXP = experimentos/2026-11-12-archivo-ficticio
BANCO_ENV = PILA_PROYECTO=odin-banco PILA_ESTADO=$(CURDIR)/banco/estado ODIN_API_PORT=48080 ODIN_PANEL_PORT=48088 ODIN_MCP_PORT=48090
CONFIG ?= banco/configs/piloto.toml
ARCHIVOS ?= ficticio
EXPEDIENTES ?=

banco-arriba:
	$(BANCO_ENV) $(EXP)/pila/pila.sh up

banco-cargar:
	@if [ -n "$(EXPEDIENTES)" ] && [ ! -f $(EXP)/pila/cargar_expedientes.py ]; then echo "EXPEDIENTES=1 pide $(EXP)/pila/cargar_expedientes.py, que no existe en esta rama"; exit 1; fi; \
	D=$(EXP)/datos; if [ "$(ARCHIVOS)" != ficticio ]; then D=$$($(BANCO_ENV) python3 banco/capas.py fusionar --archivos "$(ARCHIVOS)") || exit 1; fi; \
	$(BANCO_ENV) python3 $(EXP)/pila/cargar.py --manifiesto $$D/manifiesto.json --corpus $$D/corpus --marcar || exit 1; \
	if [ -n "$(EXPEDIENTES)" ] && [ -f $$D/expedientes.json ]; then $(BANCO_ENV) python3 $(EXP)/pila/cargar_expedientes.py --mapa $(CURDIR)/banco/estado/carga.json --datos $$D --marcar-casos || exit 1; fi

banco-abajo:
	$(BANCO_ENV) $(EXP)/pila/pila.sh down

banco:
	$(BANCO_ENV) python3 banco/banco.py run --config $(CONFIG) $(if $(REINICIAR),--reiniciar-presupuesto)

banco-reanudar:
	$(BANCO_ENV) python3 banco/banco.py reanudar --config $(CONFIG)

banco-barrido:
	$(BANCO_ENV) python3 banco/banco.py sweep --config $(CONFIG) $(if $(REINICIAR),--reiniciar-presupuesto)

banco-informe:
	@test -n "$(RESULTADOS)" || { echo "Falta RESULTADOS=<carpeta de banco/resultados>"; exit 1; }
	python3 banco/banco.py report --results $(RESULTADOS)

banco-test:
	python3 -m unittest discover -s banco -t . && python3 -m unittest banco.archivos.generalidad.test_generar && (cd $(EXP)/medir && python3 -m unittest)
