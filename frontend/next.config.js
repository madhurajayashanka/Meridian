/** @type {import('next').NextConfig} */

// Fail fast in production if required env vars are missing
if (process.env.NODE_ENV === "production") {
  const required = ["NEXT_PUBLIC_API_URL", "NEXT_PUBLIC_AI_URL"];
  const missing = required.filter((k) => !process.env[k]);
  if (missing.length > 0) {
    throw new Error(`Missing required env vars: ${missing.join(", ")}`);
  }
}

const nextConfig = {
  reactStrictMode: true,
  pageExtensions: ["ts", "tsx"],

  env: {
    NEXT_PUBLIC_API_URL:
      process.env.NEXT_PUBLIC_API_URL || "http://localhost:8000",
    NEXT_PUBLIC_AI_URL:
      process.env.NEXT_PUBLIC_AI_URL || "http://localhost:8080",
  },

  headers: async () => {
    const apiUrl = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8000";
    const aiUrl = process.env.NEXT_PUBLIC_AI_URL || "http://localhost:8080";

    const csp = [
      "default-src 'self'",
      `connect-src 'self' ${apiUrl} ${aiUrl}`,
      "script-src 'self' 'unsafe-inline'",   // unsafe-inline needed for Next.js inline scripts
      "style-src 'self' 'unsafe-inline'",
      "img-src 'self' data: blob:",
      "font-src 'self'",
      "frame-ancestors 'none'",
      "base-uri 'self'",
      "form-action 'self'",
    ].join("; ");

    return [
      {
        source: "/:path*",
        headers: [
          { key: "Content-Security-Policy", value: csp },
          { key: "X-Content-Type-Options", value: "nosniff" },
          { key: "X-Frame-Options", value: "DENY" },
          { key: "X-XSS-Protection", value: "1; mode=block" },
          { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
          { key: "Permissions-Policy", value: "camera=(), microphone=(), geolocation=()" },
          {
            key: "Strict-Transport-Security",
            value: "max-age=63072000; includeSubDomains; preload",
          },
        ],
      },
    ];
  },
};

module.exports = nextConfig;
