# Meridian Sequence Diagrams: All Core Scenarios

Last updated: 2026-03-29

This guide visualizes the major runtime flows in Meridian across Frontend, API, AI service, Redis, and PostgreSQL.

## 1. User Authentication (GraphQL)

```mermaid
sequenceDiagram
    autonumber
    participant U as User
    participant FE as Frontend (Next.js)
    participant API as Spring Boot API
    participant DB as PostgreSQL

    U->>FE: Submit login/register form
    FE->>API: POST /graphql (Login/Register mutation)
    API->>DB: Validate/Create user and token records
    DB-->>API: User + auth data
    API-->>FE: accessToken + refreshToken + user
    FE-->>U: Authenticated UI state
```

## 2. Token Refresh Flow (GraphQL)

```mermaid
sequenceDiagram
    autonumber
    participant FE as Frontend useApiClient
    participant API as Spring Boot API
    participant DB as PostgreSQL

    FE->>FE: Detect expired/near-expiry access token
    FE->>API: POST /graphql (refreshToken mutation)
    API->>DB: Validate refresh token
    DB-->>API: Valid token + user
    API-->>FE: New accessToken + refreshToken
    FE->>FE: Retry original request
```

## 3. Project and Dashboard Data (GraphQL)

```mermaid
sequenceDiagram
    autonumber
    participant FE as Frontend
    participant API as Spring Boot GraphQL
    participant DB as PostgreSQL

    FE->>API: POST /graphql (projects/jobs/reports queries)
    API->>DB: Query user-scoped data
    DB-->>API: Rows
    API-->>FE: GraphQL response
```

## 4. Document Upload (REST) + Metadata Read (GraphQL)

```mermaid
sequenceDiagram
    autonumber
    participant U as User
    participant FE as Frontend
    participant API as Spring Boot REST
    participant DB as PostgreSQL
    participant AI as FastAPI AI Service

    U->>FE: Upload PDF/TXT
    FE->>API: POST /api/documents/upload (multipart/form-data)
    API->>DB: Create/update document record
    API-->>FE: Upload response (id/status)

    Note over API,AI: Optional/Service-managed processing path
    API->>AI: POST /api/v1/documents/process (service key)
    AI->>DB: Store chunk embeddings
    AI-->>API: Processing result (chunk_count/status)

    FE->>API: POST /graphql (documents query)
    API->>DB: Read document metadata/chunkCount
    API-->>FE: GraphQL data
```

## 5. Research Job Submission and Orchestration

```mermaid
sequenceDiagram
    autonumber
    participant U as User
    participant FE as Frontend
    participant API as Spring Boot GraphQL
    participant DB as PostgreSQL
    participant AI as FastAPI AI Service
    participant RG as LangGraph Workflow
    participant R as Redis

    U->>FE: Start research
    FE->>API: POST /graphql (createResearchJob mutation)
    API->>DB: Create job (PENDING)
    API->>AI: POST /api/v1/jobs/start (REST + service key)
    AI->>R: Save initial checkpoint
    AI->>RG: Execute Planner -> Research -> Analysis -> Critic -> Synthesizer
    RG-->>AI: Final state
    AI->>R: Save final checkpoint + stream events
    AI-->>API: Callback webhook complete/failed
    API->>DB: Update job/report state
    API-->>FE: Job appears as RUNNING/COMPLETE in GraphQL reads
```

## 6. Live Progress Streaming (SSE Direct FE -> AI)

```mermaid
sequenceDiagram
    autonumber
    participant FE as Frontend useSSE
    participant AI as FastAPI AI Service
    participant R as Redis Streams

    FE->>AI: GET /ai/stream/{jobId}?token=JWT
    AI->>AI: Validate JWT
    AI->>R: XREAD job:{jobId}:events
    R-->>AI: agent_update/job_complete/job_failed events
    AI-->>FE: text/event-stream messages
    FE->>FE: Update live timeline UI
```

## 7. AI -> API Completion/Failure Webhooks

```mermaid
sequenceDiagram
    autonumber
    participant AI as FastAPI AI Service
    participant API as Spring Boot Webhook Controller
    participant DB as PostgreSQL

    AI->>API: POST /api/webhooks/jobs/{jobId}/complete
    API->>DB: Set job COMPLETE + create/update report
    DB-->>API: Persisted
    API-->>AI: 200 OK

    AI->>API: POST /api/webhooks/jobs/{jobId}/failed
    API->>DB: Set job FAILED + error message
    DB-->>API: Persisted
    API-->>AI: 200 OK
```

## 8. Job Status Polling (REST API Boundary)

```mermaid
sequenceDiagram
    autonumber
    participant API as Spring Boot
    participant AI as FastAPI
    participant R as Redis

    API->>AI: GET /api/v1/jobs/{jobId}
    AI->>R: Read langgraph checkpoint
    R-->>AI: Current state
    AI-->>API: status + agent_logs + error
```

## 9. Chat via GraphQL Proxy (Primary Product Path)

```mermaid
sequenceDiagram
    autonumber
    participant U as User
    participant FE as Frontend
    participant API as Spring Boot GraphQL
    participant DB as PostgreSQL
    participant AI as FastAPI

    U->>FE: Ask report question
    FE->>API: POST /graphql (sendChatMessage mutation)
    API->>DB: Save user message
    API->>AI: POST /api/v1/chat/{reportId}?token=JWT&user_id&message
    AI->>DB: RAG retrieval + message persistence path
    AI-->>API: Final assistant text
    API->>DB: Save assistant message
    API-->>FE: GraphQL response with assistant message
```

## 10. Direct Frontend -> AI Chat (Possible Architecture Path)

```mermaid
sequenceDiagram
    autonumber
    participant FE as Frontend
    participant AI as FastAPI
    participant DB as PostgreSQL

    FE->>AI: POST /api/v1/chat/{reportId}?token=JWT&user_id&message
    AI->>DB: Retrieve relevant chunks + write chat
    DB-->>AI: Context + persistence result
    AI-->>FE: JSON response { response, report_id }
```

Note:

- This path is technically available, but the main product flow currently uses GraphQL mutation proxy through API for consistent authorization and data ownership checks.

## 11. Error/Fallback Scenario: AI Job Failure

```mermaid
sequenceDiagram
    autonumber
    participant AI as FastAPI + LangGraph
    participant R as Redis
    participant API as Spring Boot Webhook
    participant FE as Frontend SSE + GraphQL

    AI->>AI: Exception or failed state
    AI->>R: Publish job_failed event
    AI->>API: POST /api/webhooks/jobs/{jobId}/failed
    API-->>AI: Ack
    R-->>FE: SSE event job_failed
    FE->>API: GraphQL query for job status refresh
    API-->>FE: FAILED + errorMessage
```

## 12. Communication Pattern Summary

1. Frontend <-> API: Mostly GraphQL, plus REST for file upload.
2. Frontend <-> AI: Direct SSE for live updates, optional direct chat endpoint exists.
3. API <-> AI: REST for job submission/status and report-completion webhooks.
4. AI <-> Redis: Streams for eventing and checkpoints.
5. API/AI <-> PostgreSQL: Primary persistence and retrieval.

## 13. Practical Guidance

1. Keep job creation routed through API -> AI (current design) for stronger governance and consistent authz.
2. Keep SSE direct FE -> AI for low-latency live updates.
3. Keep multipart upload as REST; GraphQL remains ideal for typed business entities.
4. If enabling direct FE -> AI chat broadly, enforce the same authorization and audit guarantees as API proxy path.
