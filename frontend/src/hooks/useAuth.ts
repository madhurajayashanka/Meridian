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
  logout: () => void;
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

  logout: () => {
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

export const useAuth = () => {
  const accessToken = useAuthStore((state) => state.accessToken);
  const refreshToken = useAuthStore((state) => state.refreshToken);
  const userId = useAuthStore((state) => state.userId);
  const email = useAuthStore((state) => state.email);
  const setAuth = useAuthStore((state) => state.setAuth);
  const logout = useAuthStore((state) => state.logout);

  return {
    accessToken,
    refreshToken,
    userId,
    email,
    user: userId || email ? { id: userId, email } : null,
    isAuthenticated: !!accessToken,
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
