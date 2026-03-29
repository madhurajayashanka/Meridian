# 05 — Database Schema

All 11 tables, columns, relationships, and indexes.

---

## Diagram: Entity Relationship (ERD)

```
┌──────────────┐         ┌──────────────────┐
│    users     │         │  refresh_tokens  │
│──────────────│         │──────────────────│
│ id (PK)      │◀────────│ user_id (FK)     │
│ email        │  1:many │ token_hash       │
│ password_hash│         │ expires_at       │
│ name         │         │ revoked_at       │
│ failed_logins│         └──────────────────┘
│ locked_until │
│ is_active    │         ┌──────────────────┐
│ deleted_at   │         │    api_keys      │
└──────┬───────┘         │──────────────────│
       │                 │ user_id (FK)     │
       │ 1:many          │ name, key_hash   │
       ▼                 │ expires_at       │
┌──────────────┐         └──────────────────┘
│   projects   │
│──────────────│         ┌──────────────────┐
│ id (PK)      │         │   audit_logs     │
│ user_id (FK) │         │──────────────────│
│ name         │         │ user_id (FK)     │
│ description  │         │ entity_type      │
│ is_archived  │         │ entity_id        │
│ deleted_at   │         │ action           │
└──────┬───────┘         │ changes (JSONB)  │
       │                 │ ip_address       │
       │ 1:many          └──────────────────┘
       ├──────────────────────────────────────┐
       │                                      │
       ▼                                      ▼
┌──────────────┐                    ┌──────────────────┐
│  documents   │                    │  research_jobs   │
│──────────────│                    │──────────────────│
│ id (PK)      │                    │ id (PK)          │
│ project_id   │                    │ project_id (FK)  │
│ user_id      │                    │ user_id (FK)     │
│ original_    │                    │ query            │
│  filename    │                    │ status           │
│ s3_key       │                    │ llm_provider     │
│ file_type    │                    │ research_depth   │
│ file_size    │                    │ started_at       │
│ status       │                    │ completed_at     │
│ chunk_count  │                    │ error_message    │
│ deleted_at   │                    └────────┬─────────┘
└──────┬───────┘                             │
       │                                     │ 1:1
       │ 1:many                              ▼
       ▼                           ┌──────────────────┐
┌──────────────┐                   │     reports      │
│  embeddings  │                   │──────────────────│
│──────────────│                   │ id (PK)          │
│ id (PK)      │                   │ job_id (FK)      │
│ document_id  │◀──────────────────│ project_id (FK)  │
│ report_id    │  (report chunks)  │ user_id (FK)     │
│ content      │                   │ title            │
│ embedding    │                   │ s3_key           │
│  vector(1536)│                   │ word_count       │
│ chunk_index  │                   │ citation_count   │
│ metadata     │                   │ critic_score     │
└──────────────┘                   │ revision_count   │
                                   │ is_public        │
                                   │ deleted_at       │
                                   └────────┬─────────┘
                                            │
                                            │ 1:many
                                            ▼
                                   ┌──────────────────┐
                                   │  chat_messages   │
                                   │──────────────────│
                                   │ id (PK)          │
                                   │ report_id (FK)   │
                                   │ user_id (FK)     │
                                   │ role             │
                                   │ content          │
                                   │ tokens_used      │
                                   └──────────────────┘

research_job_documents (junction):
  job_id (FK) ──── research_jobs
  document_id (FK) ── documents

agent_logs:
  job_id (FK) ──── research_jobs
  agent_name, status, input_tokens, output_tokens, duration_ms, payload (JSONB)
```

---

## Diagram: pgvector Embeddings Table

```
embeddings
┌─────────────────────────────────────────────────────────┐
│ id          UUID PK                                      │
│ document_id UUID FK → documents (nullable)               │
│ report_id   UUID FK → reports   (nullable)               │
│ content     TEXT  (the chunk text)                       │
│ embedding   vector(1536)  ← OpenAI / Bedrock Titan dim  │
│ chunk_index INT                                          │
│ metadata    JSONB                                        │
│ created_at  TIMESTAMPTZ                                  │
└─────────────────────────────────────────────────────────┘

Index: HNSW (embedding vector_cosine_ops) m=16, ef_construction=64
Query: SELECT ... ORDER BY embedding <=> $query_vector LIMIT 5
```

---

## Table Summary

| Table | Rows represent | Key relationships |
|---|---|---|
| users | Platform accounts | owns projects, documents, jobs |
| refresh_tokens | Active JWT refresh tokens | belongs to user |
| projects | Research workspaces | owned by user, has jobs + docs |
| documents | Uploaded PDF/TXT files | belongs to project |
| embeddings | Vector chunks | from document OR report |
| research_jobs | AI research runs | belongs to project, has report |
| research_job_documents | Job ↔ document links | many-to-many junction |
| reports | Final research output | one-to-one with job |
| chat_messages | RAG chat history | belongs to report |
| agent_logs | Per-agent execution records | belongs to job |
| api_keys | Programmatic access keys | belongs to user |
| audit_logs | Security/compliance trail | references any entity |
