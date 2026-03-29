"""
Contract tests: API <-> AI service boundary.
Verifies that the FastAPI AI service honours the request/response contract
expected by the Spring Boot API (FastApiClient).

Priority 1 from ARCHITECTURE_AND_PRODUCTION_READINESS.md:
  "Add contract tests across API <-> AI service boundaries"
"""

import pytest
import json
from unittest.mock import AsyncMock, MagicMock, patch
from fastapi.testclient import TestClient


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _make_app():
    """Import main app with mocked Redis and settings."""
    import sys
    # Provide minimal env so config doesn't fail
    import os
    os.environ.setdefault("LLM_PROVIDER", "mock")
    os.environ.setdefault("REDIS_URL", "redis://localhost:6379")
    os.environ.setdefault("DATABASE_URL", "postgresql+asyncpg://x:x@localhost/x")

    # Patch redis before import
    mock_redis = MagicMock()
    mock_redis.xread.return_value = []
    mock_redis.get.return_value = None
    mock_redis.set.return_value = True
    mock_redis.xadd.return_value = "1-0"
    mock_redis.expire.return_value = True

    with patch("redis.from_url", return_value=mock_redis):
        import importlib
        import main as m
        importlib.reload(m)
        m.redis_client = mock_redis
        m.settings = m.get_settings()
        return m.app, mock_redis


# ---------------------------------------------------------------------------
# Contract: POST /api/v1/jobs/start
# ---------------------------------------------------------------------------

class TestJobStartContract:
    """
    Contract: Spring FastApiClient.startJob() sends these query params;
    AI service must accept them and return {job_id, status, message}.
    """

    REQUIRED_PARAMS = {"query", "project_id", "user_id"}
    REQUIRED_RESPONSE_KEYS = {"job_id", "status", "message"}

    def test_start_job_accepts_required_params(self):
        app, mock_redis = _make_app()
        client = TestClient(app, raise_server_exceptions=False)

        with patch("main.asyncio.create_task"):
            resp = client.post(
                "/api/v1/jobs/start",
                params={
                    "query": "What is quantum computing?",
                    "project_id": "00000000-0000-0000-0000-000000000001",
                    "user_id": "00000000-0000-0000-0000-000000000002",
                    "llm_provider": "mock",
                    "research_depth": "standard",
                },
            )

        assert resp.status_code == 200, f"Expected 200, got {resp.status_code}: {resp.text}"
        body = resp.json()
        for key in self.REQUIRED_RESPONSE_KEYS:
            assert key in body, f"Response missing required key: {key}"

    def test_start_job_returns_pending_status(self):
        app, mock_redis = _make_app()
        client = TestClient(app, raise_server_exceptions=False)

        with patch("main.asyncio.create_task"):
            resp = client.post(
                "/api/v1/jobs/start",
                params={
                    "query": "Explain large language models",
                    "project_id": "00000000-0000-0000-0000-000000000001",
                    "user_id": "00000000-0000-0000-0000-000000000002",
                },
            )

        assert resp.json()["status"] == "pending"

    def test_start_job_rejects_short_query(self):
        app, _ = _make_app()
        client = TestClient(app, raise_server_exceptions=False)

        resp = client.post(
            "/api/v1/jobs/start",
            params={
                "query": "short",  # < 10 chars
                "project_id": "00000000-0000-0000-0000-000000000001",
                "user_id": "00000000-0000-0000-0000-000000000002",
            },
        )
        assert resp.status_code == 422

    def test_start_job_missing_required_params_returns_422(self):
        app, _ = _make_app()
        client = TestClient(app, raise_server_exceptions=False)

        resp = client.post("/api/v1/jobs/start", params={"query": "What is AI?"})
        assert resp.status_code == 422


# ---------------------------------------------------------------------------
# Contract: GET /api/v1/jobs/{job_id}
# ---------------------------------------------------------------------------

class TestJobStatusContract:
    """
    Contract: Spring FastApiClient.getJobStatus() calls GET /api/v1/jobs/{job_id};
    response must include {job_id, status, query}.
    """

    REQUIRED_RESPONSE_KEYS = {"job_id", "status", "query"}

    def test_get_job_status_returns_required_fields(self):
        app, mock_redis = _make_app()
        client = TestClient(app)

        job_id = "test-job-123"
        mock_redis.get.return_value = json.dumps({
            "job_id": job_id,
            "status": "running",
            "query": "Test query",
            "agent_logs": [],
            "error": None,
            "completed_at": None,
        })

        resp = client.get(f"/api/v1/jobs/{job_id}")
        assert resp.status_code == 200
        body = resp.json()
        for key in self.REQUIRED_RESPONSE_KEYS:
            assert key in body, f"Response missing required key: {key}"

    def test_get_job_status_404_for_unknown_job(self):
        app, mock_redis = _make_app()
        mock_redis.get.return_value = None
        client = TestClient(app)

        resp = client.get("/api/v1/jobs/nonexistent-job")
        assert resp.status_code == 404


# ---------------------------------------------------------------------------
# Contract: GET /health
# ---------------------------------------------------------------------------

class TestHealthContract:
    """Spring FastApiClient.isHealthy() calls GET /health; must return 200."""

    def test_health_returns_200(self):
        app, _ = _make_app()
        client = TestClient(app)
        resp = client.get("/health")
        assert resp.status_code == 200
        assert resp.json()["status"] == "healthy"


# ---------------------------------------------------------------------------
# Contract: GET /ai/stream/{job_id} — SSE auth
# ---------------------------------------------------------------------------

class TestSSEAuthContract:
    """SSE endpoint must reject requests without a token."""

    def test_sse_rejects_missing_token(self):
        app, _ = _make_app()
        client = TestClient(app)
        resp = client.get("/ai/stream/some-job-id")
        # FastAPI returns 422 when required query param is missing
        assert resp.status_code in (401, 422)
