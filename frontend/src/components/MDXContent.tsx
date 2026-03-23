import { ReactNode } from "react";
import ReactMarkdown from "react-markdown";
import { Prism as SyntaxHighlighter } from "react-syntax-highlighter";
import { oneDark } from "react-syntax-highlighter/dist/esm/styles/prism";

interface MDXContentProps {
  content: string;
}

export default function MDXContent({ content }: MDXContentProps) {
  if (!content) {
    return null;
  }

  return (
    <ReactMarkdown
      components={{
        h1: ({ ...props }) => (
          <h1
            className="text-3xl font-bold text-white mt-8 mb-4 first:mt-0"
            {...props}
          />
        ),
        h2: ({ ...props }) => (
          <h2 className="text-2xl font-bold text-white mt-6 mb-3" {...props} />
        ),
        h3: ({ ...props }) => (
          <h3 className="text-xl font-bold text-white mt-5 mb-2" {...props} />
        ),
        h4: ({ ...props }) => (
          <h4
            className="text-lg font-semibold text-white mt-4 mb-2"
            {...props}
          />
        ),
        p: ({ ...props }) => (
          <p className="text-slate-300 leading-relaxed mb-4" {...props} />
        ),
        ul: ({ ...props }) => (
          <ul
            className="list-disc list-inside text-slate-300 mb-4 space-y-2"
            {...props}
          />
        ),
        ol: ({ ...props }) => (
          <ol
            className="list-decimal list-inside text-slate-300 mb-4 space-y-2"
            {...props}
          />
        ),
        li: ({ ...props }) => <li className="text-slate-300" {...props} />,
        blockquote: ({ ...props }) => (
          <blockquote
            className="border-l-4 border-blue-500 pl-4 italic text-slate-400 my-4"
            {...props}
          />
        ),
        code: ({ inline, className, children, ...props }: any) => {
          const match = /language-(\w+)/.exec(className || "");
          const language = match ? match[1] : "text";

          if (inline) {
            return (
              <code
                className="bg-slate-700 px-2 py-1 rounded text-slate-200 font-mono text-sm"
                {...props}
              >
                {children}
              </code>
            );
          }

          return (
            <SyntaxHighlighter
              language={language}
              style={oneDark as any}
              className="rounded-lg my-4 overflow-x-auto"
              {...props}
            >
              {String(children).replace(/\n$/, "")}
            </SyntaxHighlighter>
          );
        },
        table: ({ ...props }) => (
          <table
            className="min-w-full border-collapse border border-slate-600 my-4"
            {...props}
          />
        ),
        thead: ({ ...props }) => <thead className="bg-slate-700" {...props} />,
        tbody: ({ ...props }) => <tbody className="bg-slate-800" {...props} />,
        tr: ({ ...props }) => (
          <tr className="border border-slate-600" {...props} />
        ),
        th: ({ ...props }) => (
          <th
            className="border border-slate-600 px-4 py-2 text-left text-white font-semibold"
            {...props}
          />
        ),
        td: ({ ...props }) => (
          <td
            className="border border-slate-600 px-4 py-2 text-slate-300"
            {...props}
          />
        ),
        a: ({ ...props }) => (
          <a
            className="text-blue-400 hover:text-blue-300 underline"
            target="_blank"
            rel="noopener noreferrer"
            {...props}
          />
        ),
        hr: ({ ...props }) => (
          <hr className="border-slate-600 my-6" {...props} />
        ),
      }}
    >
      {content}
    </ReactMarkdown>
  );
}
