"use client";

import { useState } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useAuthStore } from "@/hooks/useAuth";

const NAVIGATION = [
  { label: "Home", href: "/", section: "main" },
  { label: "Features", href: "/features", section: "main" },
  { label: "Pricing", href: "/pricing", section: "main" },
  { label: "Docs", href: "/docs", section: "main" },
  { label: "FAQ", href: "/faq", section: "main" },
];

const USER_NAVIGATION = [
  { label: "Dashboard", href: "/dashboard" },
  { label: "Settings", href: "/settings" },
];

export function Navigation() {
  const pathname = usePathname();
  const isAuthenticated = useAuthStore((state) => !!state.accessToken);
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);

  return (
    <nav className="bg-slate-800 border-b border-slate-700 sticky top-0 z-50">
      <div className="max-w-6xl mx-auto px-6 py-4">
        <div className="flex items-center justify-between">
          {/* Logo */}
          <Link href="/" className="text-white font-bold text-xl flex-shrink-0">
            Meridian
          </Link>

          {/* Desktop Navigation */}
          <div className="hidden md:flex items-center gap-8">
            {NAVIGATION.map((item) => (
              <Link
                key={item.href}
                href={item.href}
                className={`text-sm transition ${
                  pathname === item.href
                    ? "text-white font-medium"
                    : "text-slate-400 hover:text-white"
                }`}
              >
                {item.label}
              </Link>
            ))}
          </div>

          {/* Right Side Actions */}
          <div className="hidden md:flex items-center gap-4">
            {isAuthenticated ? (
              <>
                {USER_NAVIGATION.map((item) => (
                  <Link
                    key={item.href}
                    href={item.href}
                    className="text-slate-400 hover:text-white text-sm transition"
                  >
                    {item.label}
                  </Link>
                ))}
                <button
                  onClick={() => useAuthStore.getState().logout()}
                  className="text-slate-400 hover:text-white text-sm transition"
                >
                  Logout
                </button>
              </>
            ) : (
              <>
                <Link
                  href="/login"
                  className="text-slate-400 hover:text-white text-sm transition"
                >
                  Login
                </Link>
                <Link
                  href="/register"
                  className="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white text-sm font-medium rounded-lg transition"
                >
                  Sign Up
                </Link>
              </>
            )}
          </div>

          {/* Mobile Menu Button */}
          <button
            onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
            className="md:hidden text-white p-2"
          >
            <svg
              className="w-6 h-6"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M4 6h16M4 12h16M4 18h16"
              />
            </svg>
          </button>
        </div>

        {/* Mobile Menu */}
        {mobileMenuOpen && (
          <div className="md:hidden mt-4 space-y-2 pb-4">
            {NAVIGATION.map((item) => (
              <Link
                key={item.href}
                href={item.href}
                className={`block px-3 py-2 rounded transition ${
                  pathname === item.href
                    ? "bg-slate-700 text-white"
                    : "text-slate-400 hover:text-white"
                }`}
                onClick={() => setMobileMenuOpen(false)}
              >
                {item.label}
              </Link>
            ))}
            <div className="border-t border-slate-700 pt-2 mt-2">
              {isAuthenticated ? (
                <>
                  {USER_NAVIGATION.map((item) => (
                    <Link
                      key={item.href}
                      href={item.href}
                      className="block px-3 py-2 text-slate-400 hover:text-white text-sm transition"
                      onClick={() => setMobileMenuOpen(false)}
                    >
                      {item.label}
                    </Link>
                  ))}
                  <button
                    onClick={() => {
                      useAuthStore.getState().logout();
                      setMobileMenuOpen(false);
                    }}
                    className="block w-full text-left px-3 py-2 text-slate-400 hover:text-white text-sm transition"
                  >
                    Logout
                  </button>
                </>
              ) : (
                <>
                  <Link
                    href="/login"
                    className="block px-3 py-2 text-slate-400 hover:text-white text-sm transition"
                    onClick={() => setMobileMenuOpen(false)}
                  >
                    Login
                  </Link>
                  <Link
                    href="/register"
                    className="block px-3 py-2 text-slate-400 hover:text-white text-sm transition"
                    onClick={() => setMobileMenuOpen(false)}
                  >
                    Sign Up
                  </Link>
                </>
              )}
            </div>
          </div>
        )}
      </div>
    </nav>
  );
}
