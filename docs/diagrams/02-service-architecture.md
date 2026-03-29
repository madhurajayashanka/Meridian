# 02 — Service Architecture

Internal structure of each service, how they connect, and what protocol each uses.

---

## Diagram: Service Communication Map

```
                    ┌─────────────────────────────────────────────────────┐
                    │                  Docker Network                      │
                    │                                                      │
  Browser           │  ┌──────────────┐         ┌──────────────────────┐ │
  ──GraphQL/REST──▶ │  │  Spring Boot │──REST──▶ │    FastAPI           │ │
  ◀──JSON/SSE────   │  │  API :8000   │◀─webhook─│    AI Service :8080  │ │
                    │  │              │          │                      │ │
                    │  │  Controllers:│          │  Endpoints:          │ │
                    │  │  Auth        │          │  POST /jobs/start    │ │
                    │  │  Project     │          │  GET  /jobs/{id}     │ │
                    │  │  Job         │          │  POST /docs/process  │ │
                    │  │  Report      │          │  POST /chat/{id}     │ │
                    │  │  Document    │          │  GET  /stream/{id}   │ │
                    │  │  Chat        │          │  GET  /health        │ │
                    │  └──────┬───────┘          └──────────┬───────────┘ │
                    │         │                             │             │
                    │         │ JPA/SQL                     │ asyncpg     │
                    │         ▼                             │             │
                    │  ┌──────────────┐                     │             │
                    │  │  PostgreSQL  │◀────────────────────┘             │
                    │  │  + pgvector  │                                   │
                    │  │  :5432       │                                   │
                    │  └──────────────┘                                   │
                    │                                                      │
                    │  ┌──────────────┐                                   │
                    │  │    Redis     │◀── Spring (rate limit, JWT)       │
                    │  │    :6379     │◀── FastAPI (checkpoints, streams) │
                    │  └──────────────┘                                   │
                    └─────────────────────────────────────────────────────┘
```

---

## Diagram: Spring Boot Internal Layers

```
HTTP Request
     │
     ▼
┌─────────────────────────────────────────────────────┐
│  Filters (ordered)                                   │
│  1. CorrelationIdFilter  (X-Correlation-ID)          │
│  2. RateLimitFilter      (Redis sliding window)      │
│  3. JwtAuthenticationFilter (RS256 validation)       │
└──────────────────────────┬──────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────┐
│  Controllers (GraphQL)                               │
│  AuthGraphQLController    ProjectGraphQLController   │
│  ResearchJobGraphQLController                        │
│  ReportGraphQLController  ChatGraphQLController      │
│  DocumentGraphQLController FeatureGateGraphQLController│
│                                                      │
│  Controllers (REST)                                  │
│  DocumentUploadController  FastAPIWebhookController  │
└──────────────────────────┬──────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────┐
│  Services                                            │
│  AuthService   ProjectService   ResearchJobService   │
│  DocumentService  ChatService   AuditService         │
│  ReportContentStorageService                         │
└──────────────────────────┬──────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────┐
│  Repositories (Spring Data JPA)                      │
│  UserRepository  ProjectRepository  JobRepository    │
│  ReportRepository  DocumentRepository                │
│  ChatMessageRepository  EmbeddingRepository          │
└──────────────────────────┬──────────────────────────┘
                           │
                           ▼
                    PostgreSQL :5432
```

---

## Diagram: FastAPI Internal Layers

```
HTTP Request
     │
     ▼
┌─────────────────────────────────────────────────────┐
│  Middleware                                          │
│  1. http_metrics_middleware  (Prometheus counters)   │
│  2. correlation_id_middleware (X-Correlation-ID)     │
└──────────────────────────┬──────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────┐
│  Dependencies                                        │
│  _require_service_key()  (X-Service-Key header)      │
│  validate_jwt_token()    (RS256 JWT)                 │
└──────────────────────────┬──────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────┐
│  Route Handlers                                      │
│  start_job()       → execute_job() [background task] │
│  stream_events()   → Redis XREAD loop                │
│  process_document()→ extract → chunk → embed         │
│  chat_with_report()→ RAGChatService                  │
│  get_job_status()  → Redis checkpoint                │
└──────────────────────────┬──────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────┐
│  Core Modules                                        │
│  app/graph/workflow.py   (LangGraph state machine)   │
│  app/agents/nodes.py     (5 agent functions)         │
│  app/llm/provider.py     (Mock/OpenAI/Bedrock)       │
│  app/rag/store.py        (EmbeddingStore, EventPub)  │
│  app/api/chat.py         (RAGChatService)            │
│  app/guardrails.py       (injection/PII/toxic check) │
│  app/metrics.py          (Prometheus metrics)        │
└──────────────────────────┬──────────────────────────┘
                           │
              ┌────────────┴────────────┐
              ▼                         ▼
       PostgreSQL                    Redis
       (asyncpg pool)                (redis-py)
```

---

## Diagram: Next.js Internal Structure

```
src/
├── app/                     (Next.js App Router pages)
│   ├── (auth)/
│   │   ├── login/page.tsx   → LoginPage
│   │   └── register/page.tsx→ RegisterPage
│   ├── dashboard/page.tsx   → DashboardPage (project list)
│   ├── projects/[id]/
│   │   ├── page.tsx         → ProjectPage (jobs list)
│   │   ├── new/page.tsx     → ResearchFormPage
│   │   └── research/page.tsx→ ResearchPage (alt form)
│   ├── jobs/[id]/
│   │   ├── live/page.tsx    → LiveMonitorPage (SSE)
│   │   └── report/page.tsx  → ReportPage (markdown + chat)
│   └── layout.tsx           → RootLayout (ErrorBoundary)
│
├── hooks/
│   ├── useAuth.ts           → Zustand auth store + token refresh
│   ├── useApiClient.ts      → Authenticated fetch wrapper
│   └── useSSE.ts            → EventSource with reconnect
│
└── components/
    ├── Navigation.tsx        → Top nav bar
    ├── AgentCard.tsx         → Agent status visualization
    ├── ChatPanel.tsx         → RAG chat UI
    ├── DocumentUpload.tsx    → Drag-drop file upload
    ├── ErrorBoundary.tsx     → React error boundary
    ├── ExportButton.tsx      → Report export
    └── ShareButton.tsx       → Report sharing
```
