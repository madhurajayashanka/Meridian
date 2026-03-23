from abc import ABC, abstractmethod
from typing import Optional, List, Dict, Any


class LLMProvider(ABC):
    """
    Abstract base class for LLM providers.
    Property: LLM-provider abstraction that requires zero code changes to switch providers
    Requirement 5.11: Support provider switching via environment variable
    """

    @abstractmethod
    async def get_chat_model(self, temperature: float = 0.3):
        """Get the chat model instance."""
        pass

    @abstractmethod
    async def get_embedding_model(self):
        """Get the embedding model instance."""
        pass

    @abstractmethod
    async def invoke_chat(self, prompt: str, temperature: float = 0.3) -> str:
        """Invoke the chat model and return response."""
        pass

    @abstractmethod
    async def embed_text(self, text: str) -> List[float]:
        """Embed text and return vector."""
        pass


class MockProvider(LLMProvider):
    """
    Mock LLM provider for development and testing.
    Returns deterministic responses without API calls.
    """

    async def get_chat_model(self, temperature: float = 0.3):
        """Return mock model."""
        return self

    async def get_embedding_model(self):
        """Return mock embedding model."""
        return self

    async def invoke_chat(self, prompt: str, temperature: float = 0.3) -> str:
        """Return mock response."""
        if "sub-question" in prompt.lower() or "decompose" in prompt.lower():
            return """[
                "What are the main components of a multi-agent research system?",
                "How do LangGraph state machines work?",
                "What are best practices for agent orchestration?",
                "How does vector search improve retrieval quality?"
            ]"""
        elif "research" in prompt.lower() or "search" in prompt.lower():
            return """{
                "sources": [
                    {"title": "Mock Source 1", "url": "https://example.com/1", "snippet": "Related content"},
                    {"title": "Mock Source 2", "url": "https://example.com/2", "snippet": "Additional details"}
                ]
            }"""
        elif "analyze" in prompt.lower():
            return """## Analysis

The research findings indicate several key insights:

1. Multi-agent systems are effective for complex tasks[1]
2. State persistence ensures fault tolerance[2]  
3. Real-time streaming provides user transparency[1]

### Key Takeaways
- Distributed agents handle parallel research
- Vector embeddings improve semantic search
- Redis checkpointing enables recovery

[1] Source: https://example.com/1
[2] Source: https://example.com/2"""
        elif "critic" in prompt.lower() or "evaluate" in prompt.lower():
            return """{
                "score": 8.5,
                "feedback": "Strong analysis with good citations and coverage of key points. Could expand on limitations.",
                "strengths": ["Good structure", "Accurate citations"],
                "improvements": ["Add more depth on edge cases"]
            }"""
        elif "synthesis" in prompt.lower() or "report" in prompt.lower():
            return """# Research Report: Multi-Agent AI Research Systems

## Executive Summary
This report synthesizes findings on autonomous multi-agent research platforms currently under development.

## Key Findings
- Agent orchestration enables parallel research execution
- Real-time streaming improves user experience
- LLM-agnostic design supports provider flexibility

## Recommendations
1. Implement robust error handling for agent failures
2. Monitor token usage across concurrent agents
3. Regularly audit output quality with human review

## Citations
1. Smith, J. (2024). "Multi-Agent AI Systems". Tech Journal.
2. Chen, M. (2024). "LangGraph Workflows". AI Research.
"""
        else:
            return "Mock response to: " + prompt[:100]

    async def embed_text(self, text: str) -> List[float]:
        """Return mock embedding (1536 dimensions like OpenAI)."""
        # Generate a deterministic mock embedding
        hash_value = hash(text) % 1000000
        return [float(hash_value % 256 - 128) / 100.0 for _ in range(1536)]


class OpenAIProvider(LLMProvider):
    """OpenAI GPT-4o provider."""

    def __init__(self, api_key: str, model: str = "gpt-4o", embedding_model: str = "text-embedding-3-small"):
        self.api_key = api_key
        self.model = model
        self.embedding_model = embedding_model
        self.chat_model = None
        self.embedding_model_obj = None

    async def get_chat_model(self, temperature: float = 0.3):
        """Get or create OpenAI chat model."""
        if self.chat_model is None:
            from langchain_openai import ChatOpenAI
            self.chat_model = ChatOpenAI(
                api_key=self.api_key,
                model_name=self.model,
                temperature=temperature
            )
        return self.chat_model

    async def get_embedding_model(self):
        """Get or create OpenAI embedding model."""
        if self.embedding_model_obj is None:
            from langchain_openai import OpenAIEmbeddings
            self.embedding_model_obj = OpenAIEmbeddings(
                api_key=self.api_key,
                model=self.embedding_model
            )
        return self.embedding_model_obj

    async def invoke_chat(self, prompt: str, temperature: float = 0.3) -> str:
        """Invoke OpenAI chat model."""
        model = await self.get_chat_model(temperature)
        response = await model.ainvoke(prompt)
        return response.content

    async def embed_text(self, text: str) -> List[float]:
        """Embed text using OpenAI."""
        embeddings = await self.get_embedding_model()
        return await embeddings.aembed_query(text)


class BedrockProvider(LLMProvider):
    """AWS Bedrock (Claude 3.5 Sonnet) provider."""

    def __init__(self, 
                 model_id: str = "anthropic.claude-3-5-sonnet-20241022-v2:0",
                 embedding_model: str = "amazon.titan-embed-text-v2:0",
                 region: str = "us-east-1"):
        self.model_id = model_id
        self.embedding_model = embedding_model
        self.region = region
        self.chat_model = None
        self.embedding_model_obj = None

    async def get_chat_model(self, temperature: float = 0.3):
        """Get or create Bedrock chat model."""
        if self.chat_model is None:
            from langchain_aws import BedrockChat
            self.chat_model = BedrockChat(
                model_id=self.model_id,
                region_name=self.region,
                model_kwargs={"temperature": temperature}
            )
        return self.chat_model

    async def get_embedding_model(self):
        """Get or create Bedrock embedding model."""
        if self.embedding_model_obj is None:
            from langchain_aws import BedrockEmbeddings
            self.embedding_model_obj = BedrockEmbeddings(
                model_id=self.embedding_model,
                region_name=self.region
            )
        return self.embedding_model_obj

    async def invoke_chat(self, prompt: str, temperature: float = 0.3) -> str:
        """Invoke Bedrock chat model."""
        model = await self.get_chat_model(temperature)
        response = await model.ainvoke(prompt)
        return response.content

    async def embed_text(self, text: str) -> List[float]:
        """Embed text using Bedrock."""
        embeddings = await self.get_embedding_model()
        return await embeddings.aembed_query(text)


def get_llm_provider(provider: str, **kwargs) -> LLMProvider:
    """
    Factory function to get LLM provider instance.
    Requirement 5.11: Select LLM provider via environment variable
    """
    if provider.lower() == "openai":
        return OpenAIProvider(
            api_key=kwargs.get("api_key"),
            model=kwargs.get("model", "gpt-4o"),
            embedding_model=kwargs.get("embedding_model", "text-embedding-3-small")
        )
    elif provider.lower() == "bedrock":
        return BedrockProvider(
            model_id=kwargs.get("model_id", "anthropic.claude-3-5-sonnet-20241022-v2:0"),
            embedding_model=kwargs.get("embedding_model", "amazon.titan-embed-text-v2:0"),
            region=kwargs.get("region", "us-east-1")
        )
    else:  # default to mock
        return MockProvider()
