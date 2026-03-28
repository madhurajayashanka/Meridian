ALTER TABLE research_jobs DROP CONSTRAINT IF EXISTS llm_provider_valid;

ALTER TABLE research_jobs
ADD CONSTRAINT llm_provider_valid
CHECK (llm_provider IN ('BEDROCK', 'OPENAI', 'MOCK'));
