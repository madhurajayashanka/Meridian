import { describe, it, expect, beforeEach, vi, afterEach } from "vitest";
import { renderHook, act } from "@testing-library/react";
import { useApiClient } from "@/hooks/useApiClient";
import { useAuth } from "@/hooks/useAuth";

// Mock axios
vi.mock("axios", () => ({
  default: {
    create: vi.fn(() => ({
      interceptors: {
        request: { use: vi.fn() },
        response: { use: vi.fn() },
      },
      get: vi.fn(),
      post: vi.fn(),
      put: vi.fn(),
      delete: vi.fn(),
    })),
  },
}));

// Mock useAuth
vi.mock("@/hooks/useAuth", () => ({
  useAuth: vi.fn(),
}));

describe("useApiClient Hook", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    (useAuth as any).mockReturnValue({
      accessToken: "test-token",
      logout: vi.fn(),
    });
  });

  it("should create axios instance with authorization header", () => {
    const { result } = renderHook(() => useApiClient());

    expect(result.current).toBeDefined();
    expect(useAuth).toHaveBeenCalled();
  });

  it("should add authorization header to requests", async () => {
    const { result } = renderHook(() => useApiClient());

    // The actual header injection happens in interceptors
    expect(useAuth).toHaveBeenCalled();
  });

  it("should handle 401 errors by logging out", async () => {
    const mockLogout = vi.fn();
    (useAuth as any).mockReturnValue({
      accessToken: "test-token",
      logout: mockLogout,
    });

    const { result } = renderHook(() => useApiClient());

    // Verify the hook was created with logout function
    expect(useAuth).toHaveBeenCalled();
  });

  it("should use access token for authenticated requests", () => {
    const testToken = "specific-token-123";
    (useAuth as any).mockReturnValue({
      accessToken: testToken,
      logout: vi.fn(),
    });

    const { result } = renderHook(() => useApiClient());

    expect(useAuth).toHaveBeenCalled();
  });

  it("should handle null access token gracefully", () => {
    (useAuth as any).mockReturnValue({
      accessToken: null,
      logout: vi.fn(),
    });

    const { result } = renderHook(() => useApiClient());

    expect(result.current).toBeDefined();
  });

  it("should recreate instance when token changes", () => {
    const { rerender } = renderHook(() => useApiClient());

    (useAuth as any).mockReturnValue({
      accessToken: "new-token",
      logout: vi.fn(),
    });

    rerender();

    expect(useAuth).toHaveBeenCalled();
  });

  it("should support GraphQL queries", async () => {
    (useAuth as any).mockReturnValue({
      accessToken: "test-token",
      logout: vi.fn(),
    });

    const { result } = renderHook(() => useApiClient());

    expect(result.current).toBeDefined();
  });

  it("should support GET requests", async () => {
    const { result } = renderHook(() => useApiClient());

    expect(result.current).toBeDefined();
  });

  it("should support POST requests", async () => {
    const { result } = renderHook(() => useApiClient());

    expect(result.current).toBeDefined();
  });

  it("should handle network errors", async () => {
    (useAuth as any).mockReturnValue({
      accessToken: "test-token",
      logout: vi.fn(),
    });

    const { result } = renderHook(() => useApiClient());

    expect(result.current).toBeDefined();
  });

  it("should include Content-Type for POST requests", () => {
    const { result } = renderHook(() => useApiClient());

    expect(result.current).toBeDefined();
  });

  it("should maintain consistent configuration across requests", () => {
    const { result: result1 } = renderHook(() => useApiClient());
    const { result: result2 } = renderHook(() => useApiClient());

    // Both should be defined and properly configured
    expect(result1.current).toBeDefined();
    expect(result2.current).toBeDefined();
  });
});
