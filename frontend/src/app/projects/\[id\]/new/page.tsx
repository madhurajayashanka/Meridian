"use client";

import React, { useState, useEffect } from "react";
import { useRouter, useParams } from "next/navigation";
import { useAuth } from "@/hooks/useAuth";
import { useApiClient } from "@/hooks/useApiClient";
import Navigation from "@/components/Navigation";
import Link from "next/link";

interface Document {
  id: string;
  originalFilename: string;
  status: "PROCESSING" | "READY" | "FAILED";
  chunkCount?: number;
}

export default function ResearchFormPage() {
  const router = useRouter();
  const params = useParams();
  const projectId = params.id as string;
  const { isAuthenticated, userId } = useAuth();
  const apiClient = useApiClient();

  const [query, setQuery] = useState("");
  const [llmProvider, setLlmProvider] = useState("BEDROCK");
  const [researchDepth, setResearchDepth] = useState("STANDARD");
  const [selectedDocuments, setSelectedDocuments] = useState<string[]>([]);
  const [documents, setDocuments] = useState<Document[]>([]);
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Redirect if not authenticated
  useEffect(() => {
    if (!isAuthenticated) {
      router.push("/login");
    }
  }, [isAuthenticated, router]);

  // Load documents for the project
  useEffect(() => {
    const loadDocuments = async () => {
      if (!projectId) return;

      setLoading(true);
      setError(null);

      try {
        const response = await apiClient.post("/graphql", {
          query: `
              query GetDocuments($projectId: ID!) {
                documents(projectId: $projectId) {
                  id
                  originalFilename
                  status
                  chunkCount
                }
              }
            `,
          variables: { projectId },
        });

        if (response.data?.documents) {
          setDocuments(
            response.data.documents.filter(
              (doc: Document) => doc.status === "READY",
            ),
          );
        }
      } catch (err) {
        console.error("Failed to load documents:", err);
        // Non-fatal error, allow form submission without documents
      } finally {
        setLoading(false);
      }
    };

    loadDocuments();
  }, [projectId, apiClient]);

  const handleDocumentToggle = (docId: string) => {
    setSelectedDocuments((prev) => {
      if (prev.includes(docId)) {
        return prev.filter((id) => id !== docId);
      } else if (prev.length < 5) {
        return [...prev, docId];
      }
      return prev;
    });
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    // Validation
    if (query.length < 10) {
      setError("Query must be at least 10 characters");
      return;
    }
    if (query.length > 500) {
      setError("Query must not exceed 500 characters");
      return;
    }

    if (!projectId) {
      setError("Project not found");
      return;
    }

    setSubmitting(true);

    try {
      const response = await apiClient.post("/graphql", {
        query: `
            mutation CreateResearchJob($input: CreateResearchJobInput!) {
              createResearchJob(input: $input) {
                id
                status
                createdAt
              }
            }
          `,
        variables: {
          input: {
            projectId,
            query,
            llmProvider,
            researchDepth,
            documentIds: selectedDocuments,
          },
        },
      });

      if (response.data?.createResearchJob?.id) {
        const jobId = response.data.createResearchJob.id;
        router.push(`/jobs/${jobId}/live`);
      } else {
        setError("Failed to create research job");
      }
    } catch (err: any) {
      console.error("Research submission error:", err);
      setError(err?.message || "Failed to submit research job");
    } finally {
      setSubmitting(false);
    }
  };

  if (!isAuthenticated) {
    return null;
  }

  return (
    <div className="min-h-screen bg-slate-950 text-white">
      <Navigation />

      <main className="max-w-2xl mx-auto px-4 py-12">
        <div className="mb-8">
          <Link
            href={`/projects/${projectId}`}
            className="text-blue-400 hover:text-blue-300"
          >
            ← Back to Project
          </Link>
        </div>

        <div className="bg-slate-900 rounded-lg border border-slate-700 p-8">
          <h1 className="text-3xl font-bold mb-2">New Research Job</h1>
          <p className="text-slate-400 mb-8">
            Use the AI agents to conduct research on any topic
          </p>

          {error && (
            <div className="bg-red-900 border border-red-700 rounded-lg p-4 mb-6">
              <p className="text-red-200">{error}</p>
            </div>
          )}

          <form onSubmit={handleSubmit} className="space-y-6">
            {/* Query Input */}
            <div>
              <label className="block text-sm font-medium mb-2">
                Research Query
              </label>
              <div className="relative">
                <textarea
                  value={query}
                  onChange={(e) => setQuery(e.target.value)}
                  maxLength={500}
                  rows={4}
                  placeholder="Enter your research question (10-500 characters)..."
                  className="w-full bg-slate-800 border border-slate-700 rounded-lg px-4 py-3 text-white placeholder-slate-500 focus:outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500"
                />
              </div>
              <div className="flex justify-between text-xs text-slate-400 mt-1">
                <span>Character count: {query.length}/500</span>
                {query.length < 10 && (
                  <span className="text-red-400">
                    Minimum 10 characters required
                  </span>
                )}
              </div>
            </div>

            {/* LLM Provider Select */}
            <div>
              <label className="block text-sm font-medium mb-2">
                LLM Provider
              </label>
              <select
                value={llmProvider}
                onChange={(e) => setLlmProvider(e.target.value)}
                className="w-full bg-slate-800 border border-slate-700 rounded-lg px-4 py-3 text-white focus:outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500"
              >
                <option value="BEDROCK">AWS Bedrock (Claude 3.5 Sonnet)</option>
                <option value="OPENAI">OpenAI (GPT-4o)</option>
              </select>
              <p className="text-xs text-slate-400 mt-1">
                Choose the LLM model for research synthesis
              </p>
            </div>

            {/* Research Depth Select */}
            <div>
              <label className="block text-sm font-medium mb-2">
                Research Depth
              </label>
              <select
                value={researchDepth}
                onChange={(e) => setResearchDepth(e.target.value)}
                className="w-full bg-slate-800 border border-slate-700 rounded-lg px-4 py-3 text-white focus:outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500"
              >
                <option value="QUICK">Quick (1 agent, ~5 min)</option>
                <option value="STANDARD">Standard (3 agents, ~10 min)</option>
                <option value="DEEP">
                  Deep (5 agents with critiques, ~20 min)
                </option>
              </select>
              <p className="text-xs text-slate-400 mt-1">
                More depth = longer processing but more thorough research
              </p>
            </div>

            {/* Document Selection */}
            {documents.length > 0 && (
              <div>
                <label className="block text-sm font-medium mb-2">
                  Documents (Optional)
                </label>
                <p className="text-xs text-slate-400 mb-3">
                  Select up to 5 documents to augment the research
                </p>
                <div className="space-y-2 max-h-48 overflow-y-auto bg-slate-800 border border-slate-700 rounded-lg p-3">
                  {documents.map((doc) => (
                    <label
                      key={doc.id}
                      className="flex items-center gap-3 cursor-pointer hover:bg-slate-700 p-2 rounded"
                    >
                      <input
                        type="checkbox"
                        checked={selectedDocuments.includes(doc.id)}
                        onChange={() => handleDocumentToggle(doc.id)}
                        disabled={
                          !selectedDocuments.includes(doc.id) &&
                          selectedDocuments.length >= 5
                        }
                        className="w-4 h-4 rounded border-slate-600 text-blue-500 cursor-pointer"
                      />
                      <div className="flex-1">
                        <span className="text-sm">{doc.originalFilename}</span>
                        {doc.chunkCount && (
                          <span className="text-xs text-slate-400 ml-2">
                            ({doc.chunkCount} chunks)
                          </span>
                        )}
                      </div>
                    </label>
                  ))}
                </div>
                <p className="text-xs text-slate-400 mt-2">
                  Selected: {selectedDocuments.length}/5
                </p>
              </div>
            )}

            {/* Submit Button */}
            <div className="flex gap-3 pt-4">
              <button
                type="submit"
                disabled={submitting || query.length < 10}
                className="flex-1 bg-blue-600 hover:bg-blue-700 disabled:bg-slate-700 disabled:cursor-not-allowed text-white font-medium py-3 px-4 rounded-lg transition"
              >
                {submitting ? "Submitting..." : "Start Research"}
              </button>
              <Link
                href={`/projects/${projectId}`}
                className="bg-slate-800 hover:bg-slate-700 text-white font-medium py-3 px-4 rounded-lg transition text-center"
              >
                Cancel
              </Link>
            </div>
          </form>
        </div>
      </main>
    </div>
  );
}
