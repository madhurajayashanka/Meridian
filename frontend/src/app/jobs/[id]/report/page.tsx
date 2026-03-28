"use client";

import { useState, useEffect, type ReactNode } from "react";
import { useRouter, useParams } from "next/navigation";
import { useAuth } from "@/hooks/useAuth";
import { useApiClient } from "@/hooks/useApiClient";
import { Navigation } from "@/components/Navigation";
import { ChatPanel } from "@/components/ChatPanel";
import Link from "next/link";

interface HeadingTOC {
  level: number;
  text: string;
  id: string;
}

interface Report {
  id: string;
  title: string;
  content: string | null;
  wordCount: number | null;
  citationCount: number;
  criticScore: number | null;
  revisionCount: number;
  createdAt: string;
  job: {
    id: string;
    query: string;
    status: string;
    project: {
      id: string;
      name: string;
    };
  };
}

export default function ReportPage() {
  const router = useRouter();
  const params = useParams();
  const jobId = params.id as string;

  const { isAuthenticated } = useAuth();
  const apiClient = useApiClient();

  const [report, setReport] = useState<Report | null>(null);
  const [reportContent, setReportContent] = useState<string>("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [tableOfContents, setTableOfContents] = useState<HeadingTOC[]>([]);
  const [showTOC] = useState(true);

  const formatNullableNumber = (value: number | null): string => {
    if (value === null) {
      return "N/A";
    }
    return value.toLocaleString();
  };

  useEffect(() => {
    if (!isAuthenticated) {
      router.push("/login");
    }
  }, [isAuthenticated, router]);

  useEffect(() => {
    const loadReport = async () => {
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
                project {
                  id
                  name
                }
                report {
                  id
                  title
                  content
                  wordCount
                  citationCount
                  criticScore
                  revisionCount
                  createdAt
                }
              }
            }
          `,
          variables: { id: jobId },
        });

        if (response.data?.job?.report) {
          const reportData = response.data.job.report;
          const jobData = response.data.job;

          setReport({
            ...reportData,
            job: jobData,
          });

          const content =
            reportData.content ||
            "## Report content unavailable\n\nThis job completed, but no markdown report content was persisted for it.";

          setReportContent(content);
          setTableOfContents(extractHeadings(content));
        } else {
          setError("Report not found");
        }
      } catch (err: any) {
        console.error("Failed to load report:", err);
        setError(err?.message || "Failed to load report");
      } finally {
        setLoading(false);
      }
    };

    loadReport();
  }, [jobId, apiClient]);

  const extractHeadings = (content: string): HeadingTOC[] => {
    const headings: HeadingTOC[] = [];
    const lines = content.split("\n");

    lines.forEach((line) => {
      const h2Match = line.match(/^## (.+)$/);
      const h3Match = line.match(/^### (.+)$/);

      if (h2Match) {
        const text = h2Match[1];
        headings.push({ level: 2, text, id: slugify(text) });
      } else if (h3Match) {
        const text = h3Match[1];
        headings.push({ level: 3, text, id: slugify(text) });
      }
    });

    return headings;
  };

  const slugify = (text: string): string => {
    return text
      .toLowerCase()
      .trim()
      .replace(/[^\w\s-]/g, "")
      .replace(/\s+/g, "-")
      .replace(/-+/g, "-");
  };

  const renderMarkdown = (content: string): ReactNode => {
    const paragraphs: ReactNode[] = [];
    let currentParagraph = "";
    const lines = content.split("\n");

    lines.forEach((line, index) => {
      if (line.match(/^## /)) {
        if (currentParagraph) {
          paragraphs.push(
            <p
              key={`p${index}`}
              className="text-slate-300 leading-relaxed mb-4"
            >
              {currentParagraph}
            </p>,
          );
          currentParagraph = "";
        }
        const title = line.replace(/^## /, "");
        const id = slugify(title);
        paragraphs.push(
          <h2
            key={`h2${index}`}
            id={id}
            className="text-2xl font-bold text-white mt-8 mb-4"
          >
            {title}
          </h2>,
        );
      } else if (line.match(/^### /)) {
        if (currentParagraph) {
          paragraphs.push(
            <p
              key={`p${index}`}
              className="text-slate-300 leading-relaxed mb-4"
            >
              {currentParagraph}
            </p>,
          );
          currentParagraph = "";
        }
        const title = line.replace(/^### /, "");
        const id = slugify(title);
        paragraphs.push(
          <h3
            key={`h3${index}`}
            id={id}
            className="text-lg font-semibold text-white mt-6 mb-3"
          >
            {title}
          </h3>,
        );
      } else if (line.match(/^- /)) {
        if (currentParagraph) {
          paragraphs.push(
            <p
              key={`p${index}`}
              className="text-slate-300 leading-relaxed mb-4"
            >
              {currentParagraph}
            </p>,
          );
          currentParagraph = "";
        }
        const item = line.replace(/^- /, "");
        paragraphs.push(
          <li key={`li${index}`} className="text-slate-300 ml-6 mb-2 list-disc">
            {item}
          </li>,
        );
      } else if (line.trim()) {
        currentParagraph += (currentParagraph ? " " : "") + line;
      } else if (currentParagraph) {
        paragraphs.push(
          <p key={`p${index}`} className="text-slate-300 leading-relaxed mb-4">
            {currentParagraph}
          </p>,
        );
        currentParagraph = "";
      }
    });

    if (currentParagraph) {
      paragraphs.push(
        <p key="pfinal" className="text-slate-300 leading-relaxed mb-4">
          {currentParagraph}
        </p>,
      );
    }

    return <div>{paragraphs}</div>;
  };

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
          <p className="text-slate-400">Loading report...</p>
        </div>
      </div>
    );
  }

  if (!report || error) {
    return (
      <div className="min-h-screen bg-slate-950 text-white">
        <Navigation />
        <main className="max-w-6xl mx-auto px-4 py-12">
          <div className="bg-red-900/20 border border-red-700 rounded-lg p-6">
            <p className="text-red-300">{error || "Report not found"}</p>
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

      <main className="max-w-7xl mx-auto px-4 py-12">
        <div className="mb-8">
          <Link
            href={`/projects/${report.job.project.id}`}
            className="text-blue-400 hover:text-blue-300 text-sm"
          >
            Back to {report.job.project.name}
          </Link>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-4 gap-8">
          {showTOC && tableOfContents.length > 0 && (
            <aside className="lg:block hidden">
              <div className="bg-slate-900 border border-slate-700 rounded-lg p-6 sticky top-24">
                <h3 className="text-sm font-semibold text-white mb-4 uppercase tracking-wide">
                  Contents
                </h3>
                <nav className="space-y-2">
                  {tableOfContents.map((heading) => (
                    <a
                      key={heading.id}
                      href={`#${heading.id}`}
                      className={`block text-sm transition hover:text-blue-400 ${
                        heading.level === 3
                          ? "ml-4 text-slate-400"
                          : "text-slate-300 font-medium"
                      }`}
                    >
                      {heading.text}
                    </a>
                  ))}
                </nav>
              </div>
            </aside>
          )}

          <div className="lg:col-span-3">
            <div className="bg-slate-900 border border-slate-700 rounded-lg p-8 mb-8">
              <h1 className="text-3xl font-bold text-white mb-4">
                Research Report
              </h1>
              <p className="text-slate-400 mb-6">{report.job.query}</p>

              <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-6 py-6 border-t border-b border-slate-700">
                <div>
                  <p className="text-slate-400 text-sm">Words</p>
                  <p className="text-white font-medium">
                    {formatNullableNumber(report.wordCount)}
                  </p>
                </div>
                <div>
                  <p className="text-slate-400 text-sm">Citations</p>
                  <p className="text-white font-medium">
                    {report.citationCount}
                  </p>
                </div>
                <div>
                  <p className="text-slate-400 text-sm">Quality Score</p>
                  <div className="flex items-center gap-2">
                    <div className="w-24 h-2 bg-slate-700 rounded-full overflow-hidden">
                      <div
                        className={`h-full ${
                          report.criticScore === null
                            ? "bg-slate-500"
                            : report.criticScore >= 8
                              ? "bg-green-500"
                              : report.criticScore >= 6
                                ? "bg-yellow-500"
                                : "bg-red-500"
                        }`}
                        style={{
                          width: `${report.criticScore === null ? 0 : (report.criticScore / 10) * 100}%`,
                        }}
                      />
                    </div>
                    <span className="text-white font-medium">
                      {report.criticScore === null
                        ? "N/A"
                        : `${report.criticScore.toFixed(1)}/10`}
                    </span>
                  </div>
                </div>
                <div>
                  <p className="text-slate-400 text-sm">Revisions</p>
                  <p className="text-white font-medium">
                    {report.revisionCount}
                  </p>
                </div>
              </div>
            </div>

            <article className="max-w-none mb-12">
              {renderMarkdown(reportContent)}
            </article>

            <div className="mt-16">
              <h2 className="text-2xl font-bold text-white mb-6">
                Ask Questions About This Report
              </h2>
              <ChatPanel reportId={report.id} />
            </div>
          </div>
        </div>
      </main>
    </div>
  );
}
