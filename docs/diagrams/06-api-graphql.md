# 06 — GraphQL API

All queries and mutations with their inputs, outputs, and auth requirements.

---

## Diagram: Query Map

```
Query Root
├── me                          → User (requires auth)
├── user(id)                    → User
├── projects                    → [Project!]!  (user's own)
├── project(id)                 → Project
├── jobs(projectId?)            → [ResearchJob!]!
├── job(id)                     → ResearchJob
├── documents(projectId!)       → [Document!]!
├── document(id)                → Document
├── reports(projectId?)         → [Report!]!
├── report(id)                  → Report  (content loaded from storage)
├── publicReports               → [Report!]!  (no auth needed)
├── chatMessages(reportId!)     → [ChatMessage!]!
└── health                      → String
```

---

## Diagram: Mutation Map

```
Mutation Root
│
├── AUTH
│   ├── register(email, password, name)         → AuthPayload
│   ├── login(email, password)                  → AuthPayload
│   ├── refreshToken(refreshToken)              → AuthPayload
│   ├── logout                                  → Boolean
│   └── changePassword(current, new)            → Boolean
│
├── USER PROFILE
│   ├── updateProfile(name?, avatarUrl?)        → User
│   └── deleteAccount                           → Boolean
│
├── PROJECTS
│   ├── createProject(input)                    → Project
│   ├── updateProject(input)                    → Project
│   ├── deleteProject(id)                       → Boolean (soft delete)
│   └── archiveProject(id)                      → Boolean
│
├── RESEARCH JOBS
│   ├── createResearchJob(input)                → ResearchJob
│   └── cancelResearchJob(id)                   → Boolean
│
├── DOCUMENTS
│   ├── uploadDocument(projectId, file)         → Document (use REST instead)
│   └── deleteDocument(id)                      → Boolean (soft delete + embeddings)
│
├── REPORTS
│   ├── exportReport(id, format)                → String (markdown content)
│   ├── deleteReport(id)                        → Boolean (soft delete)
│   ├── publishReport(id)                       → Boolean (isPublic=true)
│   └── unpublishReport(id)                     → Boolean (isPublic=false)
│
└── CHAT
    ├── sendChatMessage(input)                  → ChatMessage (proxied to AI service)
    └── clearChatHistory(reportId)              → Boolean
```

---

## Diagram: AuthPayload Type

```
AuthPayload {
  accessToken:  String  (JWT RS256, 15 min)
  refreshToken: String  (JWT RS256, 7 days, stored as hash in DB)
  user:         User
  expiresIn:    Int     (seconds = 900)
}
```

---

## Diagram: createResearchJob Input

```
CreateResearchJobInput {
  projectId:     ID!              (must own this project)
  query:         String!          (10-500 chars)
  llmProvider:   LLMProvider      (BEDROCK | OPENAI | MOCK, default BEDROCK)
  researchDepth: ResearchDepth    (QUICK | STANDARD | DEEP, default STANDARD)
  documentIds:   [ID!]            (max 5, must be READY status, must belong to project)
}
```

---

## Diagram: GraphQL Request/Response Format

```
Request:
POST /graphql
Authorization: Bearer <accessToken>
Content-Type: application/json

{
  "query": "mutation CreateJob($input: CreateResearchJobInput!) {
    createResearchJob(input: $input) { id status }
  }",
  "variables": {
    "input": {
      "projectId": "uuid",
      "query": "What are the latest advances in quantum computing?",
      "llmProvider": "BEDROCK",
      "researchDepth": "STANDARD"
    }
  }
}

Response (success):
{
  "data": {
    "createResearchJob": {
      "id": "uuid",
      "status": "PENDING"
    }
  }
}

Response (error):
{
  "errors": [{
    "message": "You already have 3 active research jobs.",
    "extensions": { "classification": "ValidationError" }
  }]
}
```
