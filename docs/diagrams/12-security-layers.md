# 12 — Security Layers

Every security control, where it lives, and what it protects against.

---

## Diagram: Security Controls by Layer

```
┌─────────────────────────────────────────────────────────────────┐
│  BROWSER LAYER                                                   │
│  • CSP: connect-src scoped to API/AI URLs only                  │
│  • X-Frame-Options: DENY (clickjacking)                         │
│  • X-Content-Type-Options: nosniff                              │
│  • Strict-Transport-Security: max-age=63072000                  │
│  • Referrer-Policy: strict-origin-when-cross-origin             │
│  • Permissions-Policy: camera=(), microphone=(), geolocation=() │
│  • ErrorBoundary: no raw error details exposed to user          │
│  • Prod build fails if NEXT_PUBLIC_API_URL not set              │
└─────────────────────────────────────────────────────────────────┘
                              │ HTTPS
┌─────────────────────────────────────────────────────────────────┐
│  SPRING BOOT API LAYER                                           │
│  • JWT RS256 validation on every request (JwtAuthFilter)        │
│  • CORS: env-driven origins only, no wildcard                   │
│  • Rate limiting: 60 req/min general, 5 jobs/hour (Redis)       │
│  • Max 3 concurrent active jobs per user                        │
│  • Ownership checks on every read/write (userId isolation)      │
│  • Idempotent webhooks (terminal state guard)                   │
│  • HMAC-SHA256 verification on webhook callbacks                │
│  • Audit logging: async write to audit_logs table               │
│  • GraphiQL disabled by default (GRAPHIQL_ENABLED=false)        │
│  • Actuator: health only (no metrics/info exposed publicly)     │
│  • File upload: max 10MB, PDF/TXT only                          │
│  • Correlation ID: X-Correlation-ID on every request            │
└─────────────────────────────────────────────────────────────────┘
                              │ REST (Docker network)
┌─────────────────────────────────────────────────────────────────┐
│  FASTAPI AI LAYER                                                │
│  • X-Service-Key: internal API key on /jobs/start, /docs/process│
│  • JWT validation on SSE endpoint (/ai/stream)                  │
│  • Guardrails: prompt injection check on all user input         │
│  • Guardrails: PII + toxic output check on all LLM output       │
│  • Token budget: hard cap per job (20k/60k/120k tokens)         │
│  • Tenacity retry: 3 attempts, exp backoff on LLM calls         │
│  • CORS: env-driven, no wildcard                                │
│  • Structured JSON logging with LOG_LEVEL env                   │
└─────────────────────────────────────────────────────────────────┘
                              │ asyncpg / redis-py
┌─────────────────────────────────────────────────────────────────┐
│  DATA LAYER                                                      │
│  • PostgreSQL: not exposed to host (Docker internal only)       │
│  • Redis: not exposed to host (Docker internal only)            │
│  • RDS: storage_encrypted=true, not publicly accessible         │
│  • Redis (AWS): KMS encryption at rest, TLS in transit          │
│  • Secrets: External Secrets Operator → AWS Secrets Manager     │
│  • Soft deletes: deleted_at timestamp (GDPR compliance)         │
└─────────────────────────────────────────────────────────────────┘
                              │ VPC
┌─────────────────────────────────────────────────────────────────┐
│  INFRASTRUCTURE LAYER                                            │
│  • VPC: public subnets (ALB only), private subnets (all else)   │
│  • SG: app ports 3000/8000-8080 restricted to VPC CIDR          │
│  • SG: only 80/443 open to 0.0.0.0/0                           │
│  • EKS: public_access_cidrs restricted to VPN CIDR in prod      │
│  • IRSA: AI service pods get Bedrock+S3 only (no node-level IAM)│
│  • Terraform state: S3 encrypted + DynamoDB locking             │
│  • CI: Gitleaks (secrets scan) + Trivy (vuln scan) + CodeQL     │
└─────────────────────────────────────────────────────────────────┘
```

---

## Diagram: Guardrail Decision Tree

```
User input (query or chat message)
    │
    ▼
check_input(text)
    │
    ├── matches injection pattern?
    │   (ignore previous instructions, jailbreak, DAN mode, etc.)
    │   YES → GuardrailViolation → 400 / job failed
    │
    └── PASS → proceed to LLM

LLM output (final_report or chat response)
    │
    ▼
check_output(text)
    │
    ├── matches toxic pattern?
    │   (how to make bomb/weapon, harm instructions)
    │   YES → GuardrailViolation → blocked
    │
    ├── matches PII pattern?
    │   (SSN, credit card, email, phone, credential leak)
    │   YES → GuardrailViolation → blocked
    │
    └── PASS → return to user
```

---

## Diagram: Rate Limiting

```
Every request (except health + auth endpoints)
    │
    ▼
RateLimitFilter
    │
    ├── Redis key: "rate:general:{userId}"
    │   Window: 60 seconds
    │   Limit: 60 requests
    │   Exceeded → 429 + Retry-After header
    │
    └── For /graphql createResearchJob:
        Redis key: "rate:jobs:{userId}"
        Window: 3600 seconds (1 hour)
        Limit: 5 job submissions
        Exceeded → 429 + Retry-After header

Fail-open: if Redis is down, allow request (logged as warning)
```
