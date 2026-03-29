# 08 — Frontend Pages & Components

Page routing, component tree, and hook dependencies.

---

## Diagram: Page Route Map

```
/                           → Landing page (page.tsx)
├── /login                  → LoginPage
├── /register               → RegisterPage
├── /dashboard              → DashboardPage  [auth required]
│   └── (create project modal)
├── /projects/[id]          → ProjectPage    [auth required]
│   ├── /projects/[id]/new  → ResearchFormPage
│   └── /projects/[id]/research → ResearchPage (alt form)
├── /jobs/[id]/live         → LiveMonitorPage [auth required]
│   └── (auto-redirects to /report on complete)
├── /jobs/[id]/report       → ReportPage     [auth required]
├── /settings               → SettingsPage
├── /docs                   → DocsPage
├── /faq                    → FAQPage
└── /pricing                → PricingPage
```

---

## Diagram: Component Tree (key pages)

```
RootLayout (layout.tsx)
└── ErrorBoundary
    └── {page content}

DashboardPage
├── Navigation
├── ProjectCard[] (inline)
└── CreateProjectModal (inline)

ProjectPage
├── Navigation
├── JobCard[] (inline)
└── DocumentUpload

ResearchFormPage
├── Navigation
├── <textarea> query
├── <select> llmProvider
├── <select> researchDepth
├── DocumentSelector (inline, max 5)
└── <button> Submit → navigate to /jobs/{id}/live

LiveMonitorPage
├── Navigation
├── AgentCard × 5 (planner, research, analysis, critic, synthesizer)
│   └── status: idle | running | complete | failed
│       progress bar, output preview
└── useSSE hook → EventSource → agent_update events

ReportPage
├── Navigation
├── TOC sidebar (auto-generated from H2/H3)
├── Markdown renderer (custom, no dangerouslySetInnerHTML)
│   └── headings, paragraphs, bold, italic, citations [N]
├── Report metadata (word count, citations, critic score)
└── ChatPanel
    ├── MessageList (user/assistant bubbles)
    ├── <input> message
    └── sendChatMessage GraphQL mutation
```

---

## Diagram: Hook Dependency Map

```
useAuth (Zustand store)
  state: accessToken, refreshToken, userId, email, isInitialized
  actions: setAuth(), clearAuth(), logout()
  persistence: localStorage "auth" key
  used by: every page, useApiClient, DocumentUpload

useApiClient
  depends on: useAuth (reads accessToken, refreshToken)
  provides: get(), post(), put(), delete(), makeRequest()
  features:
    - auto token refresh before expiry (30s buffer)
    - retry once after 401 with refreshed token
    - redirect to /login on auth failure
  used by: all pages for GraphQL + REST calls

useSSE(jobId, accessToken)
  creates: EventSource to /ai/stream/{jobId}?token={accessToken}
  reconnect: exponential backoff, max 5 attempts
  events: agent_update, job_complete, job_failed, error
  used by: LiveMonitorPage
```

---

## Diagram: Auth State Flow (Frontend)

```
App loads
    │
    ▼
useAuth.initializeFromStorage()
    │ reads localStorage "auth"
    ▼
isInitialized = true

User visits protected page
    │
    ▼
accessToken present?
    │ NO → redirect to /login
    │ YES
    ▼
isTokenExpired(accessToken)?
    │ YES → tryRefreshToken() → new tokens → continue
    │ NO  → use existing token
    ▼
API call with Authorization: Bearer <token>
    │
    ▼
Response 401?
    │ YES → tryRefreshToken() → retry once → if fails → clearAuth() → /login
    │ NO  → return data
```

---

## Diagram: DocumentUpload Component Flow

```
User drags file or clicks
    │
    ▼
Validate: PDF or TXT only, max 10MB
    │ FAIL → show error
    │ PASS
    ▼
FormData { file, projectId }
POST /api/documents/upload
Authorization: Bearer <token from useAuthStore>
    │
    ▼
Response: { id, status, chunkCount }
    │
    ▼
status == "PROCESSING"?
    │ YES → poll GET document(id) every 2s (max 60s)
    │       until status == "READY" or "FAILED"
    │ NO  → show result immediately
    ▼
onUploadComplete(document) callback
```
