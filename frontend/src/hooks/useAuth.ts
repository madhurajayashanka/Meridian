import { create } from "zustand";

interface AuthState {
  accessToken: string | null;
  refreshToken: string | null;
  userId: string | null;
  email: string | null;

  setAuth: (
    accessToken: string,
    refreshToken: string,
    userId: string,
    email: string,
  ) => void;
  clearAuth: () => void;
  isAuthenticated: () => boolean;
}

export const useAuthStore = create<AuthState>((set, get) => ({
  accessToken: null,
  refreshToken: null,
  userId: null,
  email: null,

  setAuth: (accessToken, refreshToken, userId, email) => {
    set({ accessToken, refreshToken, userId, email });
    // Persist to localStorage
    if (typeof window !== "undefined") {
      localStorage.setItem(
        "auth",
        JSON.stringify({ accessToken, refreshToken, userId, email }),
      );
    }
  },

  clearAuth: () => {
    set({ accessToken: null, refreshToken: null, userId: null, email: null });
    if (typeof window !== "undefined") {
      localStorage.removeItem("auth");
    }
  },

  isAuthenticated: () => {
    const { accessToken } = get();
    return !!accessToken;
  },
}));

// Restore auth state from localStorage on app load
if (typeof window !== "undefined") {
  const stored = localStorage.getItem("auth");
  if (stored) {
    try {
      const auth = JSON.parse(stored);
      useAuthStore.setState(auth);
    } catch (e) {
      console.error("Failed to restore auth state:", e);
    }
  }
}
