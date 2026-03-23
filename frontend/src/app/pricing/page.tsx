"use client";

import { useRouter } from "next/navigation";
import { useAuthStore } from "@/hooks/useAuth";
import Link from "next/link";

const PLANS = [
  {
    name: "Starter",
    price: "FREE",
    description: "Perfect for testing out Meridian",
    features: [
      "5 research jobs per month",
      "Standard depth research",
      "Basic sources",
      "Community support",
      "Web interface only",
    ],
    limitations: [
      "No API access",
      "Limited to 5,000 words per report",
      "No advanced features",
    ],
    cta: "Get Started",
    highlighted: false,
  },
  {
    name: "Pro",
    price: "$29",
    period: "/month",
    description: "For regular researchers and professionals",
    features: [
      "Unlimited research jobs",
      "Deep research available",
      "Advanced source curation",
      "Email & priority support",
      "API access",
      "PDF & Markdown export",
      "Custom research templates",
      "Saved searches",
    ],
    limitations: [
      "Limited to 50,000 words per report",
      "Team collaboration on paid plan",
    ],
    cta: "Start Pro Trial",
    highlighted: true,
  },
  {
    name: "Enterprise",
    price: "Custom",
    description: "For teams and organizations",
    features: [
      "Everything in Pro, plus:",
      "Unlimited report length",
      "Team collaboration",
      "Custom LLM models",
      "Dedicated support",
      "SLA guarantee",
      "Advanced analytics",
      "White-label options",
    ],
    limitations: [],
    cta: "Contact Sales",
    highlighted: false,
  },
];

export default function PricingPage() {
  const router = useRouter();
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated());

  const handleCTA = (plan: string) => {
    if (!isAuthenticated) {
      router.push(`/signup?plan=${plan.toLowerCase()}`);
    } else {
      // Handle upgrade logic
      router.push(`/billing/upgrade?plan=${plan.toLowerCase()}`);
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
            <Link
              href="/features"
              className="text-slate-400 hover:text-white text-sm"
            >
              Features
            </Link>
            <Link href="/pricing" className="text-white text-sm font-medium">
              Pricing
            </Link>
            <Link
              href="/docs"
              className="text-slate-400 hover:text-white text-sm"
            >
              Docs
            </Link>
            {isAuthenticated ? (
              <Link
                href="/dashboard"
                className="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white text-sm font-medium rounded-lg transition"
              >
                Dashboard
              </Link>
            ) : (
              <>
                <Link
                  href="/login"
                  className="text-slate-400 hover:text-white text-sm"
                >
                  Login
                </Link>
                <Link
                  href="/signup"
                  className="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white text-sm font-medium rounded-lg transition"
                >
                  Sign Up
                </Link>
              </>
            )}
          </div>
        </div>
      </nav>

      {/* Hero Section */}
      <section className="bg-gradient-to-b from-slate-800 to-slate-900 border-b border-slate-700 py-16">
        <div className="max-w-6xl mx-auto px-6 text-center">
          <h1 className="text-4xl md:text-5xl font-bold text-white mb-4">
            Simple, Transparent Pricing
          </h1>
          <p className="text-xl text-slate-400 max-w-2xl mx-auto">
            Choose the plan that fits your research needs. All plans include
            full access to Meridian's powerful research capabilities.
          </p>
        </div>
      </section>

      {/* Pricing Cards */}
      <section className="max-w-6xl mx-auto px-6 py-16">
        <div className="grid md:grid-cols-3 gap-6">
          {PLANS.map((plan) => (
            <div
              key={plan.name}
              className={`rounded-lg border transition transform hover:scale-105 ${
                plan.highlighted
                  ? "bg-blue-900/20 border-blue-500 shadow-lg shadow-blue-500/10 md:scale-105"
                  : "bg-slate-800 border-slate-700 hover:border-slate-600"
              } p-8`}
            >
              {plan.highlighted && (
                <div className="bg-blue-600 text-white text-xs font-semibold px-3 py-1 rounded-full mb-4 inline-block">
                  MOST POPULAR
                </div>
              )}

              {/* Plan Header */}
              <h3 className="text-2xl font-bold text-white mb-2">
                {plan.name}
              </h3>
              <p className="text-slate-400 text-sm mb-6">{plan.description}</p>

              {/* Price */}
              <div className="mb-6">
                <div className="flex items-baseline gap-1 mb-2">
                  <span className="text-4xl font-bold text-white">
                    {plan.price}
                  </span>
                  {plan.period && (
                    <span className="text-slate-400">{plan.period}</span>
                  )}
                </div>
              </div>

              {/* CTA Button */}
              <button
                onClick={() => handleCTA(plan.name)}
                className={`w-full px-4 py-3 rounded-lg font-medium transition mb-8 ${
                  plan.highlighted
                    ? "bg-blue-600 hover:bg-blue-700 text-white"
                    : "bg-slate-700 hover:bg-slate-600 text-white"
                }`}
              >
                {plan.cta}
              </button>

              {/* Features */}
              <div className="mb-8">
                <p className="text-xs font-semibold text-slate-400 uppercase tracking-wide mb-4">
                  FEATURES
                </p>
                <ul className="space-y-3">
                  {plan.features.map((feature, idx) => (
                    <li
                      key={idx}
                      className="flex items-start gap-3 text-sm text-slate-300"
                    >
                      <span className="text-green-400 mt-0.5">✓</span>
                      <span>{feature}</span>
                    </li>
                  ))}
                </ul>
              </div>

              {/* Limitations */}
              {plan.limitations.length > 0 && (
                <div>
                  <p className="text-xs font-semibold text-slate-400 uppercase tracking-wide mb-4">
                    LIMITATIONS
                  </p>
                  <ul className="space-y-3">
                    {plan.limitations.map((limitation, idx) => (
                      <li
                        key={idx}
                        className="flex items-start gap-3 text-sm text-slate-500"
                      >
                        <span className="text-slate-500 mt-0.5">✗</span>
                        <span>{limitation}</span>
                      </li>
                    ))}
                  </ul>
                </div>
              )}
            </div>
          ))}
        </div>
      </section>

      {/* FAQ Section */}
      <section className="bg-slate-800 border-y border-slate-700 py-16">
        <div className="max-w-3xl mx-auto px-6">
          <h2 className="text-3xl font-bold text-white text-center mb-12">
            Frequently Asked Questions
          </h2>

          <div className="space-y-6">
            {[
              {
                question: "Can I change plans anytime?",
                answer:
                  "Yes! You can upgrade or downgrade your plan at any time. Changes take effect at the start of your next billing cycle.",
              },
              {
                question: "What payment methods do you accept?",
                answer:
                  "We accept all major credit cards (Visa, Mastercard, American Express) and can arrange wire transfers for enterprise customers.",
              },
              {
                question: "Is there a free trial for Pro?",
                answer:
                  "Yes! All Pro plans include a 14-day free trial with full features. No credit card required to start.",
              },
              {
                question: "Do you offer refunds?",
                answer:
                  "We offer a 30-day money-back guarantee if you're not satisfied with Meridian. No questions asked.",
              },
              {
                question: "What about API limits?",
                answer:
                  "API usage is counted as research jobs. Pro plans get unlimited jobs, while Starter plans are limited to 5 per month.",
              },
              {
                question: "Can I customize a plan for my team?",
                answer:
                  "Absolutely! Contact our sales team to discuss custom plans, volume discounts, and team features.",
              },
            ].map((faq, idx) => (
              <div
                key={idx}
                className="bg-slate-700/50 border border-slate-600 rounded-lg p-6"
              >
                <h3 className="text-lg font-semibold text-white mb-2">
                  {faq.question}
                </h3>
                <p className="text-slate-300">{faq.answer}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* CTA Section */}
      <section className="py-16">
        <div className="max-w-4xl mx-auto px-6 text-center">
          <h2 className="text-3xl font-bold text-white mb-4">
            Ready to get started?
          </h2>
          <p className="text-slate-400 mb-8">
            Join thousands of researchers using Meridian to power their
            insights.
          </p>
          <div className="flex gap-4 justify-center">
            <button
              onClick={() => !isAuthenticated && router.push("/signup")}
              className="px-8 py-3 bg-blue-600 hover:bg-blue-700 text-white font-medium rounded-lg transition"
            >
              {isAuthenticated ? "Back to Dashboard" : "Start Free Trial"}
            </button>
            <Link
              href="/docs"
              className="px-8 py-3 bg-slate-700 hover:bg-slate-600 text-white font-medium rounded-lg transition"
            >
              View Documentation
            </Link>
          </div>
        </div>
      </section>
    </div>
  );
}
