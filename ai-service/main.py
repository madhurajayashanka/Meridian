from contextlib import asynccontextmanager
from fastapi import FastAPI, HTTPException, Query, Request, UploadFile, File, Form, Header, Depends
from fastapi.responses import StreamingResponse
from fastapi.middleware.cors import CORSMiddleware
import asyncio
import json
import os
import tempfile
from datetime import datetime
import uuid
import logging
import httpx
import jwt as pyjwt
import hmac
import hashlib
import contextvars

from app.config import get_settings, Settings
from app.graph.workflow import build_research_graph, create_initial_state
from app.llm.provider import get_llm_provider
from app.rag.store import EventPublisher
from app.metrics import (
    metrics_endpoint, http_metrics_middleware,
    JOB_STARTED, JOB_COMPLETED, JOB_FAILED, JOB_DURATION,
)
import redis as redis_lib

# ── Logging ──────────────────────────────────────────────────────────────────
_log_level = os.environ.get("LOG_LEVEL", "INFO").upper()
logging.basicConfig(
    level=getattr(logging, _log_level, logging.INFO),
    format='{"time":"%(asctime)s","level":"%(levelname)s","logger":"%(name)s","msg":"%(message)s"}',
)
logger = logging.getLogger(__name__)

# ── Correlation ID ────────────────────────────────────────────────────────────
CORRELATION_ID_HEADER = "X-Correlation-ID"
_correlation_id_var: contextvars.ContextVar[str] = contextvars.ContextVar("correlation_id", default="")

def get_correlation_id() -> str:
    return _correlation_id_var.get("")

# ── Global state (populated in lifespan) ─────────────────────────────────────
settings: Settings = None
event_publisher: EventPublisher = None
research_graphs: dict = {}
redis_client = None
db_pool = None


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Initialize and teardown all services."""
    global settings, event_publisher, research_graphs, redis_client, db_pool

    settings = get_settings()
    logger.info(f"Starting with LLM provider: {settings.llm_provider}")

    # Propagate AWS credentials to environment for boto3
    for key, val in [
        ("AWS_ACCESS_KEY_ID", settings.aws_access_key_id),
        ("AWS_SECRET_ACCESS_KEY", settings.aws_secret_access_key),
        ("AWS_REGION", settings.aws_region),
        ("AWS_DEFAULT_REGION", settings.aws_region),
    ]:
        if val:
            os.environ[key] = val

    event_publisher = EventPublisher(settings.redis_url)
    await event_publisher.initialize()

    redis_client = redis_lib.from_url(settings.redis_url, decode_responses=True)

    import asyncpg
    _db_url = settings.database_url.replace("postgresql+asyncpg://", "postgresql://")
    db_pool = await asyncpg.create_pool(_db_url, min_size=2, max_size=10)

    research_graphs = {}
    get_research_graph(settings.llm_provider)

    logger.info("AI Service initialized successfully")
    yield

    # ── Shutdown ──────────────────────────────────────────────────────────────
    if event_publisher:
        await event_publisher.close()
    if redis_client:
        redis_client.close()
    if db_pool:
        await db_pool.close()
    logger.info("AI Service shutdown complete")


# ── App ───────────────────────────────────────────────────────────────────────
app = FastAPI(
    title="Meridian AI Service",
    version="1.0.0",
    description="Autonomous multi-agent research platform",
    lifespan=lifespan,
)


@app.middleware("http")
async def correlation_id_middleware(request: Request, call_next):
    correlation_id = request.headers.get(CORRELATION_ID_HEADER) or str(uuid.uuid4())
    token = _correlation_id_var.set(correlation_id)
    response = await call_next(request)
    response.headers[CORRELATION_ID_HEADER] = correlation_id
    _correlation_id_var.reset(token)
    return response


# CORS — must be added after app is created; settings loaded in lifespan so we
# read the env var directly here (same source of truth).
_cors_origins = os.environ.get("CORS_ALLOWED_ORIGINS", "http://localhost:3000").split(",")
app.add_middleware(
    CORSMiddleware,
    allow_origins=_cors_origins,
    allow_credentials=True,
    allow_methods=["GET", "POST", "OPTIONS"],
    allow_headers=["Authorization", "Content-Type", "X-Correlation-ID"],
)
app.middleware("http")(http_metrics_middleware)
app.add_route("/metrics", metrics_endpoint)


# ── Helpers ───────────────────────────────────────────────────────────────────

def get_research_graph(provider_name: str):
    normalized = (provider_name or settings.llm_provider).lower()
    if normalized in research_graphs:
        return research_graphs[normalized]

    if normalized == "bedrock" and (not settings.aws_access_key_id or not settings.aws_secret_access_key):
        raise RuntimeError("AWS Bedrock credentials are not configured")

    llm_provider = get_llm_provider(
        normalized,
        api_key=settings.openai_api_key if normalized == "openai" else None,
        model=settings.openai_model if normalized == "openai" else None,
        model_fast=settings.openai_model_fast if normalized == "openai" else None,
        embedding_model=(
            settings.openai_embedding_model if normalized == "openai"
            else settings.bedrock_embedding_model
        ),
        model_id=settings.bedrock_model_id if normalized == "bedrock" else None,
        model_id_fast=settings.bedrock_model_id_fast if normalized == "bedrock" else None,
        region=settings.aws_region,
    )
    research_graphs[normalized] = build_research_graph(llm_provider)
    return research_graphs[normalized]


def validate_jwt_token(token: str) -> dict:
    if not token:
        raise HTTPException(status_code=401, detail="Missing token")
    public_key = settings.jwt_public_key
    if not public_key:
        logger.warning("JWT_PUBLIC_KEY not configured; skipping signature verification (dev mode)")
        return {}
    try:
        pem = public_key.replace("\\n", "\n")
        return pyjwt.decode(token, pem, algorithms=["RS256"], options={"verify_exp": True})
    except pyjwt.ExpiredSignatureError:
        raise HTTPException(status_code=401, detail="Token expired")
    except pyjwt.InvalidTokenError as e:
        raise HTTPException(status_code=401, detail=f"Invalid token: {e}")


def _require_service_key(x_service_key: str = Header(default="")) -> None:
    key = settings.internal_api_key if settings else ""
    if key and x_service_key != key:
        raise HTTPException(status_code=401, detail="Invalid service key")


def _signed_headers(payload: dict) -> dict:
    body = json.dumps(payload).encode()
    secret = settings.webhook_secret if settings else ""
    headers = {"Content-Type": "application/json"}
    if secret:
        sig = "sha256=" + hmac.new(secret.encode(), body, hashlib.sha256).hexdigest()
        headers["X-Webhook-Signature"] = sig
    return headers


def extract_report_title(report_content: str, query: str) -> str:
    if report_content:
        for line in report_content.splitlines():
            if line.startswith("# "):
                return line[2:].strip()
    return f"Research: {query[:490]}"


def count_citations(report_content: str) -> int:
    import re
    return len(re.findall(r'\[\d+\]', report_content or ""))


# ── Routes ────────────────────────────────────────────────────────────────────

@app.get("/health")
async def health_check():
    checks = {"db": "ok", "redis": "ok"}
    status = "healthy"
    try:
        if db_pool:
            async with db_pool.acquire() as conn:
                await conn.fetchval("SELECT 1")
        else:
            checks["db"] = "not_initialized"
            status = "degraded"
    except Exception as e:
        checks["db"] = f"error: {e}"
        status = "degraded"
    try:
        if redis_client:
            redis_client.ping()
        else:
            checks["redis"] = "not_initialized"
            status = "degraded"
    except Exception as e:
        checks["redis"] = f"error: {e}"
        status = "degraded"
    return {
        "status": status,
        "service": "meridian-ai",
        "llm_provider": settings.llm_provider if settings else "unknown",
        "checks": checks,
        "timestamp": datetime.now().isoformat(),
    }


@app.post("/api/v1/jobs/start", dependencies=[Depends(_require_service_key)])
async def start_job(
    job_id: str = Query(None),
    query: str = Query(..., min_length=10, max_length=500),
    project_id: str = Query(...),
    user_id: str = Query(...),
    llm_provider: str = Query("bedrock"),
    research_depth: str = Query("standard"),
    document_ids: list = Query(None),
):
    try:
        job_id = job_id or str(uuid.uuid4())
        state = create_initial_state(
            job_id=job_id,
            user_id=user_id,
            query=query,
            llm_provider=llm_provider,
            research_depth=research_depth,
            uploaded_doc_ids=document_ids or [],
        )
        redis_client.set(
            f"langgraph:checkpoint:{job_id}",
            json.dumps(state, default=str),
            ex=172800,
        )
        asyncio.create_task(execute_job(job_id, state))
        JOB_STARTED.inc()
        logger.info(f"Job {job_id} started [cid={get_correlation_id()}]")
        return {"job_id": job_id, "status": "pending", "message": "Research job started"}
    except Exception as e:
        logger.error(f"Error starting job: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/api/v1/jobs/{job_id}/cancel", dependencies=[Depends(_require_service_key)])
async def cancel_job(job_id: str):
    """Cancel a running research job."""
    checkpoint = redis_client.get(f"langgraph:checkpoint:{job_id}")
    if not checkpoint:
        raise HTTPException(status_code=404, detail="Job not found")
    state = json.loads(checkpoint)
    if state.get("status") in ("complete", "failed", "cancelled"):
        return {"job_id": job_id, "status": state["status"], "message": "Job already finished"}
    state["status"] = "cancelled"
    state["error"] = "Cancelled by user"
    redis_client.set(f"langgraph:checkpoint:{job_id}", json.dumps(state, default=str), ex=86400)
    await event_publisher.publish_job_failed(job_id, "Cancelled by user")
    logger.info(f"Job {job_id} cancelled")
    return {"job_id": job_id, "status": "cancelled"}


@app.get("/api/v1/jobs/{job_id}")
async def get_job_status(job_id: str):
    checkpoint = redis_client.get(f"langgraph:checkpoint:{job_id}")
    if not checkpoint:
        raise HTTPException(status_code=404, detail="Job not found")
    state = json.loads(checkpoint)
    return {
        "job_id": job_id,
        "status": state.get("status"),
        "query": state.get("query"),
        "agent_logs": state.get("agent_logs", []),
        "error": state.get("error"),
        "completed_at": state.get("completed_at"),
    }


@app.get("/ai/stream/{job_id}")
async def stream_events(job_id: str, token: str = Query(...)):
    validate_jwt_token(token)

    async def event_generator():
        last_id = "0"
        heartbeat_interval = 15  # seconds
        idle_ticks = 0

        while True:
            try:
                messages = redis_client.xread(
                    {f"job:{job_id}:events": last_id}, block=1000, count=10
                )
                if messages:
                    idle_ticks = 0
                    for stream, stream_messages in messages:
                        for msg_id, data in stream_messages:
                            last_id = msg_id
                            event_type = data.get("type", "agent_update")
                            yield f"event: {event_type}\ndata: {json.dumps(data)}\n\n"
                else:
                    idle_ticks += 1
                    if idle_ticks >= heartbeat_interval:
                        idle_ticks = 0
                        yield f"event: heartbeat\ndata: {{}}\n\n"

                checkpoint = redis_client.get(f"langgraph:checkpoint:{job_id}")
                if checkpoint:
                    state = json.loads(checkpoint)
                    if state.get("status") in ("complete", "failed", "cancelled"):
                        if state["status"] == "complete":
                            yield f"event: job_complete\ndata: {json.dumps({'job_id': job_id, 'status': 'complete'})}\n\n"
                        else:
                            yield f"event: job_failed\ndata: {json.dumps({'job_id': job_id, 'error': state.get('error')})}\n\n"
                        break

                await asyncio.sleep(0.1)
            except Exception as e:
                logger.error(f"SSE error for job {job_id}: {e}")
                yield f"event: error\ndata: {json.dumps({'error': str(e)})}\n\n"
                break

    return StreamingResponse(
        event_generator(),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"},
    )


@app.post("/api/v1/documents/process", dependencies=[Depends(_require_service_key)])
async def process_document(
    document_id: str = Form(...),
    file_type: str = Form(...),
    file: UploadFile = File(...),
):
    from app.rag.extractor import DocumentExtractor, TextChunker
    from app.rag.store import EmbeddingStore

    tmp_path = None
    try:
        suffix = f".{file_type.lower()}"
        with tempfile.NamedTemporaryFile(delete=False, suffix=suffix) as tmp:
            tmp.write(await file.read())
            tmp_path = tmp.name

        text = DocumentExtractor.extract_text(tmp_path, file_type)
        if not text:
            raise HTTPException(status_code=422, detail="Could not extract text from document")

        chunks = TextChunker.chunk_text(text)
        if not chunks:
            raise HTTPException(status_code=422, detail="Document produced no chunks")

        provider_name = settings.llm_provider
        from app.llm.provider import get_llm_provider as _get_provider
        provider = _get_provider(
            provider_name,
            api_key=settings.openai_api_key if provider_name == "openai" else None,
            model_id=settings.bedrock_model_id if provider_name == "bedrock" else None,
            region=settings.aws_region,
        )

        if db_pool is None:
            raise HTTPException(status_code=503, detail="Database not ready")

        # Reuse global pool — no per-request pool creation
        store = EmbeddingStore.__new__(EmbeddingStore)
        store.db_url = None
        store.pool = db_pool

        for idx, chunk in enumerate(chunks):
            embedding = await provider.embed_text(chunk)
            await store.store_embedding(
                content=chunk,
                embedding=embedding,
                document_id=document_id,
                metadata={"chunk_index": idx, "document_id": document_id},
            )

        logger.info(f"Processed document {document_id}: {len(chunks)} chunks embedded")
        return {"document_id": document_id, "chunk_count": len(chunks), "status": "ready"}

    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Error processing document {document_id}: {e}")
        raise HTTPException(status_code=500, detail=str(e))
    finally:
        if tmp_path and os.path.exists(tmp_path):
            os.unlink(tmp_path)


@app.post("/api/v1/chat/{report_id}")
async def chat_with_report(
    report_id: str,
    token: str = Query(...),
    user_id: str = Query(...),
    message: str = Query(...),
):
    validate_jwt_token(token)

    from app.guardrails import check_input, GuardrailViolation
    try:
        check_input(message)
    except GuardrailViolation as e:
        raise HTTPException(status_code=400, detail=f"Message blocked: {e.reason}")

    from app.api.chat import RAGChatService

    try:
        if db_pool is None:
            raise HTTPException(status_code=503, detail="Database not ready")

        provider_name = settings.llm_provider
        from app.llm.provider import get_llm_provider as _get_provider
        provider = _get_provider(
            provider_name,
            api_key=settings.openai_api_key if provider_name == "openai" else None,
            model_id=settings.bedrock_model_id if provider_name == "bedrock" else None,
            model_id_fast=settings.bedrock_model_id_fast if provider_name == "bedrock" else None,
            region=settings.aws_region,
        )

        chat_service = RAGChatService(db_pool, provider)
        full_response = ""
        async for chunk in chat_service.stream_chat_response(
            report_id=report_id, user_message=message, user_id=user_id
        ):
            full_response += chunk

        return {"response": full_response, "report_id": report_id}

    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Chat error for report {report_id}: {e}")
        raise HTTPException(status_code=500, detail=str(e))


# ── Background job execution ──────────────────────────────────────────────────

async def execute_job(job_id: str, initial_state):
    _job_start = __import__("time").perf_counter()
    try:
        research_graph = get_research_graph(initial_state.get("llm_provider"))
        final_state = await research_graph.ainvoke(initial_state, {"recursion_limit": 100})

        redis_client.set(
            f"langgraph:checkpoint:{job_id}",
            json.dumps(final_state, default=str),
            ex=86400,
        )

        if final_state.get("status") == "complete":
            JOB_COMPLETED.inc()
            JOB_DURATION.observe(__import__("time").perf_counter() - _job_start)
            report_id = str(uuid.uuid4())
            final_report = final_state.get("final_report") or ""
            await event_publisher.publish_job_complete(job_id, report_id)

            payload = {
                "reportId": report_id,
                "title": extract_report_title(final_report, initial_state.get("query")),
                "content": final_report,
                "wordCount": len(final_report.split()) if final_report else None,
                "citationCount": count_citations(final_report),
                "criticScore": final_state.get("critic_score"),
                "revisionCount": final_state.get("analysis_iteration") or 0,
                "timestamp": int(datetime.now().timestamp() * 1000),
            }
            await _notify_api(f"/api/webhooks/jobs/{job_id}/complete", payload)
            logger.info(f"Job {job_id} completed with report {report_id}")
        else:
            JOB_FAILED.labels(reason="workflow_failed").inc()
            await event_publisher.publish_job_failed(job_id, final_state.get("error", "Unknown error"))
            fail_payload = {
                "error": final_state.get("error", "Unknown error"),
                "timestamp": int(datetime.now().timestamp() * 1000),
            }
            await _notify_api(f"/api/webhooks/jobs/{job_id}/failed", fail_payload)
            logger.error(f"Job {job_id} failed: {final_state.get('error')}")

    except Exception as e:
        JOB_FAILED.labels(reason="exception").inc()
        logger.error(f"Error executing job {job_id}: {e}")
        await event_publisher.publish_job_failed(job_id, str(e))
        await _notify_api(
            f"/api/webhooks/jobs/{job_id}/failed",
            {"error": str(e), "timestamp": int(datetime.now().timestamp() * 1000)},
        )


async def _notify_api(path: str, payload: dict):
    """Fire-and-forget callback to Spring API with HMAC signature."""
    api_url = os.environ.get("API_INTERNAL_URL", "http://api:8000")
    try:
        async with httpx.AsyncClient(timeout=10.0) as client:
            resp = await client.post(
                f"{api_url}{path}", json=payload, headers=_signed_headers(payload)
            )
            resp.raise_for_status()
    except Exception as e:
        logger.error(f"Failed to notify API at {path}: {e}")


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8080)
