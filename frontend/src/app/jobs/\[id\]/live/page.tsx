'use client';

import React, { useEffect, useState } from 'react';
import { useRouter, useParams } from 'next/navigation';
import { useAuth } from '@/hooks/useAuth';
import { useApiClient } from '@/hooks/useApiClient';
import { useSSE } from '@/hooks/useSSE';
import Navigation from '@/components/Navigation';
import AgentCard from '@/components/AgentCard';
import Link from 'next/link';

interface JobData {
  id: string;
  query: string;
  status: 'PENDING' | 'RUNNING' | 'COMPLETE' | 'FAILED' | 'CANCELLED';
  llmProvider: string;
  researchDepth: string;
  createdAt: string;
  startedAt?: string;
  completedAt?: string;
  errorMessage?: string;
  project: {
    id: string;
    name: string;
  };
}

interface AgentStatus {
  agent: string;
  status: 'idle' | 'running' | 'complete' | 'failed';
  output?: string;
  progress?: number;
  durationMs?: number;
  error?: string;
}

const AGENTS = ['planner', 'research', 'analysis', 'critic', 'synthesizer'];

export default function LiveJobPage() {
  const router = useRouter();
  const params = useParams();
  const jobId = params.id as string;

  const { isAuthenticated, accessToken } = useAuth();
  const apiClient = useApiClient();

  const [job, setJob] = useState<JobData | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [agentStatuses, setAgentStatuses] = useState<Map<string, AgentStatus>>(
    new Map(AGENTS.map((agent) => [agent, { agent, status: 'idle' }]))
  );
  const [completedRedirect, setCompletedRedirect] = useState(false);

  const { connected: sseConnected, error: sseError } = useSSE(jobId, accessToken || '');

  // Redirect if not authenticated
  useEffect(() => {
    if (!isAuthenticated) {
      router.push('/login');
    }
  }, [isAuthenticated, router]);

  // Load initial job details
  useEffect(() => {
    const loadJob = async () => {
      if (!jobId) return;

      setLoading(true);
      setError(null);

      try {
        const response = await apiClient.post(
          '/graphql',
          {
            query: `
              query GetJob($id: ID!) {
                job(id: $id) {
                  id
                  query
                  status
                  llmProvider
                  researchDepth
                  createdAt
                  startedAt
                  completedAt
                  errorMessage
                  project {
                    id
                    name
                  }
                }
              }
            `,
            variables: { id: jobId },
          }
        );

        if (response.data?.job) {
          setJob(response.data.job);
        } else {
          setError('Job not found');
        }
      } catch (err: any) {
        console.error('Failed to load job:', err);
        setError(err?.message || 'Failed to load job');
      } finally {
        setLoading(false);
      }
    };

    loadJob();
  }, [jobId, apiClient]);

  // Poll job status every 3 seconds
  useEffect(() => {
    if (!jobId || !job) return;

    const pollInterval = setInterval(async () => {
      try {
        const response = await apiClient.post(
          '/graphql',
          {
            query: `
              query GetJob($id: ID!) {
                job(id: $id) {
                  id
                  status
                  completedAt
                  errorMessage
                }
              }
            `,
            variables: { id: jobId },
          }
        );

        if (response.data?.job) {
          setJob((prev) => (prev ? { ...prev, ...response.data.job } : null));
        }
      } catch (err) {
        console.error('Error polling job status:', err);
      }
    }, 3000);

    return () => clearInterval(pollInterval);
  }, [jobId, job, apiClient]);

  // Handle job completion redirect
  useEffect(() => {
    if (job?.status === 'COMPLETE' && !completedRedirect) {
      setCompletedRedirect(true);
      setTimeout(() => {
        router.push(`/jobs/${jobId}/report`);
      }, 2000);
    }
  }, [job?.status, jobId, router, completedRedirect]);

  // Simulate agent status updates (in real implementation, listen to SSE events)
  useEffect(() => {
    if (job?.status !== 'RUNNING') return;

    // Example: update agent statuses based on time elapsed
    const timer = setInterval(() => {
      setAgentStatuses((prev) => {
        const updated = new Map(prev);
        // Simulate agent progression
        const agentIndex = AGENTS.findIndex((a) => {
          const status = updated.get(a);
          return status?.status === 'running';
        });

        if (agentIndex === -1) {
          // Start first agent
          const firstAgent = updated.get(AGENTS[0]);
          if (firstAgent?.status === 'idle') {
            updated.set(AGENTS[0], { ...firstAgent, status: 'running', progress: 0 });
          }
        } else if (agentIndex < AGENTS.length - 1) {
          // Progress current agent or move to next
          const currentAgent = updated.get(AGENTS[agentIndex])!;
          if (!currentAgent.progress) {
            currentAgent.progress = 0;
          }
          currentAgent.progress += Math.random() * 30;

          if (currentAgent.progress >= 100) {
            // Mark as complete and move to next
            currentAgent.status = 'complete';
            currentAgent.progress = 100;
            currentAgent.durationMs = Math.floor(Math.random() * 5000) + 3000;
            updated.set(AGENTS[agentIndex + 1], {
              agent: AGENTS[agentIndex + 1],
              status: 'running',
              progress: 0,
            });
          }

          updated.set(AGENTS[agentIndex], currentAgent);
        }

        return updated;
      });
    }, 2000);

    return () => clearInterval(timer);
  }, [job?.status]);

  if (!isAuthenticated) {
    return null;
  }

  if (loading) {
    return (
      <div className="min-h-screen bg-slate-950 flex items-center justify-center">
        <div className="text-center">
          <div className="flex gap-1 justify-center mb-4">
            <div className="w-3 h-3 bg-blue-500 rounded-full animate-bounce"></div>
            <div className="w-3 h-3 bg-blue-500 rounded-full animate-bounce" style={{ animationDelay: '0.2s' }}></div>
            <div className="w-3 h-3 bg-blue-500 rounded-full animate-bounce" style={{ animationDelay: '0.4s' }}></div>
          </div>
          <p className="text-slate-400">Loading job...</p>
        </div>
      </div>
    );
  }

  if (!job || error) {
    return (
      <div className="min-h-screen bg-slate-950 text-white">
        <Navigation />
        <main className="max-w-6xl mx-auto px-4 py-12">
          <div className="bg-red-900/20 border border-red-700 rounded-lg p-6">
            <p className="text-red-300">{error || 'Job not found'}</p>
            <Link href="/dashboard" className="text-blue-400 hover:text-blue-300 mt-4 inline-block">
              ← Back to Dashboard
            </Link>
          </div>
        </main>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-slate-950 text-white">
      <Navigation />

      <main className="max-w-6xl mx-auto px-4 py-12">
        {/* Breadcrumb */}
        <div className="mb-8">
          <Link href={`/projects/${job.project.id}`} className="text-blue-400 hover:text-blue-300 text-sm">
            ← Back to {job.project.name}
          </Link>
        </div>

        {/* Job Info Card */}
        <div className="bg-slate-900 border border-slate-700 rounded-lg p-6 mb-8">
          <h1 className="text-3xl font-bold mb-4">Research in Progress</h1>
          <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
            <div>
              <p className="text-slate-400 text-sm">Query</p>
              <p className="text-white font-medium truncate">{job.query}</p>
            </div>
            <div>
              <p className="text-slate-400 text-sm">Status</p>
              <div className="flex items-center gap-2 mt-1">
                <span
                  className={`inline-block w-3 h-3 rounded-full ${
                    job.status === 'RUNNING'
                      ? 'bg-blue-500 animate-pulse'
                      : job.status === 'COMPLETE'
                        ? 'bg-green-500'
                        : job.status === 'FAILED'
                          ? 'bg-red-500'
                          : 'bg-yellow-500'
                  }`}
                />
                <span className="text-white font-medium capitalize">{job.status}</span>
              </div>
            </div>
            <div>
              <p className="text-slate-400 text-sm">Model</p>
              <p className="text-white font-medium">{job.llmProvider}</p>
            </div>
            <div>
              <p className="text-slate-400 text-sm">Depth</p>
              <p className="text-white font-medium capitalize">{job.researchDepth.toLowerCase()}</p>
            </div>
          </div>
        </div>

        {/* SSE Connection Status */}
        {job.status === 'RUNNING' && !sseConnected && (
          <div className="mb-8 p-4 bg-yellow-900/20 border border-yellow-700 rounded-lg">
            <p className="text-yellow-300 text-sm">
              {sseError ? `Connection error: ${sseError}` : 'Connecting to real-time updates...'}
            </p>
          </div>
        )}

        {/* Error Display */}
        {job.status === 'FAILED' && job.errorMessage && (
          <div className="mb-8 p-6 bg-red-900/20 border border-red-700 rounded-lg">
            <p className="text-red-400 font-medium">Research Failed</p>
            <p className="text-red-300 text-sm mt-2">{job.errorMessage}</p>
          </div>
        )}

        {/* Agent Cards Grid */}
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-5 gap-4 mb-12">
          {AGENTS.map((agent) => {
            const status = agentStatuses.get(agent);
            return (
              <AgentCard
                key={agent}
                agentName={agent}
                status={status?.status || 'idle'}
                progress={status?.progress}
                output={status?.output}
                error={status?.error}
              />
            );
          })}
        </div>

        {/* Completion Message */}
        {job.status === 'COMPLETE' && (
          <div className="text-center py-12">
            <div className="inline-block mb-4">
              <div className="w-16 h-16 bg-green-900/20 border-2 border-green-500 rounded-full flex items-center justify-center">
                <span className="text-3xl">✓</span>
              </div>
            </div>
            <h2 className="text-2xl font-bold text-white mb-2">Research Complete!</h2>
            <p className="text-slate-400 mb-6">Your research report is ready. Redirecting to report...</p>
            <Link
              href={`/jobs/${jobId}/report`}
              className="inline-block px-6 py-3 bg-blue-600 hover:bg-blue-700 text-white font-medium rounded-lg transition"
            >
              View Report Now
            </Link>
          </div>
        )}

        {/* Failure Message */}
        {job.status === 'FAILED' && (
          <div className="text-center py-12">
            <div className="inline-block mb-4">
              <div className="w-16 h-16 bg-red-900/20 border-2 border-red-500 rounded-full flex items-center justify-center">
                <span className="text-3xl">✗</span>
              </div>
            </div>
            <h2 className="text-2xl font-bold text-white mb-2">Research Failed</h2>
            <p className="text-slate-400 mb-6">An error occurred during the research process.</p>
            <Link
              href="/dashboard"
              className="inline-block px-6 py-3 bg-blue-600 hover:bg-blue-700 text-white font-medium rounded-lg transition"
            >
              Back to Dashboard
            </Link>
          </div>
        )}
      </main>
    </div>
  );
}
              <span>Duration:</span>
              <span>{(log.durationMs / 1000).toFixed(2)}s</span>
            </div>
          )}
          {log.progress && (
            <div>
              <div className="flex justify-between mb-1 text-slate-400">
                <span>Progress</span>
                <span>{log.progress}%</span>
              </div>
              <div className="w-full bg-slate-700 rounded-full h-2">
                <div
                  className="bg-blue-500 h-2 rounded-full transition-all"
                  style={{ width: `${log.progress}%` }}
                />
              </div>
            </div>
          )}
          {log.error && (
            <div className="text-red-400 text-xs mt-2 p-2 bg-red-900/20 rounded">
              {log.error}
            </div>
          )}
        </div>
      )}

      {!log && (
        <div className="mt-4">
          <div className="text-sm text-slate-500">Waiting to start...</div>
        </div>
      )}
    </div>
  );
};

export default function LiveJobPage() {
  const router = useRouter();
  const params = useParams();
  const jobId = params.id as string;

  const isAuthenticated = useAuthStore((state) => state.isAuthenticated());
  const accessToken = useAuthStore((state) => state.accessToken);

  const { data, loading, refetch } = useQuery(JOB_QUERY, {
    variables: { id: jobId },
    skip: !jobId,
    pollInterval: 2000, // Poll every 2 seconds for status updates
  });

  const {
    events,
    connected,
    error: sseError,
  } = useSSE(jobId, accessToken || "");

  useEffect(() => {
    if (!isAuthenticated) {
      router.push("/login?redirect=/dashboard");
    }
  }, [isAuthenticated, router]);

  useEffect(() => {
    // When job completes, redirect to report page
    if (data?.job?.status === "COMPLETE") {
      setTimeout(() => {
        router.push(`/jobs/${jobId}/report`);
      }, 2000);
    }
  }, [data?.job?.status, jobId, router]);

  if (!isAuthenticated) {
    return null;
  }

  const job = data?.job;
  const agentLogs = job?.agentLogs || [];

  return (
    <div className="min-h-screen bg-slate-900">
      {/* Header */}
      <header className="bg-slate-800 border-b border-slate-700 sticky top-0 z-50">
        <div className="max-w-6xl mx-auto px-6 py-4">
          <Link
            href="/dashboard"
            className="text-blue-400 hover:text-blue-300 text-sm mb-2 block"
          >
            ← Back to Dashboard
          </Link>
          <h1 className="text-2xl font-bold text-white">
            Research in Progress
          </h1>
        </div>
      </header>

      {/* Main Content */}
      <main className="max-w-6xl mx-auto px-6 py-8">
        {/* Job Info Card */}
        <div className="bg-slate-800 rounded-lg border border-slate-700 p-6 mb-8">
          <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
            <div>
              <p className="text-slate-400 text-sm">Query</p>
              <p className="text-white font-medium truncate">
                {job?.query || "Loading..."}
              </p>
            </div>
            <div>
              <p className="text-slate-400 text-sm">Status</p>
              <div className="flex items-center gap-2 mt-1">
                <span
                  className={`inline-block w-2 h-2 rounded-full ${
                    job?.status === "RUNNING"
                      ? "bg-blue-500 animate-pulse"
                      : job?.status === "COMPLETE"
                        ? "bg-green-500"
                        : "bg-yellow-500"
                  }`}
                />
                <span className="text-white font-medium">
                  {job?.status || "Loading"}
                </span>
              </div>
            </div>
            <div>
              <p className="text-slate-400 text-sm">Provider</p>
              <p className="text-white font-medium">{job?.llmProvider}</p>
            </div>
            <div>
              <p className="text-slate-400 text-sm">Depth</p>
              <p className="text-white font-medium">{job?.researchDepth}</p>
            </div>
          </div>
        </div>

        {/* Overall Progress */}
        {job?.status === "RUNNING" && (
          <div className="mb-8 bg-slate-800 rounded-lg border border-slate-700 p-6">
            <p className="text-slate-300 text-sm mb-3">Overall Progress</p>
            <div className="w-full bg-slate-700 rounded-full h-3">
              <div
                className="bg-gradient-to-r from-blue-500 to-purple-500 h-3 rounded-full transition-all"
                style={{ width: `${job?.progress || 0}%` }}
              />
            </div>
            <p className="text-slate-400 text-xs mt-2">
              {job?.progress || 0}% complete
            </p>
          </div>
        )}

        {/* Error Message */}
        {job?.status === "FAILED" && job?.errorMessage && (
          <div className="mb-8 p-6 bg-red-900/20 border border-red-700 rounded-lg">
            <p className="text-red-400 font-medium">Job Failed</p>
            <p className="text-red-300 text-sm mt-2">{job?.errorMessage}</p>
          </div>
        )}

        {/* SSE Connection Status */}
        {!connected && job?.status === "RUNNING" && (
          <div className="mb-8 p-4 bg-yellow-900/20 border border-yellow-700 rounded-lg">
            <p className="text-yellow-400 text-sm">
              {sseError
                ? `Connection error: ${sseError}`
                : "Attempting to connect to real-time updates..."}
            </p>
          </div>
        )}

        {/* Agent Cards */}
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-5 gap-4 mb-8">
          {AGENTS.map((agent) => {
            const agentLog = agentLogs.find((log: any) => log.agent === agent);
            return <AgentCard key={agent} agent={agent} log={agentLog} />;
          })}
        </div>

        {/* Completion Message */}
        {job?.status === "COMPLETE" && (
          <div className="text-center py-12">
            <div className="inline-block mb-4">
              <div className="w-16 h-16 bg-green-900/20 border-2 border-green-500 rounded-full flex items-center justify-center">
                <span className="text-3xl">✓</span>
              </div>
            </div>
            <h2 className="text-2xl font-bold text-white mb-2">
              Research Complete!
            </h2>
            <p className="text-slate-400 mb-6">
              Your research report is ready. Redirecting...
            </p>
            <Link
              href={`/jobs/${jobId}/report`}
              className="inline-block px-6 py-3 bg-blue-600 hover:bg-blue-700 text-white font-medium rounded-lg transition"
            >
              View Report
            </Link>
          </div>
        )}

        {/* Failure Message */}
        {job?.status === "FAILED" && (
          <div className="text-center py-12">
            <div className="inline-block mb-4">
              <div className="w-16 h-16 bg-red-900/20 border-2 border-red-500 rounded-full flex items-center justify-center">
                <span className="text-3xl">✗</span>
              </div>
            </div>
            <h2 className="text-2xl font-bold text-white mb-2">
              Research Failed
            </h2>
            <p className="text-slate-400 mb-6">
              An error occurred during research processing.
            </p>
            <Link
              href="/dashboard"
              className="inline-block px-6 py-3 bg-blue-600 hover:bg-blue-700 text-white font-medium rounded-lg transition"
            >
              Back to Dashboard
            </Link>
          </div>
        )}

        {/* Real-time Events Log (Debug) */}
        {events.length > 0 && (
          <div className="mt-12">
            <h3 className="text-lg font-semibold text-white mb-4">Event Log</h3>
            <div className="space-y-2 max-h-64 overflow-y-auto">
              {events
                .slice()
                .reverse()
                .map((event, idx) => (
                  <div
                    key={idx}
                    className="text-xs text-slate-400 font-mono p-2 bg-slate-800 rounded"
                  >
                    {JSON.stringify(event)}
                  </div>
                ))}
            </div>
          </div>
        )}
      </main>
    </div>
  );
}
