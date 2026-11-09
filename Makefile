.PHONY: setup test check up down demo

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
	@test -f deploy/.env || { echo "Falta deploy/.env: cópialo de deploy/.env.example (cp deploy/.env.example deploy/.env) y cambia los valores."; exit 1; }
	$(COMPOSE) up -d --build --wait

down:
	$(COMPOSE) down

demo:
	@deploy/demo/run-demo.sh
