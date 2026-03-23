import { describe, it, expect, beforeEach, afterEach, vi } from "vitest";
import { renderHook, act, waitFor } from "@testing-library/react";
import { useAuth } from "@/hooks/useAuth";
import * as localStorage from "@/utils/localStorage";

// Mock localStorage
vi.mock("@/utils/localStorage", () => ({
  __esModule: true,
  getToken: vi.fn(),
  setToken: vi.fn(),
  removeToken: vi.fn(),
  getRefreshToken: vi.fn(),
  setRefreshToken: vi.fn(),
  removeRefreshToken: vi.fn(),
}));

describe("useAuth Hook", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("should initialize with null values when no token stored", () => {
    (localStorage.getToken as any).mockReturnValue(null);
    (localStorage.getRefreshToken as any).mockReturnValue(null);

    const { result } = renderHook(() => useAuth());

    expect(result.current.accessToken).toBeNull();
    expect(result.current.refreshToken).toBeNull();
    expect(result.current.isAuthenticated).toBe(false);
  });

  it("should restore tokens from storage on mount", () => {
    const mockToken = "test-access-token";
    const mockRefreshToken = "test-refresh-token";
    (localStorage.getToken as any).mockReturnValue(mockToken);
    (localStorage.getRefreshToken as any).mockReturnValue(mockRefreshToken);

    const { result } = renderHook(() => useAuth());

    expect(result.current.accessToken).toBe(mockToken);
    expect(result.current.refreshToken).toBe(mockRefreshToken);
    expect(result.current.isAuthenticated).toBe(true);
  });

  it("should set tokens and persist to storage on login", async () => {
    const { result } = renderHook(() => useAuth());

    const tokens = {
      accessToken: "new-access-token",
      refreshToken: "new-refresh-token",
      email: "user@example.com",
      name: "Test User",
    };

    await act(async () => {
      result.current.setTokens(tokens);
    });

    expect(localStorage.setToken).toHaveBeenCalledWith(tokens.accessToken);
    expect(localStorage.setRefreshToken).toHaveBeenCalledWith(
      tokens.refreshToken,
    );
  });

  it("should clear tokens on logout", async () => {
    (localStorage.getToken as any).mockReturnValue("test-token");

    const { result } = renderHook(() => useAuth());

    await act(async () => {
      result.current.logout();
    });

    expect(localStorage.removeToken).toHaveBeenCalled();
    expect(localStorage.removeRefreshToken).toHaveBeenCalled();
    expect(result.current.accessToken).toBeNull();
    expect(result.current.isAuthenticated).toBe(false);
  });

  it("should expose userId and email from store", async () => {
    const { result } = renderHook(() => useAuth());

    const tokens = {
      accessToken: "token",
      refreshToken: "refresh",
      email: "test@example.com",
      name: "Test",
      userId: 123,
    };

    await act(async () => {
      result.current.setTokens(tokens);
    });

    expect(result.current.email).toBe("test@example.com");
  });

  it("should indicate authenticated state correctly", async () => {
    const { result } = renderHook(() => useAuth());

    expect(result.current.isAuthenticated).toBe(false);

    await act(async () => {
      result.current.setTokens({
        accessToken: "token",
        refreshToken: "refresh",
      });
    });

    expect(result.current.isAuthenticated).toBe(true);
  });
});
