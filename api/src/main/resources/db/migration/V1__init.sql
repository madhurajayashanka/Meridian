-- Meridian Platform — PostgreSQL Database Schema
-- Version: 1
-- Created: 2026-03-23

-- Enable required extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "vector";

-- ============================================================================
-- Users Table
-- ============================================================================
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    avatar_url VARCHAR(1024),
    failed_login_attempts INT DEFAULT 0,
    locked_until TIMESTAMPTZ,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ,
    
    CONSTRAINT email_format CHECK (email ~* '^[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Z|a-z]{2,}$'),
    CONSTRAINT password_hash_not_empty CHECK (password_hash != ''),
    CONSTRAINT name_not_empty CHECK (name != '')
);

CREATE INDEX idx_users_email ON users(email) WHERE deleted_at IS NULL;
CREATE INDEX idx_users_created_at ON users(created_at DESC);

-- ============================================================================
-- Refresh Tokens Table (for token rotation)
-- ============================================================================
CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    
    CONSTRAINT token_hash_not_empty CHECK (token_hash != '')
);

CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens(expires_at);

-- ============================================================================
-- Projects Table
-- ============================================================================
CREATE TABLE projects (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    is_archived BOOLEAN DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ,
    
    CONSTRAINT name_length CHECK (length(name) >= 1 AND length(name) <= 100),
    CONSTRAINT user_project_ownership UNIQUE (user_id, name)
);

CREATE INDEX idx_projects_user_id ON projects(user_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_projects_created_at ON projects(created_at DESC);

-- ============================================================================
-- Documents Table (user-uploaded PDFs and TXTs)
-- ============================================================================
CREATE TABLE documents (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    original_filename VARCHAR(255) NOT NULL,
    s3_key VARCHAR(1024) NOT NULL UNIQUE,
    file_type VARCHAR(10) NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PROCESSING',
    error_message TEXT,
    chunk_count INT DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ,
    
    CONSTRAINT file_type_valid CHECK (file_type IN ('pdf', 'txt')),
    CONSTRAINT status_valid CHECK (status IN ('PROCESSING', 'READY', 'FAILED')),
    CONSTRAINT file_size_positive CHECK (file_size_bytes > 0)
);

CREATE INDEX idx_documents_project_id ON documents(project_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_documents_user_id ON documents(user_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_documents_status ON documents(status) WHERE deleted_at IS NULL;

-- ============================================================================
-- Embeddings Table (pgvector storage for document and report chunks)
-- ============================================================================
CREATE TABLE embeddings (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    document_id UUID REFERENCES documents(id) ON DELETE CASCADE,
    report_id UUID,
    content TEXT NOT NULL,
    embedding vector(1536) NOT NULL,
    chunk_index INT,
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    
    CONSTRAINT chunk_content_not_empty CHECK (content != '')
);

-- HNSW index for fast approximate cosine similarity search
CREATE INDEX idx_embeddings_vector ON embeddings
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

CREATE INDEX idx_embeddings_document_id ON embeddings(document_id) WHERE document_id IS NOT NULL;
CREATE INDEX idx_embeddings_report_id ON embeddings(report_id) WHERE report_id IS NOT NULL;

-- ============================================================================
-- Research Jobs Table
-- ============================================================================
CREATE TABLE research_jobs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    query TEXT NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    llm_provider VARCHAR(50) NOT NULL DEFAULT 'BEDROCK',
    research_depth VARCHAR(50) NOT NULL DEFAULT 'STANDARD',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    error_message TEXT,
    
    CONSTRAINT query_length CHECK (length(query) >= 10 AND length(query) <= 500),
    CONSTRAINT status_valid CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETE', 'FAILED', 'CANCELLED')),
    CONSTRAINT llm_provider_valid CHECK (llm_provider IN ('BEDROCK', 'OPENAI')),
    CONSTRAINT depth_valid CHECK (research_depth IN ('QUICK', 'STANDARD', 'DEEP'))
);

CREATE INDEX idx_research_jobs_project_id ON research_jobs(project_id);
CREATE INDEX idx_research_jobs_user_id ON research_jobs(user_id);
CREATE INDEX idx_research_jobs_status ON research_jobs(status);
CREATE INDEX idx_research_jobs_created_at ON research_jobs(created_at DESC);

-- ============================================================================
-- Research Job Documents Association (many-to-many)
-- ============================================================================
CREATE TABLE research_job_documents (
    job_id UUID NOT NULL REFERENCES research_jobs(id) ON DELETE CASCADE,
    document_id UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    PRIMARY KEY (job_id, document_id)
);

CREATE INDEX idx_research_job_documents_job_id ON research_job_documents(job_id);
CREATE INDEX idx_research_job_documents_document_id ON research_job_documents(document_id);

-- ============================================================================
-- Reports Table (final output from Synthesizer Agent)
-- ============================================================================
CREATE TABLE reports (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    job_id UUID NOT NULL REFERENCES research_jobs(id) ON DELETE CASCADE,
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title VARCHAR(500) NOT NULL,
    s3_key VARCHAR(1024) NOT NULL UNIQUE,
    word_count INT,
    citation_count INT DEFAULT 0,
    critic_score DECIMAL(3, 1),
    revision_count INT DEFAULT 0,
    is_public BOOLEAN DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ,
    
    CONSTRAINT title_not_empty CHECK (title != ''),
    CONSTRAINT word_count_positive CHECK (word_count IS NULL OR word_count > 0),
    CONSTRAINT citation_count_non_negative CHECK (citation_count >= 0),
    CONSTRAINT critic_score_range CHECK (critic_score IS NULL OR (critic_score >= 1.0 AND critic_score <= 10.0)),
    CONSTRAINT revision_count_non_negative CHECK (revision_count >= 0)
);

CREATE INDEX idx_reports_job_id ON reports(job_id);
CREATE INDEX idx_reports_project_id ON reports(project_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_reports_user_id ON reports(user_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_reports_created_at ON reports(created_at DESC);
CREATE INDEX idx_reports_public ON reports(is_public) WHERE is_public = true AND deleted_at IS NULL;

-- ============================================================================
-- Chat Messages Table (for RAG chat with reports)
-- ============================================================================
CREATE TABLE chat_messages (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    report_id UUID NOT NULL REFERENCES reports(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role VARCHAR(50) NOT NULL,
    content TEXT NOT NULL,
    tokens_used INT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    
    CONSTRAINT role_valid CHECK (role IN ('user', 'assistant', 'system')),
    CONSTRAINT content_not_empty CHECK (content != ''),
    CONSTRAINT tokens_positive CHECK (tokens_used IS NULL OR tokens_used > 0)
);

CREATE INDEX idx_chat_messages_report_id ON chat_messages(report_id);
CREATE INDEX idx_chat_messages_user_id ON chat_messages(user_id);
CREATE INDEX idx_chat_messages_created_at ON chat_messages(created_at DESC);

-- ============================================================================
-- Agent Logs Table (for debugging and transparency)
-- ============================================================================
CREATE TABLE agent_logs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    job_id UUID NOT NULL REFERENCES research_jobs(id) ON DELETE CASCADE,
    agent_name VARCHAR(100) NOT NULL,
    status VARCHAR(50) NOT NULL,
    input_tokens INT,
    output_tokens INT,
    duration_ms INT,
    payload JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    
    CONSTRAINT agent_name_not_empty CHECK (agent_name != ''),
    CONSTRAINT status_valid CHECK (status IN ('RUNNING', 'COMPLETE', 'FAILED')),
    CONSTRAINT tokens_positive CHECK (input_tokens >= 0 AND output_tokens >= 0),
    CONSTRAINT duration_positive CHECK (duration_ms >= 0)
);

CREATE INDEX idx_agent_logs_job_id ON agent_logs(job_id);
CREATE INDEX idx_agent_logs_agent_name ON agent_logs(agent_name);
CREATE INDEX idx_agent_logs_created_at ON agent_logs(created_at DESC);

-- ============================================================================
-- API Keys Table (for future API authentication)
-- ============================================================================
CREATE TABLE api_keys (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    key_hash VARCHAR(255) NOT NULL UNIQUE,
    last_used_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ
);

CREATE INDEX idx_api_keys_user_id ON api_keys(user_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_api_keys_active ON api_keys(is_active) WHERE is_active = true;

-- ============================================================================
-- Audit Log Table (for compliance and security)
-- ============================================================================
CREATE TABLE audit_logs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    entity_type VARCHAR(100) NOT NULL,
    entity_id UUID NOT NULL,
    action VARCHAR(50) NOT NULL,
    changes JSONB,
    ip_address INET,
    user_agent TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    
    CONSTRAINT entity_type_not_empty CHECK (entity_type != ''),
    CONSTRAINT action_not_empty CHECK (action != '')
);

CREATE INDEX idx_audit_logs_user_id ON audit_logs(user_id);
CREATE INDEX idx_audit_logs_entity ON audit_logs(entity_type, entity_id);
CREATE INDEX idx_audit_logs_created_at ON audit_logs(created_at DESC);

-- ============================================================================
-- Constraints and Triggers
-- ============================================================================

-- Update updated_at timestamp automatically
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER users_updated_at BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER projects_updated_at BEFORE UPDATE ON projects
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER documents_updated_at BEFORE UPDATE ON documents
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER research_jobs_updated_at BEFORE UPDATE ON research_jobs
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER reports_updated_at BEFORE UPDATE ON reports
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ============================================================================
-- Initial Indexes for Performance
-- ============================================================================

-- Composite indexes for common queries
CREATE INDEX idx_research_jobs_user_status ON research_jobs(user_id, status);
CREATE INDEX idx_research_jobs_project_created ON research_jobs(project_id, created_at DESC);
CREATE INDEX idx_documents_project_status ON documents(project_id, status);
CREATE INDEX idx_reports_user_created ON reports(user_id, created_at DESC);

-- ============================================================================
-- Grants (for multi-tenancy safety if needed)
-- ============================================================================
-- These would be configured per environment; for now, all tables are accessible to the app user
