# Meridian — Diagram Reference

All diagrams are described here as text so you can draw them in Excalidraw or any tool.
Each file covers one level or domain. Start with `01` for the big picture, drill down from there.

| File | What to draw |
|---|---|
| `01-system-overview.md` | High-level: all services, users, AWS |
| `02-service-architecture.md` | Service internals, ports, protocols |
| `03-data-flow-research-job.md` | End-to-end research job flow |
| `04-agent-pipeline.md` | LangGraph agent nodes and state |
| `05-database-schema.md` | All 11 tables and relationships |
| `06-api-graphql.md` | All GraphQL queries and mutations |
| `07-api-rest.md` | All REST endpoints with req/res |
| `08-frontend-pages.md` | Page map, components, hooks |
| `09-auth-flow.md` | Register, login, refresh, token lifecycle |
| `10-document-rag-flow.md` | Upload → extract → embed → search |
| `11-sse-streaming.md` | SSE event flow from agent to browser |
| `12-security-layers.md` | All security controls by layer |
| `13-infra-aws.md` | AWS resources, VPC, EKS, networking |
| `14-deployment-cicd.md` | CI/CD pipeline and deploy flow |
