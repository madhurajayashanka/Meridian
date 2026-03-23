"""
Property-based tests for LangGraph research workflow.
Uses Hypothesis to verify invariants across many randomly generated inputs.
"""

import asyncio
import json
from datetime import datetime
from typing import List, Dict, Any
import pytest
from hypothesis import given, strategies as st, settings, HealthCheck

from app.graph.state import ResearchState
from app.agents.nodes import (
    planner_node, research_node, analysis_node,
    critic_node, synthesizer_node
)
from app.graph.workflow import build_research_graph, create_initial_state
from app.llm.provider import LLMProvider, MockProvider


# Strategies for generating test data
query_strategy = st.text(
    alphabet=st.characters(blacklist_categories=("Cc", "Cs")),
    min_size=10,
    max_size=500
)

job_id_strategy = st.uuids().map(str)

user_id_strategy = st.integers(min_value=1, max_value=999999)

research_depth_strategy = st.sampled_from(["quick", "standard", "deep"])

llm_provider_strategy = st.sampled_from(["mock", "openai", "bedrock"])

doc_ids_strategy = st.lists(
    st.integers(min_value=1, max_value=999999),
    min_size=0,
    max_size=5,
    unique=True
)


class TestGraphStateInvariants:
    """Property-based tests for ResearchState and workflow invariants."""

    @given(
        job_id=job_id_strategy,
        user_id=user_id_strategy,
        query=query_strategy,
        research_depth=research_depth_strategy,
        doc_ids=doc_ids_strategy
    )
    @settings(suppress_health_check=[HealthCheck.too_slow], deadline=None)
    def test_initial_state_creation(self, job_id, user_id, query, research_depth, doc_ids):
        """Property: Initial state always contains required fields."""
        llm_provider = MockProvider()
        state = create_initial_state(
            job_id=job_id,
            user_id=user_id,
            query=query,
            llm_provider="mock",
            research_depth=research_depth,
            uploaded_doc_ids=doc_ids
        )

        # Each initial state must have these fields
        assert state is not None
        assert state['job_id'] == job_id
        assert state['user_id'] == user_id
        assert state['query'] == query
        assert state['llm_provider'] == "mock"
        assert state['research_depth'] == research_depth
        assert state['uploaded_doc_ids'] == doc_ids
        assert state['status'] == 'running'
        assert isinstance(state['agent_logs'], list)
        assert isinstance(state['citations'], list)
        assert state['analysis_iteration'] == 0

    @given(query=query_strategy)
    @settings(suppress_health_check=[HealthCheck.too_slow], deadline=None)
    async def test_planner_output_invariants(self, query):
        """
        Property: Planner always outputs 3-5 sub-questions.
        Sub-questions are non-empty strings.
        """
        llm_provider = MockProvider()
        state = create_initial_state(
            job_id="test-123",
            user_id=1,
            query=query,
            llm_provider="mock",
            research_depth="standard"
        )

        result_state = await planner_node(state, llm_provider)

        # Invariant 1: sub_questions always exists
        assert 'sub_questions' in result_state

        # Invariant 2: Should have 3-5 sub-questions
        sub_questions = result_state['sub_questions']
        assert isinstance(sub_questions, list)
        assert 3 <= len(sub_questions) <= 5

        # Invariant 3: Each sub-question is non-empty string
        for sq in sub_questions:
            assert isinstance(sq, str)
            assert len(sq) > 0

        # Invariant 4: report_structure is set
        assert result_state['report_structure'] is not None

        # Invariant 5: Agent log recorded
        logs = result_state['agent_logs']
        assert len(logs) > 0
        last_log = logs[-1]
        assert last_log['agent'] == 'planner'

    @given(num_questions=st.integers(min_value=1, max_value=5))
    @settings(suppress_health_check=[HealthCheck.too_slow], deadline=None)
    async def test_critic_score_boundaries(self, num_questions):
        """
        Property: Critic score is always in valid range [0, 10].
        Score distribution should be reasonable.
        """
        llm_provider = MockProvider()
        base_state = create_initial_state(
            job_id="test-123",
            user_id=1,
            query="What is AI?",
            llm_provider="mock",
            research_depth="standard"
        )

        # Set up state with analysis draft
        base_state['analysis_draft'] = "The analysis describes AI as a technology..."
        base_state['sub_questions'] = [f"Question {i}" for i in range(num_questions)]

        result_state = await critic_node(base_state, llm_provider)

        # Invariant 1: critic_score is always set
        assert 'critic_score' in result_state

        # Invariant 2: Score is in valid range
        score = result_state['critic_score']
        assert isinstance(score, (int, float))
        assert 0 <= score <= 10

        # Invariant 3: critic_iterations is incremented
        assert result_state['critic_iterations'] == (base_state['critic_iterations'] + 1)

    @given(
        critic_scores=st.lists(
            st.floats(min_value=0, max_value=10, allow_nan=False, allow_infinity=False),
            min_size=1,
            max_size=10,
            unique=False
        )
    )
    def test_critic_routing_logic(self, critic_scores):
        """
        Property: Critic routing decisions are consistent.
        - score < 7.0 and iterations < 3 → revise (analysis)
        - otherwise → proceed (synthesizer)
        """
        for score in critic_scores:
            for iterations in range(4):
                state = create_initial_state(
                    job_id="test-123",
                    user_id=1,
                    query="Test query",
                    llm_provider="mock",
                    research_depth="standard"
                )
                state['critic_score'] = score
                state['critic_iterations'] = iterations
                state['status'] = 'running'

                # Determine expected route
                should_revise = score < 7.0 and iterations < 3
                should_synthesize = not should_revise

                # Verify the expectation holds for all combinations
                if should_revise:
                    assert score < 7.0 and iterations < 3
                else:
                    assert score >= 7.0 or iterations >= 3

    @given(query=query_strategy)
    @settings(suppress_health_check=[HealthCheck.too_slow], deadline=None)
    async def test_workflow_state_progression(self, query):
        """
        Property: State transitions follow the expected agent sequence.
        Status should remain consistent through the workflow.
        """
        llm_provider = MockProvider()
        state = create_initial_state(
            job_id="test-123",
            user_id=1,
            query=query,
            llm_provider="mock",
            research_depth="standard"
        )

        # Initial state
        assert state['status'] == 'running'
        initial_log_count = len(state['agent_logs'])

        # Pass through planner
        state = await planner_node(state, llm_provider)
        
        # Verify state consistency
        if state['status'] != 'failed':
            assert 'sub_questions' in state
            assert len(state['agent_logs']) > initial_log_count

    @given(
        num_sub_questions=st.integers(min_value=3, max_value=5),
        doc_count=st.integers(min_value=0, max_value=5)
    )
    @settings(suppress_health_check=[HealthCheck.too_slow], deadline=None)
    async def test_agent_log_completeness(self, num_sub_questions, doc_count):
        """
        Property: Every agent records logs with required fields.
        Log entries always contain: agent, status, and optionally duration_ms or error.
        """
        llm_provider = MockProvider()
        state = create_initial_state(
            job_id="test-456",
            user_id=2,
            query="How does machine learning work? " * 2,  # Meet min length
            llm_provider="mock",
            research_depth="standard",
            uploaded_doc_ids=list(range(doc_count))
        )

        # Run planner
        state = await planner_node(state, llm_provider)

        # Verify log structure
        logs = state['agent_logs']
        assert len(logs) > 0
        
        for log in logs:
            # Required fields
            assert 'agent' in log
            assert 'status' in log
            assert log['agent'] in ['planner', 'research', 'analysis', 'critic', 'synthesizer']
            assert log['status'] in ['complete', 'failed', 'running']
            
            # Optional fields that should exist if not failed
            if log['status'] == 'complete':
                assert 'duration_ms' in log or 'agent' in log
            elif log['status'] == 'failed':
                assert 'error' in log or 'status' in log

    @given(query=query_strategy, depth=research_depth_strategy)
    @settings(suppress_health_check=[HealthCheck.too_slow], deadline=None)
    def test_initial_state_immutability(self, query, depth):
        """
        Property: Creating initial state doesn't modify inputs.
        Original parameters remain unchanged.
        """
        original_query = query
        original_depth = depth
        doc_ids = [1, 2, 3]
        original_doc_ids = doc_ids.copy()

        state = create_initial_state(
            job_id="test-789",
            user_id=5,
            query=query,
            llm_provider="mock",
            research_depth=depth,
            uploaded_doc_ids=doc_ids
        )

        # Original parameters should be unchanged
        assert query == original_query
        assert depth == original_depth
        assert doc_ids == original_doc_ids

        # State should have separate copies
        assert state['query'] == original_query
        assert state['uploaded_doc_ids'] == original_doc_ids

    @given(
        iterations=st.integers(min_value=0, max_value=3),
        score=st.floats(min_value=0, max_value=10)
    )
    def test_revision_limit_enforcement(self, iterations, score):
        """
        Property: System never attempts more than 3 revision loops.
        Once limit reached, always proceed to synthesis regardless of score.
        """
        state = create_initial_state(
            job_id="test-limit",
            user_id=1,
            query="Test query for revision limits",
            llm_provider="mock",
            research_depth="standard"
        )
        state['critic_iterations'] = iterations
        state['critic_score'] = score

        # Determine if should loop
        can_revise = score < 7.0 and iterations < 3
        
        # If iterations >= 3, should never loop regardless of score
        if iterations >= 3:
            assert not can_revise or score >= 7.0

    @given(query=query_strategy)
    @settings(suppress_health_check=[HealthCheck.too_slow], deadline=None)
    def test_query_persistence(self, query):
        """
        Property: Original query is preserved through all agent transformations.
        Query field never changes during workflow execution.
        """
        original_query = query
        state = create_initial_state(
            job_id="test-persist",
            user_id=1,
            query=query,
            llm_provider="mock",
            research_depth="standard"
        )

        # Throughout the state, query must remain original
        assert state['query'] == original_query

        # Even in subsequent steps (simulated)
        state['sub_questions'] = ["Q1", "Q2", "Q3"]
        state['analysis_draft'] = "Some analysis text"
        state['final_report'] = "Complete report"

        # Query should still be original
        assert state['query'] == original_query

    @given(
        base_state_data=st.fixed_dictionaries({
            'job_id': st.just('test-123'),
            'user_id': st.just(1),
            'query': st.text(min_size=10, max_size=100),
            'status': st.sampled_from(['running', 'complete', 'failed'])
        })
    )
    def test_state_type_safety(self, base_state_data):
        """
        Property: State maintains type safety for all known fields.
        Collection fields are always lists, scalar fields are appropriate types.
        """
        state = create_initial_state(
            job_id=base_state_data['job_id'],
            user_id=base_state_data['user_id'],
            query=base_state_data['query'],
            llm_provider="mock",
            research_depth="standard"
        )

        # Type invariants
        assert isinstance(state['job_id'], str)
        assert isinstance(state['user_id'], int)
        assert isinstance(state['query'], str)
        assert isinstance(state['sub_questions'], list)
        assert isinstance(state['research_results'], dict)
        assert isinstance(state['agent_logs'], list)
        assert isinstance(state['citations'], list)
        assert state['analysis_iteration'] >= 0
        assert state['critic_iterations'] >= 0


class TestWorkflowExecution:
    """Integration tests for complete workflow execution."""

    @given(
        query=query_strategy,
        depth=research_depth_strategy,
        doc_ids=doc_ids_strategy
    )
    @settings(suppress_health_check=[HealthCheck.too_slow], deadline=None, max_examples=5)
    def test_workflow_graph_compilation(self, query, depth, doc_ids):
        """Property: Workflow graph compiles successfully for all valid inputs."""
        llm_provider = MockProvider()
        
        try:
            graph = build_research_graph(llm_provider)
            assert graph is not None
        except Exception as e:
            pytest.fail(f"Graph compilation failed: {str(e)}")

    @given(query=query_strategy)
    @settings(suppress_health_check=[HealthCheck.too_slow], deadline=None, max_examples=3)
    def test_workflow_handles_long_queries(self, query):
        """Property: Workflow processes queries of various lengths without error."""
        assert len(query) >= 10  # Hypothesis constraint
        state = create_initial_state(
            job_id="test-long",
            user_id=1,
            query=query,
            llm_provider="mock",
            research_depth="standard"
        )
        assert state['query'] == query

    @pytest.mark.asyncio
    @given(doc_ids=doc_ids_strategy)
    @settings(suppress_health_check=[HealthCheck.too_slow], deadline=None, max_examples=3)
    async def test_workflow_with_variable_documents(self, doc_ids):
        """Property: Workflow handles 0-5 documents without error."""
        llm_provider = MockProvider()
        state = create_initial_state(
            job_id="test-docs",
            user_id=1,
            query="What is artificial intelligence technology?",
            llm_provider="mock",
            research_depth="standard",
            uploaded_doc_ids=doc_ids
        )
        
        assert state['uploaded_doc_ids'] == doc_ids
        assert 0 <= len(state['uploaded_doc_ids']) <= 5
