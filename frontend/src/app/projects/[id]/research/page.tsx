"use client";

import { useState, useEffect, type FormEvent } from "react";
import { useRouter, useParams } from "next/navigation";
import { useAuth } from "@/hooks/useAuth";
import { useApiClient } from "@/hooks/useApiClient";
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
  const { isAuthenticated, isReady } = useAuth();
  const { post } = useApiClient();

  const [query, setQuery] = useState("");
  const [llmProvider, setLlmProvider] = useState("MOCK");
  const [researchDepth, setResearchDepth] = useState("STANDARD");
  const [selectedDocuments, setSelectedDocuments] = useState<string[]>([]);
  const [documents, setDocuments] = useState<Document[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (isReady && !isAuthenticated) {
      router.push("/login?redirect=/dashboard");
    }
  }, [isAuthenticated, isReady, router]);

  useEffect(() => {
    const loadDocuments = async () => {
      if (!projectId || !isAuthenticated) {
        return;
      }

      setError(null);

      try {
        const response = await post("/graphql", {
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

        if (response?.data?.documents) {
          setDocuments(
            response.data.documents.filter(
              (doc: Document) => doc.status === "READY",
            ),
          );
        }
      } catch (err) {
        console.error("Failed to load documents:", err);
      }
    };

    loadDocuments();
  }, [projectId, isAuthenticated, post]);

  if (!isAuthenticated) {
    return null;
  }

  const handleDocumentToggle = (docId: string) => {
    setSelectedDocuments((prev) => {
      if (prev.includes(docId)) {
        return prev.filter((id) => id !== docId);
      }
      if (prev.length < 5) {
        return [...prev, docId];
      }
      return prev;
    });
  };

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);

    // Validation
    if (!query.trim() || query.length < 10) {
      setError("Research query must be at least 10 characters");
      return;
    }

    if (query.length > 500) {
      setError("Research query must be 500 characters or fewer");
      return;
    }

    if (selectedDocuments.length > 5) {
      setError("Maximum 5 documents can be selected");
      return;
    }

    if (!projectId) {
      setError("Project not found");
      return;
    }

    setSubmitting(true);

    try {
      const response = await post("/graphql", {
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
            query: query.trim(),
            llmProvider,
            researchDepth,
            documentIds: selectedDocuments,
          },
        },
      });

      if (response?.data?.createResearchJob?.id) {
        router.push(`/jobs/${response.data.createResearchJob.id}/live`);
      } else if (response?.errors?.length) {
        setError(
          response.errors[0]?.message || "Failed to create research job",
        );
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

  const queryLength = query.length;
  const isQueryValid = queryLength >= 10 && queryLength <= 500;

  if (!isReady || !isAuthenticated) {
    return null;
  }

  return (
    <div className="min-h-screen bg-slate-900">
      {/* Header */}
      <header className="bg-slate-800 border-b border-slate-700 sticky top-0 z-50">
        <div className="max-w-4xl mx-auto px-6 py-4 flex justify-between items-center">
          <div className="flex items-center gap-4">
            <Link
              href="/dashboard"
              className="text-blue-400 hover:text-blue-300"
            >
              ← Projects
            </Link>
            <span className="text-slate-500">/</span>
            <span className="text-white font-medium">Research</span>
          </div>
        </div>
      </header>

      {/* Main Content */}
      <main className="max-w-4xl mx-auto px-6 py-12">
        <div className="bg-slate-800 rounded-lg border border-slate-700 p-8">
          <h1 className="text-3xl font-bold text-white mb-2">
            Submit Research Query
          </h1>
          <p className="text-slate-400 mb-8">
            Ask anything. Our AI will research comprehensively and synthesize
            findings.
          </p>

          {/* Error Message */}
          {error && (
            <div className="mb-6 p-4 bg-red-900/20 border border-red-700 rounded-lg">
              <p className="text-red-400 text-sm">{error}</p>
            </div>
          )}

          <form onSubmit={handleSubmit} className="space-y-8">
            {/* Research Query */}
            <div>
              <label
                htmlFor="query"
                className="block text-sm font-medium text-slate-300 mb-3"
              >
                Research Query
              </label>
              <textarea
                id="query"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                placeholder="What would you like to research? E.g., 'What are the latest advances in quantum computing?'"
                rows={5}
                className="w-full px-4 py-3 bg-slate-700 border border-slate-600 rounded-lg text-white placeholder-slate-500 focus:outline-none focus:border-blue-500 transition resize-none"
                disabled={submitting}
              />
              <div className="mt-2 flex justify-between items-center">
                <p
                  className={`text-xs ${queryLength < 10 ? "text-red-400" : "text-slate-400"}`}
                >
                  {queryLength} / 500 characters
                </p>
                {!isQueryValid && queryLength > 0 && (
                  <p className="text-red-400 text-xs">
                    {queryLength < 10
                      ? "Minimum 10 characters required"
                      : "Maximum 500 characters allowed"}
                  </p>
                )}
              </div>
            </div>

            {/* LLM Provider */}
            <div>
              <label
                htmlFor="provider"
                className="block text-sm font-medium text-slate-300 mb-3"
              >
                AI Provider
              </label>
              <select
                id="provider"
                value={llmProvider}
                onChange={(e) => setLlmProvider(e.target.value)}
                className="w-full px-4 py-2 bg-slate-700 border border-slate-600 rounded-lg text-white focus:outline-none focus:border-blue-500 transition"
                disabled={submitting}
              >
                <option value="MOCK">
                  Mock (Local development, no keys required)
                </option>
                <option value="BEDROCK">AWS Bedrock (Claude 3.5)</option>
                <option value="OPENAI">OpenAI (GPT-4o)</option>
              </select>
              <p className="text-xs text-slate-500 mt-2">
                Use Mock for local development without AWS or OpenAI
                credentials.
              </p>
            </div>

            {/* Research Depth */}
            <div>
              <label
                htmlFor="depth"
                className="block text-sm font-medium text-slate-300 mb-3"
              >
                Research Depth
              </label>
              <div className="space-y-3">
                {[
                  {
                    value: "QUICK",
                    label: "Quick (1 revision loop)",
                    desc: "Fast, less thorough",
                  },
                  {
                    value: "STANDARD",
                    label: "Standard (2 revision loops)",
                    desc: "Balanced speed & quality",
                  },
                  {
                    value: "DEEP",
                    label: "Deep (3 revision loops)",
                    desc: "Thorough, slower",
                  },
                ].map((option) => (
                  <label
                    key={option.value}
                    className={`flex items-center p-3 border rounded-lg cursor-pointer transition ${
                      researchDepth === option.value
                        ? "bg-blue-900/20 border-blue-500"
                        : "bg-slate-700/50 border-slate-600 hover:border-slate-500"
                    }`}
                  >
                    <input
                      type="radio"
                      name="depth"
                      value={option.value}
                      checked={researchDepth === option.value}
                      onChange={(e) => setResearchDepth(e.target.value)}
                      disabled={submitting}
                      className="w-4 h-4"
                    />
                    <div className="ml-3">
                      <p className="text-white font-medium">{option.label}</p>
                      <p className="text-slate-400 text-sm">{option.desc}</p>
                    </div>
                  </label>
                ))}
              </div>
            </div>

            {/* Document Selection */}
            {documents.length > 0 && (
              <div>
                <label className="block text-sm font-medium text-slate-300 mb-3">
                  Reference Documents (Optional, max 5)
                </label>
                <div className="space-y-2 max-h-48 overflow-y-auto">
                  {documents.map((doc) => (
                    <label
                      key={doc.id}
                      className="flex items-center p-3 bg-slate-700/50 border border-slate-600 rounded-lg cursor-pointer hover:border-blue-500 transition"
                    >
                      <input
                        type="checkbox"
                        checked={selectedDocuments.includes(doc.id)}
                        onChange={() => handleDocumentToggle(doc.id)}
                        disabled={
                          submitting ||
                          (selectedDocuments.length >= 5 &&
                            !selectedDocuments.includes(doc.id))
                        }
                        className="w-4 h-4"
                      />
                      <div className="ml-3 flex-1">
                        <p className="text-white text-sm">
                          {doc.originalFilename}
                        </p>
                        <p className="text-slate-500 text-xs">
                          {doc.chunkCount
                            ? `${doc.chunkCount} chunks`
                            : "Ready"}
                        </p>
                      </div>
                    </label>
                  ))}
                </div>
                <p className="text-xs text-slate-500 mt-2">
                  {selectedDocuments.length} / 5 documents selected
                </p>
              </div>
            )}

            {/* Submit Button */}
            <div className="flex gap-4 pt-4">
              <button
                type="submit"
                disabled={submitting || !isQueryValid}
                className="flex-1 py-3 bg-blue-600 hover:bg-blue-700 disabled:bg-slate-600 text-white font-medium rounded-lg transition"
              >
                {submitting ? "Submitting..." : "Start Research"}
              </button>
              <Link
                href={`/projects/${projectId}`}
                className="px-6 py-3 bg-slate-700 hover:bg-slate-600 text-white font-medium rounded-lg transition"
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
