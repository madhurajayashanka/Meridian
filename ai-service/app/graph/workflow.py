from typing import Any, Dict
from datetime import datetime
from langgraph.graph import StateGraph
from app.graph.state import ResearchState, PROMPT_VERSIONS, TOKEN_BUDGETS
from app.agents.nodes import (
    planner_node, research_node, analysis_node,
    critic_node, synthesizer_node
)
from app.llm.provider import LLMProvider

# Maximum wall-clock seconds a job may run before being force-failed
JOB_TIMEOUT_SECS = {
    "quick": 120,
    "standard": 300,
    "deep": 600,
}


def _is_terminal(state: ResearchState) -> bool:
    return state.get("status") in ("failed", "cancelled")


def _check_timeout(state: ResearchState) -> ResearchState:
    """Fail the job if it has exceeded its wall-clock budget."""
    started = state.get("started_at")
    if started:
        try:
            elapsed = (datetime.now() - datetime.fromisoformat(str(started))).total_seconds()
            limit = JOB_TIMEOUT_SECS.get(state.get("research_depth", "standard"), 300)
            if elapsed > limit:
                state["status"] = "failed"
                state["error"] = f"Job timed out after {int(elapsed)}s (limit {limit}s)"
        except Exception:
            pass
    return state


def _fail_or(next_node):
    def route(state):
        _check_timeout(state)
        return "END" if _is_terminal(state) else next_node
    return route


def build_research_graph(llm_provider: LLMProvider):
    """Build LangGraph workflow. Depth-aware: quick skips analysis/critic loop."""

    graph = StateGraph(ResearchState)

    graph.add_node("planner",     lambda s: planner_node(s, llm_provider))
    graph.add_node("research",    lambda s: research_node(s, llm_provider))
    graph.add_node("analysis",    lambda s: analysis_node(s, llm_provider))
    graph.add_node("critic",      lambda s: critic_node(s, llm_provider))
    graph.add_node("synthesizer", lambda s: synthesizer_node(s, llm_provider))

    graph.add_conditional_edges("planner", _fail_or("research"),
                                {"research": "research", "END": "__end__"})

    def after_research(state):
        _check_timeout(state)
        if _is_terminal(state):
            return "END"
        return "synthesizer" if state.get("research_depth") == "quick" else "analysis"

    graph.add_conditional_edges("research", after_research,
                                {"analysis": "analysis", "synthesizer": "synthesizer", "END": "__end__"})

    graph.add_conditional_edges("analysis", _fail_or("critic"),
                                {"critic": "critic", "END": "__end__"})

    def critic_route(state):
        _check_timeout(state)
        if _is_terminal(state):
            return "END"
        if state.get("critic_score", 0) < 7.0 and state.get("critic_iterations", 0) < 3:
            return "analysis"
        return "synthesizer"

    graph.add_conditional_edges("critic", critic_route,
                                {"analysis": "analysis", "synthesizer": "synthesizer", "END": "__end__"})

    graph.add_edge("synthesizer", "__end__")
    graph.set_entry_point("planner")
    return graph.compile()


def create_initial_state(
    job_id: str,
    user_id: str,
    query: str,
    llm_provider: str,
    research_depth: str,
    uploaded_doc_ids: list = None,
) -> ResearchState:
    return ResearchState(
        job_id=job_id,
        user_id=user_id,
        query=query,
        llm_provider=llm_provider,
        research_depth=research_depth,
        uploaded_doc_ids=uploaded_doc_ids or [],
        prompt_versions=dict(PROMPT_VERSIONS),
        model_id=None,
        retrieval_config_version="1.0.0",
        citation_policy_version="1.0.0",
        guardrail_policy_version="1.0.0",
        sub_questions=[],
        report_structure=None,
        research_results={},
        analysis_draft=None,
        analysis_iteration=0,
        critic_score=None,
        critic_feedback=None,
        critic_iterations=0,
        final_report=None,
        tokens_used=0,
        token_budget=TOKEN_BUDGETS.get(research_depth, TOKEN_BUDGETS["standard"]),
        citations=[],
        agent_logs=[],
        status="running",
        error=None,
        started_at=datetime.now().isoformat(),
        completed_at=None,
    )
