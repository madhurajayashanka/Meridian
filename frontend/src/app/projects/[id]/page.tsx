"use client";

import { useEffect, useMemo, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import Link from "next/link";
import { Navigation } from "@/components/Navigation";
import { useAuth } from "@/hooks/useAuth";
import { useApiClient } from "@/hooks/useApiClient";

interface ProjectData {
  id: string;
  name: string;
  description?: string;
  createdAt: string;
  updatedAt: string;
  jobCount: number;
  lastActivityAt?: string;
}

interface JobData {
  id: string;
  query: string;
  status: "PENDING" | "RUNNING" | "COMPLETE" | "FAILED" | "CANCELLED";
  createdAt: string;
  llmProvider: string;
  report?: {
    id: string;
  };
}

interface DocumentData {
  id: string;
  originalFilename: string;
  status: "PROCESSING" | "READY" | "FAILED";
  chunkCount?: number;
  createdAt: string;
}

export default function ProjectDetailsPage() {
  const params = useParams();
  const projectId = params.id as string;
  const router = useRouter();

  const { isAuthenticated, isReady } = useAuth();
  const { post } = useApiClient();

  const [project, setProject] = useState<ProjectData | null>(null);
  const [jobs, setJobs] = useState<JobData[]>([]);
  const [documents, setDocuments] = useState<DocumentData[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (isReady && !isAuthenticated) {
      router.push("/login?redirect=/dashboard");
    }
  }, [isAuthenticated, isReady, router]);

  useEffect(() => {
    const loadProjectData = async () => {
      if (!projectId || !isAuthenticated) return;

      setLoading(true);
      setError(null);

      try {
        const response = await post("/graphql", {
          query: `
            query GetProjectPage($id: ID!) {
              project(id: $id) {
                id
                name
                description
                createdAt
                updatedAt
                jobCount
                lastActivityAt
              }
              jobs(projectId: $id) {
                id
                query
                status
                createdAt
                llmProvider
                report {
                  id
                }
              }
              documents(projectId: $id) {
                id
                originalFilename
                status
                chunkCount
                createdAt
              }
            }
          `,
          variables: { id: projectId },
        });

        if (!response?.data?.project) {
          setProject(null);
          setJobs([]);
          setDocuments([]);
          setError("Project not found");
          return;
        }

        setProject(response.data.project);
        setJobs(response.data.jobs || []);
        setDocuments(response.data.documents || []);
      } catch (err: any) {
        console.error("Failed to load project page:", err);
        setError(err?.message || "Failed to load project");
      } finally {
        setLoading(false);
      }
    };

    loadProjectData();
  }, [projectId, isAuthenticated, post]);

  const sortedJobs = useMemo(
    () =>
      [...jobs].sort((a, b) => +new Date(b.createdAt) - +new Date(a.createdAt)),
    [jobs],
  );

  const sortedDocuments = useMemo(
    () =>
      [...documents].sort(
        (a, b) => +new Date(b.createdAt) - +new Date(a.createdAt),
      ),
    [documents],
  );

  const formatDate = (iso?: string) => {
    if (!iso) return "-";
    return new Date(iso).toLocaleString();
  };

  if (!isReady || !isAuthenticated) {
    return null;
  }

  if (loading) {
    return (
      <div className="min-h-screen bg-slate-950 text-white">
        <Navigation />
        <main className="max-w-6xl mx-auto px-4 py-12">
          <p className="text-slate-400">Loading project...</p>
        </main>
      </div>
    );
  }

  if (!project || error) {
    return (
      <div className="min-h-screen bg-slate-950 text-white">
        <Navigation />
        <main className="max-w-6xl mx-auto px-4 py-12">
          <div className="bg-red-900/20 border border-red-700 rounded-lg p-6">
            <p className="text-red-300">{error || "Project not found"}</p>
            <Link
              href="/dashboard"
              className="inline-block mt-4 text-blue-400 hover:text-blue-300"
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

      <main className="max-w-6xl mx-auto px-4 py-12 space-y-8">
        <section className="flex flex-col gap-4 md:flex-row md:items-start md:justify-between">
          <div>
            <Link
              href="/dashboard"
              className="text-blue-400 hover:text-blue-300 text-sm"
            >
              Back to Dashboard
            </Link>
            <h1 className="text-3xl font-bold mt-2">{project.name}</h1>
            {project.description && (
              <p className="text-slate-400 mt-2 max-w-2xl">
                {project.description}
              </p>
            )}
            <p className="text-slate-500 text-sm mt-3">
              {project.jobCount} jobs • Last activity:{" "}
              {formatDate(project.lastActivityAt)}
            </p>
          </div>

          <div className="flex gap-3">
            <Link
              href={`/projects/${project.id}/research`}
              className="bg-blue-600 hover:bg-blue-700 text-white font-medium py-2 px-4 rounded-lg transition"
            >
              Start Research
            </Link>
            <Link
              href={`/projects/${project.id}/new`}
              className="bg-slate-800 hover:bg-slate-700 text-white font-medium py-2 px-4 rounded-lg border border-slate-700 transition"
            >
              Advanced Setup
            </Link>
          </div>
        </section>

        <section className="bg-slate-900 border border-slate-700 rounded-lg p-6">
          <h2 className="text-xl font-semibold mb-4">Research Jobs</h2>

          {sortedJobs.length === 0 ? (
            <p className="text-slate-400">
              No jobs yet. Start your first research run.
            </p>
          ) : (
            <div className="space-y-3">
              {sortedJobs.map((job) => (
                <div
                  key={job.id}
                  className="flex flex-col gap-3 rounded-lg border border-slate-700 bg-slate-800/50 p-4 md:flex-row md:items-center md:justify-between"
                >
                  <div className="min-w-0">
                    <p className="font-medium truncate">{job.query}</p>
                    <p className="text-xs text-slate-400 mt-1">
                      {job.status} • {job.llmProvider} •{" "}
                      {formatDate(job.createdAt)}
                    </p>
                  </div>

                  <Link
                    href={
                      job.status === "COMPLETE" && job.report?.id
                        ? `/jobs/${job.id}/report`
                        : `/jobs/${job.id}/live`
                    }
                    className="text-blue-400 hover:text-blue-300 text-sm whitespace-nowrap"
                  >
                    Open job
                  </Link>
                </div>
              ))}
            </div>
          )}
        </section>

        <section className="bg-slate-900 border border-slate-700 rounded-lg p-6">
          <h2 className="text-xl font-semibold mb-4">Documents</h2>

          {sortedDocuments.length === 0 ? (
            <p className="text-slate-400">
              No documents uploaded for this project yet.
            </p>
          ) : (
            <div className="space-y-3">
              {sortedDocuments.map((document) => (
                <div
                  key={document.id}
                  className="flex items-center justify-between rounded-lg border border-slate-700 bg-slate-800/50 p-4"
                >
                  <div className="min-w-0">
                    <p className="font-medium truncate">
                      {document.originalFilename}
                    </p>
                    <p className="text-xs text-slate-400 mt-1">
                      {document.status}
                      {document.chunkCount
                        ? ` • ${document.chunkCount} chunks`
                        : ""}
                    </p>
                  </div>
                  <span className="text-xs text-slate-500 ml-3 whitespace-nowrap">
                    {formatDate(document.createdAt)}
                  </span>
                </div>
              ))}
            </div>
          )}
        </section>
      </main>
    </div>
  );
}
