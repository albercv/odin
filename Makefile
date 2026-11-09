.PHONY: setup test check up down demo

setup:
	@echo "pendiente (tarea 11)"; exit 1

test:
	cd api && mvn -q test
	cd mcp && npm ci --silent && npm test

check:
	cd api && mvn -q verify
	cd mcp && npm ci --silent && npm run lint && npm run typecheck && npm test

up:
	@echo "pendiente (tarea 11)"; exit 1

down:
	@echo "pendiente (tarea 11)"; exit 1

demo:
	@echo "pendiente (tarea 12)"; exit 1
