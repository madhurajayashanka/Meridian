"use client";

interface AgentCardProps {
  name: string;
  status: "idle" | "running" | "complete" | "failed";
  progress?: number;
  output?: string;
  error?: string;
}

export function AgentCard({
  name,
  status,
  progress = 0,
  output,
  error,
}: AgentCardProps) {
  const statusConfig = {
    idle: {
      bg: "bg-slate-700",
      text: "text-slate-300",
      icon: "○",
      pulse: false,
    },
    running: {
      bg: "bg-blue-900/50",
      text: "text-blue-400",
      icon: "◉",
      pulse: true,
    },
    complete: {
      bg: "bg-green-900/50",
      text: "text-green-400",
      icon: "✓",
      pulse: false,
    },
    failed: {
      bg: "bg-red-900/50",
      text: "text-red-400",
      icon: "✕",
      pulse: false,
    },
  };

  const config = statusConfig[status];

  return (
    <div className={`${config.bg} border border-slate-600 rounded-lg p-4 transition-all duration-300`}>
      {/* Header */}
      <div className="flex items-center justify-between mb-3">
        <div className="flex items-center gap-2">
          <span
            className={`text-xl ${config.text} ${
              config.pulse ? "animate-pulse" : ""
            }`}
          >
            {config.icon}
          </span>
          <h3 className={`font-semibold ${config.text} capitalize`}>
            {name.replace("_", " ")}
          </h3>
        </div>
        <span className={`text-xs px-3 py-1 rounded-full bg-slate-700 ${config.text}`}>
          {status.toUpperCase()}
        </span>
      </div>

      {/* Progress Bar (for running status) */}
      {status === "running" && progress > 0 && (
        <div className="mb-3">
          <div className="w-full bg-slate-600 rounded-full h-2 overflow-hidden">
            <div
              className="bg-blue-500 h-full transition-all duration-300 rounded-full"
              style={{ width: `${Math.min(progress, 100)}%` }}
            />
          </div>
          <p className="text-xs text-slate-400 mt-1">{progress}%</p>
        </div>
      )}

      {/* Output */}
      {output && status === "complete" && (
        <div className="bg-slate-800/50 rounded p-3 border border-slate-600/50 max-h-40 overflow-y-auto">
          <p className="text-sm text-slate-300 break-words">{output}</p>
        </div>
      )}

      {/* Error Message */}
      {error && status === "failed" && (
        <div className="bg-red-900/20 rounded p-3 border border-red-700/50">
          <p className="text-sm text-red-300">{error}</p>
        </div>
      )}
    </div>
  );
}
