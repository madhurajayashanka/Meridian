import { create } from "zustand";
import { useEffect, useState } from "react";

interface AuthState {
  accessToken: string | null;
  refreshToken: string | null;
  userId: string | null;
  email: string | null;
  isInitialized: boolean;

  setAuth: (
    accessToken: string,
    refreshToken: string,
    userId: string,
    email: string,
  ) => void;
  clearAuth: () => void;
  logout: () => void;
  isAuthenticated: () => boolean;
  initializeFromStorage: () => void;
}

export const useAuthStore = create<AuthState>((set, get) => ({
  accessToken: null,
  refreshToken: null,
  userId: null,
  email: null,
  isInitialized: false,

  initializeFromStorage: () => {
    const { isInitialized } = get();
    if (isInitialized) {
      return;
    }

    if (typeof window !== "undefined") {
      try {
        const stored = localStorage.getItem("auth");
        if (stored) {
          const auth = JSON.parse(stored);
          set({ ...auth, isInitialized: true });
        } else {
          set({ isInitialized: true });
        }
      } catch (e) {
        console.error("Failed to restore auth state:", e);
        set({ isInitialized: true });
      }
    }
  },

  setAuth: (accessToken, refreshToken, userId, email) => {
    set({ accessToken, refreshToken, userId, email, isInitialized: true });
    // Persist to localStorage
    if (typeof window !== "undefined") {
      localStorage.setItem(
        "auth",
        JSON.stringify({ accessToken, refreshToken, userId, email }),
      );
    }
  },

  clearAuth: () => {
    set({
      accessToken: null,
      refreshToken: null,
      userId: null,
      email: null,
      isInitialized: true,
    });
    if (typeof window !== "undefined") {
      localStorage.removeItem("auth");
    }
  },

  logout: () => {
    set({
      accessToken: null,
      refreshToken: null,
      userId: null,
      email: null,
      isInitialized: true,
    });
    if (typeof window !== "undefined") {
      localStorage.removeItem("auth");
    }
  },

  isAuthenticated: () => {
    const { accessToken } = get();
    return !!accessToken;
  },
}));

export const useAuth = () => {
  const [isReady, setIsReady] = useState(false);
  const accessToken = useAuthStore((state) => state.accessToken);
  const refreshToken = useAuthStore((state) => state.refreshToken);
  const userId = useAuthStore((state) => state.userId);
  const email = useAuthStore((state) => state.email);
  const setAuth = useAuthStore((state) => state.setAuth);
  const logout = useAuthStore((state) => state.logout);
  const initializeFromStorage = useAuthStore(
    (state) => state.initializeFromStorage,
  );
  const isInitialized = useAuthStore((state) => state.isInitialized);

  // Initialize auth state from localStorage on mount
  useEffect(() => {
    initializeFromStorage();
    setIsReady(true);
  }, [initializeFromStorage]);

  return {
    accessToken,
    refreshToken,
    userId,
    email,
    user: userId || email ? { id: userId, email } : null,
    isAuthenticated: !!accessToken,
    isInitialized,
    isReady,
    setTokens: ({
      accessToken: nextAccessToken,
      refreshToken: nextRefreshToken,
      userId: nextUserId,
      email: nextEmail,
    }: {
      accessToken: string;
      refreshToken: string;
      userId?: string | number;
      email?: string;
      name?: string;
    }) =>
      setAuth(
        nextAccessToken,
        nextRefreshToken,
        nextUserId != null ? String(nextUserId) : "",
        nextEmail ?? "",
      ),
    logout,
  };
};
