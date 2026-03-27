"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { useAuthStore } from "@/hooks/useAuth";

export default function SettingsPage() {
  const router = useRouter();
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated());
  const logout = useAuthStore((state) => state.logout);
  const email = useAuthStore((state) => state.email);

  useEffect(() => {
    if (!isAuthenticated) {
      router.push("/login?redirect=/settings");
    }
  }, [isAuthenticated, router]);

  if (!isAuthenticated) {
    return null;
  }

  const handleLogout = () => {
    logout();
    router.push("/login");
  };

  return (
    <div className="min-h-screen bg-slate-900">
      <header className="bg-slate-800 border-b border-slate-700 sticky top-0 z-50">
        <div className="max-w-4xl mx-auto px-6 py-4">
          <Link
            href="/dashboard"
            className="text-blue-400 hover:text-blue-300 text-sm mb-2 block"
          >
            ← Back to Dashboard
          </Link>
          <h1 className="text-2xl font-bold text-white">Settings</h1>
        </div>
      </header>

      <main className="max-w-4xl mx-auto px-6 py-8">
        <section className="mb-8">
          <h2 className="text-xl font-semibold text-white mb-4">Account</h2>
          <div className="bg-slate-800 rounded-lg border border-slate-700 p-6 space-y-4">
            <div>
              <label className="block text-sm text-slate-400 mb-2">Email</label>
              <p className="text-white font-medium">{email || "Unknown"}</p>
            </div>
            <div className="pt-4 border-t border-slate-700">
              <button
                onClick={handleLogout}
                className="px-4 py-2 bg-red-600 hover:bg-red-700 text-white text-sm font-medium rounded-lg transition"
              >
                Logout
              </button>
            </div>
          </div>
        </section>

        <section>
          <h2 className="text-xl font-semibold text-white mb-4">Profile</h2>
          <div className="bg-slate-800 rounded-lg border border-slate-700 p-6">
            <p className="text-slate-300 text-sm">
              Full settings management is being migrated. Core account access is
              available here.
            </p>
          </div>
        </section>
      </main>
    </div>
  );
}
