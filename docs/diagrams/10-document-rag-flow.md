# 10 — Document & RAG Flow

Upload → extract → chunk → embed → store → search → answer.

---

## Diagram: Document Upload Pipeline

```
Browser              Spring Boot           FastAPI              PostgreSQL
   │                     │                    │                     │
   │─POST /api/docs/─────▶                    │                     │
   │  upload              │ validate ownership │                     │
   │  (multipart)         │ validate file type │                     │
   │                      │ validate size ≤10MB│                     │
   │                      │──INSERT document───────────────────────▶│
   │                      │  status=PROCESSING │                     │
   │                      │                    │                     │
   │                      │─POST /api/v1/docs/─▶                    │
   │                      │  process            │                     │
   │                      │  X-Service-Key      │                     │
   │                      │  (multipart: file,  │                     │
   │                      │   document_id,      │                     │
   │                      │   file_type)        │                     │
   │                      │                     │ write to tmp file   │
   │                      │                     │ DocumentExtractor   │
   │                      │                     │  PDF: pypdf         │
   │                      │                     │  TXT: utf-8 read    │
   │                      │                     │ TextChunker         │
   │                      │                     │  sentence-based     │
   │                      │                     │  512 tokens/chunk   │
   │                      │                     │  50 token overlap   │
   │                      │                     │ delete tmp file     │
   │                      │                     │                     │
   │                      │                     │ for each chunk:     │
   │                      │                     │  embed_text(chunk)  │
   │                      │                     │  → vector[1536]     │
   │                      │                     │──INSERT embeddings──▶
   │                      │                     │  document_id=uuid   │
   │                      │                     │  embedding=vector   │
   │                      │                     │                     │
   │                      │◀──{chunk_count: 42}─│                     │
   │                      │ UPDATE document     │                     │
   │                      │  status=READY       │                     │
   │                      │  chunk_count=42     │                     │
   │◀──{id, status:READY}─│                     │                     │
```

---

## Diagram: RAG Search (during Research agent)

```
Research Agent (FastAPI)
    │
    │ for each sub_question:
    │
    ▼
embed_text(question) → query_vector[1536]
    │
    ▼
SELECT content, 1-(embedding <=> query_vector) as score
FROM embeddings
WHERE document_id = ANY([uploaded_doc_ids])
ORDER BY embedding <=> query_vector
LIMIT 3
    │
    ▼
top-3 chunks with similarity scores
    │
    ▼
append to research_sources as type="document"
```

---

## Diagram: RAG Chat (report Q&A)

```
User types question in ChatPanel
    │
    ▼
mutation sendChatMessage({reportId, content})
    │
    ▼
Spring Boot ChatGraphQLController
    │ generate short-lived accessToken for service call
    ▼
POST /api/v1/chat/{reportId}
  ?user_id=uuid&message=question&token=jwt
    │
    ▼
FastAPI chat endpoint
    │
    ├─1─▶ check_input(message)  ← guardrail
    │
    ├─2─▶ embed_text(message) → query_vector
    │
    ├─3─▶ SELECT chunks FROM embeddings
    │       WHERE report_id = reportId
    │       ORDER BY embedding <=> query_vector
    │       LIMIT 5
    │       (logs: hits, max/min/avg similarity scores)
    │
    ├─4─▶ SELECT last 5 chat_messages for context
    │
    ├─5─▶ Build LLM messages:
    │       system: "You are answering about this report. Context: {chunks}"
    │       history: [user/assistant messages]
    │       user: {question}
    │
    ├─6─▶ llm_provider.invoke_chat(messages)
    │       (with tenacity retry: 3 attempts, exp backoff 1-8s)
    │
    ├─7─▶ INSERT chat_messages (user message + assistant response)
    │
    └─8─▶ return { response: "..." }
    │
    ▼
Spring saves ChatMessage to DB
Returns ChatMessage to frontend
```

---

## Diagram: Report Embedding (for RAG)

```
After job completes, Synthesizer produces final_report (Markdown)
    │
    ▼
Split by H2 headers → chunks[]
    │
    ▼
for each chunk:
  embed_text(chunk) → vector[1536]
  INSERT embeddings (report_id=uuid, embedding=vector, chunk_index=N)
    │
    ▼
Report is now searchable via RAG chat
```
