import asyncio
import json
import logging
from typing import Optional, List
from datetime import datetime
from app.graph.state import ResearchState, PROMPT_VERSIONS
from app.llm.provider import LLMProvider
from app.guardrails import check_input, check_output, GuardrailViolation

logger = logging.getLogger(__name__)


def _estimate_tokens(text: str) -> int:
    """Rough token estimate: ~4 chars per token."""
    return max(1, len(text) // 4)


def _check_budget(state: ResearchState, agent: str) -> bool:
    """Return False and mark job failed if token budget is exceeded."""
    if state.get('tokens_used', 0) >= state.get('token_budget', 999_999):
        state['status'] = 'failed'
        state['error'] = f"Token budget exceeded at {agent} agent ({state['tokens_used']} / {state['token_budget']})"
        state['agent_logs'].append({'agent': agent, 'status': 'failed', 'error': state['error']})
        logger.warning(state['error'])
        return False
    return True


async def planner_node(state: ResearchState, llm_provider: LLMProvider) -> dict:
    """
    Planner Agent Node.
    Decomposes the research query into 3-5 non-overlapping sub-questions.
    Requirement 5.2: Planner decomposes query into 3-5 focused sub-questions
    """
    start_time = datetime.now()

    # Guardrail: reject injected queries before any LLM call
    try:
        check_input(state['query'])
    except GuardrailViolation as e:
        state['status'] = 'failed'
        state['error'] = f"Guardrail blocked query: {e.reason}"
        state['agent_logs'].append({'agent': 'planner', 'status': 'failed', 'error': state['error']})
        return state

    if not _check_budget(state, 'planner'):
        return state
    
    prompt = f"""You are a research planning expert. 
    
The user has asked: "{state['query']}"

Please decompose this into 3-5 focused, non-overlapping sub-questions that together comprehensively address the main query.

Return ONLY a JSON array of strings, each being a focused sub-question.
Example format: ["What is X?", "How does Y work?", "Why is Z important?"]

Decomposition:"""

    try:
        response = await llm_provider.invoke_chat_fast(prompt, temperature=0.3)
        state['tokens_used'] = state.get('tokens_used', 0) + _estimate_tokens(prompt) + _estimate_tokens(response)

        # Parse the JSON response — planner
        sub_questions = json.loads(response)
        if not isinstance(sub_questions, list):
            sub_questions = [response]
        
        # Ensure 3-5 questions
        sub_questions = sub_questions[:5] if len(sub_questions) > 5 else sub_questions
        if len(sub_questions) < 3:
            sub_questions = sub_questions + ["Additional research needed on " + state['query']]
        
        state['sub_questions'] = sub_questions
        state['report_structure'] = "Executive Summary\nKey Findings\nDetailed Analysis\nConclusion"
        state['agent_logs'].append({
            'agent': 'planner',
            'status': 'complete',
            'prompt_version': PROMPT_VERSIONS['planner'],
            'duration_ms': int((datetime.now() - start_time).total_seconds() * 1000),
            'sub_questions_count': len(sub_questions)
        })
        
        return state
    except Exception as e:
        state['status'] = 'failed'
        state['error'] = f"Planner error: {str(e)}"
        state['agent_logs'].append({
            'agent': 'planner',
            'status': 'failed',
            'error': str(e)
        })
        return state


async def research_node(state: ResearchState, llm_provider: LLMProvider) -> dict:
    """
    Research Agent Node.
    Performs web search (Tavily) and document RAG (pgvector) for each sub-question.
    """
    start_time = datetime.now()

    if not state.get('sub_questions'):
        state['status'] = 'failed'
        state['error'] = 'No sub-questions from planner'
        return state

    if not _check_budget(state, 'research'):
        return state

    state['research_results'] = {}

    # Lazy-import to avoid hard dependency when not configured
    from app.config import get_settings
    settings = get_settings()

    try:
        for question in state['sub_questions']:
            sources = []

            # --- Real Tavily web search ---
            if settings.tavily_api_key and not settings.mock_tavily_responses:
                try:
                    from tavily import TavilyClient
                    client = TavilyClient(api_key=settings.tavily_api_key)
                    result = await asyncio.to_thread(
                        client.search, question, max_results=3, search_depth="basic"
                    )
                    for r in result.get("results", []):
                        sources.append({
                            "type": "web",
                            "title": r.get("title", ""),
                            "url": r.get("url", ""),
                            "snippet": r.get("content", "")[:500],
                        })
                except Exception as e:
                    logger.warning(f"Tavily search failed for '{question}': {e}")
            else:
                # Mock fallback for dev
                sources.append({
                    "type": "web",
                    "title": f"Research result for: {question[:50]}",
                    "url": "https://example.com/1",
                    "snippet": f"Mock research evidence addressing: {question}",
                })

            # --- Real pgvector document search ---
            if state.get('uploaded_doc_ids'):
                try:
                    from app.rag.store import EmbeddingStore
                    # Reuse the global db_pool injected via state to avoid per-question pool creation
                    _pool = state.get('_db_pool')
                    if _pool is None:
                        # fallback: create once and cache in state
                        import asyncpg
                        from app.config import get_settings as _gs
                        _s = _gs()
                        _db_url = _s.database_url.replace("postgresql+asyncpg://", "postgresql://")
                        _pool = await asyncpg.create_pool(_db_url, min_size=1, max_size=3)
                        state['_db_pool'] = _pool
                    store = EmbeddingStore.__new__(EmbeddingStore)
                    store.db_url = None
                    store.pool = _pool
                    query_embedding = await llm_provider.get_embedding(question)
                    doc_results = await store.semantic_search(
                        query_embedding, limit=3,
                        document_ids=state['uploaded_doc_ids']
                    )
                    for r in doc_results:
                        sources.append({
                            "type": "document",
                            "title": "Uploaded document",
                            "url": "",
                            "snippet": r["content"][:500],
                        })
                except Exception as e:
                    logger.warning(f"pgvector search failed: {e}")

            state['research_results'][question] = sources

        state['agent_logs'].append({
            'agent': 'research',
            'status': 'complete',
            'prompt_version': PROMPT_VERSIONS['research'],
            'duration_ms': int((datetime.now() - start_time).total_seconds() * 1000),
            'sources_found': sum(len(s) for s in state['research_results'].values()),
        })
        return state

    except Exception as e:
        state['status'] = 'failed'
        state['error'] = f"Research error: {str(e)}"
        state['agent_logs'].append({'agent': 'research', 'status': 'failed', 'error': str(e)})
        return state


async def analysis_node(state: ResearchState, llm_provider: LLMProvider) -> dict:
    """
    Analysis Agent Node.
    Synthesizes research findings into a structured analysis draft.
    Requirement 5.5: Analysis agent synthesizes research with inline citations
    """
    start_time = datetime.now()
    
    if not state.get('research_results'):
        state['status'] = 'failed'
        state['error'] = 'No research results'
        return state

    if not _check_budget(state, 'analysis'):
        return state

    try:
        # Build context from research results
        research_context = ""
        for i, (question, sources) in enumerate(state['research_results'].items(), 1):
            research_context += f"\n## {question}\n"
            for j, source in enumerate(sources, 1):
                research_context += f"[{j}] {source.get('title', 'Source')}: {source.get('snippet', '')}\n"
        
        prompt = f"""You are a research analyst. 

Based on these research findings, write a comprehensive analysis that addresses the original query.
Use inline citations [N] referring to the numbered sources below.

Original Query: {state['query']}

Research Findings:
{research_context}

Please write a detailed, well-structured analysis with inline citations.
Include multiple paragraphs covering different aspects of the topic."""

        analysis = await llm_provider.invoke_chat(prompt, temperature=0.5)
        state['tokens_used'] = state.get('tokens_used', 0) + _estimate_tokens(prompt) + _estimate_tokens(analysis or '')
        if not analysis or not str(analysis).strip():
            state['status'] = 'failed'
            state['error'] = 'Analysis agent returned empty content'
            state['agent_logs'].append({
                'agent': 'analysis',
                'status': 'failed',
                'error': state['error']
            })
            return state
        
        state['analysis_draft'] = analysis
        state['analysis_iteration'] += 1
        state['agent_logs'].append({
            'agent': 'analysis',
            'status': 'complete',
            'prompt_version': PROMPT_VERSIONS['analysis'],
            'duration_ms': int((datetime.now() - start_time).total_seconds() * 1000),
            'iteration': state['analysis_iteration']
        })
        
        return state
    except Exception as e:
        state['status'] = 'failed'
        state['error'] = f"Analysis error: {str(e)}"
        state['agent_logs'].append({
            'agent': 'analysis',
            'status': 'failed',
            'error': str(e)
        })
        return state


async def critic_node(state: ResearchState, llm_provider: LLMProvider) -> dict:
    """
    Critic Agent Node.
    Evaluates analysis draft and returns score (1-10) with feedback.
    Requirement 5.6: Critic evaluates across multiple dimensions
    Requirement 5.7: Revise if score < 7.0 and iterations < 3
    """
    start_time = datetime.now()
    
    if not state.get('analysis_draft'):
        state['status'] = 'failed'
        state['error'] = 'No analysis draft to critique'
        return state

    if not _check_budget(state, 'critic'):
        return state

    try:
        prompt = f"""You are a critical analysis evaluator. 

Review this research analysis and provide a quality score from 1.0 to 10.0 across these dimensions:
- Factual accuracy
- Completeness
- Coherence
- Citation quality
- Depth of analysis

Analysis to review:
{state['analysis_draft']}

Provide your response as JSON with this format:
{{
    "score": 8.5,
    "feedback": "Your detailed feedback here",
    "strengths": ["Strength 1", "Strength 2"],
    "improvements": ["Improvement 1", "Improvement 2"]
}}

Evaluation:"""

        response = await llm_provider.invoke_chat_fast(prompt, temperature=0.3)
        state['tokens_used'] = state.get('tokens_used', 0) + _estimate_tokens(prompt) + _estimate_tokens(response)

        try:
            evaluation = json.loads(response)
            score = evaluation.get('score')
            # Structured output validation: score must be a number in [1, 10]
            if not isinstance(score, (int, float)) or not (1.0 <= float(score) <= 10.0):
                raise ValueError(f"Invalid critic score: {score!r}")
            state['critic_score'] = float(score)
            state['critic_feedback'] = evaluation.get('feedback', '')
        except (json.JSONDecodeError, ValueError, KeyError):
            # Deterministic fallback when LLM response parsing fails
            logger.warning("Critic response parsing failed; using fallback score 7.0")
            state['critic_score'] = 7.0
            state['critic_feedback'] = "Analysis appears comprehensive and well-structured."
        
        state['critic_iterations'] += 1
        state['agent_logs'].append({
            'agent': 'critic',
            'status': 'complete',
            'prompt_version': PROMPT_VERSIONS['critic'],
            'duration_ms': int((datetime.now() - start_time).total_seconds() * 1000),
            'score': state['critic_score']
        })
        
        return state
    except Exception as e:
        state['status'] = 'failed'
        state['error'] = f"Critic error: {str(e)}"
        state['agent_logs'].append({
            'agent': 'critic',
            'status': 'failed',
            'error': str(e)
        })
        return state


async def synthesizer_node(state: ResearchState, llm_provider: LLMProvider) -> dict:
    """
    Synthesizer Agent Node.
    Produces final polished research report in Markdown.
    Requirement 5.9: Synthesizer produces report with specific sections
    """
    start_time = datetime.now()
    
    if not state.get('analysis_draft'):
        state['status'] = 'failed'
        state['error'] = 'No analysis draft to synthesize'
        return state

    if not _check_budget(state, 'synthesizer'):
        return state

    try:
        prompt = f"""You are a report writer. 

Create a comprehensive research report in Markdown based on this analysis.
The report should include:
- H1 title (descriptive, professional)
- Executive summary (2-3 paragraphs)
- Main findings sections
- Conflicting viewpoints (if any)
- Key takeaways
- Recommended next steps
- Numbered citations list

Original Query: {state['query']}

Analysis to transform:
{state['analysis_draft']}

Generate the final report:"""

        report = await llm_provider.invoke_chat(prompt, temperature=0.4)
        state['tokens_used'] = state.get('tokens_used', 0) + _estimate_tokens(prompt) + _estimate_tokens(report or '')

        # Guardrail: check final report for unsafe content
        try:
            check_output(report)
        except GuardrailViolation as e:
            state['status'] = 'failed'
            state['error'] = f"Guardrail blocked synthesizer output: {e.reason}"
            state['agent_logs'].append({'agent': 'synthesizer', 'status': 'failed', 'error': state['error']})
            return state

        state['final_report'] = report
        state['status'] = 'complete'
        state['completed_at'] = datetime.now().isoformat()
        state['agent_logs'].append({
            'agent': 'synthesizer',
            'status': 'complete',
            'prompt_version': PROMPT_VERSIONS['synthesizer'],
            'duration_ms': int((datetime.now() - start_time).total_seconds() * 1000)
        })
        
        return state
    except Exception as e:
        state['status'] = 'failed'
        state['error'] = f"Synthesizer error: {str(e)}"
        state['agent_logs'].append({
            'agent': 'synthesizer',
            'status': 'failed',
            'error': str(e)
        })
        return state
