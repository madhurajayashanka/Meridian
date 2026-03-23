# Meridian Platform — Quick Start Guide

## 🚀 Getting Started (5 minutes)

### 1. Setup Environment

```bash
cd meridian

# Create .env file from template
make dev-setup

# Generate RSA keypair for JWT signing
make dev-keys
# (Copy outputs into .env: JWT_SECRET_KEY and JWT_PUBLIC_KEY)
```

### 2. Edit .env File

Customize environment variables (optional for development):

```bash
# API Configuration
API_PORT=8000
AI_SERVICE_PORT=8080

# LLM Provider (mock|openai|bedrock)
LLM_PROVIDER=mock

# Database (pre-configured for Docker)
DATABASE_URL=postgresql+asyncpg://meridian:meridian_dev_password@postgres:5432/meridian

# Optional: Add real API keys if not using mock
# TAVILY_API_KEY=your_key
# OPENAI_API_KEY=your_key
```

### 3. Start All Services

```bash
make up

# Services will be available at:
# - Frontend:    http://localhost:3000
# - GraphQL:     http://localhost:8000/graphql
# - API Health:  http://localhost:8000/actuator/health
# - AI Health:   http://localhost:8080/health
```

### 4. View Logs

```bash
make logs              # All services
make logs-api          # Spring Boot API
make logs-ai           # FastAPI AI
make logs-postgres     # Database
```

### 5. Stop Services

```bash
make down
```

---

## 📋 Project Structure

```
meridian/
├── api/                      # Spring Boot GraphQL API (Java 21)
│   ├── build.gradle
│   ├── src/main/java/com/meridian/
│   │   ├── auth/            # JWT, login, register
│   │   ├── project/         # Project CRUD
│   │   ├── job/             # Research job management
│   │   ├── report/          # Report retrieval
│   │   ├── document/        # Document management
│   │   ├── chat/            # Chat history
│   │   ├── config/          # Security, GraphQL config
│   │   └── common/          # Exceptions, utilities
│   └── src/main/resources/
│       ├── db/migration/    # Flyway migrations
│       └── application.yml  # Configuration
│
├── ai-service/              # FastAPI AI Service (Python 3.11)
│   ├── main.py             # FastAPI app
│   ├── app/
│   │   ├── config.py       # Settings
│   │   ├── agents/         # 5-agent nodes
│   │   ├── graph/          # LangGraph workflow
│   │   ├── llm/            # LLM providers
│   │   ├── rag/            # Vector store & events
│   │   └── api/            # REST endpoints
│   ├── tests/              # Pytest test suite
│   └── requirements.txt
│
├── frontend/               # Next.js SPA (TypeScript)
│   ├── src/
│   │   ├── app/           # Pages & routing
│   │   ├── components/    # React components
│   │   ├── hooks/         # Custom hooks (auth, SSE, API)
│   │   ├── types/         # TypeScript interfaces
│   │   └── styles/        # Tailwind CSS
│   ├── package.json
│   ├── tsconfig.json
│   └── next.config.js
│
├── infra/                 # Terraform (AWS infrastructure)
│   └── modules/           # VPC, EKS, RDS, S3, ECR
│
├── helm/                  # Kubernetes manifests
│   ├── api/              # Spring Boot Helm chart
│   ├── ai-service/       # FastAPI Helm chart
│   └── frontend/         # Next.js Helm chart
│
├── docker-compose.yml     # Local development
├── Makefile               # Development commands
├── .env.example           # Environment template
├── README.md              # Project overview
└── docs/                  # Central documentation
   └── IMPLEMENTATION_STATUS.md  # Current progress

```

---

## 🧪 Testing

```bash
# Run all tests
make test

# Unit tests only
make test-unit

# Integration tests
make test-integration

# E2E Playwright tests (requires built frontend)
make test-e2e
```

### Property-Based Tests

The test suite includes property-based tests using:

- **Java**: jqwik (100+ iterations per property)
- **Python**: hypothesis (100+ iterations per property)
- **TypeScript**: fast-check (100+ iterations per property)

See `ai-service/tests/test_agents.py` for examples of property-based testing.

---

## 🏗️ Architecture Overview

### Five-Agent Orchestration

When a user submits a research query, Meridian orchestrates this workflow:

```
1. Planner Agent
   └─> Decompose query into 3-5 sub-questions

2. Research Agent (parallel for each sub-question)
   ├─> Web search via Tavily API
   └─> Semantic search in pgvector (user docs)

3. Analysis Agent
   └─> Synthesize findings into structured draft

4. Critic Agent
   ├─> Evaluate draft (score 1.0-10.0)
   └─> If score < 7.0 and iterations < 3:
       └─> Loop back to Analysis
       Else: Continue to Synthesizer

5. Synthesizer Agent
   └─> Generate final Markdown report

All agent states checkpointed to Redis for resumability.
```

### Real-Time Streaming

The frontend receives live agent updates via Server-Sent Events:

```
Browser  ──SSE─→  FastAPI  ──Redis Stream───┐
                                            │
┌───────────────────────────────────────────┘
│
└─→ Agent Update Events
    ├─ agent: "planner", status: "complete"
    ├─ agent: "research", status: "running"
    ├─ job_complete: {report_id: "..."}
    └─ job_failed: {error: "..."}
```

---

## 🔐 Authentication Flow

```
1. User registers with email/password
   └─> PasswordEncoder.encode() → Bcrypt $2b$12$ hash
   └─> Returns access_token (15 min) + refresh_token (7 days)

2. User logs in
   └─> JwtUtil.generateAccessToken() → RS256 signed JWT
   └─> RefreshToken stored in PostgreSQL

3. Accessing protected endpoints
   └─> Frontend sends: Authorization: Bearer {access_token}
   └─> JwtAuthenticationFilter validates signature

4. Token expiry → use refresh_token
   └─> Old refresh_token revoked
   └─> New pair issued (Property 7: Password change revokes all)
```

---

## 📦 Key Technologies

| Layer              | Stack                                         |
| ------------------ | --------------------------------------------- |
| **Frontend**       | Next.js 14 + TypeScript + Tailwind + Zustand  |
| **API**            | Spring Boot 3 + GraphQL + Spring Data JPA     |
| **AI**             | FastAPI + LangGraph + LangChain + Pydantic    |
| **Database**       | PostgreSQL 15 + pgvector (HNSW)               |
| **Cache/Queue**    | Redis 7 (state, events, rate limit)           |
| **Authentication** | JWT (RS256) + Bcrypt                          |
| **Search**         | Tavily API + pgvector semantic search         |
| \*\*LLM            | AWS Bedrock (Claude) / OpenAI (GPT-4o) / Mock |
| **Deployment**     | Docker + Kubernetes + Terraform               |
| **CI/CD**          | GitHub Actions                                |

---

## 🛠️ Development Workflow

### Making Changes

1. **Backend API**

   ```bash
   # Edit Spring Boot code in api/src/main/java/
   # Changes hot-reload in Docker container
   make logs-api
   ```

2. **AI Service**

   ```bash
   # Edit FastAPI code in ai-service/app/
   # Changes hot-reload in Docker container
   make logs-ai
   ```

3. **Frontend**
   ```bash
   # Edit Next.js code in frontend/src/
   # Changes hot-reload via Next.js dev server
   make logs  # Monitor dev server
   ```

### Database Migrations

```bash
# Add new migration
vim api/src/main/resources/db/migration/V{VERSION}__{description}.sql

# Run migrations (automatic on API startup)
```

---

## 🚨 Troubleshooting

### Services Won't Start

```bash
# Check logs
make logs

# Ensure ports are free
lsof -i :3000
lsof -i :8000
lsof -i :8080
lsof -i :5432
lsof -i :6379
```

### Database Connection Error

```bash
# Ensure PostgreSQL is healthy
docker-compose ps
docker-compose logs postgres

# Manually inspect database
docker-compose exec postgres psql -U meridian -d meridian
```

### JWT Key Issues

```bash
# Regenerate keys
make dev-keys

# Ensure .env has correct format (no spaces):
JWT_SECRET_KEY=-----BEGIN PRIVATE KEY-----\n...
```

### Redis Connection Error

```bash
# Check Redis
docker-compose exec redis redis-cli ping
# Should return: PONG
```

---

## 📚 API Examples

### Register User

```bash
curl -X POST http://localhost:8000/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "user@example.com",
    "password": "securepass123",
    "name": "John Doe"
  }'

# Response:
# {
#   "access_token": "eyJ0eXAiOiJKV1QiLCJhbGc...",
#   "refresh_token": "eyJ0eXAiOiJKV1QiLCJhbGc...",
#   "expires_in": 900,
#   "token_type": "Bearer"
# }
```

### Submit Research Job

```bash
curl -X POST "http://localhost:8080/api/v1/jobs/start?query=What%20is%20machine%20learning&project_id=proj-1&user_id=user-1&llm_provider=mock&research_depth=standard" \
  -H "Authorization: Bearer {access_token}"

# Response:
# {
#   "job_id": "550e8400-e29b-41d4-a716-446655440000",
#   "status": "pending",
#   "message": "Research job started"
# }
```

### Stream Job Events

```bash
curl -N "http://localhost:8080/ai/stream/550e8400-e29b-41d4-a716-446655440000?token={access_token}"

# Response (Server-Sent Events):
# event: agent_update
# data: {"agent":"planner","status":"complete","progress":"100"}
#
# event: agent_update
# data: {"agent":"research","status":"running","progress":"0"}
```

---

## 📖 Further Reading

- **Design Document**: [design.md](../.kiro/specs/meridian-platform/design.md)
- **Requirements Document**: [requirements.md](../.kiro/specs/meridian-platform/requirements.md)
- **Implementation Plan**: [tasks.md](../.kiro/specs/meridian-platform/tasks.md)
- **Complete Specification**: [Meridian_Complete_Docs.md](./Meridian_Complete_Docs.md)
- **Implementation Status**: [IMPLEMENTATION_STATUS.md](./IMPLEMENTATION_STATUS.md)

---

## 💡 Next Steps

1. **Run the project**: `make up` and visit http://localhost:3000
2. **Implement GraphQL schema** (Task 5): Define CRUD mutations in Spring Boot
3. **Build auth pages** (Task 25): Login/register forms in Next.js
4. **Create research form** (Task 27): Query input, job submission UI
5. **Test everything** (Tasks 35-37): Unit, integration, and E2E tests

---

**Need help?** Check the logs: `make logs`

Happy researching! 🔍📊
