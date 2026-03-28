"use client";

import { useEffect, useState } from "react";
import { useRouter, useParams } from "next/navigation";
import { useAuth } from "@/hooks/useAuth";
import { useApiClient } from "@/hooks/useApiClient";
import { useSSE } from "@/hooks/useSSE";
import { Navigation } from "@/components/Navigation";
import { AgentCard } from "@/components/AgentCard";
import Link from "next/link";

interface JobData {
  id: string;
  query: string;
  status: "PENDING" | "RUNNING" | "COMPLETE" | "FAILED" | "CANCELLED";
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
  status: "idle" | "running" | "complete" | "failed";
  output?: string;
  progress?: number;
  durationMs?: number;
  error?: string;
}

const AGENTS = ["planner", "research", "analysis", "critic", "synthesizer"];

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
    new Map(AGENTS.map((agent) => [agent, { agent, status: "idle" }])),
  );
  const [completedRedirect, setCompletedRedirect] = useState(false);

  const {
    events,
    connected: sseConnected,
    error: sseError,
  } = useSSE(jobId, accessToken || "");

  useEffect(() => {
    if (!isAuthenticated) {
      router.push("/login");
    }
  }, [isAuthenticated, router]);

  useEffect(() => {
    const loadJob = async () => {
      if (!jobId) return;

      setLoading(true);
      setError(null);

      try {
        const response = await apiClient.post("/graphql", {
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
        });

        if (response.data?.job) {
          setJob(response.data.job);
        } else {
          setError("Job not found");
        }
      } catch (err: any) {
        console.error("Failed to load job:", err);
        setError(err?.message || "Failed to load job");
      } finally {
        setLoading(false);
      }
    };

    loadJob();
  }, [jobId, apiClient]);

  useEffect(() => {
    if (!jobId || !job) return;

    const pollInterval = setInterval(async () => {
      try {
        const response = await apiClient.post("/graphql", {
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
        });

        if (response.data?.job) {
          setJob((prev) => (prev ? { ...prev, ...response.data.job } : null));
        }
      } catch (err) {
        console.error("Error polling job status:", err);
      }
    }, 3000);

    return () => clearInterval(pollInterval);
  }, [jobId, job, apiClient]);

  useEffect(() => {
    if (job?.status === "COMPLETE" && !completedRedirect) {
      setCompletedRedirect(true);
      setTimeout(() => {
        router.push(`/jobs/${jobId}/report`);
      }, 2000);
    }
  }, [job?.status, jobId, router, completedRedirect]);

  useEffect(() => {
    if (!events.length) {
      return;
    }

    const latestEvent = events[events.length - 1];
    const eventData = latestEvent.data;
    const agentName = eventData?.agent;

    if (agentName && AGENTS.includes(agentName)) {
      setAgentStatuses((prev) => {
        const updated = new Map(prev);
        updated.set(agentName, {
          agent: agentName,
          status:
            eventData.status === "complete"
              ? "complete"
              : eventData.status === "failed"
                ? "failed"
                : "running",
          progress:
            typeof eventData.progress === "string"
              ? Number(eventData.progress)
              : typeof eventData.progress === "number"
                ? eventData.progress
                : undefined,
          output: eventData.partial_output || undefined,
          error: eventData.error || undefined,
        });
        return updated;
      });
    }

    if (latestEvent.type === "job_complete") {
      setAgentStatuses(
        new Map(
          AGENTS.map((agent) => [
            agent,
            { agent, status: "complete", progress: 100 },
          ]),
        ),
      );
      setJob((prev) =>
        prev
          ? {
              ...prev,
              status: "COMPLETE",
            }
          : prev,
      );
    }

    if (latestEvent.type === "job_failed") {
      setJob((prev) =>
        prev
          ? {
              ...prev,
              status: "FAILED",
              errorMessage: eventData?.error || prev.errorMessage,
            }
          : prev,
      );
    }
  }, [events]);

  if (!isAuthenticated) {
    return null;
  }

  if (loading) {
    return (
      <div className="min-h-screen bg-slate-950 flex items-center justify-center">
        <div className="text-center">
          <div className="flex gap-1 justify-center mb-4">
            <div className="w-3 h-3 bg-blue-500 rounded-full animate-bounce"></div>
            <div
              className="w-3 h-3 bg-blue-500 rounded-full animate-bounce"
              style={{ animationDelay: "0.2s" }}
            ></div>
            <div
              className="w-3 h-3 bg-blue-500 rounded-full animate-bounce"
              style={{ animationDelay: "0.4s" }}
            ></div>
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
            <p className="text-red-300">{error || "Job not found"}</p>
            <Link
              href="/dashboard"
              className="text-blue-400 hover:text-blue-300 mt-4 inline-block"
            >
              Back to Dashboard
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
        <div className="mb-8">
          <Link
            href={`/projects/${job.project.id}`}
            className="text-blue-400 hover:text-blue-300 text-sm"
          >
            Back to {job.project.name}
          </Link>
        </div>

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
                    job.status === "RUNNING"
                      ? "bg-blue-500 animate-pulse"
                      : job.status === "COMPLETE"
                        ? "bg-green-500"
                        : job.status === "FAILED"
                          ? "bg-red-500"
                          : "bg-yellow-500"
                  }`}
                />
                <span className="text-white font-medium capitalize">
                  {job.status}
                </span>
              </div>
            </div>
            <div>
              <p className="text-slate-400 text-sm">Model</p>
              <p className="text-white font-medium">{job.llmProvider}</p>
            </div>
            <div>
              <p className="text-slate-400 text-sm">Depth</p>
              <p className="text-white font-medium capitalize">
                {job.researchDepth.toLowerCase()}
              </p>
            </div>
          </div>
        </div>

        {job.status === "RUNNING" && !sseConnected && (
          <div className="mb-8 p-4 bg-yellow-900/20 border border-yellow-700 rounded-lg">
            <p className="text-yellow-300 text-sm">
              {sseError
                ? `Connection error: ${sseError}`
                : "Connecting to real-time updates..."}
            </p>
          </div>
        )}

        {job.status === "FAILED" && job.errorMessage && (
          <div className="mb-8 p-6 bg-red-900/20 border border-red-700 rounded-lg">
            <p className="text-red-400 font-medium">Research Failed</p>
            <p className="text-red-300 text-sm mt-2">{job.errorMessage}</p>
          </div>
        )}

        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-5 gap-4 mb-12">
          {AGENTS.map((agent) => {
            const status = agentStatuses.get(agent);
            return (
              <AgentCard
                key={agent}
                name={agent}
                status={status?.status || "idle"}
                progress={status?.progress}
                output={status?.output}
                error={status?.error}
              />
            );
          })}
        </div>

        {job.status === "COMPLETE" && (
          <div className="text-center py-12">
            <div className="inline-block mb-4">
              <div className="w-16 h-16 bg-green-900/20 border-2 border-green-500 rounded-full flex items-center justify-center">
                <span className="text-3xl">OK</span>
              </div>
            </div>
            <h2 className="text-2xl font-bold text-white mb-2">
              Research Complete!
            </h2>
            <p className="text-slate-400 mb-6">
              Your research report is ready. Redirecting to report...
            </p>
            <Link
              href={`/jobs/${jobId}/report`}
              className="inline-block px-6 py-3 bg-blue-600 hover:bg-blue-700 text-white font-medium rounded-lg transition"
            >
              View Report Now
            </Link>
          </div>
        )}

        {job.status === "FAILED" && (
          <div className="text-center py-12">
            <div className="inline-block mb-4">
              <div className="w-16 h-16 bg-red-900/20 border-2 border-red-500 rounded-full flex items-center justify-center">
                <span className="text-3xl">X</span>
              </div>
            </div>
            <h2 className="text-2xl font-bold text-white mb-2">
              Research Failed
            </h2>
            <p className="text-slate-400 mb-6">
              An error occurred during the research process.
            </p>
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
