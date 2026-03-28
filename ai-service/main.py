from fastapi import FastAPI, HTTPException, Query, Depends
from fastapi.responses import StreamingResponse
from fastapi.middleware.cors import CORSMiddleware
import asyncio
import json
from datetime import datetime
import uuid
import logging

from app.config import get_settings, Settings
from app.graph.workflow import build_research_graph, create_initial_state
from app.llm.provider import get_llm_provider
from app.rag.store import EventPublisher
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

# Global state
settings = None
event_publisher = None
research_graph = None
redis_client = None
active_jobs = {}  # job_id -> state


@app.on_event("startup")
async def startup():
    """Initialize services on startup."""
    global settings, event_publisher, research_graph, redis_client
    
    settings = get_settings()
    logger.info(f"Starting with LLM provider: {settings.llm_provider}")
    
    # Initialize event publisher
    event_publisher = EventPublisher(settings.redis_url)
    await event_publisher.initialize()
    
    # Initialize Redis client
    redis_client = redis.from_url(settings.redis_url, decode_responses=True)
    
    # Initialize LLM provider and graph
    llm_provider = get_llm_provider(
        settings.llm_provider,
        api_key=settings.openai_api_key if settings.llm_provider == "openai" else None,
        region=settings.aws_region
    )
    research_graph = build_research_graph(llm_provider)
    
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


@app.get("/health")
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
        
        logger.info(f"Job {job_id} started for user {user_id}")
        
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
    try:
        # Invoke the LangGraph workflow
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
            report_id = str(uuid.uuid4())
            await event_publisher.publish_job_complete(job_id, report_id)
            
            # In production, store report to S3 and update PostgreSQL
            logger.info(f"Job {job_id} completed with report {report_id}")
        else:
            await event_publisher.publish_job_failed(job_id, final_state.get('error', 'Unknown error'))
            logger.error(f"Job {job_id} failed: {final_state.get('error')}")
    
    except Exception as e:
        logger.error(f"Error executing job {job_id}: {e}")
        await event_publisher.publish_job_failed(job_id, str(e))


@app.get("/ai/stream/{job_id}")
async def stream_events(job_id: str, token: str = Query(...)):
    """
    Server-Sent Events endpoint for real-time agent activity streaming.
    Requirement 6: Real-time agent activity streaming via SSE
    Requirement 18.1: SSE endpoint with JWT validation
    """
    
    # In production: validate JWT from token parameter
    # For now, just check it's not empty
    if not token:
        return HTTPException(status_code=401, detail="Unauthorized")
    
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
