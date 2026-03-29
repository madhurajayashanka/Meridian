# 03 — Data Flow: Research Job (End-to-End)

The most important flow in the system. Every step, every service, every state transition.

---

## Diagram: Full Research Job Lifecycle

```
User (Browser)          Spring Boot API          FastAPI AI Service         Redis
      │                       │                         │                     │
      │──POST /graphql────────▶                         │                     │
      │  createResearchJob     │                         │                     │
      │                        │ validate ownership      │                     │
      │                        │ check max 3 active jobs │                     │
      │                        │ save job (PENDING)      │                     │
      │                        │──POST /api/v1/jobs/start▶                    │
      │                        │  X-Service-Key header   │                     │
      │◀──{jobId: "uuid"}──────│                         │ save checkpoint     │
      │                        │◀──{status: "pending"}───│────XSET─────────────▶
      │                        │ update job → RUNNING    │                     │
      │                        │                         │                     │
      │──GET /ai/stream/{id}───────────────────────────▶│                     │
      │  ?token=<jwt>           │                         │ validate JWT        │
      │                         │                         │──XREAD loop─────────▶
      │◀──SSE: agent_update─────────────────────────────│◀────────────────────│
      │   {agent:"planner"      │                         │                     │
      │    status:"running"}    │                         │                     │
      │                         │                         │                     │
      │  [LangGraph executes]   │                         │                     │
      │                         │                         │ Planner node        │
      │◀──SSE: agent_update─────────────────────────────│ Research node       │
      │   {agent:"research"     │                         │ Analysis node       │
      │    status:"complete"}   │                         │ Critic node (×≤3)   │
      │                         │                         │ Synthesizer node    │
      │◀──SSE: agent_update─────────────────────────────│                     │
      │   {agent:"synthesizer"  │                         │                     │
      │    status:"complete"}   │                         │                     │
      │                         │                         │──POST /webhooks/────▶
      │                         │                         │  jobs/{id}/complete │
      │                         │◀────────────────────────│  X-Webhook-Signature│
      │                         │ create Report row       │                     │
      │                         │ update job → COMPLETE   │                     │
      │◀──SSE: job_complete─────────────────────────────│                     │
      │   {reportId: "uuid"}    │                         │                     │
      │                         │                         │                     │
      │──navigate to /report────▶                         │                     │
      │──GET report(id)─────────▶                         │                     │
      │◀──{title, content, ...}─│                         │                     │
```

---

## Diagram: Job Status State Machine

```
         ┌─────────┐
         │ PENDING │  (created, waiting for AI service)
         └────┬────┘
              │ AI service accepts job
              ▼
         ┌─────────┐
         │ RUNNING │  (LangGraph executing)
         └────┬────┘
         ┌────┴────┐
         │         │
         ▼         ▼
    ┌──────────┐  ┌────────┐
    │ COMPLETE │  │ FAILED │
    └──────────┘  └────────┘
         │
    (also possible from RUNNING)
         ▼
    ┌───────────┐
    │ CANCELLED │  (user cancels while RUNNING)
    └───────────┘
```

---

## Diagram: Quick vs Standard/Deep Depth

```
QUICK depth:
  Planner ──▶ Research ──▶ Synthesizer ──▶ COMPLETE
  (no analysis, no critic loop — fastest path)

STANDARD / DEEP depth:
  Planner ──▶ Research ──▶ Analysis ──▶ Critic
                                           │
                              score ≥ 7.0  │  score < 7.0 AND iterations < 3
                                    ▼      │         ▼
                              Synthesizer  └──▶ Analysis (retry)
                                    │
                                    ▼
                                COMPLETE
```

---

## Diagram: Webhook Callback (AI → Spring)

```
FastAPI                              Spring Boot
   │                                      │
   │  POST /api/webhooks/jobs/{id}/complete
   │  Headers:                            │
   │    Content-Type: application/json    │
   │    X-Webhook-Signature: sha256=...   │
   │  Body: {                             │
   │    reportId, title, content,         │
   │    wordCount, citationCount,         │
   │    criticScore, revisionCount        │
   │  }                                   │
   │─────────────────────────────────────▶│
   │                                      │ verify HMAC-SHA256
   │                                      │ check job not already terminal
   │                                      │ create Report row
   │                                      │ update job → COMPLETE
   │◀──── 200 OK ─────────────────────────│
```
