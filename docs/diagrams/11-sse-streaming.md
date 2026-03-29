# 11 — SSE Streaming

How real-time agent updates flow from LangGraph to the browser.

---

## Diagram: SSE Full Flow

```
Browser              FastAPI              Redis              LangGraph
   │                    │                   │                    │
   │─GET /ai/stream/────▶                   │                    │
   │  {jobId}?token=jwt  │ validate JWT      │                    │
   │                     │                   │                    │
   │◀──text/event-stream─│                   │                    │
   │   (connection open) │                   │                    │
   │                     │                   │                    │
   │                     │  [background]     │                    │
   │                     │                   │                    │
   │                     │                   │◀──planner starts───│
   │                     │                   │  XADD job:{id}:events
   │                     │                   │  {agent:"planner",  │
   │                     │                   │   status:"running"} │
   │                     │                   │                    │
   │                     │──XREAD loop───────▶                    │
   │                     │◀──[messages]───────│                    │
   │◀──event: agent_update                   │                    │
   │   data: {agent:"planner",status:"running"}                   │
   │                     │                   │                    │
   │                     │                   │◀──planner done─────│
   │                     │                   │  XADD {status:"complete"}
   │◀──event: agent_update                   │                    │
   │   data: {agent:"planner",status:"complete"}                  │
   │                     │                   │                    │
   │   [research, analysis, critic, synthesizer events...]        │
   │                     │                   │                    │
   │                     │                   │◀──job complete─────│
   │                     │                   │  XADD {type:"job_complete",
   │                     │                   │        report_id:"uuid"}
   │◀──event: job_complete                   │                    │
   │   data: {job_id, report_id}             │                    │
   │                     │                   │                    │
   │ navigate to /report │                   │                    │
   │ (connection closes) │                   │                    │
```

---

## Diagram: SSE Event Types

```
event: agent_update
data: {
  "agent": "planner" | "research" | "analysis" | "critic" | "synthesizer",
  "status": "running" | "complete" | "failed",
  "progress": "0" | "100",
  "partial_output": "...",
  "timestamp": "2026-03-29T10:00:00"
}

event: job_complete
data: {
  "type": "job_complete",
  "job_id": "uuid",
  "report_id": "uuid",
  "status": "complete"
}

event: job_failed
data: {
  "type": "job_failed",
  "job_id": "uuid",
  "error": "Token budget exceeded at analysis agent",
  "status": "failed"
}

event: error
data: { "error": "Redis connection lost" }
```

---

## Diagram: Redis Stream Structure

```
Key:   job:{job_id}:events
Type:  Redis Stream (XADD / XREAD)
TTL:   24 hours

Entry format:
  {
    type:           string
    agent:          string
    status:         string
    progress:       string
    partial_output: string
    timestamp:      ISO string
  }

SSE reader:
  last_id = "0"  (start from beginning on connect)
  loop:
    messages = XREAD {key: last_id} BLOCK 1000ms COUNT 10
    for each message:
      last_id = message.id
      yield SSE event
    check checkpoint for terminal status
    sleep 100ms
```

---

## Diagram: Frontend SSE Reconnect

```
useSSE(jobId, accessToken)
    │
    ▼
new EventSource(url)
    │
    ├── onmessage → dispatch to handlers
    ├── onerror   → schedule reconnect
    │               delay = min(1000 * 2^attempt, 30000)
    │               attempt++
    │               if attempt > 5 → give up
    └── onopen    → reset attempt counter

Event handlers:
  "agent_update" → update AgentCard status
  "job_complete" → set jobComplete=true → navigate to /report
  "job_failed"   → show error state
```
