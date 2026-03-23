# Meridian — Autonomous Multi-Agent Research Platform

An open-source, LLM-provider agnostic research platform that orchestrates five specialised AI agents to produce comprehensive, cited research reports from a single user query. All agent activity streams in real time via Server-Sent Events.

## Quick Start

### Prerequisites

- Docker and Docker Compose
- Make
- Node.js 18+ (for frontend development)
- Java 21 (for API service development)
- Python 3.11+ (for AI service development)

### Setup

1. **Clone and navigate to the project:**

   ```bash
   cd meridian
   ```

2. **Generate JWT keys:**

   ```bash
   make dev-keys
   ```

3. **Configure environment:**

   ```bash
   make dev-setup
   ```

   Edit `.env` with your API keys (Tavily, OpenAI/Bedrock)

4. **Start all services:**

   ```bash
   make up
   ```

5. **Access the platform:**
   - Frontend: http://localhost:3000
   - GraphQL API: http://localhost:8000/graphql
   - AI Service Health: http://localhost:8080/health

### Useful Commands

```bash
make logs         # View logs from all services
make logs-api     # View API service logs
make logs-ai      # View AI service logs
make clean        # Reset all containers and volumes
make test         # Run all tests
```

## Architecture

### Three-Service Microservices Architecture

```
┌─────────────┐
│  Frontend   │ (Next.js + TypeScript)
│ Port: 3000  │
└──────┬──────┘
       │ GraphQL
       │ SSE
       ▼
┌──────────────────┐        ┌─────────────────┐
│  Spring Boot API │────────│  FastAPI AI     │
│   Port: 8000     │ REST   │   Port: 8080    │
│  (GraphQL, JWT)  │        │  (LangGraph)    │
└────────┬─────────┘        └────────┬────────┘
         │                          │
         │                    ┌─────┴──────────┐
         │                    │                │
         └────────┬───────────┴─────┬──────────┘
                  │                 │
                  ▼                 ▼
            ┌──────────┐      ┌──────────┐
            │PostgreSQL│      │  Redis   │
            │  pgvector│      │ Streams  │
            └──────────┘      └──────────┘
```

### Services

**Frontend** — Next.js SPA

- User registration, login, MFA (future)
- Project and document management
- Research job submission
- Live agent activity streaming
- Report viewing with Markdown rendering
- Chat-with-report RAG interface

**API Service** — Spring Boot GraphQL

- User & project CRUD, ownership enforcement
- Authentication & JWT token management
- Research job submission and cancellation
- Rate limiting (Redis-backed)
- Report metadata and chat history persistence
- Flyway database migrations
- Content policy validation

**AI Service** — FastAPI + LangGraph

- Five-agent orchestration (Planner → Research → Analysis → Critic → Synthesizer)
- Web search via Tavily API
- Semantic search over documents via pgvector
- LLM-provider abstraction (AWS Bedrock, OpenAI, Mock for dev)
- Server-Sent Events streaming of agent state
- Redis checkpointing for fault tolerance
- Document extraction (PDF, TXT)
- Report generation and S3 storage

## Project Structure

```
meridian/
├── frontend/              # Next.js 14, TypeScript, Tailwind
├── api/                   # Spring Boot 3, Java 21
├── ai-service/            # FastAPI, Python 3.11, LangGraph
├── infra/                 # Terraform modules (VPC, EKS, RDS, etc.)
├── helm/                  # Kubernetes Helm charts
├── docker-compose.yml     # Local development environment
├── .env.example           # Environment configuration template
├── Makefile               # Development automation
└── README.md              # This file
```

## LLM Providers

Meridian works with:

- **AWS Bedrock** (Claude 3.5 Sonnet) — Production recommended
- **OpenAI GPT-4o** — Alternative, requires API key
- **Mock provider** — For development, no API keys needed

Switch via environment variable:

```bash
LLM_PROVIDER=bedrock    # AWS Bedrock
LLM_PROVIDER=openai     # OpenAI
LLM_PROVIDER=mock       # Mock (default for dev)
```

## Deployment

### Kubernetes on AWS EKS

1. **Build Terraform infrastructure:**

   ```bash
   cd infra
   terraform init
   terraform plan
   terraform apply
   ```

2. **Deploy via Helm:**
   ```bash
   cd ../helm
   helm install meridian ./api -n meridian --create-namespace
   helm install meridian-ai ./ai-service -n meridian
   helm install meridian-frontend ./frontend -n meridian
   ```

### Docker Compose (Development)

All services run in Docker with hot-reload enabled:

```bash
make up
```

## Testing

### Unit & Property Tests

```bash
make test-unit
```

Uses:

- **Java**: jqwik (property-based testing)
- **Python**: pytest + hypothesis
- **TypeScript**: Vitest + fast-check

Minimum 100 iterations per property test.

### Integration Tests

```bash
make test-integration
```

Uses Testcontainers for PostgreSQL + Redis.

### E2E Tests

```bash
make test-e2e
```

Uses Playwright for comprehensive browser testing.

### All Tests

```bash
make test
```

Target: ≥80% line coverage (Java/Python), ≥70% (TypeScript)

## Design & Docs

See the comprehensive documentation at `.kiro/specs/meridian-platform/`:

- `design.md` — Technical architecture and data models
- `requirements.md` — Functional & non-functional requirements
- `tasks.md` — 37-task implementation plan
- `docs/Meridian_Complete_Docs.md` — Full specification
- `docs/QUICKSTART.md` — Setup and operation guide
- `docs/IMPLEMENTATION_STATUS.md` — Full implementation status

## Licensing

Meridian is open source under the MIT License.

**Built with ❤️ by the Meridian team**
