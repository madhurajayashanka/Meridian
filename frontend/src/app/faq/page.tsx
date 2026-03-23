"use client";

import { useState } from "react";
import Link from "next/link";

const FAQS = [
  {
    category: "Getting Started",
    questions: [
      {
        q: "What is Meridian?",
        a: "Meridian is an AI-powered research platform that helps you conduct in-depth research on any topic. Using advanced language models and web search, Meridian gathers sources, synthesizes information, and generates comprehensive research reports.",
      },
      {
        q: "How do I get started with Meridian?",
        a: "Getting started is easy! Sign up for a free account, enter your research query, and Meridian will begin researching. You can watch the progress in real-time and access your report when complete.",
      },
      {
        q: "Do I need technical skills to use Meridian?",
        a: "No! Meridian is designed to be user-friendly for everyone. Our web interface requires no technical knowledge. If you want to use the API or CLI, we provide comprehensive documentation.",
      },
    ],
  },
  {
    category: "Features & Capabilities",
    questions: [
      {
        q: "What LLM models does Meridian support?",
        a: "Meridian supports OpenAI (GPT-4, GPT-3.5), Anthropic (Claude), and Google (Gemini). You can choose your preferred provider in settings.",
      },
      {
        q: "What research depths are available?",
        a: "We offer three research depths: Quick (5-10 min) for basic overviews, Standard (15-20 min) for comprehensive research, and Deep (30-45 min) for exhaustive analysis.",
      },
      {
        q: "Can I customize the research process?",
        a: "Yes! Pro and Enterprise tiers allow you to create custom research templates, select specific sources, and configure research strategies.",
      },
      {
        q: "What file formats can I export?",
        a: "You can export reports as PDF, Markdown, and Word documents. Pro plans also support HTML and structured JSON.",
      },
    ],
  },
  {
    category: "Pricing & Billing",
    questions: [
      {
        q: "Is there a free plan?",
        a: "Yes! Our Starter plan is free and includes 5 research jobs per month, perfect for trying out Meridian.",
      },
      {
        q: "What's the difference between Pro and Enterprise?",
        a: "Pro is for individuals and small teams with unlimited research jobs and API access. Enterprise includes team collaboration, custom models, and dedicated support.",
      },
      {
        q: "Can I cancel anytime?",
        a: "Yes! You can cancel your subscription at any time. Your account status changes at the end of your billing period.",
      },
      {
        q: "Do you offer refunds?",
        a: "We offer a 30-day money-back guarantee if you're not satisfied. After that, refunds are handled case-by-case.",
      },
    ],
  },
  {
    category: "API & Integration",
    questions: [
      {
        q: "Can I use Meridian programmatically?",
        a: "Yes! Pro and Enterprise plans include full API access. You can create jobs, retrieve results, and integrate Meridian into your applications.",
      },
      {
        q: "What integrations are available?",
        a: "We support Slack, GPT plugins, Zapier, and custom webhooks. More integrations are coming soon.",
      },
      {
        q: "Can I set up webhooks for real-time updates?",
        a: "Yes! With Pro and Enterprise plans, you can configure webhooks to receive updates when research jobs complete.",
      },
    ],
  },
  {
    category: "Data & Privacy",
    questions: [
      {
        q: "Is my research data private?",
        a: "Yes! Your research queries and results are encrypted and only accessible by you. We never share your data with third parties.",
      },
      {
        q: "How long do you keep my data?",
        a: "Free tier data is kept for 30 days. Paid plans keep data indefinitely unless you delete it. You can export or delete any of your research at any time.",
      },
      {
        q: "Does Meridian train on my data?",
        a: "No. We do not use your research queries or results to train our models or improve the service. Your data remains your own.",
      },
      {
        q: "Is Meridian GDPR compliant?",
        a: "Yes! We are fully GDPR compliant and handle data according to all applicable regulations.",
      },
    ],
  },
  {
    category: "Technical Support",
    questions: [
      {
        q: "How do I report a bug?",
        a: "You can report bugs on our GitHub page or email support@meridian.dev. Include details about what happened and steps to reproduce.",
      },
      {
        q: "What if a research job fails?",
        a: "If a job fails, you'll see an error message. Check our troubleshooting guide or contact support for help.",
      },
      {
        q: "How can I request a feature?",
        a: "We love feature requests! Share your ideas on our Discord community, GitHub, or email them to us directly.",
      },
      {
        q: "Is there a status page for service uptime?",
        a: "Yes! Visit status.meridian.dev to check our service status and incident history.",
      },
    ],
  },
];

export default function FAQPage() {
  const [expandedIndex, setExpandedIndex] = useState<number | null>(null);

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
            <Link
              href="/docs"
              className="text-slate-400 hover:text-white text-sm"
            >
              Docs
            </Link>
          </div>
        </div>
      </nav>

      {/* Hero Section */}
      <section className="bg-gradient-to-b from-slate-800 to-slate-900 border-b border-slate-700 py-16">
        <div className="max-w-4xl mx-auto px-6 text-center">
          <h1 className="text-4xl md:text-5xl font-bold text-white mb-4">
            Frequently Asked Questions
          </h1>
          <p className="text-xl text-slate-400 max-w-2xl mx-auto">
            Find answers to common questions about Meridian. Can't find what
            you're looking for?{" "}
            <Link
              href="mailto:support@meridian.dev"
              className="text-blue-400 hover:text-blue-300"
            >
              Contact us
            </Link>
            .
          </p>
        </div>
      </section>

      {/* FAQ Content */}
      <section className="max-w-4xl mx-auto px-6 py-16">
        <div className="space-y-12">
          {FAQS.map((category, catIdx) => (
            <div key={catIdx}>
              <h2 className="text-2xl font-bold text-white mb-6 pb-4 border-b border-slate-700">
                {category.category}
              </h2>
              <div className="space-y-4">
                {category.questions.map((faq, qIdx) => {
                  const questionIndex = FAQS.slice(0, catIdx).reduce(
                    (sum, cat) => sum + cat.questions.length,
                    qIdx,
                  );

                  return (
                    <div
                      key={qIdx}
                      className="bg-slate-800 border border-slate-700 rounded-lg overflow-hidden hover:border-slate-600 transition"
                    >
                      <button
                        onClick={() =>
                          setExpandedIndex(
                            expandedIndex === questionIndex
                              ? null
                              : questionIndex,
                          )
                        }
                        className="w-full px-6 py-4 flex items-center justify-between hover:bg-slate-700/50 transition"
                      >
                        <h3 className="text-lg font-semibold text-white text-left">
                          {faq.q}
                        </h3>
                        <span
                          className={`text-slate-400 transition transform ${
                            expandedIndex === questionIndex ? "rotate-180" : ""
                          }`}
                        >
                          ▼
                        </span>
                      </button>

                      {expandedIndex === questionIndex && (
                        <div className="px-6 py-4 bg-slate-700/30 border-t border-slate-700">
                          <p className="text-slate-300 leading-relaxed">
                            {faq.a}
                          </p>
                        </div>
                      )}
                    </div>
                  );
                })}
              </div>
            </div>
          ))}
        </div>
      </section>

      {/* CTA Section */}
      <section className="bg-slate-800 border-y border-slate-700 py-16">
        <div className="max-w-4xl mx-auto px-6 text-center">
          <h2 className="text-2xl font-bold text-white mb-4">
            Still have questions?
          </h2>
          <p className="text-slate-400 mb-8">
            Get in touch with our team and we'll help you out.
          </p>
          <div className="flex gap-4 justify-center flex-wrap">
            <Link
              href="https://discord.gg/meridian"
              className="px-6 py-3 bg-blue-600 hover:bg-blue-700 text-white font-medium rounded-lg transition"
            >
              Join Discord
            </Link>
            <Link
              href="mailto:support@meridian.dev"
              className="px-6 py-3 bg-slate-700 hover:bg-slate-600 text-white font-medium rounded-lg transition"
            >
              Email Support
            </Link>
            <Link
              href="/docs"
              className="px-6 py-3 bg-slate-700 hover:bg-slate-600 text-white font-medium rounded-lg transition"
            >
              View Docs
            </Link>
          </div>
        </div>
      </section>
    </div>
  );
}
