"use client";

import { useState } from "react";

interface ShareButtonProps {
  jobId: string;
}

export function ShareButton({ jobId }: ShareButtonProps) {
  const [copied, setCopied] = useState(false);

  const handleShare = async () => {
    const shareUrl = `${window.location.origin}/jobs/${jobId}/report`;

    if (navigator.share) {
      try {
        await navigator.share({
          title: "Meridian Research Report",
          url: shareUrl,
        });
      } catch (error) {
        console.error("Share failed:", error);
      }
    } else {
      // Fallback: copy to clipboard
      navigator.clipboard.writeText(shareUrl);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    }
  };

  return (
    <button
      onClick={handleShare}
      className="px-4 py-2 bg-slate-700 hover:bg-slate-600 text-white text-sm font-medium rounded-lg transition flex items-center gap-2"
    >
      {copied ? (
        <>
          <span>✓</span>
          <span>Copied</span>
        </>
      ) : (
        <>
          <span>🔗</span>
          <span>Share</span>
        </>
      )}
    </button>
  );
}
