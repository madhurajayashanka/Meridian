import Link from "next/link";

export default function HomePage() {
  return (
    <main className="min-h-screen bg-slate-950 text-white flex items-center justify-center p-6">
      <div className="w-full max-w-2xl text-center">
        <h1 className="text-4xl md:text-5xl font-bold mb-4">Meridian</h1>
        <p className="text-slate-300 text-lg mb-8">
          Autonomous multi-agent research platform.
        </p>
        <div className="flex flex-col sm:flex-row gap-3 justify-center">
          <Link
            href="/login"
            className="inline-block rounded-lg bg-blue-600 px-6 py-3 font-medium hover:bg-blue-700 transition"
          >
            Sign In
          </Link>
          <Link
            href="/register"
            className="inline-block rounded-lg border border-slate-600 px-6 py-3 font-medium hover:bg-slate-800 transition"
          >
            Create Account
          </Link>
          <Link
            href="/docs"
            className="inline-block rounded-lg border border-slate-600 px-6 py-3 font-medium hover:bg-slate-800 transition"
          >
            Docs
          </Link>
        </div>
      </div>
    </main>
  );
}
