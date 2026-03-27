.PHONY: help up down logs logs-api logs-ai logs-postgres logs-redis restart clean test build health \
	dev-setup dev-keys dev-check dev-install dev-install-frontend dev-install-ai

# Colors for output
BLUE   := \033[0;34m
GREEN  := \033[0;32m
YELLOW := \033[0;33m
RED    := \033[0;31m
NC     := \033[0m # No Color

help:
	@echo "$(BLUE)Meridian Platform — Local Development$(NC)"
	@echo ""
	@echo "$(GREEN)Setup:$(NC)"
	@echo "  make dev-setup              One-time setup (env + JWT keys)"
	@echo "  make dev-check              Verify prerequisites for Docker run"
	@echo "  make dev-keys               Regenerate JWT RSA keypair and inject into .env"
	@echo "  make dev-install            Install local test/development dependencies"
	@echo "  make dev-install-frontend   Install frontend npm deps + Playwright browsers"
	@echo "  make dev-install-ai         Install AI service Python deps"
	@echo ""
	@echo "$(GREEN)Running:$(NC)"
	@echo "  make health            Verify service health endpoints"
	@echo "  make up                Build and start all services"
	@echo "  make down              Stop and remove all containers"
	@echo "  make restart           Restart all services"
	@echo "  make status            Show container status"
	@echo ""
	@echo "$(GREEN)Logs:$(NC)"
	@echo "  make logs              Tail logs from all services"
	@echo "  make logs-api          Tail logs from API service"
	@echo "  make logs-ai           Tail logs from AI service"
	@echo "  make logs-postgres     Tail logs from PostgreSQL"
	@echo "  make logs-redis        Tail logs from Redis"
	@echo ""
	@echo "$(GREEN)Database:$(NC)"
	@echo "  make migrate           Run Flyway migrations"
	@echo "  make seed              Seed database with sample data"
	@echo ""
	@echo "$(GREEN)Testing:$(NC)"
	@echo "  make test              Run all tests (unit, integration, E2E)"
	@echo "  make test-unit         Run unit tests only"
	@echo "  make test-integration  Run integration tests only"
	@echo "  make test-e2e          Run E2E Playwright tests"
	@echo ""
	@echo "$(GREEN)Maintenance:$(NC)"
	@echo "  make build             Build Docker images"
	@echo "  make clean             Remove containers, volumes, and networks"
	@echo ""

up:
	@[ -f .env ] || { echo "$(RED)✗ .env not found. Run 'make dev-setup' first$(NC)"; exit 1; }
	@echo "$(BLUE)Starting Meridian platform...$(NC)"
	docker compose up -d
	@echo "$(GREEN)Meridian is running!$(NC)"
	@echo "API:       http://localhost:8000"
	@echo "AI Service: http://localhost:8080"
	@echo "Frontend:  http://localhost:3000"
	@echo "GraphQL:   http://localhost:8000/graphql"
	@echo "Run 'make health' to verify all services are ready"

down:
	@echo "$(BLUE)Stopping Meridian platform...$(NC)"
	docker compose down
	@echo "$(GREEN)Meridian stopped.$(NC)"

logs:
	docker compose logs -f

logs-api:
	docker compose logs -f api

logs-ai:
	docker compose logs -f ai-service

logs-postgres:
	docker compose logs -f postgres

logs-redis:
	docker compose logs -f redis

restart:
	@echo "$(BLUE)Restarting Meridian platform...$(NC)"
	docker compose restart
	@echo "$(GREEN)Services restarted.$(NC)"

clean:
	@echo "$(YELLOW)WARNING: This will remove all containers, volumes, and networks.$(NC)"
	@read -p "Continue? [y/N] " -n 1 -r; \
	echo; \
	if [[ $$REPLY =~ ^[Yy]$$ ]]; then \
		docker compose down -v; \
		echo "$(GREEN)Cleaned up all Meridian containers and volumes.$(NC)"; \
	fi

build:
	@echo "$(BLUE)Building Meridian services...$(NC)"
	docker compose build
	@echo "$(GREEN)Build completed.$(NC)"

test: test-unit test-integration test-e2e
	@echo "$(GREEN)All tests passed!$(NC)"

test-unit:
	@echo "$(BLUE)Running unit tests...$(NC)"
	cd api && mvn test
	cd ../ai-service && python3 -m pytest tests/unit -v
	cd ../frontend && npm test

test-integration:
	@echo "$(BLUE)Running integration tests...$(NC)"
	cd api && mvn verify -Pintegration-tests
	cd ../ai-service && python3 -m pytest tests/integration -v

test-e2e:
	@echo "$(BLUE)Running E2E tests with Playwright...$(NC)"
	cd frontend && npx playwright test

migrate:
	@echo "$(BLUE)Flyway migrations run automatically when the API starts.$(NC)"
	@echo "Use 'make restart' after updating SQL migrations."

seed:
	@echo "$(YELLOW)No seed task is implemented for the API yet.$(NC)"
	@echo "Add SQL seed data under api/src/main/resources/db/migration if needed."

status:
	@echo "$(BLUE)Meridian Service Status:$(NC)"
	docker compose ps

health:
	@echo "$(BLUE)Checking service health...$(NC)"
	@curl -sf http://localhost:3000 >/dev/null && echo "$(GREEN)✓ Frontend is reachable$(NC)" || echo "$(YELLOW)  Frontend not ready yet$(NC)"
	@curl -sf http://localhost:8000/actuator/health >/dev/null && echo "$(GREEN)✓ API is healthy$(NC)" || echo "$(YELLOW)  API not ready yet$(NC)"
	@curl -sf http://localhost:8080/health >/dev/null && echo "$(GREEN)✓ AI service is healthy$(NC)" || echo "$(YELLOW)  AI service not ready yet$(NC)"

# ─── Development Setup ────────────────────────────────────────────────────────

dev-check:
	@echo "$(BLUE)Checking prerequisites...$(NC)"
	@command -v docker      >/dev/null 2>&1 || { echo "$(RED)✗ docker not found$(NC)";               exit 1; }
	@(command -v docker-compose >/dev/null 2>&1 || docker compose version >/dev/null 2>&1) || \
		{ echo "$(RED)✗ docker-compose not found$(NC)"; exit 1; }
	@command -v make        >/dev/null 2>&1 || { echo "$(RED)✗ make not found$(NC)";                 exit 1; }
	@command -v openssl     >/dev/null 2>&1 || { echo "$(RED)✗ openssl not found$(NC)";             exit 1; }
	@command -v python3     >/dev/null 2>&1 || { echo "$(RED)✗ python3 not found (need 3.11+)$(NC)"; exit 1; }
	@echo "$(GREEN)✓ All prerequisites satisfied$(NC)"
	@printf "  docker:  "; docker --version
	@printf "  python:  "; python3 --version

dev-keys:
	@echo "$(BLUE)Generating JWT RSA keypair...$(NC)"
	@[ -f .env ] || { echo "$(RED)✗ .env not found — run 'make dev-setup' first$(NC)"; exit 1; }
	@python3 scripts/inject-jwt.py
	@echo "$(GREEN)✓ JWT keys injected into .env$(NC)"

dev-install-frontend:
	@echo "$(BLUE)Preparing frontend npm cache in project folder...$(NC)"
	@mkdir -p frontend/.npm-cache
	@echo "$(BLUE)Installing frontend dependencies...$(NC)"
	cd frontend && NPM_CONFIG_CACHE=$$(pwd)/.npm-cache npm install
	@echo "$(BLUE)Installing Playwright browsers (skipped if unavailable)...$(NC)"
	cd frontend && NPM_CONFIG_CACHE=$$(pwd)/.npm-cache npx playwright install --with-deps || \
		echo "$(YELLOW)  Playwright browser install skipped (run manually if needed: cd frontend && npx playwright install)$(NC)"
	@echo "$(GREEN)✓ Frontend dependencies ready$(NC)"

dev-install-ai:
	@echo "$(BLUE)Installing AI service Python dependencies...$(NC)"
	cd ai-service && python3 -m pip install -r requirements.txt
	@echo "$(GREEN)✓ AI service dependencies ready$(NC)"

dev-install: dev-install-frontend dev-install-ai
	@echo "$(GREEN)✓ All dependencies installed$(NC)"

dev-setup: dev-check
	@echo "$(BLUE)Setting up development environment...$(NC)"
	@if [ ! -f .env ]; then \
		cp .env.example .env; \
		chmod 600 .env; \
		echo "$(GREEN)✓ .env created from .env.example$(NC)"; \
	else \
		echo "$(YELLOW)  .env already exists — skipping copy$(NC)"; \
	fi
	@$(MAKE) --no-print-directory dev-keys
	@echo ""
	@echo "$(GREEN)Dev environment ready!$(NC)"
	@echo "  LLM_PROVIDER=mock is set — no API keys needed to start"
	@echo "  For local tests outside Docker: run make dev-install"
	@echo "  Edit .env to configure OPENAI_API_KEY or AWS credentials"
	@echo ""
	@echo "Run: $(BLUE)make up$(NC)"

.DEFAULT_GOAL := help
