from typing import Any, Dict
from datetime import datetime
from langgraph.graph import StateGraph
from app.graph.state import ResearchState, AGENT_SEQUENCE
from app.agents.nodes import (
    planner_node, research_node, analysis_node, 
    critic_node, synthesizer_node
)
from app.llm.provider import LLMProvider


def build_research_graph(llm_provider: LLMProvider):
    """
    Build the LangGraph research workflow.
    Requirement 5.1: Execute agents in sequence: Planner → Research → Analysis → Critic → Synthesizer
    Requirement 5.10: Persist state to Redis after each agent transition
    """
    
    graph = StateGraph(ResearchState)
    
    # Add agent nodes
    graph.add_node("planner", lambda state: planner_node(state, llm_provider))
    graph.add_node("research", lambda state: research_node(state, llm_provider))
    graph.add_node("analysis", lambda state: analysis_node(state, llm_provider))
    graph.add_node("critic", lambda state: critic_node(state, llm_provider))
    graph.add_node("synthesizer", lambda state: synthesizer_node(state, llm_provider))
    
    # Define edges
    graph.add_edge("planner", "research")
    graph.add_edge("research", "analysis")
    graph.add_edge("analysis", "critic")
    
    # Conditional edge: Critic can loop back to Analysis or proceed to Synthesizer
    # Requirement 5.7: Revise if score < 7.0 and iterations < 3
    def critic_route(state: ResearchState):
        if state['status'] == 'failed':
            return  "END"
        
        if (state.get('critic_score', 0) < 7.0 and 
            state.get('critic_iterations', 0) < 3):
            # Revise analysis
            return "analysis"
        else:
            # Proceed to synthesis
            return "synthesizer"
    
    graph.add_conditional_edges(
        "critic",
        critic_route,
        {
            "analysis": "analysis",
            "synthesizer": "synthesizer",
            "END": "__end__"
        }
    )
    
    graph.add_edge("synthesizer", "__end__")
    
    # Set entry point
    graph.set_entry_point("planner")
    
    # Compile and return
    compiled_graph = graph.compile()
    return compiled_graph


def create_initial_state(
    job_id: str,
    user_id: str,
    query: str,
    llm_provider: str,
    research_depth: str,
    uploaded_doc_ids: list = None
) -> ResearchState:
    """Create initial ResearchState for a new job."""
    return ResearchState(
        job_id=job_id,
        user_id=user_id,
        query=query,
        llm_provider=llm_provider,
        research_depth=research_depth,
        uploaded_doc_ids=uploaded_doc_ids or [],
        
        # Agent outputs (empty initially)
        sub_questions=[],
        report_structure=None,
        research_results={},
        analysis_draft=None,
        analysis_iteration=0,
        critic_score=None,
        critic_feedback=None,
        critic_iterations=0,
        final_report=None,
        
        # Citations and logging
        citations=[],
        agent_logs=[],
        
        # Status
        status="running",
        error=None,
        started_at=datetime.now().isoformat(),
        completed_at=None
    )
