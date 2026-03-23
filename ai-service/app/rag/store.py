import json
from typing import Optional, List, Dict, Any
import asyncpg
import redis


class EmbeddingStore:
    """Vector store for document and report embeddings using pgvector."""
    
    def __init__(self, db_url: str):
        self.db_url = db_url
        self.pool = None
    
    async def initialize(self):
        """Initialize database connection pool."""
        self.pool = await asyncpg.create_pool(self.db_url)
    
    async def close(self):
        """Close database connections."""
        if self.pool:
            await self.pool.close()
    
    async def store_embedding(self, content: str, embedding: List[float], 
                            document_id: Optional[str] = None,
                            report_id: Optional[str] = None,
                            metadata: Optional[Dict] = None):
        """Store embedding in pgvector."""
        async with self.pool.acquire() as conn:
            await conn.execute(
                """
                INSERT INTO embeddings (document_id, report_id, content, embedding, metadata)
                VALUES ($1, $2, $3, $4, $5)
                """,
                document_id, report_id, content, embedding, 
                json.dumps(metadata or {})
            )
    
    async def semantic_search(self, query_embedding: List[float], 
                            limit: int = 5) -> List[Dict[str, Any]]:
        """Search for similar embeddings using cosine similarity."""
        async with self.pool.acquire() as conn:
            results = await conn.fetch(
                """
                SELECT id, content, metadata, 1 - (embedding <=> $1) as similarity
                FROM embeddings
                ORDER BY embedding <=> $1
                LIMIT $2
                """,
                query_embedding, limit
            )
            
            return [
                {
                    "id": str(r['id']),
                    "content": r['content'],
                    "similarity": float(r['similarity']),
                    "metadata": json.loads(r['metadata']) if r['metadata'] else {}
                }
                for r in results
            ]


class EventPublisher:
    """Publish agent events to Redis Streams for real-time SSE."""
    
    def __init__(self, redis_url: str):
        self.redis_url = redis_url
        self.redis = None
    
    async def initialize(self):
        """Initialize Redis connection."""
        self.redis = redis.from_url(self.redis_url, decode_responses=True)
    
    async def close(self):
        """Close Redis connection."""
        if self.redis:
            self.redis.close()
    
    async def publish_event(self, job_id: str, event: Dict[str, Any]):
        """Publish event to Redis Stream."""
        if not self.redis:
            return
        
        stream_key = f"job:{job_id}:events"
        
        # Add required fields
        event['timestamp'] = event.get('timestamp') or datetime.now().isoformat()
        
        try:
            self.redis.xadd(stream_key, event)
            # Set TTL to 24 hours
            self.redis.expire(stream_key, 86400)
        except Exception as e:
            print(f"Error publishing event: {e}")
    
    async def publish_agent_complete(self, job_id: str, agent: str, 
                                    partial_output: Optional[str] = None):
        """Publish agent completion event."""
        event = {
            'agent': agent,
            'status': 'complete',
            'progress': '100',
            'partial_output': partial_output or ''
        }
        await self.publish_event(job_id, event)
    
    async def publish_agent_running(self, job_id: str, agent: str):
        """Publish agent running event."""
        event = {
            'agent': agent,
            'status': 'running',
            'progress': '0',
            'partial_output': ''
        }
        await self.publish_event(job_id, event)
    
    async def publish_job_complete(self, job_id: str, report_id: str):
        """Publish job completion event."""
        event = {
            'type': 'job_complete',
            'job_id': job_id,
            'report_id': report_id,
            'status': 'complete'
        }
        await self.publish_event(job_id, event)
    
    async def publish_job_failed(self, job_id: str, error: str):
        """Publish job failure event."""
        event = {
            'type': 'job_failed',
            'job_id': job_id,
            'error': error,
            'status': 'failed'
        }
        await self.publish_event(job_id, event)


from datetime import datetime
