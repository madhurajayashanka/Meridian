# 14 — Deployment & CI/CD

How code goes from commit to production.

---

## Diagram: CI Pipeline (ci.yml)

```
git push / PR
    │
    ▼
┌─────────────────────────────────────────────────────────────┐
│  Parallel jobs:                                              │
│                                                              │
│  backend (Spring Boot)          ai-service (FastAPI)         │
│  ├── Checkstyle lint            ├── Pylint lint              │
│  ├── Unit tests (JUnit)         ├── Black format check       │
│  ├── Integration tests          ├── isort check              │
│  ├── JaCoCo coverage ≥80%       ├── Property-based tests     │
│  └── Build JAR                  ├── Unit tests               │
│                                 └── Coverage ≥75%            │
│                                                              │
│  frontend (Next.js)             secrets-scan                 │
│  ├── ESLint                     └── Gitleaks (full history)  │
│  ├── TypeScript check                                        │
│  ├── Vitest unit tests          security-scan                │
│  ├── Coverage ≥70%              └── Trivy (fs scan → SARIF)  │
│  └── Next.js build                                           │
│                                 codeql                       │
│                                 └── Java + Python + JS SAST  │
└─────────────────────────────────────────────────────────────┘
    │
    │ (only on push to main/develop, after all jobs pass)
    ▼
build-containers (matrix: api, ai, frontend)
    ├── Docker Buildx
    ├── Push to ghcr.io
    └── Tags: branch, sha, semver, latest (main only)
```

---

## Diagram: Deploy Pipeline (deploy.yml)

```
push to main  OR  workflow_dispatch(environment)
    │
    ▼
terraform job
    ├── terraform init
    ├── terraform validate + fmt check
    ├── terraform plan → tfplan artifact
    ├── (PR) → comment plan summary on PR
    └── (main push) → terraform apply -auto-approve
    │
    ▼ (main push only)
helm-deploy job
    ├── aws eks update-kubeconfig
    ├── kubectl create namespace meridian
    ├── helm install cert-manager
    ├── helm install nginx-ingress
    ├── helm upgrade --install meridian ./helm
    │     --set *.image.tag=${{ github.sha }}
    │     --atomic                    ← auto-rollback on failure
    │     --wait --timeout 10m
    ├── kubectl rollout status (all 3 deployments)
    ├── smoke test: poll /actuator/health until UP (10 attempts)
    └── rollback on smoke failure: helm rollback meridian
    │
    ▼
e2e-tests job
    └── Playwright tests against production URL
    │
    ▼
notify-deployment
    ├── Slack: ✅ success or ❌ failure
    └── Link to workflow run
```

---

## Diagram: Helm Chart Structure

```
helm/
├── Chart.yaml              (umbrella chart)
├── values.yaml             (global + externalSecrets config)
├── templates/
│   └── cluster-secret-store.yaml  (ESO ClusterSecretStore)
│
├── meridian-api/
│   ├── Chart.yaml
│   ├── values.yaml         (image, replicas, resources, HPA)
│   └── templates/
│       ├── deployment.yaml
│       ├── service.yaml
│       ├── ingress.yaml
│       ├── hpa.yaml        (CPU 70%, Memory 80%, 2-8 replicas)
│       ├── configmap.yaml
│       ├── secret.yaml
│       ├── serviceaccount.yaml
│       └── external-secret.yaml  (ESO → Secrets Manager)
│
├── meridian-ai/            (same structure + IRSA annotation)
└── meridian-frontend/      (same structure)
```

---

## Diagram: Environment Promotion

```
Developer
    │
    ├── feature branch → PR → CI runs
    │                         (lint, test, build, scan)
    │
    ├── merge to develop → CI + build containers (develop tag)
    │
    └── merge to main → CI + build containers (sha + latest tags)
                            │
                            ▼
                        deploy.yml triggers
                            │
                            ├── Terraform apply (prod state)
                            └── Helm deploy to EKS
                                    │
                                    ├── smoke test passes → done
                                    └── smoke test fails → auto-rollback
                                                           Slack alert
```

---

## Diagram: Local Development vs Production

```
                    Local (Docker Compose)    Production (EKS)
                    ──────────────────────    ────────────────
Frontend            localhost:3000            https://meridian.example.com
API                 localhost:8000            https://api.meridian.example.com
AI Service          localhost:8080            internal (ClusterIP only)
PostgreSQL          localhost:5432 (exposed)  RDS (private subnet, not exposed)
Redis               localhost:6379 (exposed)  ElastiCache (private subnet)
Secrets             .env file                 AWS Secrets Manager via ESO
LLM Provider        mock (no API key needed)  bedrock or openai
Embeddings          mock vectors              real 1536-dim vectors
Tavily              mock responses            real web search
TLS                 none                      cert-manager + Let's Encrypt
```
