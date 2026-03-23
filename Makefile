.PHONY: help up down logs logs-api logs-ai logs-postgres logs-redis restart clean test build

# Colors for output
BLUE := \033[0;34m
GREEN := \033[0;32m
YELLOW := \033[0;33m
NC := \033[0m # No Color

help:
	@echo "$(BLUE)Meridian Platform — Local Development$(NC)"
	@echo ""
	@echo "$(GREEN)Available targets:$(NC)"
	@echo "  make up                Build and start all services"
	@echo "  make down              Stop and remove all containers"
	@echo "  make restart           Restart all services"
	@echo "  make logs              Tail logs from all services"
	@echo "  make logs-api          Tail logs from API service"
	@echo "  make logs-ai           Tail logs from AI service"
	@echo "  make logs-postgres     Tail logs from PostgreSQL"
	@echo "  make logs-redis        Tail logs from Redis"
	@echo "  make clean             Remove containers, volumes, and networks"
	@echo "  make test              Run all tests (unit, integration, E2E)"
	@echo "  make test-unit         Run unit tests only"
	@echo "  make test-integration  Run integration tests only"
	@echo "  make test-e2e          Run E2E Playwright tests"
	@echo "  make build             Build Docker images"
	@echo "  make seed              Seed database with sample data"
	@echo "  make migrate           Run Flyway migrations"
	@echo "  make status            Show container status"
	@echo ""

up:
	@echo "$(BLUE)Starting Meridian platform...$(NC)"
	docker-compose up -d
	@echo "$(GREEN)Meridian is running!$(NC)"
	@echo "API:       http://localhost:8000"
	@echo "AI Service: http://localhost:8080"
	@echo "Frontend:  http://localhost:3000"
	@echo "GraphQL:   http://localhost:8000/graphql"

down:
	@echo "$(BLUE)Stopping Meridian platform...$(NC)"
	docker-compose down
	@echo "$(GREEN)Meridian stopped.$(NC)"

logs:
	docker-compose logs -f

logs-api:
	docker-compose logs -f api

logs-ai:
	docker-compose logs -f ai-service

logs-postgres:
	docker-compose logs -f postgres

logs-redis:
	docker-compose logs -f redis

restart:
	@echo "$(BLUE)Restarting Meridian platform...$(NC)"
	docker-compose restart
	@echo "$(GREEN)Services restarted.$(NC)"

clean:
	@echo "$(YELLOW)WARNING: This will remove all containers, volumes, and networks.$(NC)"
	@read -p "Continue? [y/N] " -n 1 -r; \
	echo; \
	if [[ $$REPLY =~ ^[Yy]$$ ]]; then \
		docker-compose down -v; \
		echo "$(GREEN)Cleaned up all Meridian containers and volumes.$(NC)"; \
	fi

build:
	@echo "$(BLUE)Building Meridian services...$(NC)"
	docker-compose build
	@echo "$(GREEN)Build completed.$(NC)"

test: test-unit test-integration test-e2e
	@echo "$(GREEN)All tests passed!$(NC)"

test-unit:
	@echo "$(BLUE)Running unit tests...$(NC)"
	cd api && ./gradlew test
	cd ../ai-service && python -m pytest tests/unit -v
	cd ../frontend && npm test

test-integration:
	@echo "$(BLUE)Running integration tests...$(NC)"
	cd api && ./gradlew integrationTest
	cd ../ai-service && python -m pytest tests/integration -v

test-e2e:
	@echo "$(BLUE)Running E2E tests with Playwright...$(NC)"
	cd frontend && npx playwright test

migrate:
	@echo "$(BLUE)Running database migrations...$(NC)"
	docker-compose exec api ./gradlew flywayMigrate
	@echo "$(GREEN)Migrations completed.$(NC)"

seed:
	@echo "$(BLUE)Seeding database with sample data...$(NC)"
	docker-compose exec api ./gradlew flywaySeed
	@echo "$(GREEN)Database seeded.$(NC)"

status:
	@echo "$(BLUE)Meridian Service Status:$(NC)"
	docker-compose ps

# Development targets
dev-setup:
	@echo "$(BLUE)Setting up development environment...$(NC)"
	cp .env.example .env
	chmod 600 .env
	@echo "$(GREEN)Configuration ready. Update .env with your keys.$(NC)"
	@echo "Then run: make up"

dev-keys:
	@echo "$(BLUE)Generating RSA keypair for JWT...$(NC)"
	openssl genrsa -out /tmp/private.pem 2048
	openssl rsa -in /tmp/private.pem -pubout -out /tmp/public.pem
	@echo "$(GREEN)Keys generated. Update .env with the following:$(NC)"
	@echo ""
	@echo "JWT_SECRET_KEY = (paste contents of /tmp/private.pem)"
	@echo "JWT_PUBLIC_KEY = (paste contents of /tmp/public.pem)"

.DEFAULT_GOAL := help
