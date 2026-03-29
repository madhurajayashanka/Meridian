# 01 — System Overview

The highest-level view. Draw this first — one box per major concern.

---

## Diagram: System Context

```
┌─────────────────────────────────────────────────────────────────────┐
│                          INTERNET                                    │
│                                                                      │
│   ┌──────────┐    HTTPS     ┌─────────────────────────────────────┐ │
│   │  Browser │ ──────────▶  │         Meridian Platform           │ │
│   │  (User)  │ ◀──────────  │                                     │ │
│   └──────────┘    SSE/GQL   │  ┌──────────┐  ┌────────────────┐  │ │
│                             │  │ Next.js  │  │  Spring Boot   │  │ │
│                             │  │ :3000    │  │  API :8000     │  │ │
│                             │  └──────────┘  └────────────────┘  │ │
│                             │                ┌────────────────┐  │ │
│                             │                │  FastAPI AI    │  │ │
│                             │                │  :8080         │  │ │
│                             │                └────────────────┘  │ │
│                             │  ┌──────────┐  ┌────────────────┐  │ │
│                             │  │PostgreSQL│  │    Redis       │  │ │
│                             │  │+pgvector │  │    :6379       │  │ │
│                             │  │ :5432    │  └────────────────┘  │ │
│                             │  └──────────┘                      │ │
│                             └─────────────────────────────────────┘ │
│                                                                      │
│   External APIs:  Tavily (web search)  ·  AWS Bedrock  ·  OpenAI   │
└─────────────────────────────────────────────────────────────────────┘
```

**Excalidraw tips:**
- Use a large outer rectangle for "Internet"
- Use a shaded rectangle for "Meridian Platform"
- Browser box on the left, platform on the right
- Arrows: solid for request, dashed for response
- External APIs at the bottom outside the platform box

---

## Diagram: User Journey (high level)

```
User
 │
 ├─1─▶ Register / Login
 │
 ├─2─▶ Create Project
 │
 ├─3─▶ Upload Documents (optional PDF/TXT)
 │
 ├─4─▶ Submit Research Query
 │         └─▶ Watch live agent progress (SSE)
 │
 ├─5─▶ Read Report (Markdown + TOC)
 │
 └─6─▶ Chat with Report (RAG Q&A)
```

**Excalidraw tips:**
- Vertical swimlane for the user
- Number each step in a circle
- Arrow from step 4 branches to "live monitoring" page

---

## Key Numbers

| Thing | Value |
|---|---|
| Services | 5 (frontend, api, ai-service, postgres, redis) |
| Agent nodes | 5 (Planner, Research, Analysis, Critic, Synthesizer) |
| DB tables | 11 |
| GraphQL mutations | 20 |
| GraphQL queries | 11 |
| REST endpoints | 7 (Spring) + 6 (FastAPI) |
| Max critic loops | 3 |
| Token budgets | quick=20k, standard=60k, deep=120k |
