"""
RAG (Retrieval-Augmented Generation) chat endpoint for conversations with reports.
Requirement 2.1.7: Chat with report (RAG)
"""

import logging
from typing import AsyncGenerator
import asyncpg

logger = logging.getLogger(__name__)


class RAGChatService:
    """Service for RAG-based chat on report content."""
    
    def __init__(self, db_pool: asyncpg.Pool, llm_provider):
        self.db_pool = db_pool
        self.llm_provider = llm_provider
    
    async def get_relevant_chunks(
        self,
        report_id: str,
        query: str,
        top_k: int = 5
    ) -> list[dict]:
        """
        Retrieve most relevant chunks from a report using pgvector similarity search.
        
        Args:
            report_id: ID of the report to search
            query: User's question/query
            top_k: Number of chunks to retrieve
            
        Returns:
            List of relevant chunks with similarity scores
        """
        try:
            # Get embedding for the query
            query_embedding = await self.llm_provider.get_embedding(query)
            # pgvector expects a list/array, not a string
            embedding_vector = query_embedding if isinstance(query_embedding, list) else list(query_embedding)

            # Search pgvector for similar chunks
            async with self.db_pool.acquire() as conn:
                chunks = await conn.fetch("""
                    SELECT 
                        id::text,
                        content,
                        (1 - (embedding <=> $1::vector)) as similarity_score,
                        metadata
                    FROM embeddings
                    WHERE report_id = $2
                    ORDER BY embedding <=> $1::vector
                    LIMIT $3
                """, embedding_vector, report_id, top_k)
            
            result = [
                {
                    'id': chunk['id'],
                    'content': chunk['content'],
                    'similarity': float(chunk['similarity_score']),
                    'metadata': chunk['metadata']
                }
                for chunk in chunks
            ]

            # Retrieval observability: log top-k scores and hit/miss
            if result:
                scores = [r['similarity'] for r in result]
                logger.info(
                    f"RAG retrieval for report {report_id}: "
                    f"top_k={top_k}, hits={len(result)}, "
                    f"max_score={max(scores):.3f}, min_score={min(scores):.3f}, "
                    f"avg_score={sum(scores)/len(scores):.3f}"
                )
            else:
                logger.warning(f"RAG retrieval MISS for report {report_id}: no chunks found")

            return result
        
        except Exception as e:
            logger.error(f"Error retrieving chunks: {e}")
            return []
    
    async def get_recent_messages(
        self,
        report_id: str,
        limit: int = 5
    ) -> list[dict]:
        """
        Get recent chat messages for a report.
        
        Args:
            report_id: ID of the report
            limit: Number of messages to retrieve
            
        Returns:
            List of recent messages in chronological order
        """
        try:
            async with self.db_pool.acquire() as conn:
                messages = await conn.fetch("""
                    SELECT 
                        id::text,
                        role,
                        content,
                        tokens_used,
                        created_at
                    FROM chat_messages
                    WHERE report_id = $1
                    ORDER BY created_at DESC
                    LIMIT $2
                """, report_id, limit)
            
            # Reverse to chronological order
            return [
                {
                    'id': msg['id'],
                    'role': msg['role'],
                    'content': msg['content'],
                    'tokensUsed': msg['tokens_used'],
                    'createdAt': msg['created_at'].isoformat()
                }
                for msg in reversed(messages)
            ]
        
        except Exception as e:
            logger.error(f"Error retrieving messages: {e}")
            return []
    
    async def stream_chat_response(
        self,
        report_id: str,
        user_message: str,
        user_id: str
    ) -> AsyncGenerator[str, None]:
        """
        Stream a chat response with RAG context.
        
        Args:
            report_id: ID of the report being discussed
            user_message: The user's question/message
            user_id: ID of the user
            
        Yields:
            Streamed response tokens
        """
        try:
            # Get relevant chunks
            chunks = await self.get_relevant_chunks(report_id, user_message)
            
            # Get recent conversation history
            recent_messages = await self.get_recent_messages(report_id, limit=5)
            
            # Build context for LLM
            context_text = "\n\n".join([
                f"Relevant information:\n{chunk['content']}"
                for chunk in chunks
            ])
            
            # Build messages for LLM
            system_prompt = f"""You are a helpful assistant answering questions about a research report. 
Use the provided relevant information from the report to answer the user's question accurately.
If the information is not in the report, say so rather than making things up.

Available context from the report:
{context_text}
"""
            
            messages = [
                {"role": "system", "content": system_prompt}
            ]
            
            # Add conversation history
            for msg in recent_messages:
                messages.append({
                    "role": msg['role'],
                    "content": msg['content']
                })
            
            # Add current user message
            messages.append({
                "role": "user",
                "content": user_message
            })
            
            # Stream response from LLM
            token_count = 0
            full_response = ""
            
            async for token in self.llm_provider.stream_chat(messages):
                full_response += token
                yield token
                
                # Rough token counting
                token_count = len(full_response.split()) // 1.3
            
            # Save chat messages to database
            async with self.db_pool.acquire() as conn:
                # Save user message
                await conn.execute("""
                    INSERT INTO chat_messages (
                        id, report_id, user_id, role, content, created_at
                    ) VALUES ($1, $2, $3, $4, $5, NOW())
                """, str(__import__('uuid').uuid4()), report_id, user_id, "user", user_message)
                
                # Save assistant response
                await conn.execute("""
                    INSERT INTO chat_messages (
                        id, report_id, user_id, role, content, tokens_used, created_at
                    ) VALUES ($1, $2, $3, $4, $5, $6, NOW())
                """, str(__import__('uuid').uuid4()), report_id, user_id, "assistant", 
                   full_response, int(token_count))
            
            logger.info(f"Saved chat messages for report {report_id}")
        
        except Exception as e:
            logger.error(f"Error in stream_chat_response: {e}")
            yield f"Error: {str(e)}"


async def create_chat_service(db_pool, llm_provider) -> RAGChatService:
    """Factory function to create RAG chat service."""
    return RAGChatService(db_pool, llm_provider)
