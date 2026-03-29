# 07 — REST API Endpoints

All REST endpoints across Spring Boot and FastAPI with exact request/response shapes.

---

## Spring Boot REST (port 8000)

### POST /api/documents/upload

```
Auth:    Bearer <accessToken>
Content: multipart/form-data

Form fields:
  projectId: UUID (string)
  file:      binary (PDF or TXT, max 10MB)

Response 200:
{
  "id": "uuid",
  "originalFilename": "paper.pdf",
  "status": "READY",          ← or "FAILED" if AI processing failed
  "chunkCount": 42,
  "fileType": "pdf",
  "fileSizeBytes": 1048576
}

Response 403: project not owned by user
Response 422: unsupported file type or file too large
```

### POST /api/webhooks/jobs/{jobId}/complete  (internal — AI service only)

```
Auth:    X-Webhook-Signature: sha256=<hmac>
Content: application/json

Body:
{
  "reportId": "uuid",
  "title": "Research: Quantum Computing Advances",
  "content": "# Report...",
  "wordCount": 1200,
  "citationCount": 8,
  "criticScore": 8.5,
  "revisionCount": 1,
  "timestamp": 1711700000000
}

Response 200: { "success": true, "message": "Job completed" }
Response 401: invalid HMAC signature
```

### POST /api/webhooks/jobs/{jobId}/failed  (internal — AI service only)

```
Body: { "error": "Token budget exceeded", "timestamp": 1711700000000 }
Response 200: { "success": true, "message": "Failure recorded" }
```

### GET /actuator/health

```
No auth required
Response 200: { "status": "UP" }
```

---

## FastAPI REST (port 8080)

### GET /health

```
No auth required

Response 200:
{
  "status": "healthy",          ← or "degraded"
  "service": "meridian-ai",
  "llm_provider": "bedrock",
  "checks": {
    "db": "ok",
    "redis": "ok"
  },
  "timestamp": "2026-03-29T10:00:00"
}
```

### GET /metrics

```
No auth required
Response: Prometheus text format
  ai_requests_total{method,endpoint,status_code}
  ai_jobs_started_total
  ai_jobs_completed_total
  ai_jobs_failed_total{reason}
  ai_request_duration_seconds{method,endpoint}
  ai_job_duration_seconds
  ai_agent_duration_seconds{agent}
  ai_rag_retrieval_score
```

### POST /api/v1/jobs/start  (internal — Spring Boot only)

```
Auth:    X-Service-Key: <INTERNAL_API_KEY>
         X-Correlation-ID: <uuid>

Query params:
  job_id:         UUID (optional, generated if absent)
  query:          string (10-500 chars, required)
  project_id:     UUID (required)
  user_id:        UUID (required)
  llm_provider:   string (bedrock|openai|mock, default bedrock)
  research_depth: string (quick|standard|deep, default standard)
  document_ids:   [UUID] (optional, repeated param)

Response 200:
{
  "job_id": "uuid",
  "status": "pending",
  "message": "Research job started"
}
```

### GET /api/v1/jobs/{job_id}  (internal)

```
Auth: X-Service-Key

Response 200:
{
  "job_id": "uuid",
  "status": "complete",
  "query": "What is quantum computing?",
  "agent_logs": [...],
  "error": null,
  "completed_at": "2026-03-29T10:05:00"
}

Response 404: job not found in Redis
```

### POST /api/v1/documents/process  (internal — Spring Boot only)

```
Auth:    X-Service-Key
Content: multipart/form-data

Form fields:
  document_id: UUID string
  file_type:   "pdf" or "txt"
  file:        binary

Response 200:
{
  "document_id": "uuid",
  "chunk_count": 42,
  "status": "ready"
}

Response 422: no text extracted or no chunks produced
Response 503: database not ready
```

### GET /ai/stream/{job_id}  (browser-facing SSE)

```
Auth: ?token=<accessToken>  (JWT validated)

Response: text/event-stream

Events:
  event: agent_update
  data: {"agent":"planner","status":"running","progress":"0","partial_output":""}

  event: agent_update
  data: {"agent":"planner","status":"complete","progress":"100","partial_output":"..."}

  event: job_complete
  data: {"job_id":"uuid","report_id":"uuid","status":"complete"}

  event: job_failed
  data: {"job_id":"uuid","error":"Token budget exceeded"}

  event: error
  data: {"error":"Redis connection lost"}
```

### POST /api/v1/chat/{report_id}  (internal — Spring Boot only)

```
Auth: ?token=<accessToken>  (JWT validated)
      X-Service-Key (if configured)

Query params:
  user_id: UUID
  message: string (guardrail-checked before LLM)

Response 200:
{
  "response": "Based on the report, quantum computing uses...",
  "report_id": "uuid"
}

Response 400: message blocked by guardrail
Response 503: database not ready
```
