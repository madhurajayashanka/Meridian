# Requirements Document

## Introduction

Meridian is an open-source autonomous multi-agent AI research platform. Users submit a research question and Meridian deploys a coordinated team of five specialised AI agents — Planner, Research, Analysis, Critic, and Synthesizer — that search the web, retrieve relevant knowledge, analyse findings, self-critique the output, and synthesise a comprehensive, cited research report. All agent activity is streamed to the user in real time via a live visual feed.

The platform targets developers, researchers, product managers, and knowledge workers who need structured, reliable, well-cited research output on complex topics. Meridian is self-hostable, LLM-provider agnostic (AWS Bedrock and OpenAI), and built on a polyglot microservices architecture: Next.js frontend, Spring Boot GraphQL API, and FastAPI AI service.

---

## Glossary

- **System**: The Meridian platform as a whole, encompassing all services.
- **Auth_Service**: The Spring Boot component responsible for user registration, login, and JWT token management.
- **API_Service**: The Spring Boot GraphQL API responsible for CRUD operations on users, projects, jobs, reports, and documents.
- **AI_Service**: The FastAPI service responsible for LangGraph agent orchestration, LLM inference, RAG, and SSE streaming.
- **Planner_Agent**: The LangGraph agent that decomposes a research query into sub-questions and defines the report structure.
- **Research_Agent**: The LangGraph agent that gathers evidence via web search (Tavily) and document RAG (pgvector).
- **Analysis_Agent**: The LangGraph agent that synthesises research findings into a structured analysis draft.
- **Critic_Agent**: The LangGraph agent that evaluates the analysis draft and triggers revision if quality is below threshold.
- **Synthesizer_Agent**: The LangGraph agent that produces the final polished research report in Markdown.
- **LangGraph_Workflow**: The stateful multi-agent graph that orchestrates the five agents in sequence with a conditional revision loop.
- **ResearchJob**: A single research task submitted by a user, associated with a query, configuration, and resulting report.
- **Report**: The final Markdown document produced by the Synthesizer_Agent, stored in S3 and indexed in PostgreSQL.
- **Project**: A named container that organises one or more ResearchJobs and Documents belonging to a user.
- **Document**: A user-uploaded PDF or TXT file that is chunked, embedded, and stored in pgvector for use by the Research_Agent.
- **Embedding**: A vector representation of a text chunk stored in pgvector, used for semantic similarity search.
- **SSE_Stream**: A Server-Sent Events connection from the browser to the AI_Service that delivers real-time agent activity events.
- **RAG**: Retrieval-Augmented Generation — the technique of retrieving relevant text chunks from pgvector and injecting them into an LLM prompt.
- **JWT**: JSON Web Token used for stateless authentication between the browser and the API_Service.
- **LLM_Provider**: An abstraction over AWS Bedrock (Claude 3.5 Sonnet) or OpenAI (GPT-4o), switchable via environment variable.
- **Tavily**: The third-party web search API used by the Research_Agent to retrieve live web results.
- **pgvector**: The PostgreSQL extension that stores and queries vector embeddings for semantic search.
- **Redis**: The in-memory data store used for LangGraph state checkpointing and agent event pub/sub.
- **S3**: AWS Simple Storage Service used to store uploaded documents and completed reports.
- **Critic_Score**: A float between 1.0 and 10.0 assigned by the Critic_Agent to the analysis draft; scores below 7.0 trigger a revision.
- **Research_Depth**: A user-selected configuration value — Quick, Standard, or Deep — that controls the number of agents and iterations used.

---

## Requirements

### Requirement 1: User Registration and Authentication

**User Story:** As a new user, I want to register with my email and password and receive authentication tokens, so that I can securely access the platform and my research data.

#### Acceptance Criteria

1. WHEN a registration request is received with a valid email, password, and name, THE Auth_Service SHALL create a user account and return a JWT access token and a refresh token.
2. WHEN a registration request is received with an email that already exists in the system, THE Auth_Service SHALL return a 409 Conflict error with a descriptive message.
3. WHEN a login request is received with valid credentials, THE Auth_Service SHALL return a JWT access token valid for 15 minutes and a refresh token valid for 7 days.
4. WHEN a login request is received with invalid credentials, THE Auth_Service SHALL return a 401 Unauthorized error.
5. WHEN a token refresh request is received with a valid, unexpired refresh token, THE Auth_Service SHALL return a new access token and rotate the refresh token.
6. WHEN a token refresh request is received with an expired or invalid refresh token, THE Auth_Service SHALL return a 401 Unauthorized error.
7. THE Auth_Service SHALL store passwords using bcrypt with a cost factor of 12.
8. THE Auth_Service SHALL sign JWT tokens using RS256 asymmetric signing.

---

### Requirement 2: User Profile Management

**User Story:** As an authenticated user, I want to update my profile and manage my account, so that I can keep my information current and control my data.

#### Acceptance Criteria

1. WHEN an authenticated user submits a profile update with a valid name or avatar URL, THE API_Service SHALL persist the changes and return the updated user object.
2. WHEN an authenticated user submits a password change with the correct current password and a new password of at least 8 characters, THE Auth_Service SHALL update the password hash and invalidate all existing refresh tokens for that user.
3. WHEN an authenticated user submits a password change with an incorrect current password, THE Auth_Service SHALL return a 403 Forbidden error.
4. WHEN an authenticated user requests account deletion, THE API_Service SHALL delete the user record and cascade deletion to all associated projects, jobs, reports, documents, embeddings, and chat messages.

---

### Requirement 3: Project Management

**User Story:** As an authenticated user, I want to create and manage named projects, so that I can organise my research jobs by topic or client.

#### Acceptance Criteria

1. WHEN an authenticated user submits a create project request with a valid name (1–100 characters), THE API_Service SHALL create a project owned by that user and return the project object.
2. THE API_Service SHALL return only projects owned by the authenticated requesting user when a list projects query is received.
3. WHEN an authenticated user submits a rename or description update for a project they own, THE API_Service SHALL persist the changes and return the updated project object.
4. WHEN an authenticated user requests deletion of a project they own, THE API_Service SHALL delete the project and cascade deletion to all associated jobs, reports, documents, and embeddings.
5. WHEN an authenticated user attempts to access or modify a project they do not own, THE API_Service SHALL return a 403 Forbidden error.
6. THE API_Service SHALL include the count of research jobs and the timestamp of the last activity when returning a project object.

---

### Requirement 4: Research Job Submission

**User Story:** As an authenticated user, I want to submit a research query with configuration options, so that I can initiate an automated research workflow tailored to my needs.

#### Acceptance Criteria

1. WHEN an authenticated user submits a research job with a query between 10 and 500 characters and a valid project ID they own, THE API_Service SHALL create a ResearchJob record with status PENDING and trigger the AI_Service to begin processing.
2. WHEN a research job submission is received with a query shorter than 10 characters or longer than 500 characters, THE API_Service SHALL return a 422 Unprocessable Entity error with a descriptive validation message.
3. WHEN a research job submission is received with a project ID that does not belong to the authenticated user, THE API_Service SHALL return a 403 Forbidden error.
4. THE API_Service SHALL accept an optional LLM provider selection (BEDROCK or OPENAI) per research job, defaulting to BEDROCK when not specified.
5. THE API_Service SHALL accept an optional research depth selection (QUICK, STANDARD, or DEEP) per research job, defaulting to STANDARD when not specified.
6. THE API_Service SHALL accept up to 5 document IDs belonging to the same project to augment agent research.
7. WHEN a research job submission contains a query that violates the content policy (harmful or illegal content), THE API_Service SHALL reject the request with a 422 error before triggering the AI_Service.
8. WHEN an authenticated user requests cancellation of a running research job they own, THE API_Service SHALL update the job status to CANCELLED and instruct the AI_Service to terminate the workflow.

---

### Requirement 5: Multi-Agent LangGraph Orchestration

**User Story:** As a user who has submitted a research job, I want five specialised agents to collaboratively research and synthesise my query, so that I receive a thorough, multi-perspective report.

#### Acceptance Criteria

1. WHEN a research job is started, THE AI_Service SHALL execute the LangGraph_Workflow in the sequence: Planner_Agent → Research_Agent → Analysis_Agent → Critic_Agent → Synthesizer_Agent.
2. WHEN the LangGraph_Workflow is started, THE Planner_Agent SHALL decompose the research query into 3 to 5 focused, non-overlapping sub-questions and define a report structure.
3. WHEN the Planner_Agent has produced sub-questions, THE Research_Agent SHALL perform a Tavily web search for each sub-question and return the top 5 results per sub-question.
4. WHEN uploaded document IDs are associated with a research job, THE Research_Agent SHALL perform a pgvector semantic similarity search and return the top 3 document chunks per sub-question.
5. WHEN the Research_Agent has completed all sub-question searches, THE Analysis_Agent SHALL synthesise the combined research findings into a structured analysis draft with inline citations.
6. WHEN the Analysis_Agent has produced a draft, THE Critic_Agent SHALL evaluate the draft across the dimensions of factual accuracy, completeness, coherence, citation quality, and depth, and return a Critic_Score between 1.0 and 10.0 with written feedback.
7. WHEN the Critic_Score is below 7.0 and the revision count is less than 3, THE LangGraph_Workflow SHALL route execution back to the Analysis_Agent with the Critic_Agent's feedback included in the prompt.
8. WHEN the Critic_Score is 7.0 or above, OR the revision count has reached 3, THE LangGraph_Workflow SHALL route execution to the Synthesizer_Agent.
9. WHEN the Synthesizer_Agent executes, THE Synthesizer_Agent SHALL produce a final report in Markdown containing: a title, executive summary, main findings sections, conflicting viewpoints, key takeaways, recommended next steps, and a numbered citations list.
10. THE AI_Service SHALL persist the LangGraph_Workflow state to Redis after each agent transition to enable fault tolerance and resumability.
11. THE AI_Service SHALL select the LLM_Provider based on the environment variable LLM_PROVIDER without requiring a code change.
12. WHEN a research job is configured with QUICK depth, THE AI_Service SHALL execute only the Planner_Agent and Research_Agent before producing a summary output.
13. WHEN a research job is configured with DEEP depth, THE AI_Service SHALL execute the full five-agent workflow with a maximum of 3 Critic revision iterations.

---

### Requirement 6: Real-Time Agent Activity Streaming

**User Story:** As a user watching a running research job, I want to see each agent's activity update in real time, so that I can understand the research process and trust the output.

#### Acceptance Criteria

1. WHEN a research job is running, THE AI_Service SHALL publish agent state transition events to a Redis stream keyed by job ID.
2. WHEN a browser client connects to the SSE endpoint for a running or recently completed job, THE AI_Service SHALL stream all buffered and subsequent agent events as Server-Sent Events.
3. THE AI_Service SHALL include the following fields in each SSE event: agent name, status (running/complete/failed), progress percentage (0–100), ISO 8601 timestamp, and partial output where available.
4. WHEN a research job completes successfully, THE AI_Service SHALL emit a job_complete SSE event containing the job ID and report ID, then close the stream gracefully.
5. WHEN a research job fails, THE AI_Service SHALL emit a job_failed SSE event containing the job ID and a descriptive error message, then close the stream gracefully.
6. WHEN an SSE client disconnects and reconnects within 24 hours, THE AI_Service SHALL replay all events from the Redis stream from the point of disconnection.
7. THE AI_Service SHALL enforce JWT authentication on the SSE endpoint and reject unauthenticated connections with a 401 response.

---

### Requirement 7: Report Storage and Management

**User Story:** As a user with completed research, I want to view, export, and manage my reports, so that I can reference and share my research findings.

#### Acceptance Criteria

1. WHEN the Synthesizer_Agent completes a report, THE AI_Service SHALL upload the Markdown content to S3 and store the report metadata (title, S3 key, citation count, word count, Critic_Score, revision count) in PostgreSQL.
2. WHEN an authenticated user requests a report they own, THE API_Service SHALL return the full Markdown content and metadata.
3. THE API_Service SHALL return only reports associated with research jobs owned by the authenticated requesting user.
4. WHEN an authenticated user requests PDF export of a report they own, THE API_Service SHALL generate a PDF from the Markdown content and return a time-limited download URL.
5. WHEN an authenticated user requests Markdown export of a report they own, THE API_Service SHALL return a time-limited S3 download URL for the stored Markdown file.
6. WHEN an authenticated user requests regeneration of a report using the same query, THE API_Service SHALL create a new ResearchJob with the same query and configuration and trigger the AI_Service.
7. THE System SHALL retain all reports until the owning user explicitly deletes them or deletes their account.

---

### Requirement 8: Chat with Report (RAG)

**User Story:** As a user reviewing a completed report, I want to ask follow-up questions and receive answers grounded in the report content, so that I can explore specific findings in depth without re-running research.

#### Acceptance Criteria

1. WHEN an authenticated user sends a chat message for a report they own, THE AI_Service SHALL retrieve the 5 most semantically relevant report chunks from pgvector using the message as the query.
2. WHEN constructing the LLM prompt for a chat response, THE AI_Service SHALL include the retrieved report chunks, the last 5 messages from the chat history, and the user's current message.
3. WHEN the LLM generates a chat response, THE AI_Service SHALL stream the response token-by-token to the browser via Server-Sent Events.
4. THE API_Service SHALL persist each user message and assistant response to the chat_messages table, associated with the report ID and user ID.
5. THE API_Service SHALL enforce a maximum of 50 chat messages per report session; WHEN the limit is reached, THE API_Service SHALL return a 422 error instructing the user to clear the chat history.
6. WHEN an authenticated user requests clearing of chat history for a report they own, THE API_Service SHALL delete all chat_messages records for that report.
7. THE AI_Service SHALL embed report content into pgvector chunks when a report is first created, using the same embedding model as the configured LLM_Provider.

---

### Requirement 9: Document Upload and Embedding

**User Story:** As a user with proprietary or local knowledge, I want to upload PDF and TXT documents to a project, so that agents can incorporate that knowledge into my research.

#### Acceptance Criteria

1. WHEN an authenticated user uploads a PDF or TXT file of 10 MB or less to a project they own, THE API_Service SHALL store the file in S3 and create a Document record with status PROCESSING.
2. WHEN a document upload is received with a file larger than 10 MB, THE API_Service SHALL return a 422 error with the message "File exceeds the 10 MB size limit."
3. WHEN a document upload is received with a file type other than PDF or TXT, THE API_Service SHALL return a 422 error with the message "Unsupported file type. Only PDF and TXT files are accepted."
4. WHEN a document is stored in S3 with status PROCESSING, THE AI_Service SHALL asynchronously extract the text, chunk it into segments of 500 tokens with a 50-token overlap, generate embeddings using the configured LLM_Provider's embedding model, and store the chunks and embeddings in pgvector.
5. WHEN document embedding completes successfully, THE AI_Service SHALL update the Document status to READY.
6. WHEN document embedding fails, THE AI_Service SHALL update the Document status to FAILED and store a descriptive error message.
7. THE API_Service SHALL enforce a maximum of 5 documents per research job when a job is submitted.
8. WHEN an authenticated user requests deletion of a document they own, THE API_Service SHALL delete the S3 object, the Document record, and all associated Embedding records from pgvector.
9. THE API_Service SHALL return the current embedding status (PROCESSING, READY, or FAILED) when a document is queried.

---

### Requirement 10: Document and Report Parsing (Round-Trip Integrity)

**User Story:** As a developer maintaining the platform, I want document text extraction and report serialisation to be reliable and verifiable, so that data integrity is preserved throughout the pipeline.

#### Acceptance Criteria

1. WHEN a PDF document is processed, THE AI_Service SHALL extract all readable text content and produce a plain-text string representation.
2. WHEN a TXT document is processed, THE AI_Service SHALL read the file content as UTF-8 encoded plain text.
3. THE AI_Service SHALL format completed reports as valid Markdown that can be parsed by a standard Markdown parser.
4. FOR ALL valid Report Markdown strings, parsing the Markdown then rendering it to HTML then extracting the text SHALL produce content semantically equivalent to the original report text (round-trip property).
5. WHEN a report is serialised to JSON for the GraphQL API response, THE API_Service SHALL produce a JSON object that, when deserialised, yields a Report object with field values identical to the original (round-trip property).

---

### Requirement 11: LLM Provider Abstraction

**User Story:** As a developer or self-hoster, I want to switch between AWS Bedrock and OpenAI by changing an environment variable, so that I can optimise for cost or capability without modifying code.

#### Acceptance Criteria

1. THE AI_Service SHALL read the LLM_PROVIDER environment variable at startup and instantiate the corresponding LLM_Provider implementation (BedrockProvider or OpenAIProvider).
2. WHEN LLM_PROVIDER is set to "bedrock", THE AI_Service SHALL use Claude 3.5 Sonnet via AWS Bedrock for all LLM inference and Amazon Titan Embeddings for all embedding operations.
3. WHEN LLM_PROVIDER is set to "openai", THE AI_Service SHALL use GPT-4o via the OpenAI API for all LLM inference and text-embedding-3-small for all embedding operations.
4. WHEN LLM_PROVIDER is set to an unsupported value, THE AI_Service SHALL log a descriptive error and refuse to start.
5. THE AI_Service SHALL expose the same internal interface for chat completion and embedding regardless of which LLM_Provider is active, such that all agents and RAG components call the same methods.

---

### Requirement 12: Rate Limiting and Content Policy

**User Story:** As a platform operator, I want to enforce usage limits and content policies, so that the platform remains available and is not misused.

#### Acceptance Criteria

1. THE API_Service SHALL enforce a rate limit of 60 requests per minute per authenticated user across all general API endpoints.
2. THE API_Service SHALL enforce a rate limit of 5 research job submissions per hour per authenticated user.
3. WHEN a rate limit is exceeded, THE API_Service SHALL return a 429 Too Many Requests response with a Retry-After header indicating when the limit resets.
4. WHEN a research job query is submitted, THE API_Service SHALL evaluate the query against the content policy before triggering the AI_Service, and SHALL reject queries containing harmful or illegal content with a 422 error.

---

### Requirement 13: Non-Functional — Performance

**User Story:** As a user, I want the platform to respond quickly and complete research jobs in a reasonable time, so that I can use it productively.

#### Acceptance Criteria

1. THE API_Service SHALL respond to non-AI GraphQL queries at the 99th percentile within 200 milliseconds under normal load.
2. THE AI_Service SHALL complete a Standard-depth research job within 5 minutes from job start to report completion under normal load.
3. THE AI_Service SHALL deliver SSE agent events to the browser within 500 milliseconds of the corresponding agent state transition.
4. THE System SHALL support 100 concurrent authenticated user sessions without degradation of the p99 API response time beyond 200 milliseconds.
5. THE AI_Service SHALL return pgvector similarity search results within 100 milliseconds for a corpus of up to 1 million embeddings.

---

### Requirement 14: Non-Functional — Availability and Fault Tolerance

**User Story:** As a user and platform operator, I want the system to be highly available and resilient to failures, so that research jobs are not lost and the platform is reliably accessible.

#### Acceptance Criteria

1. THE System SHALL maintain 99.5% uptime excluding planned maintenance windows.
2. WHEN an agent node in the LangGraph_Workflow fails, THE AI_Service SHALL retry the failed node up to 3 times before marking the ResearchJob as FAILED.
3. WHEN a ResearchJob is marked FAILED after exhausting retries, THE AI_Service SHALL emit a job_failed SSE event with a descriptive error message and offer the user the option to retry.
4. THE AI_Service SHALL checkpoint the LangGraph_Workflow state to Redis after each agent transition so that a restarted service can resume an interrupted job.
5. WHILE a ResearchJob has status RUNNING, THE AI_Service SHALL maintain the LangGraph checkpoint in Redis with a TTL of at least 48 hours.
6. THE System SHALL deploy the RDS PostgreSQL instance and ElastiCache Redis instance across two availability zones to ensure multi-AZ redundancy.

---

### Requirement 15: Non-Functional — Security

**User Story:** As a user and platform operator, I want the platform to protect user data and prevent unauthorised access, so that research data remains private and secure.

#### Acceptance Criteria

1. THE API_Service SHALL reject all requests to authenticated GraphQL endpoints that do not include a valid, unexpired JWT access token with a 401 Unauthorized response.
2. THE API_Service SHALL enforce resource ownership on all data access operations, ensuring that a user can only read or modify resources associated with their own user ID.
3. THE System SHALL transmit all data over TLS 1.2 or higher and SHALL enable HSTS on all public-facing endpoints.
4. THE API_Service SHALL apply a strict CORS policy that allows requests only from the configured frontend domain.
5. THE System SHALL never store secrets, API keys, or credentials in source code or unencrypted environment files; all secrets SHALL be stored in Kubernetes Secrets or AWS Secrets Manager.
6. THE API_Service SHALL sanitise all user-supplied content rendered as HTML using DOMPurify to prevent cross-site scripting attacks.
7. THE System SHALL apply a Content-Security-Policy header on all frontend responses.

