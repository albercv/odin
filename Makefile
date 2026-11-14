.PHONY: instalacion-desplegar instalacion-actualizar instalacion-anadir-proyecto setup test check up down demo answer-battery tagger-model tagging-battery project-proposer-battery datos-prueba-cargar datos-prueba-recuento primera-carga instalacion-reiniciar instalacion-restaurar mcp-claude-desktop

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
	python3 -m unittest discover -s deploy/instalacion -p 'test_*.py'

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

# Batería de la propuesta de proyecto con el modelo real (los 15 documentos que las reglas no resuelven). Fuera de make check.
project-proposer-battery:
	cd api && mvn -q -am -pl odin-adapter-tagger-ollama verify -Dgroups=model -DexcludedGroups= -Dit.test=ProjectProposerBatteryIT -Dfailsafe.failIfNoSpecifiedTests=false

# Instalación del propietario (D-056): datos de prueba, vaciarla, volver a una copia y conector de Claude Desktop.
# Van con cerrojo (data/instalacion.lock). INSTALACION_PROYECTO e INSTALACION_ENV eligen otra pila.
datos-prueba-cargar:
	@ARCHIVO="$(ARCHIVO)" deploy/instalacion/cerrojo.sh python3 deploy/instalacion/datos_prueba.py

# Recuento contado en la base, en tablas Markdown. La carga completa es: make datos-prueba-cargar ARCHIVO=completo
datos-prueba-recuento:
	@deploy/instalacion/cerrojo.sh deploy/instalacion/recuento.sh

instalacion-reiniciar:
	@CONFIRMAR="$(CONFIRMAR)" deploy/instalacion/cerrojo.sh deploy/instalacion/reiniciar.sh

instalacion-restaurar:
	@COPIA="$(COPIA)" CONFIRMAR="$(CONFIRMAR)" deploy/instalacion/cerrojo.sh deploy/instalacion/restaurar.sh

mcp-claude-desktop:
	@TRABAJADOR="$(TRABAJADOR)" deploy/instalacion/cerrojo.sh deploy/instalacion/mcp-claude-desktop.sh

instalacion-desplegar:
	@CONFIRMAR="$(CONFIRMAR)" TRABAJADOR="$(TRABAJADOR)" CONSTRUIR="$(CONSTRUIR)" deploy/instalacion/cerrojo.sh deploy/instalacion/desplegar.sh

instalacion-actualizar:
	@CONFIRMAR="$(CONFIRMAR)" MODO=actualizar CONSTRUIR="$(CONSTRUIR)" deploy/instalacion/cerrojo.sh deploy/instalacion/desplegar.sh

# Primera carga del archivo de una empresa (la lanza su administrador): make primera-carga CARPETA=/ruta [ARGS=--si]
primera-carga:
	@test -n "$(CARPETA)" || { echo "Falta la carpeta: make primera-carga CARPETA=/ruta/del/archivo"; exit 1; }
	@deploy/instalacion/cerrojo.sh python3 deploy/instalacion/primera_carga.py "$(CARPETA)" $(ARGS)

# Añade un proyecto nuevo sin vaciar (encargo 14): make instalacion-anadir-proyecto DATOS_PROYECTO=<carpeta> CONFIRMAR=ANADIR
instalacion-anadir-proyecto:
	@DATOS_PROYECTO="$(DATOS_PROYECTO)" CONFIRMAR="$(CONFIRMAR)" MIEMBROS="$(or $(MIEMBROS),marta)" deploy/instalacion/cerrojo.sh deploy/instalacion/anadir-proyecto.sh
