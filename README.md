# Meridian - Setup, Run, Test, Deploy

Meridian is a 3-service research platform:

- Frontend (Next.js) on port 3000
- API (Spring Boot GraphQL) on port 8000
- AI Service (FastAPI) on port 8080
- Supporting services: PostgreSQL + Redis

This guide is written as a practical, step-by-step runbook.

## 1) What You Need

### Minimum (for local run)

- Docker Desktop
- Docker Compose
- Make
- OpenSSL
- Python 3.11+

### Optional (for local development and tests outside Docker)

- Node.js 18+
- Java 21+

## 2) First-Time Setup (Local)

From the project root:

```bash
make dev-setup
```

What this does:

1. Validates prerequisites for Docker run
2. Creates `.env` from `.env.example` if missing
3. Generates a fresh JWT RSA keypair
4. Injects keys into `.env` automatically

Notes:

- Default LLM provider is `mock`, so API keys are not required for first run.
- You can later edit `.env` for OpenAI/Bedrock/Tavily settings.

## 3) Start the Platform

```bash
make up
```

Then verify services:

```bash
make health
```

Open:

- Frontend: http://localhost:3000
- GraphQL endpoint: http://localhost:8000/graphql
- API health: http://localhost:8000/actuator/health
- AI health: http://localhost:8080/health

## 4) Daily Usage Commands

```bash
make status         # container status
make logs           # all logs
make logs-api       # API logs
make logs-ai        # AI service logs
make restart        # restart all services
make down           # stop all services
```

## 5) Run Tests

### A) Install local test dependencies (one-time)

```bash
make dev-install
```

### B) Execute tests

```bash
make test-unit
make test-integration
make test-e2e
# or all
make test
```

## 6) Recommended Learning Path (Non-Developer Friendly)

1. Run `make dev-setup`
2. Run `make up`
3. Run `make health`
4. Open frontend at http://localhost:3000
5. Trigger one research flow in UI
6. Watch service logs with `make logs`
7. Stop with `make down`

## 7) Deployment Options

## Option A: Docker Compose on Any VM (Simplest)

Use this when you want a practical, quick deployment.

1. Provision a Linux VM (Ubuntu is fine)
2. Install Docker + Docker Compose + Make + OpenSSL + Python 3.11+
3. Clone repo and enter root
4. Run:

```bash
make dev-setup
make up
make health
```

5. Open firewall/security-group ports:

- 3000 (frontend)
- 8000 (API)
- 8080 (AI)

This is the easiest path for practice environments.

## Option B: Kubernetes + Helm (Production-Style)

Helm charts are in `helm/` and support:

- `meridian-api`
- `meridian-ai`
- `meridian-frontend`
- Umbrella chart: `helm/Chart.yaml`

### Important Scope Note

Current Terraform in `infra/terraform/` provisions foundational AWS resources (VPC, RDS, Redis, S3, security components), but does not currently define an EKS cluster resource in Terraform. You need an existing Kubernetes cluster (EKS or other) before Helm deploy.

### Kubernetes Deployment Steps

1. Ensure your cluster is ready and kubectl context is set
2. Build and push Docker images for api, ai-service, frontend
3. Update image repository/tags in Helm values
4. Deploy:

```bash
cd helm
helm dependency build
helm upgrade --install meridian . -n meridian --create-namespace
```

5. Verify:

```bash
kubectl get pods -n meridian
kubectl get svc -n meridian
```

## Option C: AWS Data Layer with Terraform + App Layer with Helm

Use this when practicing cloud infra plus app deployment.

1. Configure AWS credentials
2. Prepare Terraform backend (S3 + DynamoDB lock)
3. In `infra/terraform/`:

```bash
cp terraform.tfvars.example terraform.tfvars
terraform init
terraform plan
terraform apply
```

4. Capture Terraform outputs (DB/cache/storage endpoints)
5. Inject those values into Helm values or Kubernetes secrets
6. Deploy app with Helm to your existing cluster

See `infra/terraform/README.md` for infrastructure details.

## 8) Troubleshooting

### `make dev-setup` fails

- Run `make dev-check` to see missing prerequisites.

### Frontend dependency install issues

- Use local cache path automatically via Makefile target.
- Retry:

```bash
make dev-install-frontend
```

### Services not healthy right after `make up`

- Wait 30-90 seconds, then rerun:

```bash
make health
```

### API or AI service keeps restarting

- Inspect logs:

```bash
make logs-api
make logs-ai
```

### Need to reset local environment

```bash
make clean
```

## 9) Project Structure

```text
meridian/
├── frontend/          # Next.js UI
├── api/               # Spring Boot GraphQL API
├── ai-service/        # FastAPI + agent workflow
├── helm/              # Kubernetes Helm charts
├── infra/terraform/   # AWS infrastructure definitions
├── docker-compose.yml # Local full-stack runtime
├── .env.example       # Environment template
└── Makefile           # Setup/run/test automation
```

## 10) Quick Command Reference

```bash
make dev-setup      # one-time setup (env + JWT)
make up             # start all services
make health         # readiness check
make logs           # watch logs
make test           # run all tests
make down           # stop
make clean          # full reset (containers/volumes)
```

---

If you want, the next refinement can be a one-command smoke test target that creates sample data, hits health endpoints, and validates GraphQL in one pass.
