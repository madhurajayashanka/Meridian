# Design Document: Meridian Autonomous Multi-Agent Research Platform

## Overview

Meridian is a polyglot microservices platform that orchestrates five specialised AI agents in a stateful LangGraph workflow to produce comprehensive, cited research reports from a user-submitted query. The system streams real-time agent activity to the browser via Server-Sent Events, supports RAG over user-uploaded documents and completed reports, and is LLM-provider agnostic (AWS Bedrock / OpenAI).

The design covers three deployable services:
- **Frontend** — Next.js (TypeScript) App Router SPA
- **API Service** — Spring Boot (Java 21) GraphQL + REST
- **AI Service** — FastAPI (Python 3.11) with LangGraph agent orchestration

Key design goals:
- Fault-tolerant agent execution with Redis checkpointing and retry logic
- Real-time transparency via SSE streaming of every agent state transition
- LLM-provider abstraction that requires zero code changes to switch providers
- Strict resource ownership enforcement at every data access layer

---

## Architecture

### System Topology

```
┌─────────────────────────────────────────────────────────────┐
│                        Internet                              │
└──────────────────────┬──────────────────────────────────────┘
                       │
              ┌────────▼────────┐
              │  AWS CloudFront  │  (Static assets CDN)
              └────────┬────────┘
                       │
              ┌────────▼────────┐
              │  K8s Ingress    │  (nginx, TLS termination)
              └────┬────────┬───┘
                   │        │
        ┌──────────▼──┐  ┌──▼──────────────┐
        │  Next.js    │  │  Spring Boot API │  :8080
        │  Frontend   │  │  (GraphQL + JWT) │
        └─────────────┘  └──────┬───────────┘
                                 │ Internal REST (no auth)
                         ┌───────▼───────────┐
                         │   FastAPI AI Svc  │  :8000
                         │   (LangGraph)     │
                         └──┬────────────────┘
                            │
              ┌─────────────┼──────────────────┐
              │             │                  │
     ┌────────▼──┐  ┌───────▼────┐  ┌─────────▼──────┐
     │PostgreSQL │  │   Redis    │  │  AWS Bedrock   │
     │+ pgvector │  │(State/Pub) │  │  / OpenAI API  │
     └─────┬─────┘  └────────────┘  └────────────────┘
           │
     ┌─────▼──────┐
     │   AWS S3   │
     │(Docs/Rpts) │
     └────────────┘
```

### Communication Patterns

| From | To | Protocol | Purpose |
|---|---|---|---|
| Browser | Spring Boot | GraphQL over HTTPS | CRUD, auth |
| Browser | FastAPI | SSE over HTTPS | Real-time agent streaming |
| Spring Boot | FastAPI | Internal REST | Trigger/cancel research jobs |
| FastAPI | PostgreSQL | SQL (asyncpg) | Read/write all data |
| FastAPI | Redis | Pub/Sub + Streams | Agent event broadcasting, checkpointing |
| FastAPI | AWS Bedrock | boto3 | LLM inference + embeddings |
| FastAPI | OpenAI | openai SDK | LLM inference + embeddings (dev/alt) |
| FastAPI | Tavily | HTTP REST | Web search |
| FastAPI | S3 | boto3 | Document and report storage |

### Research Job Data Flow

```
1. Browser → GraphQL mutation createResearchJob → Spring Boot
2. Spring Boot → INSERT research_jobs (status=PENDING) → PostgreSQL
3. Spring Boot → POST /api/v1/jobs/start → FastAPI
4. FastAPI → SET langgraph:checkpoint:{job_id} → Redis
5. FastAPI → LangGraph.invoke() (async background task)

6. Planner node executes
   → PUBLISH job:{id}:events {agent:planner, status:running}
   → PUBLISH job:{id}:events {agent:planner, status:complete, sub_questions:[...]}

7. Research node executes (parallel per sub-question)
   → Tavily API (web search, top 5 results per sub-question)
   → pgvector similarity search (top 3 doc chunks per sub-question)
   → PUBLISH events

8. Analysis node executes
   → LLM inference with combined research context
   → PUBLISH events

9. Critic node executes
   → LLM evaluates draft, returns score 1.0–10.0
   → score < 7.0 AND iterations < 3 → route back to Analysis
   → PUBLISH critique event

10. Synthesizer node executes
    → LLM generates final Markdown report
    → S3 PUT report.md
    → UPDATE research_jobs status=COMPLETE, reports INSERT
    → PUBLISH {agent:synthesizer, status:complete}

11. SSE stream → browser (all events throughout steps 6–10)
12. Browser receives job_complete event → GraphQL query for report
```

---

## Components and Interfaces

### Frontend (Next.js)

**State Management:** Zustand for local UI state; Apollo Client cache for server state.

**Key pages and routing:**

| Route | Component | Auth |
|---|---|---|
| `/` | LandingPage | No |
| `/login` | LoginPage | No |
| `/register` | RegisterPage | No |
| `/dashboard` | DashboardPage | Yes |
| `/projects/[id]` | ProjectPage | Yes |
| `/projects/[id]/new` | NewResearchPage | Yes |
| `/jobs/[id]/live` | LiveJobPage | Yes |
| `/reports/[id]` | ReportPage | Yes |
| `/settings` | SettingsPage | Yes |

**SSE Integration (LiveJobPage):**
```typescript
// EventSource connects to FastAPI directly with JWT in query param
const source = new EventSource(`${AI_URL}/ai/stream/${jobId}?token=${accessToken}`)
source.addEventListener('agent_update', (e) => updateAgentCard(JSON.parse(e.data)))
source.addEventListener('job_complete', (e) => navigateToReport(JSON.parse(e.data).report_id))
source.addEventListener('job_failed', (e) => showError(JSON.parse(e.data).error))
```

**AgentCard states:** IDLE (grey) → RUNNING (blue, pulsing) → COMPLETE (green) → FAILED (red)

### Spring Boot API Service

**Package structure:**
```
com.meridian/
├── auth/          # JWT, registration, login, token refresh
├── project/       # Project CRUD, ownership checks
├── job/           # ResearchJob creation, cancellation, status
├── report/        # Report retrieval, export (PDF/Markdown)
├── document/      # Document metadata, deletion
├── chat/          # Chat message persistence, history
└── config/        # Security, GraphQL, CORS, rate limiting
```

**GraphQL resolvers** map 1:1 to the schema types. All resolvers annotated with `@PreAuthorize` to enforce ownership. The `createResearchJob` mutation validates query length, content policy, and document ownership before calling FastAPI.

**Internal REST client** (RestTemplate/WebClient) calls FastAPI at `AI_SERVICE_URL` for job start and cancel. No auth header — internal network only.

**Rate limiting:** Redis-backed sliding window counter. 60 req/min general, 5 jobs/hour for research job submissions. Returns 429 with `Retry-After` header on breach.

### FastAPI AI Service

**Module structure:**
```
app/
├── agents/        # planner, research, analysis, critic, synthesizer nodes
├── graph/         # LangGraph workflow definition, conditional edges
├── llm/           # LLMProvider ABC, BedrockProvider, OpenAIProvider
├── rag/           # chunking, embedding, pgvector retrieval
├── streaming/     # SSE endpoint, Redis pub/sub publisher
└── api/           # FastAPI routes (/jobs, /chat, /documents, /health)
```

**LLM Provider Abstraction:**
```python
class LLMProvider(ABC):
    @abstractmethod
    def get_chat_model(self, temperature: float = 0.3): ...
    @abstractmethod
    def get_embedding_model(self): ...

class BedrockProvider(LLMProvider):
    def get_chat_model(self, temperature=0.3):
        return ChatBedrock(model_id="anthropic.claude-3-5-sonnet-20241022-v2:0",
                           model_kwargs={"temperature": temperature, "max_tokens": 4096})
    def get_embedding_model(self):
        return BedrockEmbeddings(model_id="amazon.titan-embed-text-v2:0")

class OpenAIProvider(LLMProvider):
    def get_chat_model(self, temperature=0.3):
        return ChatOpenAI(model="gpt-4o", temperature=temperature)
    def get_embedding_model(self):
        return OpenAIEmbeddings(model="text-embedding-3-small")
```

**LangGraph Workflow:**
```python
def build_research_graph(redis_client) -> CompiledGraph:
    graph = StateGraph(ResearchState)
    graph.add_node("planner", planner_node)
    graph.add_node("research", research_node)
    graph.add_node("analysis", analysis_node)
    graph.add_node("critic", critic_node)
    graph.add_node("synthesizer", synthesizer_node)
    graph.set_entry_point("planner")
    graph.add_edge("planner", "research")
    graph.add_edge("research", "analysis")
    graph.add_edge("analysis", "critic")
    graph.add_conditional_edges("critic", should_revise,
        {"analysis": "analysis", "synthesizer": "synthesizer"})
    graph.add_edge("synthesizer", END)
    checkpointer = RedisSaver(redis_client)
    return graph.compile(checkpointer=checkpointer)
```

**SSE Streaming:** FastAPI `StreamingResponse` reads from Redis Stream (`XREAD BLOCK`) and yields SSE-formatted events. JWT validated on connection. Reconnection replays from last seen event ID.

---

## Data Models

### PostgreSQL Schema (key tables)

```sql
-- Users
CREATE TABLE users (
    id            UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    email         VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,  -- bcrypt cost 12
    name          VARCHAR(100) NOT NULL,
    avatar_url    TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at    TIMESTAMPTZ  -- soft delete
);

-- Projects
CREATE TABLE projects (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name        VARCHAR(100) NOT NULL,
    description TEXT,
    is_archived BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Research Jobs
CREATE TABLE research_jobs (
    id             UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    project_id     UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    user_id        UUID NOT NULL REFERENCES users(id),
    query          TEXT NOT NULL,
    llm_provider   VARCHAR(20) NOT NULL DEFAULT 'bedrock'
                   CHECK (llm_provider IN ('bedrock', 'openai')),
    research_depth VARCHAR(20) NOT NULL DEFAULT 'standard'
                   CHECK (research_depth IN ('quick', 'standard', 'deep')),
    status         VARCHAR(20) NOT NULL DEFAULT 'pending'
                   CHECK (status IN ('pending', 'running', 'complete', 'failed', 'cancelled')),
    document_ids   UUID[] DEFAULT '{}',
    error_message  TEXT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    started_at     TIMESTAMPTZ,
    completed_at   TIMESTAMPTZ
);

-- Reports
CREATE TABLE reports (
    id               UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    job_id           UUID UNIQUE NOT NULL REFERENCES research_jobs(id) ON DELETE CASCADE,
    title            VARCHAR(500) NOT NULL,
    s3_key           TEXT NOT NULL,
    markdown_content TEXT,
    citation_count   INTEGER NOT NULL DEFAULT 0,
    word_count       INTEGER NOT NULL DEFAULT 0,
    critic_score     DECIMAL(3,1),
    revision_count   INTEGER NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Embeddings (documents + report chunks)
CREATE TABLE embeddings (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    source_type VARCHAR(20) NOT NULL CHECK (source_type IN ('document', 'report')),
    source_id   UUID NOT NULL,
    project_id  UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    chunk_index INTEGER NOT NULL,
    chunk_text  TEXT NOT NULL,
    embedding   vector(1536),  -- 1536 OpenAI / 1024 Titan (padded)
    metadata    JSONB,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- HNSW index for fast approximate cosine similarity search
CREATE INDEX idx_embeddings_vector ON embeddings
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);
```

### LangGraph State (ResearchState TypedDict)

```python
class ResearchState(TypedDict):
    job_id: str
    query: str
    llm_provider: str           # "bedrock" | "openai"
    research_depth: str         # "quick" | "standard" | "deep"
    uploaded_doc_ids: List[str]
    sub_questions: List[str]
    report_structure: dict
    research_results: List[dict]
    analysis_draft: str
    analysis_iteration: int
    critic_score: float         # 1.0 – 10.0
    critic_feedback: str
    critic_iterations: int      # max 3
    final_report: str           # Markdown
    citations: List[dict]
    agent_logs: Annotated[List[dict], add_messages]
    status: str                 # "running" | "complete" | "failed"
    error: Optional[str]
```

### Redis Data Structures

```
langgraph:checkpoint:{job_id}   String (JSON ResearchState)   TTL: 48h
job:{job_id}:events             Redis Stream (XADD/XREAD)     TTL: 24h
job:{job_id}:subscribers        Set (SSE connection IDs)      TTL: 24h
cache:llm:{prompt_hash}         String (JSON LLM response)    TTL: 1h
ratelimit:{user_id}:{window}    Counter (INCR)                TTL: 60s
```

### GraphQL Schema (key types)

```graphql
type ResearchJob {
  id: ID!
  query: String!
  status: JobStatus!
  llmProvider: LLMProvider!
  researchDepth: ResearchDepth!
  createdAt: DateTime!
  completedAt: DateTime
  report: Report
  project: Project!
}

type Report {
  id: ID!
  title: String!
  markdownContent: String!
  s3Url: String!
  citationCount: Int!
  wordCount: Int!
  createdAt: DateTime!
  chatMessages: [ChatMessage!]!
}

enum JobStatus { PENDING RUNNING COMPLETE FAILED CANCELLED }
enum LLMProvider { BEDROCK OPENAI }
enum ResearchDepth { QUICK STANDARD DEEP }
```

### SSE Event Schema

```json
// agent_update event
{
  "agent": "planner",
  "status": "running",
  "progress": 20,
  "timestamp": "2026-01-01T12:00:00Z",
  "partial_output": null
}

// job_complete event
{ "job_id": "uuid", "report_id": "uuid", "status": "complete" }

// job_failed event
{ "job_id": "uuid", "error": "LLM rate limit exceeded", "status": "failed" }
```

---


## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system — essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property 1: Registration round-trip

*For any* valid registration input (unique email, password, name), registering should create a user account and return a valid JWT access token and refresh token, and the user should be retrievable from the system.

**Validates: Requirements 1.1**

---

### Property 2: Login returns valid tokens with correct expiry

*For any* registered user, logging in with correct credentials should return a JWT access token with a 15-minute expiry and a refresh token with a 7-day expiry.

**Validates: Requirements 1.3**

---

### Property 3: Invalid credentials are rejected

*For any* login attempt with a password that does not match the stored hash for the given email, the system should return a 401 Unauthorized response.

**Validates: Requirements 1.4**

---

### Property 4: Token refresh round-trip

*For any* valid, unexpired refresh token, calling the refresh endpoint should return a new valid access token and a new refresh token, and the old refresh token should no longer be accepted.

**Validates: Requirements 1.5**

---

### Property 5: Token security invariants

*For any* registered user, the stored password hash should begin with the bcrypt prefix `$2b$12$` (cost factor 12), and *for any* issued JWT token, the header's `alg` field should be `RS256`.

**Validates: Requirements 1.7, 1.8**

---

### Property 6: Profile update round-trip

*For any* authenticated user and valid profile update (name or avatar URL), submitting the update should persist the changes and the returned user object should reflect the new values.

**Validates: Requirements 2.1**

---

### Property 7: Password change invalidates refresh tokens

*For any* authenticated user who changes their password with the correct current password, all previously issued refresh tokens for that user should be rejected after the change.

**Validates: Requirements 2.2**

---

### Property 8: Account deletion cascades

*For any* user who deletes their account, querying for any of their associated projects, jobs, reports, documents, embeddings, or chat messages should return no results.

**Validates: Requirements 2.4**

---

### Property 9: Project creation round-trip

*For any* authenticated user and valid project name (1–100 characters), creating a project should return a project object owned by that user, and the project should be retrievable by that user.

**Validates: Requirements 3.1**

---

### Property 10: Project list isolation

*For any* two distinct users each with their own projects, the project list returned for user A should contain only projects owned by user A, and contain none of user B's projects.

**Validates: Requirements 3.2**

---

### Property 11: Project update round-trip

*For any* authenticated user and a project they own, submitting a valid rename or description update should persist the changes and the returned project object should reflect the new values.

**Validates: Requirements 3.3**

---

### Property 12: Project deletion cascades

*For any* project that is deleted, all associated research jobs, reports, documents, and embeddings should no longer be retrievable.

**Validates: Requirements 3.4**

---

### Property 13: Cross-user project access denied

*For any* authenticated user and any project not owned by that user, any attempt to read or modify that project should return a 403 Forbidden response.

**Validates: Requirements 3.5**

---

### Property 14: Project object includes job count and last activity

*For any* project returned by the API, the response object should include a non-negative integer job count and a last activity timestamp (or null if no jobs exist).

**Validates: Requirements 3.6**

---

### Property 15: Research job creation round-trip

*For any* authenticated user, valid query (10–500 characters), and owned project ID, creating a research job should return a job object with status PENDING and the job should be retrievable.

**Validates: Requirements 4.1**

---

### Property 16: Query length validation

*For any* query string with length less than 10 or greater than 500 characters, submitting a research job should return a 422 Unprocessable Entity response.

**Validates: Requirements 4.2**

---

### Property 17: Cross-user job creation denied

*For any* authenticated user and any project not owned by that user, attempting to create a research job for that project should return a 403 Forbidden response.

**Validates: Requirements 4.3**

---

### Property 18: Research job default values

*For any* research job created without specifying an LLM provider, the created job should have `llmProvider = BEDROCK`; and for any job created without specifying research depth, the created job should have `researchDepth = STANDARD`.

**Validates: Requirements 4.4, 4.5**

---

### Property 19: Document count limit per job

*For any* research job submission that includes more than 5 document IDs, the request should be rejected with a 422 error.

**Validates: Requirements 4.6**

---

### Property 20: Job cancellation

*For any* running research job owned by the authenticated user, requesting cancellation should result in the job status transitioning to CANCELLED.

**Validates: Requirements 4.8**

---

### Property 21: Workflow execution order

*For any* standard-depth research job, the agent_logs in the final ResearchState should record agent executions in the order: planner → research → analysis → critic → synthesizer.

**Validates: Requirements 5.1**

---

### Property 22: Planner sub-question count invariant

*For any* research query submitted to the Planner Agent, the output `sub_questions` list should contain between 3 and 5 entries (inclusive).

**Validates: Requirements 5.2**

---

### Property 23: Research results structural invariant

*For any* set of sub-questions produced by the Planner, the Research Agent's output should contain exactly one result entry per sub-question, each with up to 5 web results; and for any job with associated uploaded documents, each result entry should also contain up to 3 document chunks from pgvector.

**Validates: Requirements 5.3, 5.4**

---

### Property 24: Analysis draft contains citations

*For any* non-empty research results passed to the Analysis Agent, the produced `analysis_draft` should be a non-empty string containing at least one inline citation marker matching the pattern `[N]`.

**Validates: Requirements 5.5**

---

### Property 25: Critic score range invariant

*For any* analysis draft evaluated by the Critic Agent, the returned `critic_score` should be a float in the range [1.0, 10.0] and `critic_feedback` should be a non-empty string.

**Validates: Requirements 5.6**

---

### Property 26: Critic routing logic

*For any* ResearchState where `critic_score < 7.0` and `critic_iterations < 3`, the conditional edge should route to the analysis node; and for any state where `critic_score >= 7.0` OR `critic_iterations >= 3`, the conditional edge should route to the synthesizer node.

**Validates: Requirements 5.7, 5.8**

---

### Property 27: Final report structural completeness

*For any* completed research job, the `final_report` Markdown string should contain all required sections: a title (H1), executive summary, at least one main findings section, a conflicting viewpoints section, key takeaways, recommended next steps, and a numbered citations list.

**Validates: Requirements 5.9**

---

### Property 28: LangGraph checkpoint round-trip

*For any* ResearchState after an agent transition, the state should be persisted to Redis, and loading the checkpoint for that job ID should produce a state object with field values identical to the persisted state.

**Validates: Requirements 5.10, 14.4**

---

### Property 29: QUICK depth workflow truncation

*For any* research job with `research_depth = QUICK`, the agent_logs should contain entries only for the planner and research agents, and no entries for analysis, critic, or synthesizer.

**Validates: Requirements 5.12**

---

### Property 30: DEEP depth iteration cap

*For any* research job with `research_depth = DEEP`, the `critic_iterations` field in the final ResearchState should be at most 3.

**Validates: Requirements 5.13**

---

### Property 31: SSE event structural invariant

*For any* SSE event emitted by the AI Service, the event data should be a valid JSON object containing the fields: `agent` (string), `status` (one of running/complete/failed), `progress` (integer 0–100), and `timestamp` (valid ISO 8601 string).

**Validates: Requirements 6.3**

---

### Property 32: SSE reconnection replay

*For any* SSE client that disconnects after receiving N events and reconnects within 24 hours, the reconnected stream should deliver all events that occurred after the last received event ID.

**Validates: Requirements 6.6**

---

### Property 33: SSE authentication enforcement

*For any* SSE connection attempt without a valid JWT token, the AI Service should return a 401 Unauthorized response and not establish the stream.

**Validates: Requirements 6.7**

---

### Property 34: Report storage round-trip

*For any* completed research job, the report Markdown content should be stored in S3 and the report metadata (title, s3_key, citation_count, word_count, critic_score, revision_count) should be stored in PostgreSQL, and querying the report by ID should return the same content.

**Validates: Requirements 7.1, 7.2**

---

### Property 35: Report list isolation

*For any* two distinct users, the reports returned for user A should contain only reports associated with jobs owned by user A, and none of user B's reports.

**Validates: Requirements 7.3**

---

### Property 36: Report regeneration creates new job

*For any* completed research job, requesting regeneration should create a new ResearchJob with the same `query`, `llmProvider`, and `researchDepth` values as the original job.

**Validates: Requirements 7.6**

---

### Property 37: RAG prompt construction

*For any* chat message sent for a report, the constructed LLM prompt should include exactly 5 semantically retrieved report chunks from pgvector, the last 5 messages from chat history (or all messages if fewer than 5 exist), and the user's current message.

**Validates: Requirements 8.1, 8.2**

---

### Property 38: Chat persistence round-trip

*For any* chat exchange (user message + assistant response), both messages should be persisted to the `chat_messages` table and retrievable via the chat history query for that report.

**Validates: Requirements 8.4**

---

### Property 39: Chat history clear

*For any* report with existing chat messages, requesting a chat history clear should result in the chat history query returning an empty list for that report.

**Validates: Requirements 8.6**

---

### Property 40: Report embeddings created on completion

*For any* completed report, the `embeddings` table should contain at least one row with `source_type = 'report'` and `source_id` matching the report ID.

**Validates: Requirements 8.7**

---

### Property 41: Document upload creates PROCESSING record

*For any* valid file upload (PDF or TXT, ≤10 MB) to an owned project, the API should create a Document record with `status = PROCESSING` and store the file in S3.

**Validates: Requirements 9.1**

---

### Property 42: Invalid file type rejection

*For any* file upload with a MIME type other than `application/pdf` or `text/plain`, the API should return a 422 error with the message "Unsupported file type. Only PDF and TXT files are accepted."

**Validates: Requirements 9.3**

---

### Property 43: Document chunking structural invariant

*For any* document that completes processing, the embeddings stored in pgvector should have consecutive `chunk_index` values starting from 0, and adjacent chunks should share a 50-token overlap (verifiable by comparing the tail of chunk N with the head of chunk N+1).

**Validates: Requirements 9.4**

---

### Property 44: Document status transitions

*For any* document that completes embedding successfully, its status should transition to READY; and for any document whose embedding fails, its status should transition to FAILED with a non-empty `error_message`.

**Validates: Requirements 9.5, 9.6**

---

### Property 45: Document deletion cascades

*For any* document that is deleted by its owner, the S3 object, the Document record, and all associated Embedding records in pgvector should no longer be retrievable.

**Validates: Requirements 9.8**

---

### Property 46: Markdown round-trip

*For any* valid report Markdown string produced by the Synthesizer Agent, parsing the Markdown with a standard parser, rendering to HTML, and extracting the text content should produce a string that is semantically equivalent to the original report text (same headings, body text, and citation references preserved).

**Validates: Requirements 10.3, 10.4**

---

### Property 47: Report JSON serialisation round-trip

*For any* Report object returned by the GraphQL API, serialising the response to JSON and deserialising it should produce a Report object with field values identical to the original (id, title, markdownContent, citationCount, wordCount, createdAt).

**Validates: Requirements 10.5**

---

### Property 48: LLM provider model selection

*For any* AI Service instance configured with `LLM_PROVIDER=bedrock`, all LLM inference calls should use the Bedrock Claude 3.5 Sonnet model ID and all embedding calls should use the Titan Embeddings model ID; and for any instance configured with `LLM_PROVIDER=openai`, all LLM inference calls should use `gpt-4o` and all embedding calls should use `text-embedding-3-small`.

**Validates: Requirements 11.2, 11.3**

---

### Property 49: LLM provider interface uniformity

*For any* LLMProvider implementation (Bedrock or OpenAI), calling `get_chat_model()` should return an object that responds to `ainvoke()`, and calling `get_embedding_model()` should return an object that responds to `embed_documents()` and `embed_query()`.

**Validates: Requirements 11.5**

---

### Property 50: Rate limit response includes Retry-After header

*For any* response with HTTP status 429, the response headers should include a `Retry-After` field with a positive integer value indicating seconds until the rate limit resets.

**Validates: Requirements 12.3**

---

### Property 51: Content policy checked before AI Service invocation

*For any* research job submission that is rejected due to content policy violation, the AI Service's `/api/v1/jobs/start` endpoint should not have been called.

**Validates: Requirements 12.4**

---

### Property 52: Agent retry on failure

*For any* agent node that raises an exception, the LangGraph workflow should retry that node up to 3 times before marking the ResearchJob as FAILED; the retry count in the job state should not exceed 3.

**Validates: Requirements 14.2**

---

### Property 53: Unauthenticated GraphQL requests rejected

*For any* request to an authenticated GraphQL endpoint (query or mutation requiring auth) without a valid JWT access token, the response should have HTTP status 401 or a GraphQL error with code UNAUTHENTICATED.

**Validates: Requirements 15.1**

---

### Property 54: XSS sanitisation

*For any* user-supplied string containing HTML script tags or event handler attributes (e.g., `<script>`, `onerror=`), passing it through the DOMPurify sanitisation step should produce an output string that contains no executable script content.

**Validates: Requirements 15.6**

---

## Error Handling

### Spring Boot API Service

**Authentication errors:**
- Missing/expired JWT → 401 with `WWW-Authenticate: Bearer` header
- Invalid JWT signature → 401
- Insufficient ownership → 403 with descriptive message

**Validation errors:**
- Bean Validation failures → 422 with field-level error details
- Query length out of range → 422 with message specifying the constraint
- Duplicate email on registration → 409 Conflict

**Rate limiting:**
- General limit exceeded → 429 with `Retry-After` header
- Job submission limit exceeded → 429 with `Retry-After` header

**Downstream failures:**
- FastAPI unreachable → 503 Service Unavailable; job status set to FAILED
- S3 unavailable during export → 503 with retry suggestion

**GraphQL error format:**
```json
{
  "errors": [{
    "message": "Forbidden: you do not own this resource",
    "extensions": { "code": "FORBIDDEN", "field": "projectId" }
  }]
}
```

### FastAPI AI Service

**LangGraph node failures:**
- Each node wrapped in try/except; on exception, emit `job_failed` SSE event and update job status
- Retry logic: up to 3 retries per node with exponential backoff (1s, 2s, 4s)
- After 3 retries: mark job FAILED, emit `job_failed` event, preserve checkpoint for debugging

**LLM provider errors:**
- Bedrock throttling (ThrottlingException) → retry with backoff, surface as transient error
- OpenAI rate limit (429) → retry with backoff
- Invalid provider value at startup → log error and raise `SystemExit(1)` (fail fast)

**SSE stream errors:**
- Client disconnect detected → stop publishing, clean up subscriber set in Redis
- Redis unavailable → return 503 on SSE connect; running jobs continue with in-memory fallback for events

**Document processing errors:**
- PDF extraction failure (corrupted file) → set document status to FAILED with error message
- Embedding API failure → set document status to FAILED, store error message, do not retry automatically

**Content policy:**
- Harmful query detected → return 422 before any LLM call; log the rejection (not the query content)

### Frontend

**Network errors:**
- GraphQL network error → toast notification with retry button
- SSE connection lost → automatic reconnect with exponential backoff (max 5 attempts); show "Reconnecting..." indicator
- SSE reconnect failed after 5 attempts → show "Connection lost. Refresh to retry." banner

**State consistency:**
- Apollo Client cache invalidated on job status change to COMPLETE/FAILED
- Zustand store reset on logout

---

## Testing Strategy

### Dual Testing Approach

Both unit tests and property-based tests are required. Unit tests verify specific examples, edge cases, and integration points. Property-based tests verify universal correctness across randomised inputs. Together they provide comprehensive coverage.

### Property-Based Testing Libraries

| Service | Library | Min Iterations |
|---|---|---|
| FastAPI (Python) | `hypothesis` | 100 |
| Spring Boot (Java) | `jqwik` | 100 |
| Frontend (TypeScript) | `fast-check` | 100 |

Each property-based test must be tagged with a comment referencing the design property:
```
# Feature: meridian-platform, Property 22: Planner sub-question count invariant
```

### Unit Tests

**Spring Boot (JUnit 5 + Mockito):**
```
api/src/test/java/
├── auth/
│   ├── AuthServiceTest.java          # register, login, token refresh, bcrypt, RS256
│   └── JwtUtilTest.java              # token generation, validation, expiry
├── project/
│   └── ProjectServiceTest.java       # CRUD, ownership validation, cascade delete
├── job/
│   └── ResearchJobServiceTest.java   # create, cancel, status transitions, defaults
├── report/
│   └── ReportServiceTest.java        # retrieval, export URL generation, regeneration
├── document/
│   └── DocumentServiceTest.java      # upload validation, deletion cascade
└── graphql/
    ├── AuthResolverTest.java
    └── ProjectResolverTest.java
```

**FastAPI (pytest + pytest-asyncio):**
```
ai-service/tests/unit/
├── test_llm_provider.py              # provider switching, interface uniformity
├── test_agents/
│   ├── test_planner.py               # sub-question count (3–5), structure
│   ├── test_research.py              # result structure, tool call mocking
│   ├── test_analysis.py              # citation markers, non-empty draft
│   ├── test_critic.py                # score range [1.0, 10.0], feedback non-empty
│   └── test_synthesizer.py           # required sections present, Markdown validity
├── test_graph.py                     # routing logic, iteration cap, QUICK/DEEP depth
├── test_rag.py                       # chunking overlap, embedding round-trip
└── test_streaming.py                 # SSE event structure, Redis pub/sub
```

**Frontend (Vitest + Testing Library):**
```
frontend/src/__tests__/
├── components/AgentCard.test.tsx     # state rendering (IDLE/RUNNING/COMPLETE/FAILED)
├── components/ChatPanel.test.tsx     # message rendering, streaming indicator
├── hooks/useSSE.test.ts              # reconnection logic, event parsing
└── lib/apolloClient.test.ts          # cache invalidation on job completion
```

### Property-Based Tests

**FastAPI (Hypothesis):**

```python
# Feature: meridian-platform, Property 22: Planner sub-question count invariant
@given(query=st.text(min_size=10, max_size=500))
@settings(max_examples=100)
async def test_planner_sub_question_count(query):
    state = await planner_node(make_state(query=query))
    assert 3 <= len(state["sub_questions"]) <= 5

# Feature: meridian-platform, Property 25: Critic score range invariant
@given(draft=st.text(min_size=50))
@settings(max_examples=100)
async def test_critic_score_range(draft):
    state = await critic_node(make_state(analysis_draft=draft))
    assert 1.0 <= state["critic_score"] <= 10.0
    assert len(state["critic_feedback"]) > 0

# Feature: meridian-platform, Property 26: Critic routing logic
@given(score=st.floats(min_value=1.0, max_value=10.0),
       iterations=st.integers(min_value=0, max_value=3))
@settings(max_examples=100)
def test_critic_routing(score, iterations):
    state = make_state(critic_score=score, critic_iterations=iterations)
    result = should_revise(state)
    if score < 7.0 and iterations < 3:
        assert result == "analysis"
    else:
        assert result == "synthesizer"

# Feature: meridian-platform, Property 46: Markdown round-trip
@given(report=st.builds(make_report_markdown))
@settings(max_examples=100)
def test_markdown_round_trip(report):
    html = markdown_to_html(report)
    extracted = extract_text(html)
    assert sections_preserved(report, extracted)

# Feature: meridian-platform, Property 47: Report JSON serialisation round-trip
@given(report=st.builds(Report))
@settings(max_examples=100)
def test_report_json_round_trip(report):
    serialised = report.model_dump_json()
    deserialised = Report.model_validate_json(serialised)
    assert deserialised == report

# Feature: meridian-platform, Property 43: Document chunking structural invariant
@given(text=st.text(min_size=100))
@settings(max_examples=100)
def test_chunk_overlap(text):
    chunks = chunk_text(text, chunk_size=500, overlap=50)
    for i in range(len(chunks) - 1):
        tail = get_tokens(chunks[i])[-50:]
        head = get_tokens(chunks[i+1])[:50]
        assert tail == head
```

**Spring Boot (jqwik):**

```java
// Feature: meridian-platform, Property 16: Query length validation
@Property(tries = 100)
void queryTooShortIsRejected(@ForAll @StringLength(max = 9) String query) {
    assertThrows(ValidationException.class,
        () -> jobService.createJob(query, validProjectId, userId));
}

@Property(tries = 100)
void queryTooLongIsRejected(@ForAll @StringLength(min = 501) String query) {
    assertThrows(ValidationException.class,
        () -> jobService.createJob(query, validProjectId, userId));
}

// Feature: meridian-platform, Property 10: Project list isolation
@Property(tries = 100)
void projectListIsolation(@ForAll UUID userAId, @ForAll UUID userBId) {
    assumeThat(userAId).isNotEqualTo(userBId);
    List<Project> projectsA = projectService.listProjects(userAId);
    projectsA.forEach(p -> assertThat(p.getUserId()).isEqualTo(userAId));
}

// Feature: meridian-platform, Property 5: Token security invariants
@Property(tries = 100)
void passwordHashUsesBcryptCost12(@ForAll String password) {
    String hash = authService.hashPassword(password);
    assertThat(hash).startsWith("$2b$12$");
}
```

**Frontend (fast-check):**

```typescript
// Feature: meridian-platform, Property 31: SSE event structural invariant
test('SSE events contain required fields', () => {
  fc.assert(fc.property(
    fc.record({
      agent: fc.constantFrom('planner', 'research', 'analysis', 'critic', 'synthesizer'),
      status: fc.constantFrom('running', 'complete', 'failed'),
      progress: fc.integer({ min: 0, max: 100 }),
      timestamp: fc.date().map(d => d.toISOString()),
    }),
    (event) => {
      const parsed = parseSSEEvent(JSON.stringify(event))
      return parsed.agent !== undefined &&
             parsed.status !== undefined &&
             parsed.progress >= 0 && parsed.progress <= 100 &&
             isValidISO8601(parsed.timestamp)
    }
  ), { numRuns: 100 })
})
```

### Integration Tests

```
ai-service/tests/integration/
├── test_full_workflow.py       # End-to-end LangGraph run (mocked LLM + Tavily)
├── test_database.py            # pgvector insert + similarity search
└── test_redis.py               # State checkpoint save/load, stream replay

api/src/test/java/integration/
├── GraphQLIntegrationTest.java # Full GraphQL request/response cycle
└── AuthIntegrationTest.java    # JWT flow end-to-end
```

### End-to-End Tests (Playwright)

```
frontend/tests/e2e/
├── auth.spec.ts            # Register, login, logout
├── project.spec.ts         # Create, view, delete project
├── research.spec.ts        # Submit job, observe live view, view report
└── chat.spec.ts            # Chat with completed report
```

### Coverage Targets

| Service | Target | Tool |
|---|---|---|
| Spring Boot | 80% line coverage | JaCoCo |
| FastAPI | 80% line coverage | pytest-cov |
| Frontend | 70% line coverage | Vitest |

### Test Configuration Notes

- Property tests run with minimum 100 iterations each
- Integration tests use `testcontainers` (Java) and `pytest-docker` (Python) for real PostgreSQL + Redis instances
- LLM calls are mocked in all unit and integration tests; only E2E tests may call real LLM APIs (gated by `E2E_WITH_LLM=true`)
- Each property test must include a comment tag: `Feature: meridian-platform, Property N: {property_text}`
