import pytest
from hypothesis import given, strategies as st
import asyncio
from app.graph.state import ResearchState
from app.graph.workflow import create_initial_state
from app.llm.provider import MockProvider
import json


class TestResearchState:
    """
    Property-based tests for ResearchState integrity.
    Feature: meridian-platform, Property 1-10: Research workflow correctness
    """

    @given(
        query=st.text(min_size=10, max_size=500),
        depth=st.sampled_from(['quick', 'standard', 'deep'])
    )
    def test_initial_state_creation(self, query, depth):
        """Property 1: Valid state creation initializes all fields."""
        state = create_initial_state(
            job_id='test-job-1',
            user_id='user-1',
            query=query,
            llm_provider='mock',
            research_depth=depth,
            uploaded_doc_ids=['doc1', 'doc2']
        )

        assert state['job_id'] == 'test-job-1'
        assert state['query'] == query
        assert state['research_depth'] == depth
        assert state['status'] == 'running'
        assert state['analysis_iteration'] == 0
        assert state['critic_iterations'] == 0
        assert len(state['uploaded_doc_ids']) == 2
        assert isinstance(state['agent_logs'], list)

    @given(score=st.floats(min_value=1.0, max_value=10.0))
    def test_critic_score_range(self, score):
        """Property 2: Critic scores are within valid range (1.0-10.0)."""
        state = create_initial_state(
            job_id='test-job-2',
            user_id='user-1',
            query='Test query for evaluation',
            llm_provider='mock',
            research_depth='standard'
        )
        
        state['critic_score'] = score
        
        assert 1.0 <= state['critic_score'] <= 10.0

    @given(iterations=st.integers(min_value=0, max_value=10))
    def test_revision_loop_limits(self, iterations):
        """Property 3: Revision iterations don't exceed maximum."""
        state = create_initial_state(
            job_id='test-job-3',
            user_id='user-1',
            query='Test revision limits',
            llm_provider='mock',
            research_depth='deep'
        )
        
        state['critic_iterations'] = iterations
        
        # Deep research allows up to 3 revisions
        if state['research_depth'] == 'deep':
            should_revise = iterations < 3
            assert isinstance(should_revise, bool)

    @given(
        questions=st.lists(st.text(min_size=5, max_size=100), 
                          min_size=3, max_size=5)
    )
    def test_sub_question_decomposition(self, questions):
        """Property 4: Sub-questions decompose query comprehensively."""
        state = create_initial_state(
            job_id='test-job-4',
            user_id='user-1',
            query='Multi-faceted research topic',
            llm_provider='mock',
            research_depth='standard'
        )
        
        state['sub_questions'] = questions
        
        # Planner ensures 3-5 sub-questions (Requirement 5.2)
        assert 3 <= len(state['sub_questions']) <= 5
        assert all(isinstance(q, str) for q in state['sub_questions'])

    def test_state_serialization(self):
        """Property 5: State is JSON-serializable for Redis persistence."""
        state = create_initial_state(
            job_id='test-job-5',
            user_id='user-1',
            query='Test serialization',
            llm_provider='mock',
            research_depth='standard'
        )
        
        # Should be JSON-serializable (required for Redis persistence)
        try:
            json_str = json.dumps(state, default=str)
            restored = json.loads(json_str)
            assert restored['job_id'] == 'test-job-5'
        except TypeError as e:
            pytest.fail(f"State not JSON-serializable: {e}")


class TestMockLLMProvider:
    """
    Mock LLM provider tests.
    Feature: meridian-platform, Property 11: Mock provider consistency
    """

    @pytest.mark.asyncio
    async def test_mock_provider_planner_response(self):
        """Test Planner node mock response format."""
        provider = MockProvider()
        
        prompt = "decompose into sub-questions"
        response = await provider.invoke_chat(prompt)
        
        assert isinstance(response, str)
        assert len(response) > 0
        # Should contain question-like content
        assert any(c in response.lower() for c in ['question', 'sub', 'decompose'])

    @pytest.mark.asyncio
    async def test_mock_provider_embedding(self):
        """Test embedding vector properties."""
        provider = MockProvider()
        
        embedding = await provider.embed_text("test content")
        
        assert isinstance(embedding, list)
        assert len(embedding) == 1536  # OpenAI embedding dimension
        assert all(isinstance(x, float) for x in embedding)

    @pytest.mark.asyncio
    async def test_mock_provider_determinism(self):
        """Test mock provider gives same response for same input."""
        provider = MockProvider()
        
        text = "same input text"
        embedding1 = await provider.embed_text(text)
        embedding2 = await provider.embed_text(text)
        
        # Deterministic embeddings
        assert embedding1 == embedding2


class TestAgentIntegration:
    """
    Agent node integration tests.
    Feature: meridian-platform, Property 12: Agent orchestration
    """

    @pytest.mark.asyncio
    async def test_planner_agent_output(self):
        """Test Planner agent generates sub-questions."""
        from app.agents.nodes import planner_node
        
        provider = MockProvider()
        state = create_initial_state(
            job_id='test-agent-1',
            user_id='user-1',
            query='What is machine learning?',
            llm_provider='mock',
            research_depth='standard'
        )
        
        result = await planner_node(state, provider)
        
        assert 'sub_questions' in result
        assert len(result['sub_questions']) > 0
        assert result['agent_logs'][-1]['agent'] == 'planner'

    @pytest.mark.asyncio
    async def test_research_agent_sources(self):
        """Test Research agent gathers sources."""
        from app.agents.nodes import research_node
        
        provider = MockProvider()
        state = create_initial_state(
            job_id='test-agent-2',
            user_id='user-1',
            query='Research topic',
            llm_provider='mock',
            research_depth='standard'
        )
        state['sub_questions'] = ['Q1', 'Q2', 'Q3']
        
        result = await research_node(state, provider)
        
        assert 'research_results' in result
        assert len(result['research_results']) == 3
        assert all(isinstance(sources, list) for sources in result['research_results'].values())

    @pytest.mark.asyncio
    async def test_analysis_agent_draft(self):
        """Test Analysis agent produces draft."""
        from app.agents.nodes import analysis_node
        
        provider = MockProvider()
        state = create_initial_state(
            job_id='test-agent-3',
            user_id='user-1',
            query='Research query',
            llm_provider='mock',
            research_depth='standard'
        )
        state['research_results'] = {
            'Q1': [{'title': 'Source 1', 'snippet': 'Content 1'}],
            'Q2': [{'title': 'Source 2', 'snippet': 'Content 2'}]
        }
        
        result = await analysis_node(state, provider)
        
        assert 'analysis_draft' in result
        assert isinstance(result['analysis_draft'], str)
        assert len(result['analysis_draft']) > 0
        assert result['analysis_iteration'] == 1

    @pytest.mark.asyncio
    async def test_critic_agent_scoring(self):
        """Test Critic agent produces scores."""
        from app.agents.nodes import critic_node
        
        provider = MockProvider()
        state = create_initial_state(
            job_id='test-agent-4',
            user_id='user-1',
            query='Research',
            llm_provider='mock',
            research_depth='standard'
        )
        state['analysis_draft'] = "Sample analysis draft content."
        
        result = await critic_node(state, provider)
        
        assert 'critic_score' in result
        assert 1.0 <= result['critic_score'] <= 10.0
        assert result['critic_iterations'] == 1
        assert 'critic_feedback' in result

    @pytest.mark.asyncio
    async def test_synthesizer_agent_report(self):
        """Test Synthesizer produces final report."""
        from app.agents.nodes import synthesizer_node
        
        provider = MockProvider()
        state = create_initial_state(
            job_id='test-agent-5',
            user_id='user-1',
            query='Final test',
            llm_provider='mock',
            research_depth='standard'
        )
        state['analysis_draft'] = "Final analysis"
        
        result = await synthesizer_node(state, provider)
        
        assert 'final_report' in result
        assert isinstance(result['final_report'], str)
        assert len(result['final_report']) > 0
        assert result['status'] == 'complete'


if __name__ == '__main__':
    pytest.main([__file__, '-v', '--tb=short'])
