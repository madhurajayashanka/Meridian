# Meridian Architecture and Production Readiness Guide

Last updated: 2026-03-29

## 1. Executive Summary

This document consolidates:

- What is implemented in the current Meridian codebase
- Which architecture claims are fully supported vs partially supported
- A production readiness framework across frontend, backend, AI, microservices, communication, and cloud
- Practical next steps to move from development-grade to production-grade

Current platform architecture is strong in:

- Polyglot microservices separation (Next.js frontend, Spring Boot API, FastAPI AI service)
- Multi-agent orchestration with LangGraph
- CI/CD workflows, containerization, and Helm-based deployment manifests
- Data layer design (PostgreSQL with pgvector schema, Redis, S3)

Current platform has notable partial areas:

- RAG runtime integration has interface mismatches in chat path
- Embedding generation for reports is placeholder in parts of AI service
- AI guardrails are mostly prompt/instruction-level, not policy-engine-level
- Terraform includes rich AWS resources, but EKS resources are not currently defined in Terraform files

---

## 2. Validation of Your Architecture Statements

### Statement A

Orchestrated a 5-agent autonomous team (Planner, Researcher, Analyst, Critic, Synthesizer) using LangGraph on a polyglot microservices architecture to generate fully cited research reports.

Status: Mostly implemented (high confidence)

Evidence:

- 5 agents in AI workflow nodes:
  - ai-service/app/agents/nodes.py
- LangGraph orchestration and critic loop:
  - ai-service/app/graph/workflow.py
- Polyglot services:
  - frontend (Next.js)
  - api (Spring Boot)
  - ai-service (FastAPI)
- Citation handling and report metadata:
  - ai-service/main.py
  - ai-service/app/api/reports.py

Nuance:

- The system strongly intends cited output and includes citation counting, but "fully cited" quality depends on model behavior and enforcement checks, which are currently light.

Suggested resume-safe wording:

- Orchestrated a 5-agent LangGraph workflow (Planner, Researcher, Analyst, Critic, Synthesizer) in a polyglot microservices platform to generate structured research reports with inline citation support.

### Statement B

Engineered real-time streaming via SSE and Redis Pub/Sub backed by a RAG pipeline using pgvector and AWS Bedrock (Claude 3.5).

Status: Partially implemented

Evidence:

- SSE endpoint and streaming:
  - ai-service/main.py
  - frontend/src/hooks/useSSE.ts
- Redis event transport:
  - ai-service/app/rag/store.py (XADD)
  - ai-service/main.py (XREAD)
- RAG chat service and pgvector search SQL:
  - ai-service/app/api/chat.py
- Bedrock provider support:
  - ai-service/app/llm/provider.py

Nuance:

- Redis is implemented with Streams (XADD/XREAD), not Pub/Sub channels.
- RAG path has interface inconsistencies (chat service calls methods not in provider interface).
- Parts of embedding flow still use placeholders in report embedding path.

Suggested resume-safe wording:

- Built real-time research progress streaming over SSE with Redis Streams, with pgvector-backed retrieval workflows and Bedrock/OpenAI model-provider support.

### Statement C

Provisioned AWS EKS infrastructure with Terraform and Helm, including horizontal pod autoscaling, CI/CD via GitHub Actions, and multi-provider LLM support.

Status: Mixed (some fully implemented, some partial)

Evidence:

- Terraform foundations (VPC, RDS, Redis, S3, IAM/Secrets):
  - infra/terraform/vpc.tf
  - infra/terraform/rds.tf
  - infra/terraform/redis.tf
  - infra/terraform/s3.tf
- Helm charts and HPA templates:
  - helm/meridian-ai/templates/hpa.yaml
  - helm/meridian-api/templates/hpa.yaml
  - helm/meridian-frontend/templates/hpa.yaml
- CI/CD via GitHub Actions:
  - .github/workflows/ci.yml
  - .github/workflows/deploy.yml
- Multi-provider LLM support:
  - ai-service/app/llm/provider.py
  - api/src/main/resources/graphql/schema.graphqls

Nuance:

- Deploy workflow uses an existing EKS cluster and runs aws eks update-kubeconfig.
- Terraform files include EKS-related variables but no aws_eks_cluster or node group resources were found.

Suggested resume-safe wording:

- Provisioned AWS data and networking foundations with Terraform and deployed services with Helm to EKS; implemented HPA, GitHub Actions CI/CD, and multi-provider LLM routing.

### Statement D

Stack: Next.js, Spring Boot, FastAPI, LangGraph, PostgreSQL, Redis, EKS, Bedrock, S3, RDS, Terraform.

Status: Accurate overall

Nuance:

- EKS is used by deployment pipeline and Helm target, but cluster provisioning itself is not currently in Terraform code.

---

## 3. Current Architecture (As Built)

### 3.1 Frontend Layer

- Next.js app for project, jobs, research submission, and live updates
- GraphQL and REST interactions through custom hooks
- SSE client for live agent status

Key files:

- frontend/src/app/projects/[id]/research/page.tsx
- frontend/src/hooks/useApiClient.ts
- frontend/src/hooks/useSSE.ts

### 3.2 API Layer (Spring Boot)

- GraphQL schema and resolvers for projects/jobs/documents/reports/auth
- Job submission orchestration to AI service
- Ownership and validation checks
- Webhook receiver for AI completion/failure

Key files:

- api/src/main/resources/graphql/schema.graphqls
- api/src/main/java/com/meridian/job/service/ResearchJobService.java
- api/src/main/java/com/meridian/integration/webhook/FastAPIWebhookController.java

### 3.3 AI Layer (FastAPI + LangGraph)

- Research workflow graph with Planner -> Research -> Analysis -> Critic -> Synthesizer
- Configurable LLM providers (Mock, OpenAI, Bedrock)
- Redis checkpointing and event publishing
- Background job execution and callback to API service

Key files:

- ai-service/app/graph/workflow.py
- ai-service/app/agents/nodes.py
- ai-service/app/llm/provider.py
- ai-service/main.py

### 3.4 Data Layer

- PostgreSQL schema with pgvector embeddings table and HNSW index
- Redis for job state and stream events
- S3 for report/document content storage

Key files:

- api/src/main/resources/db/migration/V1\_\_init.sql
- ai-service/app/rag/store.py
- ai-service/app/api/reports.py

### 3.5 Infrastructure and Delivery

- Terraform for VPC/RDS/Redis/S3 and security/secrets components
- Helm charts for all three runtime services
- GitHub Actions for CI and deployment automation

Key files:

- infra/terraform/\*.tf
- helm/\*\*
- .github/workflows/ci.yml
- .github/workflows/deploy.yml

---

## 4. Production Readiness Matrix

Legend:

- Must have: baseline for serious production operation
- Should have: strongly recommended for reliability/compliance/scale
- Better to have: advanced maturity and optimization

## 4.1 Frontend (Next.js)

Must have:

- Strict input validation and sanitization for all user-entered content
- Robust auth session lifecycle (refresh, revoke, timeout, device logout)
- Centralized error boundary and user-safe fallback UI
- CSP, security headers, and trusted types policy where relevant
- Accessibility baseline (keyboard nav, focus states, contrast, ARIA)
- RUM/performance budgets (LCP, CLS, INP) and regression gates in CI

Should have:

- Feature flags for controlled rollout
- Frontend observability (trace IDs propagated from backend)
- Offline/retry behavior for critical user actions
- Request deduplication and stale-while-revalidate strategy

Better to have:

- Edge caching strategy with route-level policy
- Canary frontend releases by audience segment
- Session replay for high-severity incident debugging (privacy-preserving)

## 4.2 Backend API (Spring Boot)

Must have:

- Full authz model and tenant isolation checks on every read/write path
- Idempotency keys for mutating endpoints and webhook handlers
- API schema/versioning strategy with backward compatibility policy
- Rate limiting at gateway + service layer
- Strong input validation and output contract validation
- DB migration safety checks and rollback plan

Should have:

- Outbox pattern for reliable event publication
- Circuit breakers, retries with jitter, and bulkheads for downstream calls
- Distributed tracing and correlation IDs across all requests
- Fine-grained audit logging for sensitive actions

Better to have:

- Active-active or warm-standby failover model
- Formal SLOs tied to alerting and release gates

## 4.3 AI and LLM Layer (FastAPI, LangGraph)

Must have:

- Prompt versioning and prompt registry (versioned templates + changelog)
- Model version pinning and compatibility matrix
- Structured output validation with schema guardrails
- Prompt injection and jailbreak defense layer
- Retrieval quality checks (recall@k, grounding checks, citation verification)
- Cost, latency, and token budget enforcement per request/job
- Deterministic fallback behavior when LLM response parsing fails

Should have:

- A/B evaluation harness for prompts and model versions
- Offline golden set regression tests for quality and safety
- Hallucination classifier or groundedness verifier
- Guardrail policy engine (PII, toxicity, unsafe instructions, legal policy)
- Human-in-the-loop review workflow for critical reports

Better to have:

- Multi-model routing by task with automatic failover
- Adaptive retrieval depth by query complexity and confidence
- Semantic cache with invalidation policy

## 4.4 RAG and Data Retrieval

Must have:

- Consistent embedding pipeline (no placeholders in prod path)
- Chunking strategy benchmarks per document type
- Embedding/index migration playbooks for model changes
- Retrieval observability (top-k scores, source attribution, hit/miss)
- Data freshness and re-indexing jobs

Should have:

- Hybrid retrieval (semantic + lexical)
- Reranking layer for higher precision
- Source trust scoring and provenance metadata

Better to have:

- Multi-vector retrieval for long documents
- Query rewriting and decomposition for difficult questions

## 4.5 Microservices Architecture

Must have:

- Clear service ownership boundaries and API contracts
- mTLS or service-to-service auth for internal traffic
- Contract tests between frontend, API, and AI services
- Backward-compatible schema evolution policy
- Timeout budgets and retry policies per service call

Should have:

- Service mesh for policy, observability, and traffic control
- Async workflow/event orchestration where eventual consistency is acceptable
- Shared platform libraries for logging/metrics/tracing standards

Better to have:

- Cell-based or domain-isolated deployments for blast-radius control
- Multi-region service topology

## 4.6 Communication and Eventing

Must have:

- Exactly-defined event schemas and versioning
- Idempotent consumers and replay-safe processing
- Dead-letter strategy and replay tooling
- SSE/WebSocket auth hardening and token validation

Should have:

- Event ordering guarantees where required
- Backpressure and flow-control strategy

Better to have:

- Unified event catalog and lineage tooling
- Operational dashboards for stream lag, drops, and retries

## 4.7 Cloud and Platform (AWS, K8s, Terraform, Helm)

Must have:

- Terraform state security, locking, and environment isolation
- Secrets management with rotation and short-lived credentials
- Network segmentation, least-privilege IAM, and deny-by-default SG/NACL
- Cluster autoscaling and workload HPA with tuned requests/limits
- Backup and DR runbooks with tested restore drills
- Policy as code (OPA/Kyverno) for Kubernetes security guardrails

Should have:

- IRSA for pod-level AWS permissions
- Cost controls (budgets, anomaly detection, chargeback labels)
- Blue/green or canary deploys with automatic rollback
- Image signing and admission control verification

Better to have:

- Multi-account landing zone with centralized governance
- Multi-region DR with RPO/RTO objectives validated

## 4.8 Security and Compliance

Must have:

- Threat modeling and abuse-case modeling for AI endpoints
- SAST/DAST/Dependency scanning in CI
- Secrets scanning in commits and container images
- Encryption in transit and at rest everywhere
- RBAC/ABAC + least privilege across app and infra

Should have:

- Periodic penetration tests and remediation SLAs
- Centralized SIEM and incident response playbooks
- Data retention and deletion policy enforcement

Better to have:

- Continuous compliance checks mapped to SOC2/ISO27001 controls
- Formal red-team exercises for prompt injection and data exfiltration

## 4.9 Observability and Reliability

Must have:

- Unified logs with correlation IDs
- RED metrics (rate, errors, duration) and saturation metrics
- Distributed tracing across user request -> API -> AI -> DB/cache
- Error budget policy and alerting by SLO, not only CPU/memory

Should have:

- Synthetic checks for core user journeys
- Runbooks attached to alerts
- Automated incident timeline capture

Better to have:

- Auto-remediation for known failure patterns
- Chaos testing for critical dependencies

---

## 5. Prompt Versioning and AI Lifecycle Practices

Prompt versioning is one of the highest-impact improvements for this project.

Recommended approach:

1. Store prompts as versioned artifacts (for each agent node)
2. Include prompt version in every job state and agent log
3. Pin model + prompt version per environment
4. Add evaluation suite before promoting prompt versions
5. Support rollback to prior prompt version instantly

Minimum metadata to track per run:

- prompt_version
- model_provider
- model_id
- retrieval_config_version
- citation_policy_version
- guardrail_policy_version

Files where this can be integrated quickly:

- ai-service/app/agents/nodes.py
- ai-service/app/graph/state.py
- ai-service/main.py
- api/src/main/java/com/meridian/job/service/ResearchJobService.java

---

## 6. Priority Action Plan (Practical)

Priority 0 (immediate correctness):

1. Fix AI chat provider interface mismatch in RAG chat service
2. Replace placeholder embedding writes with real embedding calls
3. Enforce real JWT validation on SSE endpoint

Priority 1 (production baseline):

1. Implement prompt versioning and run metadata tracking
2. Add AI guardrails for injection, toxic output, and PII leakage
3. Add contract tests across API <-> AI service boundaries
4. Add idempotency keys and replay safety for webhook and job-completion flows

Priority 2 (scale and resilience):

1. Add EKS provisioning module in Terraform or document external ownership clearly
2. Add distributed tracing and SLO-based alerting
3. Add canary releases and auto-rollback in deployment pipeline

---

## 7. Resume and Portfolio Positioning Advice

When describing this project publicly, use language that distinguishes implemented vs in-progress capabilities.

Good pattern:

- Built X, implemented Y, and designed Z (where Z is roadmap/partially implemented)

Example:

- Built a LangGraph-based 5-agent research system in a polyglot microservices stack, with SSE live updates via Redis Streams, pgvector-backed retrieval workflows, and multi-provider model routing (Bedrock/OpenAI/Mock). Deployed services to Kubernetes via Helm with GitHub Actions CI/CD and Terraform-managed AWS data infrastructure.

---

## 8. File Evidence Index

Core workflow and agents:

- ai-service/app/graph/workflow.py
- ai-service/app/agents/nodes.py
- ai-service/app/graph/state.py

LLM provider abstraction:

- ai-service/app/llm/provider.py
- ai-service/app/config.py

RAG and embeddings:

- ai-service/app/api/chat.py
- ai-service/app/api/reports.py
- api/src/main/resources/db/migration/V1\_\_init.sql

Streaming and events:

- ai-service/main.py
- ai-service/app/rag/store.py
- frontend/src/hooks/useSSE.ts

API integration and webhooks:

- api/src/main/java/com/meridian/job/client/FastApiClient.java
- api/src/main/java/com/meridian/integration/webhook/FastAPIWebhookController.java
- api/src/main/java/com/meridian/job/service/ResearchJobService.java

Security and auth:

- api/src/main/java/com/meridian/config/SecurityConfig.java
- api/src/main/resources/graphql/schema.graphqls

Infrastructure and delivery:

- infra/terraform/main.tf
- infra/terraform/vpc.tf
- infra/terraform/rds.tf
- infra/terraform/redis.tf
- infra/terraform/s3.tf
- helm/values.yaml
- helm/meridian-ai/templates/hpa.yaml
- .github/workflows/ci.yml
- .github/workflows/deploy.yml
