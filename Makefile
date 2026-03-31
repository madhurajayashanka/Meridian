.PHONY: help up down logs logs-api logs-ai logs-postgres logs-redis logs-frontend \
	restart clean test build health status \
	dev-setup dev-keys dev-check dev-install dev-install-frontend dev-install-ai \
	migrate seed \
	aws-destroy aws-destroy-env aws-plan aws-apply tf-bootstrap \
	prod-deploy destroy-all

# ── Colors ────────────────────────────────────────────────────────────────────
BLUE   := \033[0;34m
GREEN  := \033[0;32m
YELLOW := \033[0;33m
RED    := \033[0;31m
NC     := \033[0m

# ── Terraform env ─────────────────────────────────────────────────────────────
TF_ENV  ?= prod
TF_DIR   = infra/terraform/environments/$(TF_ENV)
TF_INIT  = terraform init -backend-config=backend.hcl -input=false
TF_VARS  = -var-file=terraform.tfvars

AWS_REGION       ?= us-east-1
TF_STATE_BUCKET  ?= meridian-terraform-state
APP_DOMAIN       ?=
REGISTRY ?= ghcr.io
IMAGE_ORG ?= meridian-research
IMAGE_TAG ?= $(shell git rev-parse --short HEAD 2>/dev/null || echo latest)

help:
	@echo "$(BLUE)Meridian Platform$(NC)"
	@echo ""
	@echo "$(GREEN)Local dev:$(NC)"
	@echo "  make dev-setup              One-time setup (env + JWT keys)"
	@echo "  make dev-check              Verify prerequisites"
	@echo "  make dev-keys               Regenerate JWT RSA keypair"
	@echo "  make dev-install            Install local test/dev dependencies"
	@echo "  make up                     Build and start all services (incl. MinIO)"
	@echo "  make down                   Stop all containers"
	@echo "  make restart                Restart all services"
	@echo "  make clean                  Remove containers, volumes, networks"
	@echo "  make health                 Check all service health endpoints"
	@echo "  make status                 Show container status"
	@echo "  make seed                   Seed database with demo data"
	@echo ""
	@echo "$(GREEN)Logs:$(NC)"
	@echo "  make logs                   All services"
	@echo "  make logs-api               Spring Boot API"
	@echo "  make logs-ai                FastAPI AI service"
	@echo "  make logs-frontend          Next.js frontend"
	@echo "  make logs-postgres          PostgreSQL"
	@echo "  make logs-redis             Redis"
	@echo ""
	@echo "$(GREEN)Testing:$(NC)"
	@echo "  make test                   All tests (unit + integration + E2E)"
	@echo "  make test-unit              Unit tests only"
	@echo "  make test-integration       Integration tests only"
	@echo "  make test-e2e               Playwright E2E tests"
	@echo ""
	@echo "$(GREEN)Cloud (AWS):$(NC)"
	@echo "  make tf-bootstrap           Create Terraform state bucket + DynamoDB (one-time)"
	@echo "  make aws-plan TF_ENV=prod   Preview Terraform changes"
	@echo "  make aws-apply TF_ENV=prod  Apply Terraform"
	@echo "  make prod-deploy            Build + push images + Helm upgrade"
	@echo "  make destroy-all TF_ENV=prod  Helm uninstall → terraform destroy (clean)"
	@echo "  make aws-destroy TF_ENV=prod  Terraform destroy only"
	@echo ""

# ── Local dev ─────────────────────────────────────────────────────────────────

up:
	@[ -f .env ] || { echo "$(RED)✗ .env not found. Run 'make dev-setup' first$(NC)"; exit 1; }
	@echo "$(BLUE)Starting Meridian platform...$(NC)"
	docker compose up --build -d
	@echo "$(GREEN)Meridian is running!$(NC)"
	@echo "  Frontend:  http://localhost:3000"
	@echo "  API:       http://localhost:8000"
	@echo "  AI:        http://localhost:8080"
	@echo "  GraphQL:   http://localhost:8000/graphql"
	@echo "  MinIO UI:  http://localhost:9001  (user: minioadmin)"
	@echo "Run 'make health' to verify all services are ready"

down:
	docker compose down

logs:
	docker compose logs -f

logs-api:
	docker compose logs -f api

logs-ai:
	docker compose logs -f ai-service

logs-frontend:
	docker compose logs -f frontend

logs-postgres:
	docker compose logs -f postgres

logs-redis:
	docker compose logs -f redis

restart:
	docker compose restart

status:
	docker compose ps

clean:
	@echo "$(YELLOW)WARNING: This will remove all containers, volumes, and networks.$(NC)"
	@read -p "Continue? [y/N] " -n 1 -r; \
	echo; \
	if [[ $$REPLY =~ ^[Yy]$$ ]]; then \
		docker compose down -v --remove-orphans; \
		echo "$(GREEN)✓ Cleaned up.$(NC)"; \
	fi

build:
	docker compose build

health:
	@echo "$(BLUE)Checking service health...$(NC)"
	@curl -sf http://localhost:3000 >/dev/null \
		&& echo "$(GREEN)✓ Frontend$(NC)" \
		|| echo "$(YELLOW)  Frontend not ready$(NC)"
	@curl -sf http://localhost:8000/actuator/health >/dev/null \
		&& echo "$(GREEN)✓ API$(NC)" \
		|| echo "$(YELLOW)  API not ready$(NC)"
	@curl -sf http://localhost:8080/health >/dev/null \
		&& echo "$(GREEN)✓ AI service$(NC)" \
		|| echo "$(YELLOW)  AI service not ready$(NC)"
	@curl -sf http://localhost:9001/minio/health/live >/dev/null \
		&& echo "$(GREEN)✓ MinIO$(NC)" \
		|| echo "$(YELLOW)  MinIO not ready$(NC)"

migrate:
	@echo "$(BLUE)Flyway migrations run automatically on API startup.$(NC)"
	@echo "To force: make restart"

seed:
	@echo "$(BLUE)Seeding database with demo data...$(NC)"
	@docker compose exec postgres psql -U meridian -d meridian -f /dev/stdin <<'SQL'
	INSERT INTO users (email, password_hash, name)
	VALUES ('demo@meridian.local', '$$2a$$10$$placeholder', 'Demo User')
	ON CONFLICT (email) DO NOTHING;
	SQL
	@echo "$(GREEN)✓ Demo user: demo@meridian.local$(NC)"

# ── Testing ───────────────────────────────────────────────────────────────────

test: test-unit test-integration test-e2e
	@echo "$(GREEN)All tests passed!$(NC)"

test-unit:
	@echo "$(BLUE)Running unit tests...$(NC)"
	cd api && mvn test
	cd ai-service && python3 -m pytest tests/ -v --ignore=tests/integration
	cd frontend && npm test

test-integration:
	@echo "$(BLUE)Running integration tests...$(NC)"
	cd api && mvn verify -Pintegration-tests
	cd ai-service && python3 -m pytest tests/integration -v

test-e2e:
	@echo "$(BLUE)Running E2E tests...$(NC)"
	cd frontend && npx playwright test

# ── Dev setup ─────────────────────────────────────────────────────────────────

dev-check:
	@echo "$(BLUE)Checking prerequisites...$(NC)"
	@command -v docker      >/dev/null 2>&1 || { echo "$(RED)✗ docker$(NC)";   exit 1; }
	@(docker compose version >/dev/null 2>&1) || { echo "$(RED)✗ docker compose$(NC)"; exit 1; }
	@command -v make        >/dev/null 2>&1 || { echo "$(RED)✗ make$(NC)";     exit 1; }
	@command -v openssl     >/dev/null 2>&1 || { echo "$(RED)✗ openssl$(NC)";  exit 1; }
	@command -v python3     >/dev/null 2>&1 || { echo "$(RED)✗ python3$(NC)";  exit 1; }
	@echo "$(GREEN)✓ All prerequisites satisfied$(NC)"

dev-keys:
	@[ -f .env ] || { echo "$(RED)✗ .env not found — run 'make dev-setup' first$(NC)"; exit 1; }
	@python3 scripts/inject-jwt.py
	@echo "$(GREEN)✓ JWT keys injected$(NC)"

dev-install-frontend:
	cd frontend && npm install
	cd frontend && npx playwright install --with-deps || \
		echo "$(YELLOW)  Playwright install skipped$(NC)"

dev-install-ai:
	cd ai-service && python3 -m pip install -r requirements.txt

dev-install: dev-install-frontend dev-install-ai
	@echo "$(GREEN)✓ All dependencies installed$(NC)"

dev-setup: dev-check
	@if [ ! -f .env ]; then \
		cp .env.example .env; \
		chmod 600 .env; \
		echo "$(GREEN)✓ .env created$(NC)"; \
	else \
		echo "$(YELLOW)  .env already exists$(NC)"; \
	fi
	@$(MAKE) --no-print-directory dev-keys
	@echo ""
	@echo "$(GREEN)Dev environment ready! Run: make up$(NC)"

# ── AWS / Terraform ───────────────────────────────────────────────────────────

tf-bootstrap: ## One-time: create S3 state bucket + DynamoDB lock table + write backend.hcl files
	@echo "$(BLUE)Bootstrapping Terraform state backend...$(NC)"
	@bash scripts/tf-bootstrap.sh $(AWS_REGION) $(TF_STATE_BUCKET)
	@echo "$(GREEN)✓ Bootstrap complete$(NC)"

aws-plan:
	@echo "$(BLUE)Terraform plan — $(TF_ENV)$(NC)"
	cd $(TF_DIR) && $(TF_INIT) && terraform plan $(TF_VARS)

aws-apply:
	@echo "$(BLUE)Terraform apply — $(TF_ENV)$(NC)"
	cd $(TF_DIR) && $(TF_INIT) && terraform apply $(TF_VARS)

aws-destroy:
	@echo "$(RED)╔══════════════════════════════════════════════════════════╗$(NC)"
	@echo "$(RED)║  WARNING: DESTROY all AWS resources for: $(TF_ENV)$(NC)"
	@echo "$(RED)╚══════════════════════════════════════════════════════════╝$(NC)"
	@read -p "Type the environment name to confirm ($(TF_ENV)): " confirm && \
		[ "$$confirm" = "$(TF_ENV)" ] || (echo "$(RED)Aborted.$(NC)" && exit 1)
	cd $(TF_DIR) && $(TF_INIT) && terraform destroy $(TF_VARS) -auto-approve
	@echo "$(GREEN)✓ AWS resources for $(TF_ENV) destroyed.$(NC)"

aws-destroy-env:
	@$(MAKE) aws-destroy TF_ENV=$(TF_ENV)

prod-deploy: ## Build + push images + Helm upgrade (requires REGISTRY, IMAGE_ORG, IMAGE_TAG, APP_DOMAIN)
	@[ -n "$(APP_DOMAIN)" ] || { echo "$(RED)✗ APP_DOMAIN is required: make prod-deploy APP_DOMAIN=yourdomain.com$(NC)"; exit 1; }
	@echo "$(BLUE)Building and pushing images (tag: $(IMAGE_TAG))...$(NC)"
	docker build -t $(REGISTRY)/$(IMAGE_ORG)/meridian-api:$(IMAGE_TAG) ./api
	docker build -t $(REGISTRY)/$(IMAGE_ORG)/meridian-ai:$(IMAGE_TAG) ./ai-service
	docker build -t $(REGISTRY)/$(IMAGE_ORG)/meridian-frontend:$(IMAGE_TAG) ./frontend
	docker push $(REGISTRY)/$(IMAGE_ORG)/meridian-api:$(IMAGE_TAG)
	docker push $(REGISTRY)/$(IMAGE_ORG)/meridian-ai:$(IMAGE_TAG)
	docker push $(REGISTRY)/$(IMAGE_ORG)/meridian-frontend:$(IMAGE_TAG)
	@echo "$(BLUE)Deploying via Helm...$(NC)"
	helm upgrade --install meridian ./helm \
		--namespace meridian --create-namespace \
		--values helm/values.yaml \
		--set global.domain=$(APP_DOMAIN) \
		--set meridian-api.image.tag=$(IMAGE_TAG) \
		--set meridian-ai.image.tag=$(IMAGE_TAG) \
		--set meridian-frontend.image.tag=$(IMAGE_TAG) \
		--wait --timeout 10m --atomic
	@echo "$(GREEN)✓ Deployed $(IMAGE_TAG) to $(APP_DOMAIN)$(NC)"

destroy-all: ## Clean destroy: Helm uninstall first, then terraform destroy
	@echo "$(RED)This will destroy ALL cloud resources for $(TF_ENV). Helm first, then Terraform.$(NC)"
	@read -p "Type the environment name to confirm ($(TF_ENV)): " confirm && \
		[ "$$confirm" = "$(TF_ENV)" ] || (echo "$(RED)Aborted.$(NC)" && exit 1)
	@echo "$(YELLOW)Step 1: Uninstalling Helm release (removes load balancers)...$(NC)"
	-helm uninstall meridian --namespace meridian --wait --timeout 5m
	-kubectl delete namespace meridian --ignore-not-found
	@echo "$(YELLOW)Step 2: Destroying Terraform infrastructure...$(NC)"
	cd $(TF_DIR) && $(TF_INIT) && terraform destroy $(TF_VARS) -auto-approve
	@echo "$(GREEN)✓ All resources for $(TF_ENV) destroyed.$(NC)"

.DEFAULT_GOAL := help
