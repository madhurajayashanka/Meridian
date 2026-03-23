# Meridian — Autonomous Multi-Agent Research Platform
## Complete Project Documentation
### PRD · SRS · Technical Specification · UI/UX · Deployment Guide

**Version:** 1.0.0  
**Author:** Madhura Jayashanka  
**Date:** 2026  
**Status:** Active Development  
**Repository:** github.com/madhurajayashanka/meridian  

---

## Table of Contents

1. [Product Requirements Document (PRD)](#1-product-requirements-document)
2. [Software Requirements Specification (SRS)](#2-software-requirements-specification)
3. [System Architecture](#3-system-architecture)
4. [Agent Design Specification](#4-agent-design-specification)
5. [API Specification](#5-api-specification)
6. [Database Schema](#6-database-schema)
7. [UI/UX Specification](#7-uiux-specification)
8. [Infrastructure & Deployment](#8-infrastructure--deployment)
9. [CI/CD Pipeline](#9-cicd-pipeline)
10. [Security Specification](#10-security-specification)
11. [Testing Specification](#11-testing-specification)
12. [Non-Functional Requirements](#12-non-functional-requirements)
13. [Implementation Roadmap](#13-implementation-roadmap)
14. [Open Source Contribution Guide](#14-open-source-contribution-guide)

---

# 1. Product Requirements Document

## 1.1 Executive Summary

Meridian is an open-source autonomous multi-agent AI research platform. Users submit a research question and Meridian deploys a coordinated team of five specialised AI agents that search the web, retrieve relevant knowledge, analyse findings, self-critique the output, and synthesise a comprehensive, cited research report — all in real time with a live visual feed of agent activity.

Meridian targets developers, researchers, and knowledge workers who need structured, reliable, well-cited research output on complex topics without manually aggregating dozens of sources.

## 1.2 Problem Statement

| Problem | Impact |
|---|---|
| Manual research is slow — aggregating sources takes hours | Lost productivity |
| LLM outputs are often hallucinated or uncited | Trust issues, unusable in professional contexts |
| Existing AI research tools (Perplexity, ChatGPT) are black boxes | No transparency into reasoning process |
| No open-source, self-hostable alternative exists | Lock-in to paid, proprietary services |
| Single-agent LLM systems produce shallow analysis | Poor output quality on complex topics |

## 1.3 Solution

Meridian solves this by orchestrating five specialised agents in a stateful LangGraph workflow with a self-critique revision loop. The platform is fully transparent — users see each agent's activity in real time. It is self-hostable, open source, and LLM-provider agnostic (supports AWS Bedrock and OpenAI interchangeably).

## 1.4 Target Users

### Primary Users
- **Developers and engineers** researching technical topics (architecture decisions, library comparisons, security vulnerabilities)
- **Students and researchers** conducting academic literature reviews
- **Product managers and analysts** performing competitive and market research
- **Technical writers** gathering material for documentation and articles

### Secondary Users
- **Teams and organisations** self-hosting Meridian as an internal research tool
- **Open source contributors** building on the Meridian codebase

## 1.5 User Personas

### Persona 1 — Kavya, Senior Software Engineer
- **Age:** 28, Colombo
- **Goal:** Research "best practices for multi-tenant SaaS architecture on AWS" before an architecture review meeting
- **Pain point:** Spending 3 hours reading blog posts and Stack Overflow threads, most of which are outdated
- **How Meridian helps:** Submits the query, gets a structured 1500-word report with citations in under 5 minutes

### Persona 2 — Dinesh, Undergraduate Researcher
- **Age:** 22, University of Moratuwa
- **Goal:** Literature review on "machine learning applications in crop disease detection"
- **Pain point:** Cannot access all papers behind paywalls; struggles to synthesise findings from 20+ sources
- **How Meridian helps:** Uploads accessible PDFs + runs web research; Meridian synthesises across all sources

### Persona 3 — Samantha, Product Manager
- **Age:** 32, Remote
- **Goal:** Competitive analysis: "How are the top 5 project management SaaS tools differentiating in 2025?"
- **Pain point:** Manual research produces biased results depending on which sources she happens to find
- **How Meridian helps:** Structured, multi-source analysis with conflicting viewpoints surfaced automatically

## 1.6 Product Goals

### Business Goals
1. Become the leading open-source multi-agent research platform on GitHub (target: 1,000 stars within 3 months of launch)
2. Demonstrate production-grade AI engineering patterns for the developer community
3. Establish Meridian as a reference implementation for LangGraph multi-agent systems

### User Goals
1. Reduce research time from hours to minutes for complex topics
2. Produce trustworthy, cited output that users can verify
3. Enable transparent research via real-time agent visibility

### Technical Goals
1. Demonstrate polyglot microservices architecture (FastAPI + Spring Boot)
2. Production K8s deployment on AWS EKS via Terraform
3. LLM-agnostic design supporting Bedrock and OpenAI interchangeably

## 1.7 Success Metrics

| Metric | Target | Measurement |
|---|---|---|
| GitHub Stars | 500+ in month 1 | GitHub API |
| Research job completion rate | >95% | Prometheus / CloudWatch |
| Average report generation time | <4 minutes | Custom metric |
| Agent revision loop trigger rate | 20–40% of jobs | Application logs |
| p99 API response latency | <200ms (non-AI endpoints) | Grafana |
| User retention (return visits) | >40% week 2 | Analytics |

## 1.8 Out of Scope (v1.0)

- Real-time collaborative editing of reports
- Mobile native applications
- Voice input / output
- Billing and payment processing
- Team workspaces with shared permissions
- Integration with external knowledge bases (Notion, Confluence)
- Fine-tuned or custom-trained models

---

# 2. Software Requirements Specification

## 2.1 Functional Requirements

### 2.1.1 Authentication & User Management

| ID | Requirement | Priority |
|---|---|---|
| FR-AUTH-01 | Users shall be able to register with email and password | Must Have |
| FR-AUTH-02 | Users shall be able to log in and receive a JWT access token and refresh token | Must Have |
| FR-AUTH-03 | Access tokens shall expire after 15 minutes; refresh tokens after 7 days | Must Have |
| FR-AUTH-04 | Users shall be able to update their profile (name, avatar) | Should Have |
| FR-AUTH-05 | Users shall be able to change their password | Should Have |
| FR-AUTH-06 | Users shall be able to delete their account and all associated data | Should Have |
| FR-AUTH-07 | System shall support OAuth2 login via Google (v1.1) | Could Have |

### 2.1.2 Project Management

| ID | Requirement | Priority |
|---|---|---|
| FR-PROJ-01 | Users shall be able to create named projects to organise research jobs | Must Have |
| FR-PROJ-02 | Users shall be able to view all projects in a dashboard | Must Have |
| FR-PROJ-03 | Users shall be able to rename and delete projects | Must Have |
| FR-PROJ-04 | Each project shall display the count of research jobs and last activity date | Should Have |
| FR-PROJ-05 | Users shall be able to archive projects without deleting them | Could Have |

### 2.1.3 Research Job Submission

| ID | Requirement | Priority |
|---|---|---|
| FR-JOB-01 | Users shall be able to submit a research query (10–500 characters) | Must Have |
| FR-JOB-02 | Users shall be able to associate a research job with a project | Must Have |
| FR-JOB-03 | Users shall be able to select the LLM provider (Bedrock / OpenAI) per job | Should Have |
| FR-JOB-04 | Users shall be able to set research depth: Quick (2 agents), Standard (5 agents), Deep (5 agents + extra iterations) | Should Have |
| FR-JOB-05 | Users shall be able to upload up to 5 PDF/TXT documents to augment agent research | Should Have |
| FR-JOB-06 | System shall reject queries that violate content policy (harmful, illegal content) | Must Have |
| FR-JOB-07 | Users shall be able to cancel a running research job | Should Have |

### 2.1.4 Agent Orchestration

| ID | Requirement | Priority |
|---|---|---|
| FR-AGENT-01 | System shall orchestrate 5 specialised agents in a LangGraph state machine | Must Have |
| FR-AGENT-02 | Planner Agent shall decompose the query into 3–5 sub-questions | Must Have |
| FR-AGENT-03 | Research Agent shall perform web search using Tavily API for each sub-question | Must Have |
| FR-AGENT-04 | Research Agent shall perform semantic search over user-uploaded documents via pgvector | Must Have |
| FR-AGENT-05 | Analysis Agent shall synthesise research findings using retrieved context | Must Have |
| FR-AGENT-06 | Critic Agent shall evaluate analysis output and return a score (1–10) with critique | Must Have |
| FR-AGENT-07 | System shall re-route to Analysis Agent if Critic score is below 7 (max 3 iterations) | Must Have |
| FR-AGENT-08 | Synthesizer Agent shall produce the final structured report in Markdown | Must Have |
| FR-AGENT-09 | Agent state shall be persisted in Redis for fault tolerance and resumability | Must Have |
| FR-AGENT-10 | System shall support swapping LLM provider via environment variable without code change | Must Have |

### 2.1.5 Real-Time Streaming

| ID | Requirement | Priority |
|---|---|---|
| FR-STREAM-01 | System shall stream agent status events to the frontend in real time via SSE | Must Have |
| FR-STREAM-02 | Each SSE event shall include: agent name, status, progress percentage, timestamp, partial output | Must Have |
| FR-STREAM-03 | Frontend shall display each agent as a live card updating in real time | Must Have |
| FR-STREAM-04 | System shall handle SSE reconnection automatically on client disconnect | Should Have |
| FR-STREAM-05 | Streaming shall terminate gracefully when job completes or fails | Must Have |

### 2.1.6 Report Management

| ID | Requirement | Priority |
|---|---|---|
| FR-RPT-01 | Completed reports shall be stored in S3 (Markdown) and PostgreSQL (metadata) | Must Have |
| FR-RPT-02 | Users shall be able to view, scroll, and navigate reports with section headers | Must Have |
| FR-RPT-03 | Reports shall include: executive summary, main findings, conflicting viewpoints, citations, key takeaways, recommended next steps | Must Have |
| FR-RPT-04 | Users shall be able to export reports as PDF and Markdown | Should Have |
| FR-RPT-05 | Users shall be able to share reports via public link | Could Have |
| FR-RPT-06 | Users shall be able to regenerate a report with the same query | Should Have |
| FR-RPT-07 | System shall store all reports indefinitely until user deletes them | Must Have |

### 2.1.7 Chat with Report (RAG)

| ID | Requirement | Priority |
|---|---|---|
| FR-CHAT-01 | Users shall be able to ask follow-up questions about a completed report | Must Have |
| FR-CHAT-02 | System shall perform RAG over the report's content stored in pgvector | Must Have |
| FR-CHAT-03 | Chat responses shall stream token-by-token to the frontend | Must Have |
| FR-CHAT-04 | System shall maintain chat history per report session (max 50 messages) | Must Have |
| FR-CHAT-05 | Users shall be able to clear chat history | Should Have |
| FR-CHAT-06 | Chat context shall include the 5 most relevant report chunks + last 5 messages | Must Have |

### 2.1.8 Document Management

| ID | Requirement | Priority |
|---|---|---|
| FR-DOC-01 | Users shall be able to upload PDF and TXT files (max 10MB each, max 5 per job) | Must Have |
| FR-DOC-02 | System shall extract text, chunk, embed, and store in pgvector on upload | Must Have |
| FR-DOC-03 | Documents shall be associated with a project and available to all jobs in that project | Should Have |
| FR-DOC-04 | Users shall be able to view and delete uploaded documents | Must Have |
| FR-DOC-05 | System shall display embedding status: processing / ready / failed | Must Have |

## 2.2 Use Cases

### UC-01: Submit Research Job

**Actor:** Authenticated user  
**Precondition:** User is logged in and has at least one project  
**Main Flow:**
1. User navigates to a project
2. User clicks "New Research"
3. User enters research query in the text field
4. User optionally selects LLM provider and research depth
5. User optionally uploads supporting documents
6. User clicks "Start Research"
7. System creates a ResearchJob record with status PENDING
8. System triggers AI service via internal REST call
9. System redirects user to the job live view
10. Frontend connects to SSE stream for real-time updates
11. Agents execute sequentially/in parallel per the LangGraph workflow
12. Job completes; system stores report; frontend displays report

**Alternative Flow — Job Fails:**
- Step 11a: If any agent fails after 3 retries, system marks job FAILED
- System sends SSE event with error details
- User is offered the option to retry

**Postcondition:** Report is stored and available in project history

---

### UC-02: Chat with Report

**Actor:** Authenticated user  
**Precondition:** User has a completed research report  
**Main Flow:**
1. User opens a completed report
2. User clicks the "Chat" tab
3. User types a follow-up question
4. System retrieves top 5 relevant chunks from pgvector
5. System constructs prompt: system message + retrieved context + chat history + user question
6. System streams LLM response token-by-token via SSE
7. Response is appended to chat history in PostgreSQL

**Postcondition:** Chat message and response stored; user can continue conversation

---

### UC-03: Upload Document

**Actor:** Authenticated user  
**Precondition:** User is in a project  
**Main Flow:**
1. User navigates to project Documents section
2. User clicks "Upload Document"
3. User selects PDF or TXT file (max 10MB)
4. System validates file type and size
5. System uploads file to S3
6. System triggers async background task: extract text → chunk (500 tokens, 50 overlap) → embed (AWS Bedrock Titan Embeddings / OpenAI text-embedding-3-small) → store in pgvector
7. System shows "Processing" status; updates to "Ready" when complete

**Alternative Flow — Unsupported File:**
- Step 4a: System returns 422 error; user sees "Unsupported file type" message

---

### UC-04: View Agent Activity Live

**Actor:** Authenticated user  
**Precondition:** Research job is running  
**Main Flow:**
1. User is on the job live view page
2. Frontend has established SSE connection to `/ai/stream/{job_id}`
3. System emits events as each agent transitions state
4. Frontend renders 5 agent cards; each card updates colour and progress in real time:
   - Grey = IDLE, Blue = RUNNING, Green = COMPLETE, Red = FAILED
5. User can see partial output for each agent as it generates
6. On job completion, "View Report" button appears

---

## 2.3 User Stories

```
As a user, I want to submit a research question and receive a structured report
so that I can conduct research without spending hours manually reading sources.

As a user, I want to see each AI agent working in real time
so that I understand how the report was generated and can trust the output.

As a user, I want to chat with my completed report
so that I can explore specific points in more depth without re-running research.

As a user, I want to upload my own documents
so that agents can incorporate proprietary or local knowledge into the research.

As a user, I want to organise research jobs into projects
so that I can manage research across multiple topics or clients.

As a user, I want to export reports as PDF
so that I can share them with colleagues who don't use Meridian.

As a developer, I want to self-host Meridian on my own infrastructure
so that my research data stays within my organisation.

As a developer, I want to swap between AWS Bedrock and OpenAI via config
so that I can optimise for cost or capability without changing code.
```

---

# 3. System Architecture

## 3.1 Architecture Overview

Meridian follows a **polyglot microservices architecture** with three deployable services:

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
              │   K8s Ingress   │  (nginx, TLS termination)
              └────┬────────┬───┘
                   │        │
        ┌──────────▼──┐  ┌──▼──────────────┐
        │  React SPA  │  │  Spring Boot API │  :8080
        │  (Next.js)  │  │  (GraphQL + REST)│
        └─────────────┘  └──────┬───────────┘
                                 │ Internal REST
                         ┌───────▼───────────┐
                         │   FastAPI AI Svc  │  :8000
                         │   (LangGraph)     │
                         └──┬────────────────┘
                            │
              ┌─────────────┼──────────────────┐
              │             │                  │
     ┌────────▼──┐  ┌───────▼────┐  ┌─────────▼──────┐
     │PostgreSQL │  │   Redis    │  │   AWS Bedrock  │
     │+ pgvector │  │(State/PubSub)│  │  / OpenAI API  │
     └───────────┘  └────────────┘  └────────────────┘
              │
     ┌────────▼──────┐
     │   AWS S3      │
     │(Docs/Reports) │
     └───────────────┘
```

## 3.2 Service Responsibilities

### React Frontend (Next.js + TypeScript)
- Server-side rendered pages for SEO on public pages
- Client-side SPA for authenticated app
- Apollo Client for GraphQL communication with Spring Boot
- EventSource API for SSE streaming from FastAPI
- Zustand for local UI state management

### Spring Boot Core API (Java 21)
- GraphQL API (Spring for GraphQL)
- User authentication and authorisation (Spring Security + JWT)
- User, Project, ResearchJob, Report CRUD
- Document metadata management
- Internal REST calls to FastAPI AI service
- PostgreSQL persistence via Spring Data JPA

### FastAPI AI Service (Python 3.11)
- LangGraph multi-agent workflow execution
- LangChain tool integrations (web search, RAG)
- LLM provider abstraction (Bedrock / OpenAI)
- pgvector embedding storage and retrieval
- Redis pub/sub for agent event streaming
- SSE endpoint for real-time frontend updates
- S3 document upload and report storage
- Background task processing (FastAPI BackgroundTasks)

## 3.3 Communication Patterns

| From | To | Protocol | Purpose |
|---|---|---|---|
| Browser | Spring Boot | GraphQL over HTTPS | CRUD operations, auth |
| Browser | FastAPI | SSE over HTTPS | Real-time agent streaming |
| Spring Boot | FastAPI | REST (internal, no auth) | Trigger research jobs |
| FastAPI | PostgreSQL | SQL (asyncpg) | Read/write data |
| FastAPI | Redis | Redis pub/sub | Agent event broadcasting |
| FastAPI | AWS Bedrock | AWS SDK (boto3) | LLM inference |
| FastAPI | OpenAI | openai Python SDK | LLM inference (dev) |
| FastAPI | Tavily | HTTP REST | Web search |
| FastAPI | S3 | AWS SDK (boto3) | Document/report storage |

## 3.4 Data Flow — Research Job

```
1. User → GraphQL mutation (createResearchJob) → Spring Boot
2. Spring Boot → PostgreSQL (INSERT research_jobs status=PENDING)
3. Spring Boot → FastAPI POST /jobs/start {job_id, query, config}
4. FastAPI → Redis SET job:{id}:state = initial_state
5. FastAPI → LangGraph.invoke() (async, background task)

6. LangGraph: PLANNER node runs
   └→ FastAPI → Redis PUBLISH job:{id}:events {agent:planner, status:running}
   └→ FastAPI → Redis PUBLISH job:{id}:events {agent:planner, status:complete, output:sub_questions}

7. LangGraph: RESEARCH nodes run (parallel)
   └→ FastAPI → Tavily API (web search per sub-question)
   └→ FastAPI → pgvector (semantic search over user docs)
   └→ Redis PUBLISH events per research agent

8. LangGraph: ANALYSIS node runs
   └→ FastAPI → LLM (Bedrock or OpenAI) with research context
   └→ Redis PUBLISH events

9. LangGraph: CRITIC node runs
   └→ FastAPI → LLM (evaluate analysis, return score)
   └→ If score < 7 → route back to ANALYSIS (max 3 times)
   └→ Redis PUBLISH critique event

10. LangGraph: SYNTHESIZER node runs
    └→ FastAPI → LLM (generate final report Markdown)
    └→ FastAPI → S3 (upload report.md)
    └→ FastAPI → PostgreSQL (UPDATE research_jobs status=COMPLETE, report_url)
    └→ Redis PUBLISH {agent:synthesizer, status:complete}

11. SSE stream → browser (all events in real time throughout steps 6–10)
12. Browser receives completion event → fetches report via GraphQL query
```

---

# 4. Agent Design Specification

## 4.1 LangGraph State Definition

```python
from typing import TypedDict, List, Optional, Annotated
from langgraph.graph.message import add_messages

class ResearchState(TypedDict):
    # Input
    job_id: str
    query: str
    llm_provider: str                    # "bedrock" | "openai"
    research_depth: str                  # "quick" | "standard" | "deep"
    uploaded_doc_ids: List[str]

    # Planner output
    sub_questions: List[str]
    report_structure: dict               # {sections: [...]}

    # Research output
    research_results: List[dict]         # [{sub_question, web_results, doc_results}]

    # Analysis
    analysis_draft: str
    analysis_iteration: int              # tracks revision count

    # Critic
    critic_score: float                  # 1.0 – 10.0
    critic_feedback: str
    critic_iterations: int               # max 3

    # Final
    final_report: str                    # Markdown
    citations: List[dict]                # [{title, url, snippet}]

    # Metadata
    agent_logs: Annotated[List[dict], add_messages]
    status: str                          # "running" | "complete" | "failed"
    error: Optional[str]
```

## 4.2 Agent 1 — Planner

**Purpose:** Decompose the research query into focused sub-questions and define the report structure.

**Input:** `query`, `research_depth`  
**Output:** `sub_questions` (list), `report_structure` (dict)

**System Prompt:**
```
You are a research planning expert. Given a research query, your job is to:
1. Identify 3-5 focused sub-questions that together fully answer the main query
2. Define a clear report structure with section titles
3. Return structured JSON only

The sub-questions should be:
- Specific and searchable (can be answered by a web search)
- Non-overlapping
- Together comprehensive enough to answer the main query

Return JSON: {"sub_questions": [...], "report_structure": {"sections": [...]}}
```

**LangGraph Node:**
```python
async def planner_node(state: ResearchState) -> ResearchState:
    await emit_event(state["job_id"], "planner", "running", 0)
    
    llm = get_llm(state["llm_provider"])
    response = await llm.ainvoke(PLANNER_PROMPT.format(query=state["query"]))
    parsed = parse_json_response(response)
    
    await emit_event(state["job_id"], "planner", "complete", 100, parsed)
    return {**state, "sub_questions": parsed["sub_questions"],
            "report_structure": parsed["report_structure"]}
```

## 4.3 Agent 2 — Research

**Purpose:** Gather evidence for each sub-question via web search and document RAG.

**Input:** `sub_questions`, `uploaded_doc_ids`, `job_id`  
**Output:** `research_results` (list of findings per sub-question)

**Tools:**
- `TavilySearchTool` — web search (returns title, URL, snippet, full content)
- `PGVectorRetriever` — semantic search over user-uploaded documents

**Process per sub-question:**
1. Run Tavily search → top 5 results
2. Run pgvector similarity search → top 3 document chunks
3. Combine and deduplicate results
4. Store: `{sub_question, web_results: [...], doc_results: [...], combined_context: str}`

**Node runs in parallel** — one Research Agent instance per sub-question:
```python
async def research_node(state: ResearchState) -> ResearchState:
    tasks = [research_sub_question(sq, state) for sq in state["sub_questions"]]
    results = await asyncio.gather(*tasks)
    return {**state, "research_results": results}
```

## 4.4 Agent 3 — Analysis

**Purpose:** Synthesise research findings into a structured analysis.

**Input:** `research_results`, `report_structure`, `critic_feedback` (on revision)  
**Output:** `analysis_draft` (string)

**System Prompt:**
```
You are an expert research analyst. Using the provided research findings, write a 
detailed analysis that:
- Directly answers each sub-question with evidence
- Surfaces conflicting viewpoints and data
- Cites sources inline using [1], [2] notation
- Follows the provided report structure
- Is factual, balanced, and well-organised

{critic_feedback_section}

Research findings:
{research_context}

Report structure to follow:
{report_structure}
```

**On revision:** The `critic_feedback_section` is populated with the Critic's specific critique, directing the Analysis Agent to address identified weaknesses.

## 4.5 Agent 4 — Critic

**Purpose:** Evaluate the analysis draft for quality, accuracy, and completeness. Trigger revision if below threshold.

**Input:** `analysis_draft`, `research_results`, `query`  
**Output:** `critic_score` (float), `critic_feedback` (string)

**Evaluation dimensions:**
| Dimension | Weight | Description |
|---|---|---|
| Factual accuracy | 30% | Claims are supported by provided sources |
| Completeness | 25% | All sub-questions are addressed |
| Coherence | 20% | Logical flow, no contradictions |
| Citation quality | 15% | Sources are correctly cited and relevant |
| Depth | 10% | Analysis goes beyond surface-level summaries |

**Scoring:**
- Score ≥ 7.0 → proceed to Synthesizer
- Score < 7.0 AND `critic_iterations` < 3 → route back to Analysis with feedback
- Score < 7.0 AND `critic_iterations` >= 3 → proceed to Synthesizer with warning flag

**Conditional Edge:**
```python
def should_revise(state: ResearchState) -> str:
    if state["critic_score"] < 7.0 and state["critic_iterations"] < 3:
        return "analysis"   # route back
    return "synthesizer"    # proceed
```

## 4.6 Agent 5 — Synthesizer

**Purpose:** Produce the final polished research report in Markdown.

**Input:** `analysis_draft`, `research_results`, `report_structure`, `query`  
**Output:** `final_report` (Markdown string), `citations` (list)

**Output format:**
```markdown
# {Report Title}

**Research Query:** {query}  
**Generated:** {timestamp}  
**Sources:** {source_count} web sources, {doc_count} uploaded documents  

---

## Executive Summary
{2-3 paragraph summary of key findings}

## {Section 1 Title}
{content with inline citations [1][2]}

## {Section N Title}
{content}

## Conflicting Viewpoints
{areas where sources disagreed}

## Key Takeaways
- {takeaway 1}
- {takeaway 2}
- {takeaway 3}

## Recommended Next Steps
1. {action 1}
2. {action 2}

## Citations
[1] {Title} — {URL}
[2] {Title} — {URL}
```

## 4.7 LangGraph Workflow Definition

```python
from langgraph.graph import StateGraph, END
from langgraph.checkpoint.redis import RedisSaver

def build_research_graph(redis_client) -> StateGraph:
    graph = StateGraph(ResearchState)

    # Add nodes
    graph.add_node("planner", planner_node)
    graph.add_node("research", research_node)
    graph.add_node("analysis", analysis_node)
    graph.add_node("critic", critic_node)
    graph.add_node("synthesizer", synthesizer_node)

    # Add edges
    graph.set_entry_point("planner")
    graph.add_edge("planner", "research")
    graph.add_edge("research", "analysis")
    graph.add_edge("analysis", "critic")
    graph.add_conditional_edges(
        "critic",
        should_revise,
        {"analysis": "analysis", "synthesizer": "synthesizer"}
    )
    graph.add_edge("synthesizer", END)

    # Redis checkpointing for fault tolerance
    checkpointer = RedisSaver(redis_client)
    return graph.compile(checkpointer=checkpointer)
```

## 4.8 LLM Provider Abstraction

```python
from abc import ABC, abstractmethod
from langchain_aws import ChatBedrock
from langchain_openai import ChatOpenAI

class LLMProvider(ABC):
    @abstractmethod
    def get_chat_model(self, temperature: float = 0.3): pass
    
    @abstractmethod
    def get_embedding_model(self): pass

class BedrockProvider(LLMProvider):
    def get_chat_model(self, temperature=0.3):
        return ChatBedrock(
            model_id="anthropic.claude-3-5-sonnet-20241022-v2:0",
            model_kwargs={"temperature": temperature, "max_tokens": 4096}
        )
    
    def get_embedding_model(self):
        return BedrockEmbeddings(model_id="amazon.titan-embed-text-v2:0")

class OpenAIProvider(LLMProvider):
    def get_chat_model(self, temperature=0.3):
        return ChatOpenAI(model="gpt-4o", temperature=temperature)
    
    def get_embedding_model(self):
        return OpenAIEmbeddings(model="text-embedding-3-small")

def get_provider(provider_name: str) -> LLMProvider:
    providers = {"bedrock": BedrockProvider, "openai": OpenAIProvider}
    return providers[provider_name]()
```

---

# 5. API Specification

## 5.1 GraphQL API (Spring Boot — Port 8080)

### Schema

```graphql
scalar DateTime
scalar Upload

# ── Types ──────────────────────────────────────

type User {
  id: ID!
  email: String!
  name: String!
  avatarUrl: String
  createdAt: DateTime!
  projects: [Project!]!
}

type Project {
  id: ID!
  name: String!
  description: String
  jobCount: Int!
  lastActivityAt: DateTime
  createdAt: DateTime!
  researchJobs: [ResearchJob!]!
  documents: [Document!]!
}

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

type ChatMessage {
  id: ID!
  role: MessageRole!
  content: String!
  createdAt: DateTime!
}

type Document {
  id: ID!
  filename: String!
  fileSize: Int!
  status: DocumentStatus!
  uploadedAt: DateTime!
  s3Url: String!
}

type AuthPayload {
  accessToken: String!
  refreshToken: String!
  user: User!
}

# ── Enums ──────────────────────────────────────

enum JobStatus { PENDING RUNNING COMPLETE FAILED CANCELLED }
enum LLMProvider { BEDROCK OPENAI }
enum ResearchDepth { QUICK STANDARD DEEP }
enum MessageRole { USER ASSISTANT }
enum DocumentStatus { PROCESSING READY FAILED }

# ── Queries ────────────────────────────────────

type Query {
  me: User!
  project(id: ID!): Project!
  projects: [Project!]!
  researchJob(id: ID!): ResearchJob!
  report(id: ID!): Report!
  chatHistory(reportId: ID!): [ChatMessage!]!
  documents(projectId: ID!): [Document!]!
}

# ── Mutations ──────────────────────────────────

type Mutation {
  # Auth
  register(input: RegisterInput!): AuthPayload!
  login(input: LoginInput!): AuthPayload!
  refreshToken(refreshToken: String!): AuthPayload!
  updateProfile(input: UpdateProfileInput!): User!
  changePassword(input: ChangePasswordInput!): Boolean!
  deleteAccount: Boolean!

  # Projects
  createProject(input: CreateProjectInput!): Project!
  updateProject(id: ID!, input: UpdateProjectInput!): Project!
  deleteProject(id: ID!): Boolean!

  # Research Jobs
  createResearchJob(input: CreateResearchJobInput!): ResearchJob!
  cancelResearchJob(id: ID!): ResearchJob!
  regenerateReport(jobId: ID!): ResearchJob!

  # Documents
  deleteDocument(id: ID!): Boolean!

  # Reports
  exportReport(id: ID!, format: ExportFormat!): String!  # returns download URL
}

# ── Input Types ────────────────────────────────

input RegisterInput { email: String!, password: String!, name: String! }
input LoginInput { email: String!, password: String! }
input UpdateProfileInput { name: String, avatarUrl: String }
input ChangePasswordInput { currentPassword: String!, newPassword: String! }
input CreateProjectInput { name: String!, description: String }
input UpdateProjectInput { name: String, description: String }
input CreateResearchJobInput {
  projectId: ID!
  query: String!
  llmProvider: LLMProvider = BEDROCK
  researchDepth: ResearchDepth = STANDARD
  documentIds: [ID!]
}
enum ExportFormat { PDF MARKDOWN }
```

## 5.2 FastAPI REST Endpoints

### Base URL: `/api/v1` (internal) · `/ai` (SSE, accessible from browser)

| Method | Path | Auth | Description |
|---|---|---|---|
| `POST` | `/api/v1/jobs/start` | Internal | Trigger research job (called by Spring Boot) |
| `POST` | `/api/v1/jobs/{id}/cancel` | Internal | Cancel running job |
| `GET` | `/ai/stream/{job_id}` | JWT | SSE stream for job events |
| `POST` | `/api/v1/chat` | Internal | Chat message with RAG |
| `POST` | `/api/v1/documents/process` | Internal | Process uploaded document |
| `GET` | `/api/v1/health` | None | Health check |

### POST /api/v1/jobs/start

**Request:**
```json
{
  "job_id": "uuid",
  "query": "What are the best practices for...",
  "llm_provider": "bedrock",
  "research_depth": "standard",
  "document_ids": ["uuid1", "uuid2"],
  "user_id": "uuid"
}
```

**Response (202 Accepted):**
```json
{
  "job_id": "uuid",
  "status": "running",
  "message": "Research job started"
}
```

### GET /ai/stream/{job_id}

**Headers:** `Authorization: Bearer {jwt}`  
**Response:** `text/event-stream`

**SSE Event format:**
```
event: agent_update
data: {"agent": "planner", "status": "running", "progress": 20, "timestamp": "...", "partial_output": null}

event: agent_update  
data: {"agent": "planner", "status": "complete", "progress": 100, "timestamp": "...", "partial_output": {"sub_questions": [...]}}

event: job_complete
data: {"job_id": "uuid", "report_id": "uuid", "status": "complete"}

event: job_failed
data: {"job_id": "uuid", "error": "LLM rate limit exceeded", "status": "failed"}
```

### POST /api/v1/chat

**Request:**
```json
{
  "report_id": "uuid",
  "message": "Can you elaborate on the cost implications?",
  "chat_history": [
    {"role": "user", "content": "..."},
    {"role": "assistant", "content": "..."}
  ]
}
```

**Response:** Streaming (Server-Sent Events, token by token)

---

# 6. Database Schema

## 6.1 PostgreSQL Schema

```sql
-- Enable pgvector extension
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ── Users ───────────────────────────────────────────────────

CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    email           VARCHAR(255) UNIQUE NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    name            VARCHAR(100) NOT NULL,
    avatar_url      TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ
);

CREATE TABLE refresh_tokens (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash      VARCHAR(255) UNIQUE NOT NULL,
    expires_at      TIMESTAMPTZ NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ── Projects ─────────────────────────────────────────────────

CREATE TABLE projects (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name            VARCHAR(100) NOT NULL,
    description     TEXT,
    is_archived     BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ── Documents ─────────────────────────────────────────────────

CREATE TABLE documents (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    project_id      UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    user_id         UUID NOT NULL REFERENCES users(id),
    filename        VARCHAR(255) NOT NULL,
    file_size       INTEGER NOT NULL,
    mime_type       VARCHAR(100) NOT NULL,
    s3_key          TEXT NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'processing'
                    CHECK (status IN ('processing', 'ready', 'failed')),
    error_message   TEXT,
    uploaded_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ── Research Jobs ─────────────────────────────────────────────

CREATE TABLE research_jobs (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    project_id      UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    user_id         UUID NOT NULL REFERENCES users(id),
    query           TEXT NOT NULL,
    llm_provider    VARCHAR(20) NOT NULL DEFAULT 'bedrock'
                    CHECK (llm_provider IN ('bedrock', 'openai')),
    research_depth  VARCHAR(20) NOT NULL DEFAULT 'standard'
                    CHECK (research_depth IN ('quick', 'standard', 'deep')),
    status          VARCHAR(20) NOT NULL DEFAULT 'pending'
                    CHECK (status IN ('pending', 'running', 'complete', 'failed', 'cancelled')),
    document_ids    UUID[] DEFAULT '{}',
    error_message   TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    started_at      TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ
);

-- ── Reports ──────────────────────────────────────────────────

CREATE TABLE reports (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    job_id          UUID UNIQUE NOT NULL REFERENCES research_jobs(id) ON DELETE CASCADE,
    title           VARCHAR(500) NOT NULL,
    s3_key          TEXT NOT NULL,
    markdown_content TEXT,
    citation_count  INTEGER NOT NULL DEFAULT 0,
    word_count      INTEGER NOT NULL DEFAULT 0,
    source_count    INTEGER NOT NULL DEFAULT 0,
    critic_score    DECIMAL(3,1),
    revision_count  INTEGER NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ── Chat Messages ─────────────────────────────────────────────

CREATE TABLE chat_messages (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    report_id       UUID NOT NULL REFERENCES reports(id) ON DELETE CASCADE,
    user_id         UUID NOT NULL REFERENCES users(id),
    role            VARCHAR(10) NOT NULL CHECK (role IN ('user', 'assistant')),
    content         TEXT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ── Vector Embeddings ─────────────────────────────────────────
-- Stores chunked text + embeddings from documents AND report content

CREATE TABLE embeddings (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    source_type     VARCHAR(20) NOT NULL CHECK (source_type IN ('document', 'report')),
    source_id       UUID NOT NULL,           -- document.id or report.id
    project_id      UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    chunk_index     INTEGER NOT NULL,
    chunk_text      TEXT NOT NULL,
    embedding       vector(1536),             -- 1536 for OpenAI, 1024 for Titan
    metadata        JSONB,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ── Indexes ──────────────────────────────────────────────────

CREATE INDEX idx_projects_user_id ON projects(user_id);
CREATE INDEX idx_research_jobs_project_id ON research_jobs(project_id);
CREATE INDEX idx_research_jobs_status ON research_jobs(status);
CREATE INDEX idx_documents_project_id ON documents(project_id);
CREATE INDEX idx_chat_messages_report_id ON chat_messages(report_id);
CREATE INDEX idx_embeddings_source ON embeddings(source_type, source_id);

-- Vector similarity index (HNSW for fast approximate search)
CREATE INDEX idx_embeddings_vector ON embeddings
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);
```

## 6.2 Redis Data Structures

```
# Job state (LangGraph checkpointing)
KEY: langgraph:checkpoint:{job_id}
TYPE: String (JSON serialised ResearchState)
TTL: 48 hours

# Agent event stream
KEY: job:{job_id}:events  
TYPE: Redis Stream (XADD / XREAD)
TTL: 24 hours

# Active SSE connections
KEY: job:{job_id}:subscribers
TYPE: Set (connection IDs)
TTL: 24 hours

# Response cache (avoid duplicate LLM calls)
KEY: cache:llm:{prompt_hash}
TYPE: String (JSON response)
TTL: 1 hour

# Rate limiting
KEY: ratelimit:{user_id}:{window}
TYPE: Counter (INCR)
TTL: 60 seconds
```

---

# 7. UI/UX Specification

## 7.1 Page Inventory

| Page | Route | Auth Required | Description |
|---|---|---|---|
| Landing | `/` | No | Product overview, features, GitHub link |
| Login | `/login` | No | Email/password login form |
| Register | `/register` | No | Registration form |
| Dashboard | `/dashboard` | Yes | Projects overview |
| Project | `/projects/{id}` | Yes | Job history + documents |
| New Research | `/projects/{id}/new` | Yes | Query input form |
| Live Job | `/jobs/{id}/live` | Yes | Real-time agent activity |
| Report View | `/reports/{id}` | Yes | Full report + chat panel |
| Settings | `/settings` | Yes | Profile + preferences |

## 7.2 Component Hierarchy

```
App
├── Layout
│   ├── Navbar (logo, nav links, user menu)
│   └── Sidebar (project list, collapsed on mobile)
│
├── Pages
│   ├── LandingPage
│   │   ├── HeroSection (headline, CTA, demo gif)
│   │   ├── FeaturesGrid (agent cards explanation)
│   │   ├── TechStackSection (logos)
│   │   └── GitHubCTA
│   │
│   ├── DashboardPage
│   │   ├── ProjectCard[] (name, job count, last activity)
│   │   └── CreateProjectModal
│   │
│   ├── ProjectPage
│   │   ├── ProjectHeader (name, actions)
│   │   ├── TabBar (Jobs | Documents)
│   │   ├── JobsTab
│   │   │   ├── NewResearchButton
│   │   │   └── JobRow[] (query, status badge, date, actions)
│   │   └── DocumentsTab
│   │       ├── UploadDocumentButton
│   │       └── DocumentRow[] (name, size, status, delete)
│   │
│   ├── NewResearchPage
│   │   ├── QueryInput (textarea, character counter)
│   │   ├── ProviderSelector (Bedrock / OpenAI toggle)
│   │   ├── DepthSelector (Quick / Standard / Deep)
│   │   ├── DocumentSelector (multi-select from project docs)
│   │   └── SubmitButton
│   │
│   ├── LiveJobPage
│   │   ├── JobHeader (query, status, elapsed time)
│   │   ├── AgentGraph
│   │   │   └── AgentCard × 5 (Planner, Research, Analysis, Critic, Synthesizer)
│   │   │       ├── AgentIcon
│   │   │       ├── AgentName + StatusBadge
│   │   │       ├── ProgressBar
│   │   │       └── PartialOutputPreview (collapsible)
│   │   └── ViewReportButton (appears on completion)
│   │
│   └── ReportPage
│       ├── ReportHeader (title, metadata, export button)
│       ├── ReportBody (Markdown rendered, table of contents sidebar)
│       └── ChatPanel (slide-in drawer)
│           ├── ChatHistory (message bubbles)
│           ├── ChatInput (textarea + send)
│           └── StreamingIndicator
```

## 7.3 Key UI Components — Detailed Specification

### AgentCard

```
┌─────────────────────────────────────┐
│  🔵  Planner Agent         RUNNING  │
│  ━━━━━━━━━━━━━━━━━━━━░░░░░░  60%   │
│  > Decomposing query into           │
│    sub-questions...                  │
│                          ▼ details  │
└─────────────────────────────────────┘
```

**States:**
- IDLE → grey border, grey icon, "Waiting..."
- RUNNING → blue border, blue pulsing icon, progress bar animating, partial output visible
- COMPLETE → green border, green checkmark, output collapsed with expand option
- FAILED → red border, red X icon, error message visible
- SKIPPED → grey border, "Not required for this depth"

### ReportBody

- Sticky table of contents on desktop (left sidebar, 280px)
- Section headers: H2 auto-generates ToC entries
- Citations: inline [1] links scroll to citations section
- Code blocks: syntax highlighted via Prism.js
- Copy button on code blocks
- "Back to top" floating button

### ChatPanel

- Slides in from right as a drawer (480px wide on desktop)
- Message bubbles: user (right, blue bg), assistant (left, grey bg)
- Streaming response: shows cursor blinking while streaming
- Markdown rendered in assistant messages
- "Sourced from report sections:" chips below each assistant message

## 7.4 Design System

### Colours
```
Primary:     #0055A0  (accent blue — consistent with CV brand)
Primary-50:  #E6F0FA
Success:     #1D9E75
Warning:     #BA7517
Error:       #E24B4A
Background:  #FAFAFA (light) / #0F0F0F (dark)
Surface:     #FFFFFF (light) / #1A1A1A (dark)
Border:      rgba(0,0,0,0.12) (light) / rgba(255,255,255,0.12) (dark)
Text-primary:#111111 (light) / #EFEFEF (dark)
Text-muted:  #666666 (light) / #999999 (dark)
```

### Typography
```
Font family: Inter (display) + JetBrains Mono (code)
H1: 32px / 500 weight
H2: 24px / 500 weight
H3: 18px / 500 weight
Body: 16px / 400 weight / 1.7 line-height
Small: 13px / 400 weight
Code: 14px / JetBrains Mono
```

### Spacing
```
Base unit: 4px
Component padding: 16px / 24px
Section gap: 32px / 48px
Card radius: 12px
Button radius: 8px
```

### Agent Status Colours
```
IDLE:     #888780 (grey)
RUNNING:  #378ADD (blue) + pulse animation
COMPLETE: #1D9E75 (green)
FAILED:   #E24B4A (red)
```

## 7.5 Responsive Breakpoints

| Breakpoint | Width | Layout changes |
|---|---|---|
| Mobile | <640px | Sidebar hidden (hamburger menu), single column |
| Tablet | 640–1024px | Sidebar collapsed to icons, agent cards 2-column |
| Desktop | >1024px | Full sidebar, agent cards row, chat drawer visible |

---

# 8. Infrastructure & Deployment

## 8.1 AWS Architecture

```
┌─── AWS Account ─────────────────────────────────────────┐
│                                                           │
│  ┌─── VPC (10.0.0.0/16) ──────────────────────────────┐ │
│  │                                                      │ │
│  │  ┌── Public Subnets (AZ-a, AZ-b) ──────────────┐   │ │
│  │  │  Internet Gateway                             │   │ │
│  │  │  NAT Gateway                                  │   │ │
│  │  │  ALB (Application Load Balancer)              │   │ │
│  │  └──────────────────────────────────────────────┘   │ │
│  │                                                      │ │
│  │  ┌── Private Subnets (AZ-a, AZ-b) ────────────┐    │ │
│  │  │  EKS Worker Nodes (t3.medium × 2-5)         │    │ │
│  │  │  RDS PostgreSQL (db.t3.medium, Multi-AZ)    │    │ │
│  │  │  ElastiCache Redis (cache.t3.micro)         │    │ │
│  │  └─────────────────────────────────────────────┘    │ │
│  └──────────────────────────────────────────────────────┘ │
│                                                           │
│  S3 Bucket (meridian-docs-reports)                        │
│  ECR (meridian/frontend, meridian/api, meridian/ai)       │
│  AWS Bedrock (us-east-1)                                  │
│  CloudFront Distribution (frontend CDN)                   │
│  Route53 (meridian.yourdomain.com)                        │
│  ACM Certificate (TLS)                                    │
│  IAM Roles (EKS node role, Bedrock access role)           │
└───────────────────────────────────────────────────────────┘
```

## 8.2 Terraform Structure

```
infra/
├── main.tf                  # Provider config, backend (S3 + DynamoDB state)
├── variables.tf             # All input variables
├── outputs.tf               # Cluster endpoint, RDS endpoint, etc.
├── terraform.tfvars         # Non-secret values (gitignored for secrets)
│
├── modules/
│   ├── vpc/
│   │   ├── main.tf          # VPC, subnets, IGW, NAT, route tables
│   │   ├── variables.tf
│   │   └── outputs.tf
│   │
│   ├── eks/
│   │   ├── main.tf          # EKS cluster, node group, IAM roles
│   │   ├── variables.tf
│   │   └── outputs.tf
│   │
│   ├── rds/
│   │   ├── main.tf          # RDS PostgreSQL, subnet group, security group
│   │   ├── variables.tf
│   │   └── outputs.tf
│   │
│   ├── elasticache/
│   │   ├── main.tf          # Redis cluster, subnet group
│   │   ├── variables.tf
│   │   └── outputs.tf
│   │
│   ├── s3/
│   │   ├── main.tf          # S3 bucket, bucket policy, CORS
│   │   ├── variables.tf
│   │   └── outputs.tf
│   │
│   └── ecr/
│       ├── main.tf          # ECR repos × 3
│       ├── variables.tf
│       └── outputs.tf
```

### Key Terraform Resources

```hcl
# modules/eks/main.tf (excerpt)

resource "aws_eks_cluster" "meridian" {
  name     = "meridian-${var.environment}"
  role_arn = aws_iam_role.eks_cluster.arn
  version  = "1.29"

  vpc_config {
    subnet_ids              = var.private_subnet_ids
    endpoint_private_access = true
    endpoint_public_access  = true
  }
}

resource "aws_eks_node_group" "meridian" {
  cluster_name    = aws_eks_cluster.meridian.name
  node_group_name = "meridian-workers"
  node_role_arn   = aws_iam_role.eks_node.arn
  subnet_ids      = var.private_subnet_ids
  instance_types  = ["t3.medium"]

  scaling_config {
    desired_size = 2
    max_size     = 5
    min_size     = 1
  }

  update_config { max_unavailable = 1 }
}
```

## 8.3 Kubernetes Manifests Structure

```
k8s/
├── namespace.yaml
│
├── helm/
│   ├── meridian-api/           # Spring Boot Helm chart
│   │   ├── Chart.yaml
│   │   ├── values.yaml
│   │   └── templates/
│   │       ├── deployment.yaml
│   │       ├── service.yaml
│   │       ├── configmap.yaml
│   │       ├── secret.yaml
│   │       ├── hpa.yaml        # Horizontal Pod Autoscaler
│   │       └── ingress.yaml
│   │
│   ├── meridian-ai/            # FastAPI Helm chart
│   │   ├── Chart.yaml
│   │   ├── values.yaml
│   │   └── templates/
│   │       ├── deployment.yaml
│   │       ├── service.yaml
│   │       ├── configmap.yaml
│   │       ├── secret.yaml
│   │       └── hpa.yaml
│   │
│   └── meridian-frontend/      # Next.js Helm chart
│       ├── Chart.yaml
│       ├── values.yaml
│       └── templates/
│           ├── deployment.yaml
│           ├── service.yaml
│           └── ingress.yaml
│
└── monitoring/
    ├── prometheus-values.yaml  # kube-prometheus-stack config
    └── grafana-dashboards/
        ├── meridian-overview.json
        └── agent-performance.json
```

### Deployment Spec — AI Service

```yaml
# k8s/helm/meridian-ai/templates/deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: meridian-ai
  namespace: meridian
spec:
  replicas: 2
  selector:
    matchLabels:
      app: meridian-ai
  template:
    metadata:
      labels:
        app: meridian-ai
    spec:
      serviceAccountName: meridian-ai-sa  # IRSA for Bedrock access
      containers:
        - name: meridian-ai
          image: {{ .Values.image.repository }}:{{ .Values.image.tag }}
          ports:
            - containerPort: 8000
          env:
            - name: DATABASE_URL
              valueFrom:
                secretKeyRef:
                  name: meridian-secrets
                  key: database-url
            - name: REDIS_URL
              valueFrom:
                secretKeyRef:
                  name: meridian-secrets
                  key: redis-url
            - name: LLM_PROVIDER
              valueFrom:
                configMapKeyRef:
                  name: meridian-config
                  key: llm-provider
            - name: AWS_REGION
              value: "us-east-1"
          resources:
            requests:
              memory: "512Mi"
              cpu: "250m"
            limits:
              memory: "2Gi"
              cpu: "1000m"
          livenessProbe:
            httpGet:
              path: /api/v1/health
              port: 8000
            initialDelaySeconds: 30
            periodSeconds: 10
          readinessProbe:
            httpGet:
              path: /api/v1/health
              port: 8000
            initialDelaySeconds: 10
            periodSeconds: 5
```

### Horizontal Pod Autoscaler

```yaml
# k8s/helm/meridian-ai/templates/hpa.yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: meridian-ai-hpa
  namespace: meridian
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: meridian-ai
  minReplicas: 2
  maxReplicas: 8
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70
    - type: Resource
      resource:
        name: memory
        target:
          type: Utilization
          averageUtilization: 80
```

### Ingress

```yaml
# Ingress routing
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: meridian-ingress
  namespace: meridian
  annotations:
    nginx.ingress.kubernetes.io/rewrite-target: /
    cert-manager.io/cluster-issuer: letsencrypt-prod
spec:
  ingressClassName: nginx
  tls:
    - hosts:
        - meridian.yourdomain.com
      secretName: meridian-tls
  rules:
    - host: meridian.yourdomain.com
      http:
        paths:
          - path: /api/graphql
            pathType: Prefix
            backend:
              service:
                name: meridian-api
                port:
                  number: 8080
          - path: /ai/stream
            pathType: Prefix
            backend:
              service:
                name: meridian-ai
                port:
                  number: 8000
          - path: /
            pathType: Prefix
            backend:
              service:
                name: meridian-frontend
                port:
                  number: 3000
```

## 8.4 Docker Configuration

### docker-compose.yml (local development)

```yaml
version: "3.9"
services:

  postgres:
    image: pgvector/pgvector:pg16
    environment:
      POSTGRES_DB: meridian
      POSTGRES_USER: meridian
      POSTGRES_PASSWORD: meridian_dev
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
      - ./scripts/init.sql:/docker-entrypoint-initdb.d/init.sql

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
    command: redis-server --appendonly yes
    volumes:
      - redis_data:/data

  api:
    build:
      context: ./api
      dockerfile: Dockerfile.dev
    ports:
      - "8080:8080"
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/meridian
      SPRING_DATASOURCE_USERNAME: meridian
      SPRING_DATASOURCE_PASSWORD: meridian_dev
      AI_SERVICE_URL: http://ai-service:8000
      JWT_SECRET: dev_jwt_secret_change_in_prod
    depends_on:
      - postgres
    volumes:
      - ./api:/app

  ai-service:
    build:
      context: ./ai-service
      dockerfile: Dockerfile.dev
    ports:
      - "8000:8000"
    environment:
      DATABASE_URL: postgresql+asyncpg://meridian:meridian_dev@postgres:5432/meridian
      REDIS_URL: redis://redis:6379
      LLM_PROVIDER: openai
      OPENAI_API_KEY: ${OPENAI_API_KEY}
      TAVILY_API_KEY: ${TAVILY_API_KEY}
      AWS_REGION: us-east-1
      S3_BUCKET: meridian-dev
    depends_on:
      - postgres
      - redis
    volumes:
      - ./ai-service:/app

  frontend:
    build:
      context: ./frontend
      dockerfile: Dockerfile.dev
    ports:
      - "3000:3000"
    environment:
      NEXT_PUBLIC_GRAPHQL_URL: http://localhost:8080/api/graphql
      NEXT_PUBLIC_AI_URL: http://localhost:8000
    depends_on:
      - api
    volumes:
      - ./frontend:/app

volumes:
  postgres_data:
  redis_data:
```

---

# 9. CI/CD Pipeline

## 9.1 GitHub Actions Workflows

### Workflow 1: Pull Request Checks

```yaml
# .github/workflows/pr-checks.yml
name: PR Checks
on:
  pull_request:
    branches: [main, develop]

jobs:
  test-api:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { java-version: '21', distribution: 'temurin' }
      - run: cd api && ./mvnw test
      - run: cd api && ./mvnw verify -P integration-test

  test-ai-service:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-python@v5
        with: { python-version: '3.11' }
      - run: cd ai-service && pip install -r requirements-dev.txt
      - run: cd ai-service && pytest tests/ -v --cov=app --cov-report=xml

  test-frontend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with: { node-version: '20' }
      - run: cd frontend && npm ci
      - run: cd frontend && npm run type-check
      - run: cd frontend && npm run lint
      - run: cd frontend && npm test -- --coverage

  security-scan:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: aquasecurity/trivy-action@master
        with:
          scan-type: fs
          severity: HIGH,CRITICAL
```

### Workflow 2: Deploy to Production

```yaml
# .github/workflows/deploy.yml
name: Deploy
on:
  push:
    branches: [main]

env:
  AWS_REGION: us-east-1
  ECR_REGISTRY: ${{ secrets.AWS_ACCOUNT_ID }}.dkr.ecr.us-east-1.amazonaws.com

jobs:
  build-and-push:
    runs-on: ubuntu-latest
    strategy:
      matrix:
        service: [api, ai-service, frontend]
    steps:
      - uses: actions/checkout@v4

      - name: Configure AWS credentials
        uses: aws-actions/configure-aws-credentials@v4
        with:
          aws-access-key-id: ${{ secrets.AWS_ACCESS_KEY_ID }}
          aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
          aws-region: ${{ env.AWS_REGION }}

      - name: Login to ECR
        uses: aws-actions/amazon-ecr-login@v2

      - name: Build and push ${{ matrix.service }}
        run: |
          IMAGE=$ECR_REGISTRY/meridian/${{ matrix.service }}:${{ github.sha }}
          docker build -t $IMAGE ./${{ matrix.service }}
          docker push $IMAGE
          echo "IMAGE=$IMAGE" >> $GITHUB_ENV

  deploy:
    needs: build-and-push
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Configure AWS credentials
        uses: aws-actions/configure-aws-credentials@v4
        with:
          aws-access-key-id: ${{ secrets.AWS_ACCESS_KEY_ID }}
          aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
          aws-region: ${{ env.AWS_REGION }}

      - name: Update kubeconfig
        run: aws eks update-kubeconfig --name meridian-prod --region ${{ env.AWS_REGION }}

      - name: Deploy API
        run: |
          helm upgrade --install meridian-api ./k8s/helm/meridian-api \
            --namespace meridian \
            --set image.tag=${{ github.sha }} \
            --set image.repository=$ECR_REGISTRY/meridian/api \
            --wait

      - name: Deploy AI Service
        run: |
          helm upgrade --install meridian-ai ./k8s/helm/meridian-ai \
            --namespace meridian \
            --set image.tag=${{ github.sha }} \
            --set image.repository=$ECR_REGISTRY/meridian/ai-service \
            --wait

      - name: Deploy Frontend
        run: |
          helm upgrade --install meridian-frontend ./k8s/helm/meridian-frontend \
            --namespace meridian \
            --set image.tag=${{ github.sha }} \
            --set image.repository=$ECR_REGISTRY/meridian/frontend \
            --wait

      - name: Verify deployment
        run: kubectl rollout status deployment/meridian-api -n meridian
```

---

# 10. Security Specification

## 10.1 Authentication & Authorisation

| Requirement | Implementation |
|---|---|
| Password storage | bcrypt with cost factor 12 |
| JWT signing | RS256 (asymmetric), private key in K8s Secret |
| Access token expiry | 15 minutes |
| Refresh token expiry | 7 days, rotated on use |
| RBAC | Spring Security method-level `@PreAuthorize` |
| Resource ownership | All queries filter by authenticated user_id |

## 10.2 API Security

| Concern | Mitigation |
|---|---|
| Rate limiting | Redis-based: 60 req/min per user (general), 5 jobs/hour per user (research) |
| Input validation | Bean Validation (Spring Boot), Pydantic (FastAPI), 500 char query limit |
| SQL injection | Parameterised queries only (JPA, asyncpg) |
| XSS | Content-Security-Policy headers, DOMPurify on rendered Markdown |
| CORS | Strict allowlist: only frontend domain |
| Secrets | K8s Secrets + AWS Secrets Manager; never in code or env files |
| TLS | ACM certificate, TLS 1.2+ only, HSTS enabled |

## 10.3 AWS IAM

```
EKS Node Role permissions:
  - ecr:GetAuthorizationToken
  - ecr:BatchGetImage
  - logs:CreateLogStream
  - logs:PutLogEvents

AI Service IRSA (Pod-level):
  - bedrock:InvokeModel
  - bedrock:InvokeModelWithResponseStream
  - s3:GetObject
  - s3:PutObject
  - s3:DeleteObject
  (scoped to meridian-* S3 bucket only)
```

## 10.4 Data Privacy

- User data deletion cascade: deleting account removes all projects, jobs, reports, embeddings
- S3 objects tagged with user_id for targeted deletion
- No PII stored in Redis (only job state and events)
- Embeddings do not contain original file content (chunk text is stored separately)

---

# 11. Testing Specification

## 11.1 Unit Tests

### Spring Boot (JUnit 5 + Mockito)
```
api/src/test/java/
├── auth/
│   ├── AuthServiceTest.java        # register, login, token refresh
│   └── JwtUtilTest.java            # token generation, validation, expiry
├── project/
│   └── ProjectServiceTest.java     # CRUD, ownership validation
├── job/
│   └── ResearchJobServiceTest.java # create, cancel, status transitions
└── graphql/
    ├── AuthResolverTest.java
    └── ProjectResolverTest.java
```

### FastAPI (pytest + pytest-asyncio)
```
ai-service/tests/
├── unit/
│   ├── test_llm_provider.py        # provider switching, mock LLM calls
│   ├── test_agents/
│   │   ├── test_planner.py         # sub-question generation
│   │   ├── test_research.py        # tool calls, result merging
│   │   ├── test_analysis.py        # synthesis with mock context
│   │   ├── test_critic.py          # scoring, feedback generation
│   │   └── test_synthesizer.py     # report formatting, citations
│   ├── test_rag.py                 # chunking, embedding, retrieval
│   └── test_streaming.py           # SSE event format, Redis pub/sub
```

## 11.2 Integration Tests

```
ai-service/tests/integration/
├── test_full_workflow.py       # End-to-end LangGraph run (mocked LLM)
├── test_database.py            # pgvector insert + similarity search
└── test_redis.py               # State checkpoint save/load

api/src/test/java/integration/
├── GraphQLIntegrationTest.java # Full GraphQL request/response cycle
└── AuthIntegrationTest.java    # JWT flow end-to-end
```

## 11.3 End-to-End Tests (Playwright)

```
frontend/tests/e2e/
├── auth.spec.ts            # Register, login, logout
├── project.spec.ts         # Create, view, delete project
├── research.spec.ts        # Submit job, observe live view, view report
└── chat.spec.ts            # Chat with completed report
```

## 11.4 Test Coverage Targets

| Service | Target | Tool |
|---|---|---|
| Spring Boot | 80% line coverage | JaCoCo |
| FastAPI | 80% line coverage | pytest-cov |
| Frontend | 70% line coverage | Vitest |

---

# 12. Non-Functional Requirements

## 12.1 Performance

| Metric | Requirement | Measurement |
|---|---|---|
| API response time (p99) | < 200ms (non-AI endpoints) | Prometheus |
| Research job completion | < 5 minutes (standard depth) | CloudWatch metric |
| SSE event latency | < 500ms from agent event to browser | Custom metric |
| Concurrent users | 100 concurrent sessions without degradation | Load test |
| Database query time | < 50ms (p99) | pg_stat_statements |
| Vector search time | < 100ms for 1M embeddings | pgvector benchmark |

## 12.2 Availability

| Requirement | Target |
|---|---|
| Uptime SLA | 99.5% (excluding planned maintenance) |
| Recovery Time Objective (RTO) | < 30 minutes |
| Recovery Point Objective (RPO) | < 1 hour (daily RDS snapshots) |
| Multi-AZ deployment | RDS and Redis span 2 AZs |

## 12.3 Scalability

- EKS HPA scales AI service pods 2→8 based on CPU/memory
- RDS read replica can be added for read-heavy workloads
- S3 and Bedrock scale infinitely with usage
- Redis can be upgraded to cluster mode for horizontal scaling

## 12.4 Observability

### Metrics (Prometheus + Grafana)
- Request rate, error rate, latency per endpoint
- Research job completion rate and average duration
- Agent execution time per agent type
- Critic score distribution
- LLM provider latency and error rate
- Pod CPU/memory utilisation
- PostgreSQL query performance

### Logging (structured JSON)
- All logs to stdout → collected by Fluentd → CloudWatch Logs
- Log levels: ERROR, WARN, INFO, DEBUG (configurable per environment)
- Correlation ID propagated across all services per request
- Agent execution logs stored in PostgreSQL `embeddings.metadata`

### Alerting
- PagerDuty alerts: job failure rate > 5%, p99 latency > 2s, pod OOMKilled
- Slack alerts: deployment events, daily digest of key metrics

---

# 13. Implementation Roadmap

## Phase 1 — Foundation (Week 1–2)

### Week 1
| Day | Task | Service | Deliverable |
|---|---|---|---|
| 1 | Monorepo setup, Docker Compose, Terraform scaffold | All | Repo structure |
| 2 | VPC + RDS + Redis + ECR Terraform modules | Infra | `terraform plan` passes |
| 3 | Spring Boot project setup, PostgreSQL entities, JPA | API | DB migrations run |
| 4 | JWT auth (register, login, refresh) | API | Auth endpoints working |
| 5 | GraphQL schema + resolvers (User, Project) | API | GraphQL playground working |
| 6 | FastAPI project setup, LLM abstraction layer | AI | `/health` endpoint |
| 7 | LangGraph state definition + Redis checkpointing | AI | State save/load working |

### Week 2
| Day | Task | Service | Deliverable |
|---|---|---|---|
| 8–9 | Planner + Research agents, Tavily integration | AI | 2-agent workflow running |
| 10 | pgvector setup, document embedding pipeline | AI | Similarity search working |
| 11–12 | Analysis + Critic agents, conditional edge | AI | Revision loop working |
| 13–14 | Synthesizer agent, full workflow end-to-end | AI | Complete report generated |

## Phase 2 — Real-Time & Frontend (Week 3–4)

### Week 3
| Day | Task | Service | Deliverable |
|---|---|---|---|
| 15–16 | Redis pub/sub + FastAPI SSE endpoint | AI | SSE stream working |
| 17 | Spring Boot → FastAPI job trigger integration | API + AI | Full job creation flow |
| 18–19 | React project setup, routing, Apollo Client | Frontend | App shell running |
| 20–21 | Dashboard, Project pages, New Research form | Frontend | Core pages done |

### Week 4
| Day | Task | Service | Deliverable |
|---|---|---|---|
| 22–23 | Live job page with AgentGraph component, SSE | Frontend | Real-time view working |
| 24–25 | Report page, Markdown renderer, ToC | Frontend | Report readable |
| 26 | Chat panel, streaming chat response | Frontend + AI | Chat working |
| 27–28 | Document upload (S3 + pgvector pipeline) | API + AI | Upload + embed working |

## Phase 3 — Infrastructure & Polish (Week 5–6)

### Week 5
| Day | Task | Deliverable |
|---|---|---|
| 29–31 | Helm charts for all 3 services | Helm charts deployable |
| 32–33 | EKS deployment via GitHub Actions | Services running on EKS |
| 34–35 | Prometheus + Grafana + basic dashboards | Monitoring live |

### Week 6
| Day | Task | Deliverable |
|---|---|---|
| 36–37 | Report export (PDF, Markdown) | Export feature |
| 38 | Architecture diagram (Excalidraw) | README asset |
| 39 | README (setup guide, architecture, contributing) | README complete |
| 40 | Demo recording (3 min screen + walkthrough) | YouTube video |
| 41 | Open source launch (GitHub, YouTube, Medium, HN) | Public launch |
| 42 | Performance testing, bug fixes, documentation cleanup | Production-ready |

---

# 14. Open Source Contribution Guide

## 14.1 Repository Structure

```
meridian/
├── README.md
├── CONTRIBUTING.md
├── CODE_OF_CONDUCT.md
├── LICENSE                    (MIT)
├── docker-compose.yml
├── docker-compose.prod.yml
│
├── frontend/                  (Next.js + TypeScript)
│   ├── src/
│   │   ├── app/               (Next.js App Router pages)
│   │   ├── components/
│   │   ├── lib/               (Apollo client, utils)
│   │   ├── hooks/
│   │   └── types/
│   ├── package.json
│   └── Dockerfile
│
├── api/                       (Spring Boot)
│   ├── src/main/java/com/meridian/
│   │   ├── auth/
│   │   ├── project/
│   │   ├── job/
│   │   ├── report/
│   │   ├── document/
│   │   └── config/
│   ├── pom.xml
│   └── Dockerfile
│
├── ai-service/                (FastAPI + LangGraph)
│   ├── app/
│   │   ├── agents/            (planner, research, analysis, critic, synthesizer)
│   │   ├── graph/             (LangGraph workflow definition)
│   │   ├── llm/               (provider abstraction)
│   │   ├── rag/               (embedding, retrieval)
│   │   ├── streaming/         (SSE, Redis pub/sub)
│   │   └── api/               (FastAPI routes)
│   ├── tests/
│   ├── requirements.txt
│   └── Dockerfile
│
├── infra/                     (Terraform)
│   ├── modules/
│   └── environments/
│       ├── dev/
│       └── prod/
│
├── k8s/                       (Helm charts)
│   └── helm/
│
└── .github/
    └── workflows/
```

## 14.2 Local Setup (Quick Start)

```bash
# 1. Clone the repository
git clone https://github.com/madhurajayashanka/meridian
cd meridian

# 2. Copy environment variables
cp .env.example .env
# Fill in: OPENAI_API_KEY, TAVILY_API_KEY

# 3. Start all services
docker compose up -d

# 4. Run database migrations
docker compose exec api ./mvnw flyway:migrate

# 5. Open the app
open http://localhost:3000
```

## 14.3 Environment Variables

| Variable | Service | Description | Required |
|---|---|---|---|
| `LLM_PROVIDER` | AI | `bedrock` or `openai` | Yes |
| `OPENAI_API_KEY` | AI | OpenAI API key (if using OpenAI) | Conditional |
| `AWS_REGION` | AI | AWS region for Bedrock | Conditional |
| `TAVILY_API_KEY` | AI | Tavily search API key | Yes |
| `DATABASE_URL` | AI | PostgreSQL connection string | Yes |
| `REDIS_URL` | AI | Redis connection string | Yes |
| `S3_BUCKET` | AI | S3 bucket name for storage | Yes |
| `JWT_SECRET` | API | JWT signing secret (min 32 chars) | Yes |
| `SPRING_DATASOURCE_URL` | API | JDBC connection string | Yes |
| `AI_SERVICE_URL` | API | FastAPI internal URL | Yes |
| `NEXT_PUBLIC_GRAPHQL_URL` | Frontend | GraphQL endpoint URL | Yes |
| `NEXT_PUBLIC_AI_URL` | Frontend | FastAPI base URL | Yes |

## 14.4 Contributing Guidelines

1. **Fork** the repository and create a branch: `git checkout -b feature/your-feature`
2. **Write tests** for any new functionality — PRs without tests will not be merged
3. **Follow the code style**: ESLint + Prettier (frontend), Google Java Format (API), Black + Ruff (AI service)
4. **Update documentation** if you change APIs, configuration, or architecture
5. **Submit a PR** with a clear description of the change and why it was made

### Good First Issues (for contributors)
- Add support for additional LLM providers (Groq, Anthropic direct API)
- Add more export formats (Word DOCX, HTML)
- Improve the Critic Agent scoring rubric
- Add research job templates for common use cases
- Implement OAuth2 Google login
- Add dark mode to the frontend

---

*Document version: 1.0.0 — Maintained by Madhura Jayashanka*  
*Last updated: 2026*  
*License: MIT*
