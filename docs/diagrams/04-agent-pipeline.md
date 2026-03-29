# 04 — Agent Pipeline (LangGraph)

Internal structure of the AI agent workflow.

---

## Diagram: LangGraph State Machine

```
                    ┌─────────────────────────────────────────────────┐
                    │              ResearchState (TypedDict)           │
                    │                                                  │
                    │  job_id, user_id, query, llm_provider            │
                    │  research_depth, uploaded_doc_ids                │
                    │  sub_questions, research_results                 │
                    │  analysis_draft, critic_score, critic_feedback   │
                    │  final_report, citations, agent_logs             │
                    │  tokens_used, token_budget                       │
                    │  prompt_versions, model_id                       │
                    │  status, error, started_at, completed_at         │
                    └─────────────────────────────────────────────────┘
                                         │
                                    ENTRY POINT
                                         │
                                         ▼
                              ┌──────────────────┐
                              │    PLANNER        │
                              │                  │
                              │ Input: query      │
                              │ LLM: fast model   │
                              │ Guardrail: input  │
                              │ Output:           │
                              │  sub_questions[]  │
                              │  (3-5 items)      │
                              └────────┬─────────┘
                                       │ failed? → END
                                       ▼
                              ┌──────────────────┐
                              │    RESEARCH       │
                              │                  │
                              │ For each question:│
                              │  • Tavily search  │
                              │  • pgvector search│
                              │    (if docs)      │
                              │ Output:           │
                              │  research_results │
                              └────────┬─────────┘
                                       │
                          ┌────────────┴────────────┐
                          │ depth == "quick"?        │
                          │ YES              NO      │
                          ▼                  ▼
                   ┌────────────┐   ┌──────────────────┐
                   │SYNTHESIZER │   │    ANALYSIS       │
                   │(skip loop) │   │                  │
                   └────────────┘   │ Input: research   │
                          │         │ LLM: full model   │
                          │         │ Output:           │
                          │         │  analysis_draft   │
                          │         │  analysis_iter++  │
                          │         └────────┬─────────┘
                          │                  │ failed? → END
                          │                  ▼
                          │         ┌──────────────────┐
                          │         │     CRITIC        │
                          │         │                  │
                          │         │ Input: draft      │
                          │         │ LLM: fast model   │
                          │         │ Output:           │
                          │         │  critic_score     │
                          │         │  critic_feedback  │
                          │         │  critic_iter++    │
                          │         └────────┬─────────┘
                          │                  │
                          │     ┌────────────┴────────────┐
                          │     │ score < 7.0 AND          │
                          │     │ iterations < 3?          │
                          │     │ YES              NO      │
                          │     ▼                  ▼       │
                          │  (loop back        ┌──────────────────┐
                          │  to ANALYSIS)      │  SYNTHESIZER     │
                          │                    │                  │
                          │                    │ Input: draft     │
                          │                    │ LLM: full model  │
                          │                    │ Guardrail: output│
                          │                    │ Output:          │
                          └───────────────────▶│  final_report    │
                                               │  status=complete │
                                               └────────┬─────────┘
                                                        │
                                                       END
```

---

## Diagram: LLM Model Routing

```
Agent Node          Model Used          Why
──────────────────────────────────────────────────────
Planner             fast model          Simple JSON decomposition
Research            (no LLM call)       Tavily + pgvector only
Analysis            full model          Complex synthesis, citations
Critic              fast model          JSON scoring, structured output
Synthesizer         full model          Final polished Markdown report

Bedrock:  fast = claude-3-haiku   full = claude-3-5-sonnet
OpenAI:   fast = gpt-4o-mini      full = gpt-4o
Mock:     same mock for all
```

---

## Diagram: Token Budget Enforcement

```
Per job, before each agent node:

  tokens_used >= token_budget?
       │
       YES ──▶ status = "failed"
       │       error = "Token budget exceeded at {agent}"
       │       return state (skip LLM call)
       │
       NO  ──▶ invoke LLM
               tokens_used += estimate(prompt) + estimate(response)
               continue

Budgets:
  quick    = 20,000 tokens
  standard = 60,000 tokens
  deep     = 120,000 tokens
```

---

## Diagram: Guardrail Checks

```
User query
    │
    ▼
check_input(query)  ←── Planner node (before any LLM call)
    │                   Also: chat endpoint (before RAG)
    │ PASS
    ▼
[LLM calls...]
    │
    ▼
check_output(final_report)  ←── Synthesizer node (after LLM)
    │
    │ PASS
    ▼
Store report / return to user

check_input checks:  prompt injection patterns, jailbreak keywords
check_output checks: toxic content patterns, PII patterns (SSN, credit card, email, phone, credentials)
```

---

## Diagram: Redis Checkpointing

```
After each agent node completes:

  redis.SET "langgraph:checkpoint:{job_id}"
            JSON(full ResearchState)
            TTL: 48 hours

SSE stream reads:
  redis.XREAD "job:{job_id}:events"
              block=1000ms

Events published to stream:
  {type: "agent_update", agent: "planner", status: "complete", ...}
  {type: "agent_update", agent: "research", status: "complete", ...}
  {type: "job_complete", report_id: "uuid"}
  {type: "job_failed", error: "..."}

Stream TTL: 24 hours
```
