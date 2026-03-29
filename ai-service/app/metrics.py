"""
Prometheus RED metrics (Rate, Errors, Duration) for the AI service.
Exposes /metrics endpoint consumed by Prometheus scraper.
"""
from prometheus_client import Counter, Histogram, generate_latest, CONTENT_TYPE_LATEST
from fastapi import Request
from fastapi.responses import Response
import time

# --- Counters ---
REQUEST_COUNT = Counter(
    "ai_requests_total",
    "Total HTTP requests",
    ["method", "endpoint", "status_code"],
)

JOB_STARTED = Counter("ai_jobs_started_total", "Research jobs started")
JOB_COMPLETED = Counter("ai_jobs_completed_total", "Research jobs completed successfully")
JOB_FAILED = Counter("ai_jobs_failed_total", "Research jobs failed", ["reason"])

GUARDRAIL_BLOCKED = Counter(
    "ai_guardrail_blocked_total",
    "Requests blocked by guardrails",
    ["check_type"],
)

AGENT_ERRORS = Counter(
    "ai_agent_errors_total",
    "Agent node errors",
    ["agent"],
)

TOKEN_BUDGET_EXCEEDED = Counter(
    "ai_token_budget_exceeded_total",
    "Jobs stopped due to token budget",
)

# --- Histograms ---
REQUEST_LATENCY = Histogram(
    "ai_request_duration_seconds",
    "HTTP request latency",
    ["method", "endpoint"],
    buckets=[0.1, 0.5, 1, 2, 5, 10, 30, 60],
)

JOB_DURATION = Histogram(
    "ai_job_duration_seconds",
    "End-to-end research job duration",
    buckets=[5, 15, 30, 60, 120, 300, 600],
)

AGENT_DURATION = Histogram(
    "ai_agent_duration_seconds",
    "Per-agent execution duration",
    ["agent"],
    buckets=[1, 2, 5, 10, 30, 60],
)

RAG_RETRIEVAL_SCORE = Histogram(
    "ai_rag_retrieval_score",
    "Top-1 similarity score from RAG retrieval",
    buckets=[0.1, 0.3, 0.5, 0.7, 0.8, 0.9, 1.0],
)


async def metrics_endpoint(_request: Request) -> Response:
    """Expose Prometheus metrics at /metrics."""
    return Response(generate_latest(), media_type=CONTENT_TYPE_LATEST)


async def http_metrics_middleware(request: Request, call_next):
    """Middleware: record request count and latency for every HTTP call."""
    start = time.perf_counter()
    response = await call_next(request)
    duration = time.perf_counter() - start

    endpoint = request.url.path
    REQUEST_COUNT.labels(
        method=request.method,
        endpoint=endpoint,
        status_code=str(response.status_code),
    ).inc()
    REQUEST_LATENCY.labels(method=request.method, endpoint=endpoint).observe(duration)

    return response
