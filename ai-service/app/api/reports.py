"""
Report storage service for saving final reports to S3 and PostgreSQL.
Requirement 2.1.6: Report management and storage
"""

import logging
import boto3
from datetime import datetime
from typing import Optional
import asyncpg
import json

logger = logging.getLogger(__name__)


class ReportStorageService:
    """Service for storing and retrieving reports."""
    
    def __init__(self, db_pool: asyncpg.Pool, s3_client, s3_bucket: str = "meridian-reports"):
        self.db_pool = db_pool
        self.s3_client = s3_client
        self.s3_bucket = s3_bucket
    
    async def store_report(
        self,
        job_id: str,
        project_id: str,
        user_id: str,
        title: str,
        content: str,
        critic_score: float,
        revision_count: int
    ) -> Optional[str]:
        """
        Store a completed report to S3 and PostgreSQL.
        
        Args:
            job_id: ID of the research job
            project_id: ID of the project
            user_id: ID of the user
            title: Report title
            content: Report markdown content
            critic_score: Final critic evaluation score
            revision_count: Number of revisions made
            
        Returns:
            Report ID if successful, None otherwise
        """
        try:
            # Upload to S3
            s3_key = await self._upload_to_s3(job_id, content)
            
            # Save metadata to PostgreSQL
            report_id = await self._save_report_metadata(
                job_id=job_id,
                project_id=project_id,
                user_id=user_id,
                title=title,
                s3_key=s3_key,
                word_count=len(content.split()),
                citation_count=self._count_citations(content),
                critic_score=critic_score,
                revision_count=revision_count
            )
            
            # Create embeddings for report chunks (for RAG)
            await self._embed_report_chunks(report_id, content)
            
            logger.info(f"Report {report_id} stored successfully for job {job_id}")
            return report_id
        
        except Exception as e:
            logger.error(f"Error storing report for job {job_id}: {e}")
            return None
    
    async def _upload_to_s3(self, job_id: str, content: str) -> str:
        """
        Upload report markdown to S3.
        
        Args:
            job_id: ID of the job
            content: Report content
            
        Returns:
            S3 object key
        """
        s3_key = f"reports/{job_id}/report.md"
        
        try:
            self.s3_client.put_object(
                Bucket=self.s3_bucket,
                Key=s3_key,
                Body=content.encode('utf-8'),
                ContentType='text/markdown',
                Metadata={
                    'job_id': job_id,
                    'created_at': datetime.now().isoformat()
                }
            )
            
            logger.info(f"Uploaded report to S3: {s3_key}")
            return s3_key
        
        except Exception as e:
            logger.error(f"Error uploading to S3: {e}")
            raise
    
    async def _save_report_metadata(
        self,
        job_id: str,
        project_id: str,
        user_id: str,
        title: str,
        s3_key: str,
        word_count: int,
        citation_count: int,
        critic_score: float,
        revision_count: int
    ) -> str:
        """Save report metadata to PostgreSQL."""
        import uuid
        
        report_id = str(uuid.uuid4())
        
        try:
            async with self.db_pool.acquire() as conn:
                await conn.execute("""
                    INSERT INTO reports (
                        id, job_id, project_id, user_id, title, s3_key,
                        word_count, citation_count, critic_score, revision_count,
                        is_public, created_at, updated_at
                    ) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, NOW(), NOW())
                """,
                report_id, job_id, project_id, user_id, title, s3_key,
                word_count, citation_count, critic_score, revision_count, False
                )
                
                # Update research job status
                await conn.execute("""
                    UPDATE research_jobs
                    SET status = 'COMPLETE', completed_at = NOW(), updated_at = NOW()
                    WHERE id = $1
                """, job_id)
            
            logger.info(f"Saved report metadata for job {job_id}")
            return report_id
        
        except Exception as e:
            logger.error(f"Error saving report metadata: {e}")
            raise
    
    async def _embed_report_chunks(self, report_id: str, content: str):
        """
        Create embeddings for report sections for RAG.
        
        Args:
            report_id: ID of the report
            content: Full report content
        """
        try:
            # Split content by H2 headers to create meaningful chunks
            chunks = self._split_by_headers(content)
            
            # Get embeddings for each chunk
            embeddings_data = []
            for idx, chunk in enumerate(chunks):
                if not chunk.strip():
                    continue
                
                # In a real implementation, call the embedding model
                # For now, we'll store placeholder embeddings
                embeddings_data.append({
                    'chunk': chunk,
                    'chunk_index': idx
                })
            
            # Save embeddings to PostgreSQL
            if embeddings_data:
                async with self.db_pool.acquire() as conn:
                    for data in embeddings_data:
                        # Placeholder vector - in production, call embedding API
                        await conn.execute("""
                            INSERT INTO embeddings (
                                id, report_id, content, embedding, chunk_index, created_at
                            ) VALUES ($1, $2, $3, $4, $5, NOW())
                        """,
                        str(__import__('uuid').uuid4()),
                        report_id,
                        data['chunk'],
                        "[0.1]",  # Placeholder embedding - replace with real embedding API
                        data['chunk_index']
                        )
                
                logger.info(f"Created {len(embeddings_data)} embeddings for report {report_id}")
        
        except Exception as e:
            logger.warning(f"Error creating report embeddings: {e}")
            # Don't fail the entire report save if embeddings creation fails
    
    @staticmethod
    def _split_by_headers(content: str) -> list[str]:
        """Split report content by H2 headers."""
        chunks = []
        current_chunk = []
        
        for line in content.split('\n'):
            if line.startswith('## ') and current_chunk:
                chunks.append('\n'.join(current_chunk))
                current_chunk = [line]
            else:
                current_chunk.append(line)
        
        if current_chunk:
            chunks.append('\n'.join(current_chunk))
        
        return chunks
    
    @staticmethod
    def _count_citations(content: str) -> int:
        """Count citations in report (numbers in brackets)."""
        import re
        citations = re.findall(r'\[\d+\]', content)
        return len(set(int(c[1:-1]) for c in citations))  # Count unique citation numbers
    
    async def get_report(self, report_id: str) -> Optional[dict]:
        """
        Retrieve a report by ID.
        
        Args:
            report_id: ID of the report
            
        Returns:
            Report data or None if not found
        """
        try:
            async with self.db_pool.acquire() as conn:
                report = await conn.fetchrow("""
                    SELECT 
                        id::text, job_id::text, project_id::text, user_id::text,
                        title, s3_key, word_count, citation_count, critic_score,
                        revision_count, is_public, created_at, updated_at
                    FROM reports
                    WHERE id = $1 AND deleted_at IS NULL
                """, report_id)
            
            if not report:
                return None
            
            # Fetch from S3
            try:
                s3_obj = self.s3_client.get_object(
                    Bucket=self.s3_bucket,
                    Key=report['s3_key']
                )
                content = s3_obj['Body'].read().decode('utf-8')
            except Exception as e:
                logger.warning(f"Could not fetch report content from S3: {e}")
                content = None
            
            return {
                **dict(report),
                'content': content,
                'createdAt': report['created_at'].isoformat(),
                'updatedAt': report['updated_at'].isoformat()
            }
        
        except Exception as e:
            logger.error(f"Error retrieving report {report_id}: {e}")
            return None


async def create_report_storage_service(db_pool, s3_bucket: str = "meridian-reports"):
    """Factory function to create report storage service."""
    try:
        # Initialize S3 client
        s3_client = boto3.client('s3')
        
        # Verify bucket exists
        try:
            s3_client.head_bucket(Bucket=s3_bucket)
        except s3_client.exceptions.NoSuchBucket:
            logger.info(f"Creating S3 bucket: {s3_bucket}")
            s3_client.create_bucket(Bucket=s3_bucket)
        
        return ReportStorageService(db_pool, s3_client, s3_bucket)
    
    except Exception as e:
        logger.error(f"Error creating report storage service: {e}")
        raise
