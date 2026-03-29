from fastapi import FastAPI, HTTPException, Query, Request
from fastapi.responses import StreamingResponse
from fastapi.middleware.cors import CORSMiddleware
import asyncio
import json
import os
from datetime import datetime
import uuid
import logging
import httpx
import jwt as pyjwt
import hmac
import hashlib

from app.config import get_settings, Settings
from app.graph.workflow import build_research_graph, create_initial_state
from app.llm.provider import get_llm_provider
from app.rag.store import EventPublisher
from app.metrics import (
    metrics_endpoint, http_metrics_middleware,
    JOB_STARTED, JOB_COMPLETED, JOB_FAILED, JOB_DURATION,
)
import redis

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# Initialize FastAPI
app = FastAPI(
    title="Meridian AI Service",
    version="1.0.0",
    description="Autonomous multi-agent research platform"
)

# CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)
app.middleware("http")(http_metrics_middleware)
app.add_route("/metrics", metrics_endpoint)

# Global state
settings = None
event_publisher = None
research_graphs = {}
redis_client = None
active_jobs = {}  # job_id -> state


CORRELATION_ID_HEADER = "X-Correlation-ID"


@app.middleware("http")
async def correlation_id_middleware(request: Request, call_next):
    """Propagate or generate X-Correlation-ID for every request."""
    correlation_id = request.headers.get(CORRELATION_ID_HEADER) or str(uuid.uuid4())
    import contextvars
    token = _correlation_id_var.set(correlation_id)
    response = await call_next(request)
    response.headers[CORRELATION_ID_HEADER] = correlation_id
    _correlation_id_var.reset(token)
    return response


import contextvars
_correlation_id_var: contextvars.ContextVar[str] = contextvars.ContextVar(
    "correlation_id", default=""
)


def get_correlation_id() -> str:
    return _correlation_id_var.get("")



async def startup():
    """Initialize services on startup."""
    global settings, event_publisher, research_graphs, redis_client
    
    settings = get_settings()
    logger.info(f"Starting with LLM provider: {settings.llm_provider}")

    if settings.aws_access_key_id:
        os.environ["AWS_ACCESS_KEY_ID"] = settings.aws_access_key_id
    if settings.aws_secret_access_key:
        os.environ["AWS_SECRET_ACCESS_KEY"] = settings.aws_secret_access_key
    if settings.aws_region:
        os.environ["AWS_REGION"] = settings.aws_region
        os.environ["AWS_DEFAULT_REGION"] = settings.aws_region
    
    # Initialize event publisher
    event_publisher = EventPublisher(settings.redis_url)
    await event_publisher.initialize()
    
    # Initialize Redis client
    redis_client = redis.from_url(settings.redis_url, decode_responses=True)
    
    research_graphs = {}
    get_research_graph(settings.llm_provider)
    
    logger.info("AI Service initialized successfully")


@app.on_event("shutdown")
async def shutdown():
    """Cleanup on shutdown."""
    global event_publisher, redis_client
    
    if event_publisher:
        await event_publisher.close()
    
    if redis_client:
        redis_client.close()
    
    logger.info("AI Service shutdown complete")


def get_research_graph(provider_name: str):
    normalized = (provider_name or settings.llm_provider).lower()
    if normalized in research_graphs:
        return research_graphs[normalized]

    if normalized == "bedrock":
        if not settings.aws_access_key_id or not settings.aws_secret_access_key:
            raise RuntimeError("AWS Bedrock credentials are not configured for the AI service")

    llm_provider = get_llm_provider(
        normalized,
        api_key=settings.openai_api_key if normalized == "openai" else None,
        model=settings.openai_model if normalized == "openai" else None,
        embedding_model=(
            settings.openai_embedding_model if normalized == "openai" else settings.bedrock_embedding_model
        ),
        model_id=settings.bedrock_model_id if normalized == "bedrock" else None,
        region=settings.aws_region,
    )
    research_graphs[normalized] = build_research_graph(llm_provider)
    return research_graphs[normalized]


def validate_jwt_token(token: str) -> dict:
    """
    Validate a JWT token using the configured RSA public key.
    Falls back to a non-empty check when no public key is configured (dev mode).
    Returns the decoded payload or raises HTTPException on failure.
    """
    if not token:
        raise HTTPException(status_code=401, detail="Missing token")

    public_key = settings.jwt_public_key
    if not public_key:
        # Dev mode: no key configured — accept any non-empty token
        logger.warning("JWT_PUBLIC_KEY not configured; skipping signature verification (dev mode)")
        return {}

    try:
        # Normalize PEM (env vars may use literal \n)
        pem = public_key.replace("\\n", "\n")
        payload = pyjwt.decode(
            token,
            pem,
            algorithms=["RS256"],
            options={"verify_exp": True},
        )
        return payload
    except pyjwt.ExpiredSignatureError:
        raise HTTPException(status_code=401, detail="Token expired")
    except pyjwt.InvalidTokenError as e:
        raise HTTPException(status_code=401, detail=f"Invalid token: {e}")



async def health_check():
    """Health check endpoint."""
    return {
        "status": "healthy",
        "service": "meridian-ai",
        "llm_provider": settings.llm_provider,
        "timestamp": datetime.now().isoformat()
    }


@app.post("/api/v1/jobs/start")
async def start_job(
    job_id: str = Query(None),
    query: str = Query(..., min_length=10, max_length=500),
    project_id: str = Query(...),
    user_id: str = Query(...),
    llm_provider: str = Query("bedrock"),
    research_depth: str = Query("standard"),
    document_ids: list = Query(None)
):
    """
    Start a new research job.
    Requirement 4.1: Submit research query and trigger AI service
    """
    try:
        job_id = job_id or str(uuid.uuid4())
        
        # Create initial state
        state = create_initial_state(
            job_id=job_id,
            user_id=user_id,
            query=query,
            llm_provider=llm_provider,
            research_depth=research_depth,
            uploaded_doc_ids=document_ids or []
        )
        
        # Store initial state in Redis for checkpointing
        redis_client.set(
            f"langgraph:checkpoint:{job_id}",
            json.dumps(state, default=str),
            ex=172800  # 48 hours TTL
        )
        
        # Start async job execution
        asyncio.create_task(execute_job(job_id, state))
        JOB_STARTED.inc()
        logger.info(f"Job {job_id} started for user {user_id} [correlation_id={get_correlation_id()}]")
        
        return {
            "job_id": job_id,
            "status": "pending",
            "message": "Research job started"
        }
    
    except Exception as e:
        logger.error(f"Error starting job: {e}")
        raise HTTPException(status_code=500, detail=str(e))


async def execute_job(job_id: str, initial_state):
    """Execute the research job workflow."""
    _job_start = __import__('time').perf_counter()
    try:
        # Invoke the LangGraph workflow
        research_graph = get_research_graph(initial_state.get('llm_provider'))
        final_state = await research_graph.ainvoke(
            initial_state,
            {"recursion_limit": 100}
        )
        
        # Update final state in Redis
        redis_client.set(
            f"langgraph:checkpoint:{job_id}",
            json.dumps(final_state, default=str),
            ex=86400  # 24 hours TTL
        )
        
        # Publish job complete event
        if final_state.get('status') == 'complete':
            JOB_COMPLETED.inc()
            JOB_DURATION.observe(__import__('time').perf_counter() - _job_start)
            report_id = str(uuid.uuid4())
            final_report = final_state.get('final_report') or ''
            await event_publisher.publish_job_complete(job_id, report_id)
            
            # Notify Spring API to update job status and create Report row
            try:
                async with httpx.AsyncClient(timeout=10.0) as client:
                    resp = await client.post(
                        f"http://api:8000/api/webhooks/jobs/{job_id}/complete",
                        json={
                            "reportId": report_id,
                            "title": extract_report_title(final_report, initial_state.get('query')),
                            "content": final_report,
                            "wordCount": len(final_report.split()) if final_report else None,
                            "citationCount": count_citations(final_report),
                            "criticScore": final_state.get('critic_score'),
                            "revisionCount": final_state.get('analysis_iteration') or 0,
                            "timestamp": int(datetime.now().timestamp() * 1000),
                        }
                    )
                    resp.raise_for_status()
                    logger.info(f"Job {job_id} callback acknowledged by API: {resp.status_code}")
            except Exception as cb_err:
                logger.error(f"Failed to notify API of job {job_id} completion: {cb_err}")
            
            logger.info(f"Job {job_id} completed with report {report_id}")
        else:
            JOB_FAILED.labels(reason="workflow_failed").inc()
            await event_publisher.publish_job_failed(job_id, final_state.get('error', 'Unknown error'))
            # Notify Spring API of failure
            try:
                async with httpx.AsyncClient(timeout=10.0) as client:
                    await client.post(
                        f"http://api:8000/api/webhooks/jobs/{job_id}/failed",
                        json={"error": final_state.get('error', 'Unknown error'), "timestamp": int(datetime.now().timestamp() * 1000)}
                    )
            except Exception as cb_err:
                logger.error(f"Failed to notify API of job {job_id} failure: {cb_err}")
            logger.error(f"Job {job_id} failed: {final_state.get('error')}")
    
    except Exception as e:
        JOB_FAILED.labels(reason="exception").inc()
        logger.error(f"Error executing job {job_id}: {e}")
        await event_publisher.publish_job_failed(job_id, str(e))
        try:
            async with httpx.AsyncClient(timeout=10.0) as client:
                await client.post(
                    f"http://api:8000/api/webhooks/jobs/{job_id}/failed",
                    json={"error": str(e), "timestamp": int(datetime.now().timestamp() * 1000)}
                )
        except Exception as cb_err:
            logger.error(f"Failed to notify API of job {job_id} exception: {cb_err}")


def extract_report_title(report_content: str, query: str) -> str:
    if report_content:
        for line in report_content.splitlines():
            if line.startswith('# '):
                return line[2:].strip()
    return f"Research: {query[:490]}"


def count_citations(report_content: str) -> int:
    import re

    return len(re.findall(r'\[\d+\]', report_content or ''))


@app.get("/ai/stream/{job_id}")
async def stream_events(job_id: str, token: str = Query(...)):
    """
    Server-Sent Events endpoint for real-time agent activity streaming.
    Requirement 6: Real-time agent activity streaming via SSE
    Requirement 18.1: SSE endpoint with JWT validation
    """
    
    # Validate JWT — raises 401 if invalid/expired
    validate_jwt_token(token)
    
    async def event_generator():
        """Generate SSE events from Redis Streams."""
        last_id = '0'  # Start from beginning
        
        while True:
            try:
                # Read from Redis Stream
                messages = redis_client.xread(
                    {f'job:{job_id}:events': last_id},
                    block=1000,  # 1 second block
                    count=10
                )
                
                if messages:
                    for stream, stream_messages in messages:
                        for msg_id, data in stream_messages:
                            last_id = msg_id
                            
                            # Format as SSE
                            event_type = data.get('type', 'agent_update')
                            yield f"event: {event_type}\n"
                            yield f"data: {json.dumps(data)}\n\n"
                
                # Check if job is complete
                checkpoint = redis_client.get(f"langgraph:checkpoint:{job_id}")
                if checkpoint:
                    state = json.loads(checkpoint)
                    if state.get('status') in ['complete', 'failed', 'cancelled']:
                        # Send final event
                        if state['status'] == 'complete':
                            yield f"event: job_complete\n"
                            yield f"data: {json.dumps({'job_id': job_id, 'status': 'complete'})}\n\n"
                        else:
                            yield f"event: job_failed\n"
                            yield f"data: {json.dumps({'job_id': job_id, 'error': state.get('error')})}\n\n"
                        break
                
                await asyncio.sleep(0.1)
            
            except Exception as e:
                logger.error(f"Error in event generator: {e}")
                yield f"event: error\n"
                yield f"data: {json.dumps({'error': str(e)})}\n\n"
                break
    
    return StreamingResponse(
        event_generator(),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "X-Accel-Buffering": "no"
        }
    )


@app.get("/api/v1/jobs/{job_id}")
async def get_job_status(job_id: str):
    """Get job status and state."""
    checkpoint = redis_client.get(f"langgraph:checkpoint:{job_id}")
    
    if not checkpoint:
        raise HTTPException(status_code=404, detail="Job not found")
    
    state = json.loads(checkpoint)
    
    return {
        "job_id": job_id,
        "status": state.get('status'),
        "query": state.get('query'),
        "agent_logs": state.get('agent_logs', []),
        "error": state.get('error'),
        "completed_at": state.get('completed_at')
    }


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8080)
