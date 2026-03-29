# Meridian — Architecture & Production Readiness

Last updated: 2026-03-29

---

## Architecture

```
Browser (Next.js)
    │ GraphQL + SSE
    ▼
Spring Boot API  ──REST──▶  FastAPI AI Service
    │                            │
    ▼                            ▼
PostgreSQL + pgvector        Redis Streams
```

### Services

| Service | Tech | Port | Responsibility |
|---|---|---|---|
| Frontend | Next.js 14 | 3000 | UI, auth, SSE client |
| API | Spring Boot 3 / GraphQL | 8000 | Business logic, auth, job orchestration |
| AI Service | FastAPI + LangGraph | 8080 | Agent pipeline, RAG, embeddings |
| Database | PostgreSQL + pgvector | 5432 | All app data + vector embeddings |
| Cache | Redis 7 | 6379 | Job checkpoints, rate limiting, SSE streams |

### Agent Pipeline

```
Planner → Research (Tavily + pgvector) → Analysis → Critic (≤3×) → Synthesizer
                                    ↑_______________|  (if score < 7.0)
```

- `quick` depth: Planner → Research → Synthesizer (no critic loop)
- `standard` / `deep`: full pipeline with critic revision loop

---

## Security

| Control | Implementation |
|---|---|
| Auth | JWT RS256, 15-min access tokens, 7-day refresh with rotation |
| Service-to-service | `X-Service-Key` header (INTERNAL_API_KEY) on internal AI endpoints |
| Webhook integrity | HMAC-SHA256 signature on all AI→API callbacks |
| SSE auth | JWT validated on every SSE connection |
| CORS | Env-driven (`CORS_ALLOWED_ORIGINS`), no wildcard in prod |
| Rate limiting | Redis sliding window — 60 req/min general, 5 jobs/hour |
| Prompt injection | Regex guardrails on all user input before LLM calls |
| PII / toxic output | Guardrails on all LLM output before returning to user |
| CSP | Full Content-Security-Policy + HSTS + security headers |
| Secrets | External Secrets Operator → AWS Secrets Manager in prod |
| Network | DB/Redis not exposed to host; app ports VPC-internal only |
| RDS | Encryption at rest (`storage_encrypted = true`) |
| Redis | Encryption at rest (KMS) + in-transit (TLS + auth token) |

---

## Infrastructure

### Terraform (AWS)

| Resource | File |
|---|---|
| VPC, subnets, NAT, SGs | `vpc.tf` |
| RDS PostgreSQL 15 + pgvector | `rds.tf` |
| ElastiCache Redis 7 | `redis.tf` |
| S3 (documents + reports) | `s3.tf` |
| EKS cluster + node group | `eks.tf` |
| IRSA for AI service pods | `eks.tf` |

State: S3 backend with DynamoDB locking. Isolated per environment:
- `infra/terraform/environments/dev/`
- `infra/terraform/environments/staging/`
- `infra/terraform/environments/prod/`

### Kubernetes (Helm)

- HPA on all three services
- External Secrets Operator integration (enabled via `externalSecrets.enabled=true`)
- Network policies restricting inter-pod traffic
- IRSA annotation on AI service account for Bedrock + S3 access
- Canary deploy with `--atomic` + smoke test + auto-rollback

### CI/CD (GitHub Actions)

- `ci.yml`: lint → test → build → container push → SAST (CodeQL) → Trivy → Gitleaks
- `deploy.yml`: Terraform plan/apply → Helm deploy → smoke test → rollback on failure

---

## Observability

| Signal | Implementation |
|---|---|
| Metrics | Prometheus (`/metrics`) — RED metrics, job counters, agent durations, RAG scores |
| Logs | Structured JSON, `LOG_LEVEL` env-driven, correlation ID on every request |
| Tracing | `X-Correlation-ID` propagated Spring → FastAPI → response headers |
| Health | `/health` checks DB pool + Redis ping; returns `degraded` if either fails |
| Retrieval | Top-k similarity scores logged per RAG query (hit/miss) |

---

## Environment Variables

See `.env.example` for the full list. Key production variables:

| Variable | Purpose |
|---|---|
| `POSTGRES_PASSWORD` | DB password |
| `JWT_SECRET_KEY` / `JWT_PUBLIC_KEY` | RSA keypair for JWT signing |
| `WEBHOOK_SECRET` | HMAC secret for AI→API callbacks |
| `INTERNAL_API_KEY` | Service-to-service auth |
| `CORS_ALLOWED_ORIGINS` | Comma-separated allowed origins |
| `LLM_PROVIDER` | `mock` / `openai` / `bedrock` |
| `TAVILY_API_KEY` | Web search (optional, falls back to mock) |

---

## Production Checklist

Before deploying to production:

- [ ] Generate `WEBHOOK_SECRET` and `INTERNAL_API_KEY` (`openssl rand -hex 32`)
- [ ] Set `CORS_ALLOWED_ORIGINS` to your actual frontend domain
- [ ] Set `eks_public_access_cidrs` in `prod/terraform.tfvars` to your VPN CIDR
- [ ] Store all secrets in AWS Secrets Manager, point ESO `secretPath` at them
- [ ] Set `GRAPHIQL_ENABLED=false` (default)
- [ ] Set `LLM_PROVIDER=bedrock` or `openai` with real credentials
- [ ] Run `make aws-apply TF_ENV=prod` to provision infrastructure
- [ ] Run `helm upgrade --install meridian ./helm` to deploy
- [ ] Verify `make health` passes against production endpoints
