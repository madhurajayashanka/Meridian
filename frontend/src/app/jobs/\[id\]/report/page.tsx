'use client';

import React, { useState, useEffect } from 'react';
import { useRouter, useParams } from 'next/navigation';
import { useAuth } from '@/hooks/useAuth';
import { useApiClient } from '@/hooks/useApiClient';
import Navigation from '@/components/Navigation';
import ChatPanel from '@/components/ChatPanel';
import Link from 'next/link';

interface HeadingTOC {
  level: number;
  text: string;
  id: string;
}

interface Report {
  id: string;
  title: string;
  wordCount: number;
  citationCount: number;
  criticScore: number;
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
  const [reportContent, setReportContent] = useState<string>('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [tableOfContents, setTableOfContents] = useState<HeadingTOC[]>([]);
  const [showTOC, setShowTOC] = useState(true);

  // Redirect if not authenticated
  useEffect(() => {
    if (!isAuthenticated) {
      router.push('/login');
    }
  }, [isAuthenticated, router]);

  // Load report
  useEffect(() => {
    const loadReport = async () => {
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
                  project {
                    id
                    name
                  }
                  report {
                    id
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
          }
        );

        if (response.data?.job?.report) {
          const reportData = response.data.job.report;
          const jobData = response.data.job;
          
          setReport({
            ...reportData,
            job: jobData,
          });

          // In a real implementation, fetch the markdown content from S3 or API
          // For now, use placeholder content
          const placeholderContent = `
## Executive Summary

This report presents comprehensive research into the query provided. The analysis was conducted using advanced AI agents working in conjunction to investigate, synthesize, and critique the findings.

## Key Findings

Based on the research conducted, the following key findings emerged:

- **Finding 1**: The research methodology employed multiple information sources
- **Finding 2**: Cross-verification was performed to ensure accuracy
- **Finding 3**: The analysis revealed interconnected patterns across domains

### Sub-finding 1.1

Additional context and supporting evidence for the key findings.

## Methodology

The research was conducted using a multi-agent system:

1. **Planning Phase**: Decomposed the research question into sub-questions
2. **Research Phase**: Gathered relevant sources and evidence
3. **Analysis Phase**: Synthesized findings into a coherent narrative
4. **Critique Phase**: Evaluated the draft for quality and accuracy
5. **Synthesis Phase**: Generated the final comprehensive report

## Conclusions

The research demonstrates that thorough investigation combined with AI-assisted analysis can produce comprehensive and reliable reports. The methodology employed ensures both breadth and depth of coverage.
`;

          setReportContent(placeholderContent);

          // Extract table of contents from H2 and H3 headers
          const headings = extractHeadings(placeholderContent);
          setTableOfContents(headings);
        } else {
          setError('Report not found');
        }
      } catch (err: any) {
        console.error('Failed to load report:', err);
        setError(err?.message || 'Failed to load report');
      } finally {
        setLoading(false);
      }
    };

    loadReport();
  }, [jobId, apiClient]);

  const extractHeadings = (content: string): HeadingTOC[] => {
    const headings: HeadingTOC[] = [];
    const lines = content.split('\n');

    lines.forEach((line) => {
      const h2Match = line.match(/^## (.+)$/);
      const h3Match = line.match(/^### (.+)$/);

      if (h2Match) {
        const text = h2Match[1];
        const id = slugify(text);
        headings.push({ level: 2, text, id });
      } else if (h3Match) {
        const text = h3Match[1];
        const id = slugify(text);
        headings.push({ level: 3, text, id });
      }
    });

    return headings;
  };

  const slugify = (text: string): string => {
    return text
      .toLowerCase()
      .trim()
      .replace(/[^\w\s-]/g, '')
      .replace(/\s+/g, '-')
      .replace(/-+/g, '-');
  };

  const renderMarkdown = (content: string): React.ReactNode => {
    const paragraphs: React.ReactNode[] = [];
    let currentParagraph = '';

    const lines = content.split('\n');

    lines.forEach((line, index) => {
      // Headings
      if (line.match(/^## /)) {
        if (currentParagraph) {
          paragraphs.push(
            <p key={`p${index}`} className="text-slate-300 leading-relaxed mb-4">
              {currentParagraph}
            </p>
          );
          currentParagraph = '';
        }
        const title = line.replace(/^## /, '');
        const id = slugify(title);
        paragraphs.push(
          <h2 key={`h2${index}`} id={id} className="text-2xl font-bold text-white mt-8 mb-4">
            {renderInlineMarkdown(title)}
          </h2>
        );
      } else if (line.match(/^### /)) {
        if (currentParagraph) {
          paragraphs.push(
            <p key={`p${index}`} className="text-slate-300 leading-relaxed mb-4">
              {currentParagraph}
            </p>
          );
          currentParagraph = '';
        }
        const title = line.replace(/^### /, '');
        const id = slugify(title);
        paragraphs.push(
          <h3 key={`h3${index}`} id={id} className="text-lg font-semibold text-white mt-6 mb-3">
            {renderInlineMarkdown(title)}
          </h3>
        );
      } else if (line.match(/^- /)) {
        if (currentParagraph) {
          paragraphs.push(
            <p key={`p${index}`} className="text-slate-300 leading-relaxed mb-4">
              {currentParagraph}
            </p>
          );
          currentParagraph = '';
        }
        const item = line.replace(/^- /, '');
        paragraphs.push(
          <li key={`li${index}`} className="text-slate-300 ml-6 mb-2 list-disc">
            {renderInlineMarkdown(item)}
          </li>
        );
      } else if (line.trim()) {
        currentParagraph += (currentParagraph ? ' ' : '') + line;
      } else if (currentParagraph) {
        paragraphs.push(
          <p key={`p${index}`} className="text-slate-300 leading-relaxed mb-4">
            {currentParagraph}
          </p>
        );
        currentParagraph = '';
      }
    });

    if (currentParagraph) {
      paragraphs.push(
        <p key="pfinal" className="text-slate-300 leading-relaxed mb-4">
          {currentParagraph}
        </p>
      );
    }

    return <div>{paragraphs}</div>;
  };

  const renderInlineMarkdown = (text: string): React.ReactNode => {
    // Bold
    let result: React.ReactNode = text;
    result = text.replace(/\*\*(.+?)\*\*/g, (match, content) => `__${content}__`);

    // Italic
    result = result
      .toString()
      .replace(/\*(.+?)\*/g, (match, content) => `_${content}_`);

    // Citations
    result = result
      .toString()
      .replace(/\[(\d+)\]/g, (match, number) => `[${number}]`);

    return result;
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
            <div className="w-3 h-3 bg-blue-500 rounded-full animate-bounce" style={{ animationDelay: '0.2s' }}></div>
            <div className="w-3 h-3 bg-blue-500 rounded-full animate-bounce" style={{ animationDelay: '0.4s' }}></div>
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
            <p className="text-red-300">{error || 'Report not found'}</p>
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

      <main className="max-w-7xl mx-auto px-4 py-12">
        {/* Breadcrumb */}
        <div className="mb-8">
          <Link
            href={`/projects/${report.job.project.id}`}
            className="text-blue-400 hover:text-blue-300 text-sm"
          >
            ← Back to {report.job.project.name}
          </Link>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-4 gap-8">
          {/* Sidebar TOC */}
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
                        heading.level === 3 ? 'ml-4 text-slate-400' : 'text-slate-300 font-medium'
                      }`}
                    >
                      {heading.text}
                    </a>
                  ))}
                </nav>
              </div>
            </aside>
          )}

          {/* Main Content */}
          <div className="lg:col-span-3">
            {/* Report Header */}
            <div className="bg-slate-900 border border-slate-700 rounded-lg p-8 mb-8">
              <h1 className="text-3xl font-bold text-white mb-4">Research Report</h1>
              <p className="text-slate-400 mb-6">{report.job.query}</p>

              {/* Report Metadata */}
              <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-6 py-6 border-t border-b border-slate-700">
                <div>
                  <p className="text-slate-400 text-sm">Words</p>
                  <p className="text-white font-medium">{report.wordCount.toLocaleString()}</p>
                </div>
                <div>
                  <p className="text-slate-400 text-sm">Citations</p>
                  <p className="text-white font-medium">{report.citationCount}</p>
                </div>
                <div>
                  <p className="text-slate-400 text-sm">Quality Score</p>
                  <div className="flex items-center gap-2">
                    <div className="w-24 h-2 bg-slate-700 rounded-full overflow-hidden">
                      <div
                        className={`h-full ${
                          report.criticScore >= 8
                            ? 'bg-green-500'
                            : report.criticScore >= 6
                              ? 'bg-yellow-500'
                              : 'bg-red-500'
                        }`}
                        style={{ width: `${(report.criticScore / 10) * 100}%` }}
                      />
                    </div>
                    <span className="text-white font-medium">{report.criticScore.toFixed(1)}/10</span>
                  </div>
                </div>
                <div>
                  <p className="text-slate-400 text-sm">Revisions</p>
                  <p className="text-white font-medium">{report.revisionCount}</p>
                </div>
              </div>
            </div>

            {/* Report Content */}
            <article className="max-w-none mb-12">
              {renderMarkdown(reportContent)}
            </article>

            {/* Chat Panel */}
            <div className="mt-16">
              <h2 className="text-2xl font-bold text-white mb-6">Ask Questions About This Report</h2>
              <ChatPanel reportId={report.id} />
            </div>
          </div>
        </div>
      </main>
    </div>
  );
}

const JOB_REPORT_QUERY = gql`
  query GetJobReport($id: String!) {
    job(id: $id) {
      id
      query
      status
      llmProvider
      researchDepth
      createdAt
      completedAt
      durationMs
      report {
        content
        sources
        confidence
        executiveSummary
      }
    }
  }
`;

export default function JobReportPage() {
  const router = useRouter();
  const params = useParams();
  const jobId = params.id as string;

  const isAuthenticated = useAuthStore((state) => state.isAuthenticated());
  const { data, loading, error } = useQuery(JOB_REPORT_QUERY, {
    variables: { id: jobId },
    skip: !jobId,
  });

  useEffect(() => {
    if (!isAuthenticated) {
      router.push("/login?redirect=/dashboard");
    }
  }, [isAuthenticated, router]);

  if (!isAuthenticated) {
    return null;
  }

  if (loading) {
    return (
      <div className="min-h-screen bg-slate-900 flex items-center justify-center">
        <div className="text-center">
          <div className="inline-block w-12 h-12 border-4 border-slate-600 border-t-blue-500 rounded-full animate-spin mb-4" />
          <p className="text-white text-lg">Loading report...</p>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="min-h-screen bg-slate-900">
        <header className="bg-slate-800 border-b border-slate-700">
          <div className="max-w-6xl mx-auto px-6 py-4">
            <Link
              href="/dashboard"
              className="text-blue-400 hover:text-blue-300 text-sm mb-2 block"
            >
              ← Back to Dashboard
            </Link>
            <h1 className="text-2xl font-bold text-white">
              Error Loading Report
            </h1>
          </div>
        </header>
        <main className="max-w-6xl mx-auto px-6 py-8">
          <div className="bg-red-900/20 border border-red-700 rounded-lg p-6">
            <p className="text-red-400">{error.message}</p>
          </div>
        </main>
      </div>
    );
  }

  const job = data?.job;
  const report = job?.report;

  const durationMinutes = job?.durationMs
    ? Math.round(job.durationMs / 1000 / 60)
    : 0;

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
          <div className="flex items-center justify-between">
            <div>
              <h1 className="text-2xl font-bold text-white">Research Report</h1>
              <p className="text-slate-400 text-sm mt-1">{job?.query}</p>
            </div>
            <div className="flex gap-3">
              <ExportButton jobId={jobId} reportContent={report?.content} />
              <ShareButton jobId={jobId} />
            </div>
          </div>
        </div>
      </header>

      {/* Main Content */}
      <main className="max-w-4xl mx-auto px-6 py-8">
        {/* Job Metadata */}
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-8">
          <div className="bg-slate-800 rounded-lg border border-slate-700 p-4">
            <p className="text-slate-400 text-xs uppercase tracking-wide mb-1">
              Provider
            </p>
            <p className="text-white font-medium">{job?.llmProvider}</p>
          </div>
          <div className="bg-slate-800 rounded-lg border border-slate-700 p-4">
            <p className="text-slate-400 text-xs uppercase tracking-wide mb-1">
              Depth
            </p>
            <p className="text-white font-medium">{job?.researchDepth}</p>
          </div>
          <div className="bg-slate-800 rounded-lg border border-slate-700 p-4">
            <p className="text-slate-400 text-xs uppercase tracking-wide mb-1">
              Duration
            </p>
            <p className="text-white font-medium">{durationMinutes}m</p>
          </div>
          <div className="bg-slate-800 rounded-lg border border-slate-700 p-4">
            <p className="text-slate-400 text-xs uppercase tracking-wide mb-1">
              Confidence
            </p>
            <p className="text-white font-medium">
              {report?.confidence
                ? `${Math.round(report.confidence * 100)}%`
                : "N/A"}
            </p>
          </div>
        </div>

        {/* Executive Summary */}
        {report?.executiveSummary && (
          <div className="bg-gradient-to-r from-blue-900/20 to-purple-900/20 border border-blue-700/50 rounded-lg p-6 mb-8">
            <h2 className="text-lg font-semibold text-white mb-3">
              Executive Summary
            </h2>
            <p className="text-slate-300 leading-relaxed">
              {report.executiveSummary}
            </p>
          </div>
        )}

        {/* Report Content */}
        <div className="bg-slate-800 rounded-lg border border-slate-700 p-8 mb-8">
          <div className="prose prose-invert max-w-none">
            {report?.content ? (
              <MDXContent content={report.content} />
            ) : (
              <p className="text-slate-400">No report content available</p>
            )}
          </div>
        </div>

        {/* Sources */}
        {report?.sources && report.sources.length > 0 && (
          <div className="bg-slate-800 rounded-lg border border-slate-700 p-8">
            <h2 className="text-lg font-semibold text-white mb-4">Sources</h2>
            <div className="space-y-3">
              {report.sources.map((source: any, idx: number) => (
                <div
                  key={idx}
                  className="flex gap-3 p-3 bg-slate-700/50 rounded border border-slate-600 hover:border-blue-500 transition"
                >
                  <span className="text-slate-500 text-sm font-medium min-w-fit">
                    [{idx + 1}]
                  </span>
                  <div className="flex-1 min-w-0">
                    <p className="text-white text-sm font-medium truncate">
                      {typeof source === "string"
                        ? source
                        : source.title || source.url}
                    </p>
                    {typeof source === "object" && source.url && (
                      <a
                        href={source.url}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="text-blue-400 hover:text-blue-300 text-xs truncate block"
                      >
                        {source.url}
                      </a>
                    )}
                  </div>
                  {typeof source === "object" && source.url && (
                    <a
                      href={source.url}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="text-blue-400 hover:text-blue-300 text-sm font-medium"
                    >
                      →
                    </a>
                  )}
                </div>
              ))}
            </div>
          </div>
        )}

        {/* Footer */}
        <div className="mt-12 pt-8 border-t border-slate-700 text-center">
          <p className="text-slate-500 text-sm">
            Report generated on{" "}
            {job?.completedAt
              ? new Date(job.completedAt).toLocaleDateString("en-US", {
                  year: "numeric",
                  month: "long",
                  day: "numeric",
                  hour: "2-digit",
                  minute: "2-digit",
                })
              : "N/A"}
          </p>
          <div className="flex gap-4 justify-center mt-4">
            <Link
              href="/dashboard"
              className="text-blue-400 hover:text-blue-300 text-sm"
            >
              Back to Dashboard
            </Link>
            <button
              onClick={() => window.print()}
              className="text-blue-400 hover:text-blue-300 text-sm"
            >
              Print Report
            </button>
          </div>
        </div>
      </main>
    </div>
  );
}
