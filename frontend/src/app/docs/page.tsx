"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";

const DOC_SECTIONS = [
  {
    title: "Getting Started",
    description: "Learn the basics of Meridian",
    items: [
      { title: "Introduction", href: "/docs/intro" },
      { title: "Installation", href: "/docs/installation" },
      { title: "Quick Start", href: "/docs/quickstart" },
      { title: "API Keys", href: "/docs/api-keys" },
    ],
  },
  {
    title: "Web Interface",
    description: "Using the Meridian dashboard",
    items: [
      { title: "Dashboard", href: "/docs/dashboard" },
      { title: "Creating Jobs", href: "/docs/creating-jobs" },
      { title: "Advanced Search", href: "/docs/advanced-search" },
      { title: "Managing Results", href: "/docs/managing-results" },
    ],
  },
  {
    title: "API Reference",
    description: "Complete API documentation",
    items: [
      { title: "Authentication", href: "/docs/api/auth" },
      { title: "Jobs API", href: "/docs/api/jobs" },
      { title: "Research API", href: "/docs/api/research" },
      { title: "WebSocket Events", href: "/docs/api/websocket" },
    ],
  },
  {
    title: "CLI Tools",
    description: "Command-line interface",
    items: [
      { title: "Installation", href: "/docs/cli/install" },
      { title: "Commands", href: "/docs/cli/commands" },
      { title: "Configuration", href: "/docs/cli/config" },
      { title: "Examples", href: "/docs/cli/examples" },
    ],
  },
  {
    title: "Integrations",
    description: "Connect Meridian with other tools",
    items: [
      { title: "Slack", href: "/docs/integrations/slack" },
      { title: "GPT Plugins", href: "/docs/integrations/gpt-plugins" },
      { title: "Zapier", href: "/docs/integrations/zapier" },
      { title: "Custom Webhooks", href: "/docs/integrations/webhooks" },
    ],
  },
  {
    title: "Advanced Topics",
    description: "Deep dives and advanced usage",
    items: [
      { title: "Custom Models", href: "/docs/advanced/custom-models" },
      { title: "Research Strategies", href: "/docs/advanced/strategies" },
      { title: "Performance Tuning", href: "/docs/advanced/performance" },
      { title: "Troubleshooting", href: "/docs/advanced/troubleshooting" },
    ],
  },
];

const SEARCH_SUGGESTIONS = [
  "How do I create a research job?",
  "What LLM models are supported?",
  "How do I use the API?",
  "How do I export results?",
  "Can I schedule recurring research?",
];

export default function DocsPage() {
  const router = useRouter();

  const handleSearch = (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    const query = (
      e.currentTarget.elements.namedItem("search") as HTMLInputElement
    ).value;
    if (query.trim()) {
      router.push(`/docs/search?q=${encodeURIComponent(query)}`);
    }
  };

  return (
    <div className="min-h-screen bg-slate-900">
      {/* Navigation */}
      <nav className="bg-slate-800 border-b border-slate-700 sticky top-0 z-50">
        <div className="max-w-6xl mx-auto px-6 py-4 flex items-center justify-between">
          <Link href="/" className="text-white font-bold text-xl">
            Meridian
          </Link>
          <div className="flex items-center gap-4">
            <Link href="/" className="text-slate-400 hover:text-white text-sm">
              Home
            </Link>
            <Link
              href="/features"
              className="text-slate-400 hover:text-white text-sm"
            >
              Features
            </Link>
            <Link
              href="/pricing"
              className="text-slate-400 hover:text-white text-sm"
            >
              Pricing
            </Link>
          </div>
        </div>
      </nav>

      {/* Hero Section */}
      <section className="bg-gradient-to-b from-slate-800 to-slate-900 border-b border-slate-700 py-16">
        <div className="max-w-4xl mx-auto px-6 text-center">
          <h1 className="text-4xl md:text-5xl font-bold text-white mb-4">
            Documentation
          </h1>
          <p className="text-xl text-slate-400 mb-8">
            Everything you need to get the most out of Meridian
          </p>

          {/* Search */}
          <form onSubmit={handleSearch} className="mb-8">
            <div className="relative max-w-2xl mx-auto">
              <input
                type="text"
                name="search"
                placeholder="Search documentation..."
                className="w-full bg-slate-700 border border-slate-600 rounded-lg px-4 py-3 text-white placeholder-slate-500 focus:outline-none focus:border-blue-500"
              />
              <button
                type="submit"
                className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-white"
              >
                🔍
              </button>
            </div>
          </form>

          {/* Search Suggestions */}
          <div className="flex flex-wrap gap-2 justify-center">
            {SEARCH_SUGGESTIONS.map((suggestion, idx) => (
              <button
                key={idx}
                onClick={() =>
                  router.push(
                    `/docs/search?q=${encodeURIComponent(suggestion)}`,
                  )
                }
                className="px-3 py-1 text-xs bg-slate-700 hover:bg-slate-600 text-slate-300 rounded-full transition"
              >
                {suggestion}
              </button>
            ))}
          </div>
        </div>
      </section>

      {/* Doc Sections */}
      <section className="max-w-6xl mx-auto px-6 py-16">
        <div className="grid md:grid-cols-2 lg:grid-cols-3 gap-8">
          {DOC_SECTIONS.map((section, idx) => (
            <div
              key={idx}
              className="bg-slate-800 border border-slate-700 rounded-lg p-6 hover:border-slate-600 transition"
            >
              <h3 className="text-lg font-semibold text-white mb-2">
                {section.title}
              </h3>
              <p className="text-slate-400 text-sm mb-4">
                {section.description}
              </p>
              <ul className="space-y-2">
                {section.items.map((item, itemIdx) => (
                  <li key={itemIdx}>
                    <Link
                      href={item.href}
                      className="text-blue-400 hover:text-blue-300 text-sm flex items-center gap-2"
                    >
                      <span>{item.title}</span>
                      <span className="text-xs">→</span>
                    </Link>
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </div>
      </section>

      {/* Getting Help */}
      <section className="bg-slate-800 border-y border-slate-700 py-16">
        <div className="max-w-4xl mx-auto px-6 text-center">
          <h2 className="text-2xl font-bold text-white mb-4">Need Help?</h2>
          <p className="text-slate-400 mb-8">
            Can't find what you're looking for? We're here to help.
          </p>
          <div className="grid md:grid-cols-3 gap-6">
            <Link
              href="https://discord.gg/meridian"
              className="p-6 bg-slate-700/50 border border-slate-600 rounded-lg hover:border-blue-500 transition"
            >
              <div className="text-2xl mb-2">💬</div>
              <h3 className="text-white font-semibold mb-2">
                Discord Community
              </h3>
              <p className="text-slate-400 text-sm">
                Join our community for support and discussions
              </p>
            </Link>
            <Link
              href="https://github.com/meridian-dev"
              className="p-6 bg-slate-700/50 border border-slate-600 rounded-lg hover:border-blue-500 transition"
            >
              <div className="text-2xl mb-2">🐙</div>
              <h3 className="text-white font-semibold mb-2">GitHub</h3>
              <p className="text-slate-400 text-sm">
                View source code and open issues
              </p>
            </Link>
            <Link
              href="mailto:support@meridian.dev"
              className="p-6 bg-slate-700/50 border border-slate-600 rounded-lg hover:border-blue-500 transition"
            >
              <div className="text-2xl mb-2">✉️</div>
              <h3 className="text-white font-semibold mb-2">Email Support</h3>
              <p className="text-slate-400 text-sm">
                Get help from our support team
              </p>
            </Link>
          </div>
        </div>
      </section>
    </div>
  );
}
