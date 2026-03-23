# Implementation Plan: Meridian Autonomous Multi-Agent Research Platform

## Overview

Incremental implementation of the Meridian platform across three services (Next.js frontend, Spring Boot API, FastAPI AI service) deployed on Kubernetes. Each task builds on the previous, ending with full integration. All code is written in TypeScript (frontend), Java 21 (API service), and Python 3.11 (AI service).

## Tasks

- [~] 1. Monorepo setup and local development environment
  - Create root `meridian/` monorepo with subdirectories `frontend/`, `api/`, `ai-service/`, `infra/`, `helm/`
  - Add root `docker-compose.yml` with services: `postgres` (pgvector image), `redis`, `api`, `ai-service`, `frontend`
  - Add `.env.example` with all required environment variables documented
  - Add root `Makefile` with targets: `up`, `down`, `logs`, `test`
  - _Requirements: 13, 14, 15_


- [~] 2. Terraform infrastructure modules
  - [ ] 2.1 Write VPC module (`infra/modules/vpc/`) with public/private subnets across 2 AZs
    - _Requirements: 14.6_
  - [ ] 2.2 Write EKS module (`infra/modules/eks/`) with managed node group
    - _Requirements: 13.4_
  - [ ] 2.3 Write RDS module (`infra/modules/rds/`) — PostgreSQL 15 with pgvector, multi-AZ enabled
    - _Requirements: 14.6_
  - [ ] 2.4 Write ElastiCache module (`infra/modules/elasticache/`) — Redis 7, multi-AZ
    - _Requirements: 14.6_
  - [ ] 2.5 Write S3 module (`infra/modules/s3/`) for documents and reports buckets
    - _Requirements: 7.1, 9.1_
  - [ ] 2.6 Write ECR module (`infra/modules/ecr/`) with repos for all three services
  - [ ] 2.7 Write root `infra/main.tf` wiring all modules with variable inputs
    - _Requirements: 15.5_


- [~] 3. Spring Boot API — project setup and database schema
  - [ ] 3.1 Initialise Spring Boot project (`api/`) with dependencies: Spring Web, Spring Security, Spring Data JPA, GraphQL, Flyway, Redis, Lombok, jqwik
    - _Requirements: 1, 2, 3, 4_
  - [ ] 3.2 Write Flyway migration `V1__init.sql` creating all tables: `users`, `projects`, `research_jobs`, `reports`, `documents`, `embeddings`, `chat_messages`, `refresh_tokens`
    - Include HNSW index on `embeddings.embedding`, all FK constraints, and CHECK constraints matching the schema in the design
    - _Requirements: 1.7, 3.1, 4.1, 7.1, 8.4, 9.1_
  - [ ] 3.3 Write JPA entity classes for all tables in their respective packages (`auth/`, `project/`, `job/`, `report/`, `document/`, `chat/`)
    - _Requirements: 2.4, 3.4, 9.8_
  - [ ] 3.4 Write Spring Data JPA repository interfaces for each entity
    - _Requirements: 3.2, 7.3_


- [~] 4. Spring Boot API — JWT authentication
  - [ ] 4.1 Generate RSA-2048 key pair; write `JwtUtil` class that signs/verifies tokens using RS256, with 15-minute access token and 7-day refresh token expiry
    - _Requirements: 1.3, 1.8_
  - [ ] 4.2 Write `AuthService` with `register()`, `login()`, `refreshToken()`, and `changePassword()` methods; use BCryptPasswordEncoder with strength 12
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 2.2, 2.3_
  - [ ] 4.3 Write `RefreshTokenRepository` and `RefreshToken` entity; implement token rotation and invalidation on password change
    - _Requirements: 1.5, 1.6, 2.2_
  - [ ] 4.4 Write `JwtAuthenticationFilter` (OncePerRequestFilter) that validates Bearer tokens on every request
    - _Requirements: 15.1_
  - [ ] 4.5 Write `AuthController` REST endpoints: `POST /auth/register`, `POST /auth/login`, `POST /auth/refresh`
    - _Requirements: 1.1, 1.3, 1.5_
  - [ ]* 4.6 Write unit tests for `AuthService` and `JwtUtil` (JUnit 5 + Mockito)
    - Test register, login, token refresh, bcrypt hash format, RS256 alg field
    - _Requirements: 1.1–1.8_
  - [ ]* 4.7 Write property test for password hash format (Property 5)
    - **Property 5: Token security invariants**
    - **Validates: Requirements 1.7, 1.8**


- [~] 5. Spring Boot API — GraphQL schema and resolvers
  - [ ] 5.1 Write `schema.graphqls` defining all types: `User`, `Project`, `ResearchJob`, `Report`, `Document`, `ChatMessage`, enums (`JobStatus`, `LLMProvider`, `ResearchDepth`), and all queries/mutations
    - _Requirements: 3, 4, 7, 8, 9_
  - [ ] 5.2 Write `UserResolver` with `me` query and `updateProfile`, `deleteAccount` mutations; enforce `@PreAuthorize` ownership
    - _Requirements: 2.1, 2.4_
  - [ ] 5.3 Write `ProjectResolver` with `projects` query and `createProject`, `updateProject`, `deleteProject` mutations; enforce ownership on all operations
    - _Requirements: 3.1–3.6_
  - [ ] 5.4 Write `ProjectService` with ownership validation, cascade delete logic, and job count / last activity aggregation
    - _Requirements: 3.2, 3.5, 3.6_
  - [ ] 5.5 Write `ResearchJobResolver` with `researchJob` query and `createResearchJob`, `cancelResearchJob` mutations
    - _Requirements: 4.1–4.8_
  - [ ] 5.6 Write `ResearchJobService` with query length validation (10–500 chars), content policy check, document ownership check, default value assignment, and FastAPI trigger call
    - _Requirements: 4.1–4.8, 12.4_
  - [ ] 5.7 Write `ReportResolver` with `report` query and `regenerateReport`, `exportReport` mutations
    - _Requirements: 7.2–7.6_
  - [ ] 5.8 Write `DocumentResolver` with `documents` query and `deleteDocument` mutation
    - _Requirements: 9.8, 9.9_
  - [ ] 5.9 Write `ChatResolver` with `chatMessages` query and `sendChatMessage`, `clearChatHistory` mutations
    - _Requirements: 8.4–8.6_
  - [ ]* 5.10 Write unit tests for `ProjectService`, `ResearchJobService`, `ReportService`, `DocumentService`
    - _Requirements: 3, 4, 7, 9_
  - [ ]* 5.11 Write property test for query length validation (Property 16)
    - **Property 16: Query length validation**
    - **Validates: Requirements 4.2**
  - [ ]* 5.12 Write property test for project list isolation (Property 10)
    - **Property 10: Project list isolation**
    - **Validates: Requirements 3.2**


- [~] 6. Spring Boot API — rate limiting, content policy, and security config
  - [ ] 6.1 Write `RateLimitService` using Redis sliding window counters: 60 req/min general, 5 jobs/hour for job submissions; return 429 with `Retry-After` header on breach
    - _Requirements: 12.1, 12.2, 12.3_
  - [ ] 6.2 Write `ContentPolicyService` that evaluates a query string and returns a boolean; integrate into `ResearchJobService` before FastAPI call
    - _Requirements: 12.4, 4.7_
  - [ ] 6.3 Write `SecurityConfig` (Spring Security): disable CSRF for GraphQL, configure CORS to allow only `FRONTEND_URL`, register `JwtAuthenticationFilter`
    - _Requirements: 15.1, 15.4_
  - [ ] 6.4 Write `FastApiClient` (WebClient) with `startJob()` and `cancelJob()` methods targeting `AI_SERVICE_URL`; no auth header (internal network)
    - _Requirements: 4.1, 4.8_
  - [ ]* 6.5 Write property test for rate limit Retry-After header (Property 50)
    - **Property 50: Rate limit response includes Retry-After header**
    - **Validates: Requirements 12.3**
  - [ ]* 6.6 Write property test for content policy checked before AI Service invocation (Property 51)
    - **Property 51: Content policy checked before AI Service invocation**
    - **Validates: Requirements 12.4**

- [~] 7. Checkpoint — Spring Boot API baseline
  - Ensure all Spring Boot unit and property tests pass, ask the user if questions arise.


- [~] 8. FastAPI AI Service — project setup and LLM provider abstraction
  - [ ] 8.1 Initialise FastAPI project (`ai-service/`) with dependencies: `fastapi`, `uvicorn`, `langchain`, `langgraph`, `langchain-aws`, `langchain-openai`, `asyncpg`, `redis`, `boto3`, `tavily-python`, `pypdf`, `tiktoken`, `hypothesis`, `pytest-asyncio`
    - _Requirements: 11_
  - [ ] 8.2 Write `LLMProvider` abstract base class in `app/llm/base.py` with abstract methods `get_chat_model()` and `get_embedding_model()`
    - _Requirements: 11.5_
  - [ ] 8.3 Write `BedrockProvider` in `app/llm/bedrock.py` using `ChatBedrock` (Claude 3.5 Sonnet) and `BedrockEmbeddings` (Titan v2)
    - _Requirements: 11.2_
  - [ ] 8.4 Write `OpenAIProvider` in `app/llm/openai_provider.py` using `ChatOpenAI` (gpt-4o) and `OpenAIEmbeddings` (text-embedding-3-small)
    - _Requirements: 11.3_
  - [ ] 8.5 Write `get_llm_provider()` factory in `app/llm/__init__.py` that reads `LLM_PROVIDER` env var and returns the correct provider; raise `SystemExit(1)` on unsupported value
    - _Requirements: 11.1, 11.4_
  - [ ]* 8.6 Write unit tests for LLM provider switching and interface uniformity (`test_llm_provider.py`)
    - _Requirements: 11.1–11.5_
  - [ ]* 8.7 Write property test for LLM provider interface uniformity (Property 49)
    - **Property 49: LLM provider interface uniformity**
    - **Validates: Requirements 11.5**


- [~] 9. FastAPI AI Service — LangGraph state and Redis checkpointing
  - [ ] 9.1 Write `ResearchState` TypedDict in `app/graph/state.py` with all fields from the design: `job_id`, `query`, `llm_provider`, `research_depth`, `uploaded_doc_ids`, `sub_questions`, `report_structure`, `research_results`, `analysis_draft`, `analysis_iteration`, `critic_score`, `critic_feedback`, `critic_iterations`, `final_report`, `citations`, `agent_logs`, `status`, `error`
    - _Requirements: 5.1–5.13_
  - [ ] 9.2 Write `RedisSaver` checkpointer in `app/graph/checkpointer.py` that serialises/deserialises `ResearchState` to/from Redis with key `langgraph:checkpoint:{job_id}` and TTL 48 hours
    - _Requirements: 5.10, 14.4, 14.5_
  - [ ]* 9.3 Write property test for LangGraph checkpoint round-trip (Property 28)
    - **Property 28: LangGraph checkpoint round-trip**
    - **Validates: Requirements 5.10, 14.4**


- [~] 10. FastAPI AI Service — Planner Agent node
  - [ ] 10.1 Write `planner_node` in `app/agents/planner.py`; prompt the LLM to decompose the query into 3–5 non-overlapping sub-questions and define a report structure; update `ResearchState` with `sub_questions` and `report_structure`
    - _Requirements: 5.2_
  - [ ] 10.2 Write `EventPublisher` helper in `app/streaming/publisher.py` that publishes agent events to Redis Stream `job:{job_id}:events` via `XADD`; call it at the start and end of each agent node
    - _Requirements: 6.1, 6.3_
  - [ ] 10.3 Integrate `EventPublisher` into `planner_node` to emit `{agent: "planner", status: "running", progress: 10}` and `{agent: "planner", status: "complete", progress: 20}` events
    - _Requirements: 6.1, 6.3_
  - [ ]* 10.4 Write unit tests for `planner_node` with mocked LLM (`test_agents/test_planner.py`)
    - _Requirements: 5.2_
  - [ ]* 10.5 Write property test for planner sub-question count invariant (Property 22)
    - **Property 22: Planner sub-question count invariant**
    - **Validates: Requirements 5.2**


- [~] 11. FastAPI AI Service — Research Agent node (Tavily + pgvector)
  - [ ] 11.1 Write `pgvector_search()` helper in `app/rag/retrieval.py` that takes a query string and returns the top-K most similar chunks from the `embeddings` table using cosine similarity via asyncpg
    - _Requirements: 5.4, 13.5_
  - [ ] 11.2 Write `research_node` in `app/agents/research.py`; for each sub-question, call Tavily for top 5 web results and (if `uploaded_doc_ids` non-empty) call `pgvector_search()` for top 3 document chunks; aggregate into `research_results`
    - _Requirements: 5.3, 5.4_
  - [ ] 11.3 Integrate `EventPublisher` into `research_node` with progress events per sub-question
    - _Requirements: 6.1, 6.3_
  - [ ]* 11.4 Write unit tests for `research_node` with mocked Tavily and pgvector (`test_agents/test_research.py`)
    - _Requirements: 5.3, 5.4_
  - [ ]* 11.5 Write property test for research results structural invariant (Property 23)
    - **Property 23: Research results structural invariant**
    - **Validates: Requirements 5.3, 5.4**


- [~] 12. FastAPI AI Service — Analysis Agent node
  - [ ] 12.1 Write `analysis_node` in `app/agents/analysis.py`; prompt the LLM with combined research results (and `critic_feedback` if `analysis_iteration > 0`) to produce a structured `analysis_draft` with inline citation markers `[N]`; increment `analysis_iteration`
    - _Requirements: 5.5_
  - [ ] 12.2 Integrate `EventPublisher` into `analysis_node`
    - _Requirements: 6.1, 6.3_
  - [ ]* 12.3 Write unit tests for `analysis_node` with mocked LLM (`test_agents/test_analysis.py`)
    - _Requirements: 5.5_
  - [ ]* 12.4 Write property test for analysis draft contains citations (Property 24)
    - **Property 24: Analysis draft contains citations**
    - **Validates: Requirements 5.5**

- [~] 13. FastAPI AI Service — Critic Agent node with conditional edge
  - [ ] 13.1 Write `critic_node` in `app/agents/critic.py`; prompt the LLM to evaluate the `analysis_draft` across factual accuracy, completeness, coherence, citation quality, and depth; parse and store `critic_score` (float 1.0–10.0) and `critic_feedback`; increment `critic_iterations`
    - _Requirements: 5.6_
  - [ ] 13.2 Write `should_revise()` conditional edge function in `app/graph/edges.py`; return `"analysis"` if `critic_score < 7.0 AND critic_iterations < 3`, else return `"synthesizer"`
    - _Requirements: 5.7, 5.8_
  - [ ] 13.3 Integrate `EventPublisher` into `critic_node`
    - _Requirements: 6.1, 6.3_
  - [ ]* 13.4 Write unit tests for `critic_node` and `should_revise()` (`test_agents/test_critic.py`)
    - _Requirements: 5.6, 5.7, 5.8_
  - [ ]* 13.5 Write property test for critic score range invariant (Property 25)
    - **Property 25: Critic score range invariant**
    - **Validates: Requirements 5.6**
  - [ ]* 13.6 Write property test for critic routing logic (Property 26)
    - **Property 26: Critic routing logic**
    - **Validates: Requirements 5.7, 5.8**


- [~] 14. FastAPI AI Service — Synthesizer Agent node
  - [ ] 14.1 Write `synthesizer_node` in `app/agents/synthesizer.py`; prompt the LLM to produce a final Markdown report containing: H1 title, executive summary, main findings sections, conflicting viewpoints, key takeaways, recommended next steps, and numbered citations list; store in `final_report`
    - _Requirements: 5.9_
  - [ ] 14.2 Integrate `EventPublisher` into `synthesizer_node`; emit `{agent: "synthesizer", status: "complete", progress: 100}` on completion
    - _Requirements: 6.1, 6.3, 6.4_
  - [ ]* 14.3 Write unit tests for `synthesizer_node` with mocked LLM (`test_agents/test_synthesizer.py`)
    - _Requirements: 5.9_
  - [ ]* 14.4 Write property test for final report structural completeness (Property 27)
    - **Property 27: Final report structural completeness**
    - **Validates: Requirements 5.9**


- [~] 15. FastAPI AI Service — Full LangGraph workflow wiring
  - [ ] 15.1 Write `build_research_graph()` in `app/graph/workflow.py`; wire all five nodes with edges as defined in the design; attach `RedisSaver` checkpointer; compile and return the graph
    - _Requirements: 5.1, 5.10_
  - [ ] 15.2 Implement `research_depth` branching: for `QUICK` depth, add an edge from `research` directly to a summary output node (skip analysis/critic/synthesizer); for `DEEP` depth, allow up to 3 critic iterations
    - _Requirements: 5.12, 5.13_
  - [ ] 15.3 Wrap each agent node invocation in retry logic (up to 3 retries with exponential backoff 1s/2s/4s); on exhaustion, set `status = "failed"` and emit `job_failed` SSE event
    - _Requirements: 14.2, 14.3_
  - [ ]* 15.4 Write unit tests for graph routing logic (`test_graph.py`): workflow execution order, QUICK depth truncation, DEEP depth iteration cap
    - _Requirements: 5.1, 5.12, 5.13_
  - [ ]* 15.5 Write property test for workflow execution order (Property 21)
    - **Property 21: Workflow execution order**
    - **Validates: Requirements 5.1**
  - [ ]* 15.6 Write property test for QUICK depth workflow truncation (Property 29)
    - **Property 29: QUICK depth workflow truncation**
    - **Validates: Requirements 5.12**
  - [ ]* 15.7 Write property test for DEEP depth iteration cap (Property 30)
    - **Property 30: DEEP depth iteration cap**
    - **Validates: Requirements 5.13**
  - [ ]* 15.8 Write property test for agent retry on failure (Property 52)
    - **Property 52: Agent retry on failure**
    - **Validates: Requirements 14.2**

- [~] 16. Checkpoint — LangGraph workflow
  - Ensure all agent unit tests and graph routing tests pass, ask the user if questions arise.


- [~] 17. FastAPI AI Service — Redis pub/sub event publishing
  - [ ] 17.1 Extend `EventPublisher` to `XADD` events to Redis Stream `job:{job_id}:events` with TTL 24 hours; include all required fields: `agent`, `status`, `progress`, `timestamp` (ISO 8601), `partial_output`
    - _Requirements: 6.1, 6.3_
  - [ ] 17.2 Implement `job_complete` event emission in `synthesizer_node` containing `job_id` and `report_id`; implement `job_failed` event emission in the retry-exhaustion handler
    - _Requirements: 6.4, 6.5_
  - [ ]* 17.3 Write unit tests for `EventPublisher` with mocked Redis (`test_streaming.py`)
    - _Requirements: 6.1, 6.3_
  - [ ]* 17.4 Write property test for SSE event structural invariant (Property 31)
    - **Property 31: SSE event structural invariant**
    - **Validates: Requirements 6.3**

- [~] 18. FastAPI AI Service — SSE streaming endpoint with JWT auth and reconnection
  - [ ] 18.1 Write `GET /ai/stream/{job_id}` endpoint in `app/api/streaming.py` as a `StreamingResponse`; validate JWT from `?token=` query param; return 401 if invalid
    - _Requirements: 6.2, 6.7_
  - [ ] 18.2 Implement `XREAD BLOCK` loop that reads from `job:{job_id}:events` Redis Stream and yields SSE-formatted events; accept `Last-Event-ID` header for reconnection replay
    - _Requirements: 6.2, 6.6_
  - [ ] 18.3 On client disconnect, clean up subscriber entry from `job:{job_id}:subscribers` Redis set
    - _Requirements: 6.6_
  - [ ]* 18.4 Write unit tests for SSE endpoint: JWT rejection, event formatting, reconnection replay (`test_streaming.py`)
    - _Requirements: 6.2, 6.6, 6.7_
  - [ ]* 18.5 Write property test for SSE reconnection replay (Property 32)
    - **Property 32: SSE reconnection replay**
    - **Validates: Requirements 6.6**
  - [ ]* 18.6 Write property test for SSE authentication enforcement (Property 33)
    - **Property 33: SSE authentication enforcement**
    - **Validates: Requirements 6.7**


- [~] 19. FastAPI AI Service — Document processing pipeline
  - [ ] 19.1 Write `extract_text()` in `app/rag/extractor.py` that extracts plain text from PDF (using `pypdf`) and reads UTF-8 text from TXT files; set document status to FAILED with error message on extraction failure
    - _Requirements: 9.4, 10.1, 10.2_
  - [ ] 19.2 Write `chunk_text()` in `app/rag/chunker.py` that splits text into 500-token chunks with 50-token overlap using `tiktoken`; assign consecutive `chunk_index` values starting from 0
    - _Requirements: 9.4_
  - [ ] 19.3 Write `embed_and_store()` in `app/rag/embedder.py` that generates embeddings for each chunk using the configured `LLMProvider.get_embedding_model()` and inserts rows into the `embeddings` table via asyncpg
    - _Requirements: 9.4_
  - [ ] 19.4 Write `process_document()` async task in `app/api/documents.py` that orchestrates extract → chunk → embed → store; update document status to READY on success or FAILED on error
    - _Requirements: 9.4, 9.5, 9.6_
  - [ ] 19.5 Write `POST /ai/documents/process` endpoint that accepts `document_id` and `s3_key`, downloads the file from S3, and runs `process_document()` as a background task
    - _Requirements: 9.4_
  - [ ]* 19.6 Write unit tests for `extract_text()`, `chunk_text()`, `embed_and_store()` with mocked S3 and embedding API
    - _Requirements: 9.4–9.6, 10.1, 10.2_
  - [ ]* 19.7 Write property test for document chunking structural invariant (Property 43)
    - **Property 43: Document chunking structural invariant**
    - **Validates: Requirements 9.4**
  - [ ]* 19.8 Write property test for document status transitions (Property 44)
    - **Property 44: Document status transitions**
    - **Validates: Requirements 9.5, 9.6**


- [ ] 20. FastAPI AI Service — RAG chat endpoint
  - [ ] 20.1 Write `POST /ai/chat/{report_id}` endpoint in `app/api/chat.py`; validate JWT; retrieve 5 most semantically relevant report chunks from pgvector using the user message as query; fetch last 5 chat messages from PostgreSQL
    - _Requirements: 8.1, 8.2_
  - [ ] 20.2 Construct LLM prompt with retrieved chunks + chat history + user message; stream the LLM response token-by-token as SSE using `StreamingResponse`
    - _Requirements: 8.2, 8.3_
  - [ ]* 20.3 Write unit tests for chat endpoint: RAG retrieval, prompt construction, streaming response (`test_rag.py`)
    - _Requirements: 8.1–8.3_
  - [ ]* 20.4 Write property test for RAG prompt construction (Property 37)
    - **Property 37: RAG prompt construction**
    - **Validates: Requirements 8.1, 8.2**

- [ ] 21. FastAPI AI Service — Report storage (S3 + PostgreSQL)
  - [ ] 21.1 Write `store_report()` in `app/api/reports.py` that uploads the final Markdown to S3 (`reports/{job_id}/report.md`), inserts a row into the `reports` table with title, s3_key, citation_count, word_count, critic_score, and revision_count, and updates `research_jobs.status` to `complete`
    - _Requirements: 7.1_
  - [ ] 21.2 Call `embed_and_store()` for the completed report content to populate pgvector with `source_type = 'report'` chunks
    - _Requirements: 8.7_
  - [ ] 21.3 Call `store_report()` at the end of `synthesizer_node` after `final_report` is set
    - _Requirements: 7.1, 8.7_
  - [ ]* 21.4 Write unit tests for `store_report()` with mocked S3 and asyncpg
    - _Requirements: 7.1_
  - [ ]* 21.5 Write property test for report storage round-trip (Property 34)
    - **Property 34: Report storage round-trip**
    - **Validates: Requirements 7.1, 7.2**
  - [ ]* 21.6 Write property test for report embeddings created on completion (Property 40)
    - **Property 40: Report embeddings created on completion**
    - **Validates: Requirements 8.7**
  - [ ]* 21.7 Write property test for Markdown round-trip (Property 46)
    - **Property 46: Markdown round-trip**
    - **Validates: Requirements 10.3, 10.4**
  - [ ]* 21.8 Write property test for Report JSON serialisation round-trip (Property 47)
    - **Property 47: Report JSON serialisation round-trip**
    - **Validates: Requirements 10.5**


- [ ] 22. Spring Boot ↔ FastAPI integration (job trigger and cancel)
  - [ ] 22.1 Wire `ResearchJobService.createJob()` to call `FastApiClient.startJob()` after persisting the PENDING job; on 503 from FastAPI, set job status to FAILED
    - _Requirements: 4.1_
  - [ ] 22.2 Wire `ResearchJobService.cancelJob()` to call `FastApiClient.cancelJob()`; update job status to CANCELLED in PostgreSQL
    - _Requirements: 4.8_
  - [ ] 22.3 Write `POST /api/v1/jobs/start` and `POST /api/v1/jobs/cancel` endpoints in FastAPI (`app/api/jobs.py`) that accept `job_id` and trigger/cancel the LangGraph workflow background task
    - _Requirements: 4.1, 4.8_
  - [ ]* 22.4 Write integration tests for the job trigger/cancel flow with mocked FastAPI (`GraphQLIntegrationTest.java`)
    - _Requirements: 4.1, 4.8_

- [ ] 23. Checkpoint — FastAPI AI Service and Spring Boot integration
  - Ensure all FastAPI and Spring Boot integration tests pass, ask the user if questions arise.


- [ ] 24. Next.js Frontend — project setup, Apollo Client, and routing
  - [ ] 24.1 Initialise Next.js 14 project (`frontend/`) with TypeScript, App Router, Tailwind CSS, and dependencies: `@apollo/client`, `graphql`, `zustand`, `fast-check`, `@playwright/test`
    - _Requirements: 13, 15_
  - [ ] 24.2 Write `lib/apolloClient.ts` configuring Apollo Client with the Spring Boot GraphQL endpoint, JWT auth header injection from Zustand store, and cache invalidation on job status change to COMPLETE/FAILED
    - _Requirements: 15.1_
  - [ ] 24.3 Write `lib/authStore.ts` (Zustand) storing `accessToken`, `refreshToken`, `user`; implement `logout()` that resets the store
    - _Requirements: 1.3_
  - [ ] 24.4 Write `middleware.ts` protecting all `/dashboard`, `/projects`, `/jobs`, `/reports`, `/settings` routes; redirect unauthenticated users to `/login`
    - _Requirements: 15.1_
  - [ ] 24.5 Write `app/layout.tsx` with `ApolloProvider`, global CSP meta tag, and HSTS configuration
    - _Requirements: 15.3, 15.7_

- [ ] 25. Next.js Frontend — Auth pages (login and register)
  - [ ] 25.1 Write `app/(auth)/login/page.tsx` with email/password form; call `loginMutation` GraphQL mutation; store tokens in Zustand; redirect to `/dashboard`
    - _Requirements: 1.3, 1.4_
  - [ ] 25.2 Write `app/(auth)/register/page.tsx` with name/email/password form; call `registerMutation`; handle 409 Conflict error display
    - _Requirements: 1.1, 1.2_
  - [ ]* 25.3 Write unit tests for `LoginPage` and `RegisterPage` components (Vitest + Testing Library)
    - _Requirements: 1.1–1.4_


- [ ] 26. Next.js Frontend — Dashboard and Project pages
  - [ ] 26.1 Write `app/dashboard/page.tsx` that queries `projects` and renders a grid of `ProjectCard` components showing name, job count, and last activity
    - _Requirements: 3.2, 3.6_
  - [ ] 26.2 Write `components/ProjectCard.tsx` with create/rename/delete actions; call `createProject`, `updateProject`, `deleteProject` mutations
    - _Requirements: 3.1, 3.3, 3.4_
  - [ ] 26.3 Write `app/projects/[id]/page.tsx` that queries a single project and lists its research jobs with status badges
    - _Requirements: 3.5_

- [ ] 27. Next.js Frontend — New Research form
  - [ ] 27.1 Write `app/projects/[id]/new/page.tsx` with a form: query textarea (10–500 chars with live counter), LLM provider select, research depth select, document multi-select (up to 5)
    - _Requirements: 4.1–4.7_
  - [ ] 27.2 Call `createResearchJob` mutation on submit; handle 422 validation errors and 429 rate limit errors with user-facing messages; redirect to `/jobs/{id}/live` on success
    - _Requirements: 4.2, 4.7, 12.1, 12.2_


- [ ] 28. Next.js Frontend — Live Job page with AgentGraph and SSE
  - [ ] 28.1 Write `hooks/useSSE.ts` that opens an `EventSource` to `${AI_URL}/ai/stream/${jobId}?token=${accessToken}`; handles `agent_update`, `job_complete`, and `job_failed` events; implements exponential backoff reconnection (max 5 attempts) using `Last-Event-ID`
    - _Requirements: 6.2, 6.6_
  - [ ] 28.2 Write `components/AgentCard.tsx` that renders an agent's name, status badge (IDLE/RUNNING/COMPLETE/FAILED with colour coding), progress bar, and partial output text
    - _Requirements: 6.3_
  - [ ] 28.3 Write `app/jobs/[id]/live/page.tsx` that renders five `AgentCard` components in sequence; subscribes to `useSSE`; shows "Reconnecting..." indicator during reconnect; navigates to `/reports/{id}` on `job_complete`
    - _Requirements: 6.2, 6.4, 6.5_
  - [ ] 28.4 Write `components/CancelJobButton.tsx` that calls `cancelResearchJob` mutation and updates local state
    - _Requirements: 4.8_
  - [ ]* 28.5 Write unit tests for `AgentCard` (all four states) and `useSSE` hook (reconnection logic, event parsing)
    - _Requirements: 6.2, 6.3_
  - [ ]* 28.6 Write property test for SSE event structural invariant on the frontend (Property 31)
    - **Property 31: SSE event structural invariant (frontend)**
    - **Validates: Requirements 6.3**


- [ ] 29. Next.js Frontend — Report page with Markdown renderer and ToC
  - [ ] 29.1 Write `app/reports/[id]/page.tsx` that queries `report` by ID; renders Markdown content using `react-markdown` with `remark-gfm`; generates a Table of Contents from H2/H3 headings
    - _Requirements: 7.2_
  - [ ] 29.2 Add export buttons: "Export PDF" (calls `exportReport` mutation, opens pre-signed URL) and "Export Markdown" (calls `exportReport` mutation for Markdown)
    - _Requirements: 7.4, 7.5_
  - [ ] 29.3 Add "Regenerate" button that calls `regenerateReport` mutation and redirects to the new job's live page
    - _Requirements: 7.6_

- [ ] 30. Next.js Frontend — Chat panel with streaming
  - [ ] 30.1 Write `components/ChatPanel.tsx` that renders chat history and a message input; calls `POST /ai/chat/{report_id}` and reads the SSE stream token-by-token to display the assistant response progressively
    - _Requirements: 8.3, 8.4_
  - [ ] 30.2 Persist user message and assistant response via `sendChatMessage` GraphQL mutation after streaming completes; show 422 error when 50-message limit is reached
    - _Requirements: 8.4, 8.5_
  - [ ] 30.3 Add "Clear history" button that calls `clearChatHistory` mutation and resets the local message list
    - _Requirements: 8.6_
  - [ ] 30.4 Integrate `ChatPanel` into `app/reports/[id]/page.tsx` as a side panel
    - _Requirements: 8.1–8.6_
  - [ ]* 30.5 Write unit tests for `ChatPanel` (message rendering, streaming indicator, limit error)
    - _Requirements: 8.3–8.6_


- [ ] 31. Next.js Frontend — Document upload UI
  - [ ] 31.1 Write `components/DocumentUpload.tsx` with a drag-and-drop file input accepting PDF and TXT only (≤10 MB); call `uploadDocument` GraphQL mutation; show upload progress and embedding status (PROCESSING/READY/FAILED) with polling
    - _Requirements: 9.1–9.3, 9.9_
  - [ ] 31.2 Integrate `DocumentUpload` into `app/projects/[id]/page.tsx`; list existing documents with delete buttons that call `deleteDocument` mutation
    - _Requirements: 9.8_

- [ ] 32. Checkpoint — Frontend
  - Ensure all frontend unit tests pass, ask the user if questions arise.


- [ ] 33. Kubernetes Helm charts for all three services
  - [ ] 33.1 Write `helm/api/` chart with `Deployment`, `Service`, `HorizontalPodAutoscaler`, and `ConfigMap`; mount RSA keys and secrets from Kubernetes Secrets
    - _Requirements: 15.5_
  - [ ] 33.2 Write `helm/ai-service/` chart with `Deployment`, `Service`, and `ConfigMap`; include `LLM_PROVIDER` env var from Secret
    - _Requirements: 11.1, 15.5_
  - [ ] 33.3 Write `helm/frontend/` chart with `Deployment`, `Service`, and `Ingress` with TLS termination and HSTS annotation
    - _Requirements: 15.3_
  - [ ] 33.4 Write `helm/umbrella/Chart.yaml` that depends on all three sub-charts; write `values.yaml` with all configurable parameters documented
    - _Requirements: 15.5_

- [ ] 34. GitHub Actions CI/CD pipelines
  - [ ] 34.1 Write `.github/workflows/api.yml`: on push to `main`, run `./gradlew test jacocoTestReport`, build Docker image, push to ECR, deploy to EKS via Helm
    - _Requirements: 13_
  - [ ] 34.2 Write `.github/workflows/ai-service.yml`: on push to `main`, run `pytest --cov`, build Docker image, push to ECR, deploy via Helm
    - _Requirements: 13_
  - [ ] 34.3 Write `.github/workflows/frontend.yml`: on push to `main`, run `vitest --run`, build Next.js, build Docker image, push to ECR, deploy via Helm
    - _Requirements: 13_
  - [ ] 34.4 Write `.github/workflows/e2e.yml`: on push to `main` after all service deployments succeed, run Playwright E2E tests against the staging environment
    - _Requirements: 13_


- [ ] 35. Integration tests — Spring Boot and FastAPI
  - [ ] 35.1 Write `GraphQLIntegrationTest.java` using Testcontainers (PostgreSQL + Redis) covering the full auth flow, project CRUD, job creation, and report retrieval
    - _Requirements: 1, 3, 4, 7_
  - [ ] 35.2 Write `AuthIntegrationTest.java` covering register → login → refresh → password change → token invalidation
    - _Requirements: 1.1–1.8, 2.2_
  - [ ] 35.3 Write `test_full_workflow.py` (pytest + mocked LLM + Tavily) running the complete LangGraph graph from job start to report storage
    - _Requirements: 5.1–5.13_
  - [ ] 35.4 Write `test_database.py` using pytest-docker: pgvector insert, HNSW similarity search, and chunk retrieval
    - _Requirements: 13.5_
  - [ ] 35.5 Write `test_redis.py` using pytest-docker: checkpoint save/load round-trip, Redis Stream replay
    - _Requirements: 5.10, 6.6, 14.4_


- [ ] 36. End-to-end Playwright tests
  - [ ] 36.1 Write `frontend/tests/e2e/auth.spec.ts`: register new user, login, logout, verify redirect behaviour
    - _Requirements: 1.1, 1.3_
  - [ ] 36.2 Write `frontend/tests/e2e/project.spec.ts`: create project, rename, view, delete; verify cascade (jobs list empty after delete)
    - _Requirements: 3.1, 3.3, 3.4_
  - [ ] 36.3 Write `frontend/tests/e2e/research.spec.ts`: submit a research job (mocked AI service), observe live agent cards updating, navigate to completed report, verify Markdown rendering and ToC
    - _Requirements: 4.1, 5.1, 6.2, 7.2_
  - [ ] 36.4 Write `frontend/tests/e2e/chat.spec.ts`: open a completed report, send a chat message, verify streaming response appears, clear history
    - _Requirements: 8.3, 8.6_

- [ ] 37. Final checkpoint — all tests pass
  - Ensure all unit, property, integration, and E2E tests pass. Verify JaCoCo reports ≥80% line coverage for Spring Boot, pytest-cov ≥80% for FastAPI, and Vitest ≥70% for the frontend. Ask the user if questions arise.


## Notes

- Tasks marked with `*` are optional and can be skipped for a faster MVP
- Each task references specific requirements for traceability
- Checkpoints at tasks 7, 16, 23, 32, and 37 ensure incremental validation
- Property tests use `hypothesis` (Python), `jqwik` (Java), and `fast-check` (TypeScript) with minimum 100 iterations each
- Each property test must include a comment: `# Feature: meridian-platform, Property N: {property_title}`
- LLM calls are mocked in all unit and integration tests; real LLM calls are gated by `E2E_WITH_LLM=true`
- All secrets are stored in Kubernetes Secrets or AWS Secrets Manager — never in source code
