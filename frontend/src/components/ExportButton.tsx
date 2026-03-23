"use client";

import { useState } from "react";

interface ExportButtonProps {
  jobId: string;
  reportContent?: string;
}

export function ExportButton({ jobId, reportContent }: ExportButtonProps) {
  const [isExporting, setIsExporting] = useState(false);

  const handleExportPDF = async () => {
    setIsExporting(true);
    try {
      // In a real app, this would hit an API endpoint
      // For now, we'll trigger browser print dialog
      window.print();
    } catch (error) {
      console.error("Export failed:", error);
      alert("Export failed. Please try again.");
    } finally {
      setIsExporting(false);
    }
  };

  const handleExportMarkdown = () => {
    if (!reportContent) {
      alert("No content to export");
      return;
    }

    const element = document.createElement("a");
    const file = new Blob([reportContent], { type: "text/markdown" });
    element.href = URL.createObjectURL(file);
    element.download = `meridian-research-${jobId}.md`;
    document.body.appendChild(element);
    element.click();
    document.body.removeChild(element);
  };

  return (
    <div className="relative group">
      <button className="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white text-sm font-medium rounded-lg transition flex items-center gap-2">
        <span>📥</span>
        <span>Export</span>
        <span className="text-xs">▼</span>
      </button>

      {/* Dropdown Menu */}
      <div className="absolute right-0 top-full mt-2 bg-slate-800 border border-slate-700 rounded-lg shadow-lg opacity-0 invisible group-hover:opacity-100 group-hover:visible transition whitespace-nowrap z-50">
        <button
          onClick={handleExportPDF}
          disabled={isExporting}
          className="block w-full text-left px-4 py-2 text-white hover:bg-slate-700 text-sm first:rounded-t-lg disabled:opacity-50"
        >
          {isExporting ? "Exporting..." : "Export as PDF"}
        </button>
        <button
          onClick={handleExportMarkdown}
          disabled={!reportContent}
          className="block w-full text-left px-4 py-2 text-white hover:bg-slate-700 text-sm last:rounded-b-lg disabled:opacity-50"
        >
          Export as Markdown
        </button>
      </div>
    </div>
  );
}
