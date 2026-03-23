"""
Document extraction pipeline for PDF and TXT files.
Requirement 2.1.8: Document processing and storage
"""

import asyncio
import logging
import tempfile
from typing import Optional
import pypdf
from pathlib import Path

logger = logging.getLogger(__name__)


class DocumentExtractor:
    """Extract text from PDF and TXT files."""

    @staticmethod
    def extract_from_pdf(file_path: str) -> Optional[str]:
        """
        Extract text from a PDF file.
        
        Args:
            file_path: Path to the PDF file
            
        Returns:
            Extracted text or None if extraction fails
        """
        try:
            text_parts = []
            
            with open(file_path, 'rb') as pdf_file:
                pdf_reader = pypdf.PdfReader(pdf_file)
                
                if not pdf_reader.pages:
                    logger.warning(f"PDF {file_path} has no pages")
                    return None
                
                for page_num, page in enumerate(pdf_reader.pages):
                    try:
                        text = page.extract_text()
                        if text:
                            text_parts.append(text)
                    except Exception as e:
                        logger.warning(f"Failed to extract text from page {page_num} in {file_path}: {e}")
                        continue
            
            if not text_parts:
                logger.warning(f"No text extracted from PDF {file_path}")
                return None
            
            full_text = "\n".join(text_parts)
            
            # Clean up excess whitespace
            full_text = "\n".join(line.strip() for line in full_text.split("\n") if line.strip())
            
            logger.info(f"Successfully extracted {len(full_text)} characters from PDF {file_path}")
            return full_text
        
        except Exception as e:
            logger.error(f"Error extracting from PDF {file_path}: {e}")
            return None

    @staticmethod
    def extract_from_txt(file_path: str) -> Optional[str]:
        """
        Extract text from a TXT file.
        
        Args:
            file_path: Path to the TXT file
            
        Returns:
            File contents or None if reading fails
        """
        try:
            with open(file_path, 'r', encoding='utf-8', errors='replace') as txt_file:
                text = txt_file.read()
            
            if not text.strip():
                logger.warning(f"TXT file {file_path} is empty")
                return None
            
            logger.info(f"Successfully read {len(text)} characters from TXT {file_path}")
            return text
        
        except Exception as e:
            logger.error(f"Error reading TXT {file_path}: {e}")
            return None

    @staticmethod
    def extract_text(file_path: str, file_type: str) -> Optional[str]:
        """
        Extract text from a file based on its type.
        
        Args:
            file_path: Path to the file
            file_type: File type ('pdf' or 'txt')
            
        Returns:
            Extracted text or None on failure
        """
        file_type = file_type.lower()
        
        if file_type == 'pdf':
            return DocumentExtractor.extract_from_pdf(file_path)
        elif file_type == 'txt':
            return DocumentExtractor.extract_from_txt(file_path)
        else:
            logger.error(f"Unsupported file type: {file_type}")
            return None


class TextChunker:
    """Chunk text into overlapping segments for embedding."""

    DEFAULT_CHUNK_SIZE = 512
    DEFAULT_OVERLAP = 50

    @staticmethod
    def chunk_text(
        text: str,
        chunk_size: int = DEFAULT_CHUNK_SIZE,
        overlap: int = DEFAULT_OVERLAP
    ) -> list[str]:
        """
        Split text into overlapping chunks.
        
        Args:
            text: Text to chunk
            chunk_size: Tokens per chunk (approximate)
            overlap: Overlap between chunks
            
        Returns:
            List of text chunks
        """
        if not text or not text.strip():
            return []
        
        # Split by sentences first
        sentences = text.split('.')
        chunks = []
        current_chunk = []
        current_length = 0
        
        for sentence in sentences:
            sentence = sentence.strip()
            if not sentence:
                continue
            
            # Rough character-to-token ratio (4 chars ≈ 1 token)
            sentence_length = len(sentence) // 4
            
            if current_length + sentence_length > chunk_size and current_chunk:
                # Save current chunk
                chunk_text = '. '.join(current_chunk) + '.'
                chunks.append(chunk_text)
                
                # Keep last sentences for overlap
                overlap_sentences = int(len(current_chunk) * (overlap / chunk_size))
                current_chunk = current_chunk[-overlap_sentences:] if overlap_sentences > 0 else []
                current_length = sum(len(s) // 4 for s in current_chunk)
            
            current_chunk.append(sentence)
            current_length += sentence_length
        
        # Add final chunk
        if current_chunk:
            chunks.append('. '.join(current_chunk) + '.')
        
        logger.info(f"Created {len(chunks)} chunks from text")
        return chunks

    @staticmethod
    def chunk_by_tokens(
        text: str,
        chunk_size: int = DEFAULT_CHUNK_SIZE,
        overlap: int = DEFAULT_OVERLAP
    ) -> list[str]:
        """
        Split text into chunks based on approximate token count.
        This is a simpler approach that splits on boundaries.
        """
        if not text or not text.strip():
            return []
        
        # Simple character-based chunking (approximation)
        # Assuming ~4 characters per token on average
        char_size = chunk_size * 4
        overlap_size = overlap * 4
        
        chunks = []
        start = 0
        
        while start < len(text):
            end = min(start + char_size, len(text))
            
            # Try to end at a sentence boundary
            if end < len(text):
                # Look for period, newline, or other common boundaries
                for boundary in ['. ', '.\n', '\n\n', '\n']:
                    pos = text.rfind(boundary, start, end)
                    if pos > start:
                        end = pos + len(boundary)
                        break
            
            chunk = text[start:end].strip()
            if chunk:
                chunks.append(chunk)
            
            # Move start position, accounting for overlap
            start = max(start + char_size - overlap_size, end)
        
        logger.info(f"Created {len(chunks)} chunks using token-based approach")
        return chunks
