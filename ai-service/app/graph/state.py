from typing import TypedDict, Optional, List, Dict, Any
from datetime import datetime


# Prompt version registry — bump when prompts change
PROMPT_VERSIONS = {
    "planner": "1.0.0",
    "research": "1.0.0",
    "analysis": "1.0.0",
    "critic": "1.0.0",
    "synthesizer": "1.0.0",
}


class ResearchState(TypedDict):
    """
    LangGraph state for multi-agent research orchestration.
    Requirement 5: Multi-agent LangGraph orchestration
    Property 5: State persisted to Redis after each agent transition
    """
    # Job metadata
    job_id: str
    user_id: str
    query: str
    llm_provider: str  # "bedrock" or "openai"
    research_depth: str  # "quick", "standard", or "deep"
    uploaded_doc_ids: List[str]  # Document IDs provided by user

    # Run metadata (prompt versioning)
    prompt_versions: Dict[str, str]   # {agent_name: prompt_version}
    model_id: Optional[str]           # resolved model identifier
    retrieval_config_version: str     # version of retrieval config used
    citation_policy_version: str      # version of citation enforcement policy
    guardrail_policy_version: str     # version of guardrail rules

    # Agent outputs
    sub_questions: List[str]
    report_structure: Optional[str]
    research_results: Dict[str, List[Dict[str, Any]]]  # {question: [sources]}
    analysis_draft: Optional[str]
    analysis_iteration: int  # Track revision count
    critic_score: Optional[float]
    critic_feedback: Optional[str]
    critic_iterations: int
    final_report: Optional[str]
    
    # Citations
    citations: List[Dict[str, str]]

    # Token budget tracking
    tokens_used: int          # running total across all agent calls
    token_budget: int         # max tokens allowed for this job
    
    # Execution tracking
    agent_logs: List[Dict[str, Any]]
    status: str  # "pending", "running", "complete", "failed", "cancelled"
    error: Optional[str]
    started_at: Optional[datetime]
    completed_at: Optional[datetime]


# Helper constants
VALID_STATUSES = {"pending", "running", "complete", "failed", "cancelled"}
VALID_DEPTHS = {"quick", "standard", "deep"}
VALID_PROVIDERS = {"bedrock", "openai", "mock"}

TOKEN_BUDGETS = {
    "quick": 20_000,
    "standard": 60_000,
    "deep": 120_000,
}

AGENT_SEQUENCE = {
    "quick": ["planner", "research", "synthesizer"],  # skip analysis/critic loop for speed
    "standard": ["planner", "research", "analysis", "critic", "synthesizer"],
    "deep": ["planner", "research", "analysis", "critic", "synthesizer"],
}
