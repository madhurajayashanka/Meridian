from pydantic_settings import BaseSettings
from functools import lru_cache


class Settings(BaseSettings):
    """Application settings from environment variables."""
    
    # Database
    database_url: str = "postgresql+asyncpg://meridian:meridian@localhost:5432/meridian"

    # Redis
    redis_url: str = "redis://localhost:6379"
    
    # LLM Provider
    llm_provider: str = "mock"  # "bedrock", "openai", or "mock"
    openai_api_key: str = ""
    openai_model: str = "gpt-4o"
    openai_model_fast: str = "gpt-4o-mini"          # cheap model for planner/critic/research
    openai_embedding_model: str = "text-embedding-3-small"

    # AWS Bedrock
    aws_region: str = "us-east-1"
    aws_access_key_id: str = ""
    aws_secret_access_key: str = ""
    bedrock_model_id: str = "anthropic.claude-3-5-sonnet-20241022-v2:0"
    bedrock_model_id_fast: str = "anthropic.claude-3-haiku-20240307-v1:0"
    bedrock_embedding_model: str = "amazon.titan-embed-text-v2:0"
    
    # Tavily API
    tavily_api_key: str = ""
    
    # S3
    s3_bucket: str = "meridian-documents-local"
    s3_region: str = "us-east-1"
    s3_endpoint_url: str = ""
    
    # Research configuration
    research_quick_agents: str = "planner,research"
    research_standard_agents: str = "planner,research,analysis,critic,synthesizer"
    research_deep_agents: str = "planner,research,analysis,critic,synthesizer"
    research_deep_max_critic_iterations: int = 3
    
    # Internal service-to-service auth
    internal_api_key: str = ""  # set INTERNAL_API_KEY env; Spring sends this header

    # Internal service URL (AI → Spring API callbacks)
    api_internal_url: str = "http://api:8000"

    # CORS
    cors_allowed_origins: str = "http://localhost:3000"  # comma-separated list

    # JWT validation
    jwt_public_key: str = ""
    webhook_secret: str = ""
    log_level: str = "INFO"
    debug: bool = False
    e2e_with_llm: bool = False
    mock_tavily_responses: bool = True
    
    class Config:
        env_file = ".env"
        case_sensitive = False
        extra = "ignore"


@lru_cache()
def get_settings() -> Settings:
    """Get cached settings instance."""
    return Settings()
