"use client";

import { useState, useEffect } from "react";
import { useRouter, useParams } from "next/navigation";
import { useAuthStore } from "@/hooks/useAuth";
import { gql, useMutation, useQuery } from "@apollo/client";
import Link from "next/link";

const SUBMIT_JOB_MUTATION = gql`
  mutation SubmitResearchJob(
    $projectId: String!
    $query: String!
    $llmProvider: String
    $researchDepth: String
    $documentIds: [String!]
  ) {
    submitResearchJob(
      projectId: $projectId
      query: $query
      llmProvider: $llmProvider
      researchDepth: $researchDepth
      documentIds: $documentIds
    ) {
      id
      query
      status
      progress
      createdAt
    }
  }
`;

const PROJECT_QUERY = gql`
  query GetProject($id: String!) {
    project(id: $id) {
      id
      name
      documents {
        id
        filename
        status
        mimeType
      }
    }
  }
`;

export default function ResearchFormPage() {
  const router = useRouter();
  const params = useParams();
  const projectId = params.id as string;

  const isAuthenticated = useAuthStore((state) => state.isAuthenticated());
  const [query, setQuery] = useState("");
  const [llmProvider, setLlmProvider] = useState("BEDROCK");
  const [researchDepth, setResearchDepth] = useState("STANDARD");
  const [selectedDocs, setSelectedDocs] = useState<string[]>([]);
  const [error, setError] = useState("");
  const [isLoading, setIsLoading] = useState(false);

  const { data: projectData } = useQuery(PROJECT_QUERY, {
    variables: { id: projectId },
    skip: !projectId,
  });

  const [submitJob] = useMutation(SUBMIT_JOB_MUTATION, {
    onCompleted: (data) => {
      router.push(`/jobs/${data.submitResearchJob.id}/live`);
    },
    onError: (err) => {
      setError(err.message || "Failed to submit research job");
      setIsLoading(false);
    },
  });

  useEffect(() => {
    if (!isAuthenticated) {
      router.push("/login?redirect=/dashboard");
    }
  }, [isAuthenticated, router]);

  if (!isAuthenticated) {
    return null;
  }

  const project = projectData?.project;
  const availableDocs =
    project?.documents?.filter((doc: any) => doc.status === "COMPLETE") || [];

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");
    setIsLoading(true);

    // Validation
    if (!query.trim() || query.length < 10 || query.length > 500) {
      setError("Research query must be between 10 and 500 characters");
      setIsLoading(false);
      return;
    }

    if (selectedDocs.length > 5) {
      setError("Maximum 5 documents can be selected");
      setIsLoading(false);
      return;
    }

    try {
      await submitJob({
        variables: {
          projectId,
          query: query.trim(),
          llmProvider,
          researchDepth,
          documentIds: selectedDocs,
        },
      });
    } catch (err) {
      // Error handled in onError callback
    }
  };

  const queryLength = query.length;
  const isQueryValid = queryLength >= 10 && queryLength <= 500;

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
            <span className="text-white font-medium">
              {project?.name || "Loading..."}
            </span>
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
                disabled={isLoading}
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
                disabled={isLoading}
              >
                <option value="BEDROCK">AWS Bedrock (Claude 3.5)</option>
                <option value="OPENAI">OpenAI (GPT-4o)</option>
                <option value="MOCK">Mock (Development)</option>
              </select>
              <p className="text-xs text-slate-500 mt-2">
                {llmProvider === "MOCK"
                  ? "Uses deterministic mock LLM for fast development"
                  : "Uses real LLM model for production-quality results"}
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
                      disabled={isLoading}
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
            {availableDocs.length > 0 && (
              <div>
                <label className="block text-sm font-medium text-slate-300 mb-3">
                  Reference Documents (Optional, max 5)
                </label>
                <div className="space-y-2 max-h-48 overflow-y-auto">
                  {availableDocs.map((doc: any) => (
                    <label
                      key={doc.id}
                      className="flex items-center p-3 bg-slate-700/50 border border-slate-600 rounded-lg cursor-pointer hover:border-blue-500 transition"
                    >
                      <input
                        type="checkbox"
                        checked={selectedDocs.includes(doc.id)}
                        onChange={(e) => {
                          if (e.target.checked && selectedDocs.length < 5) {
                            setSelectedDocs([...selectedDocs, doc.id]);
                          } else if (!e.target.checked) {
                            setSelectedDocs(
                              selectedDocs.filter((id) => id !== doc.id),
                            );
                          }
                        }}
                        disabled={
                          isLoading ||
                          (selectedDocs.length >= 5 &&
                            !selectedDocs.includes(doc.id))
                        }
                        className="w-4 h-4"
                      />
                      <div className="ml-3 flex-1">
                        <p className="text-white text-sm">{doc.filename}</p>
                        <p className="text-slate-500 text-xs">{doc.mimeType}</p>
                      </div>
                    </label>
                  ))}
                </div>
                <p className="text-xs text-slate-500 mt-2">
                  {selectedDocs.length} / 5 documents selected
                </p>
              </div>
            )}

            {/* Submit Button */}
            <div className="flex gap-4 pt-4">
              <button
                type="submit"
                disabled={isLoading || !isQueryValid}
                className="flex-1 py-3 bg-blue-600 hover:bg-blue-700 disabled:bg-slate-600 text-white font-medium rounded-lg transition"
              >
                {isLoading ? "Submitting..." : "Start Research"}
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
