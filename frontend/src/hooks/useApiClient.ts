import { useCallback, useMemo } from "react";
import { useAuthStore } from "./useAuth";

const API_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8000";
const TOKEN_EXPIRY_BUFFER_MS = 30_000;

const REFRESH_MUTATION = `
  mutation RefreshToken($refreshToken: String!) {
    refreshToken(refreshToken: $refreshToken) {
      accessToken
      refreshToken
      user {
        id
        email
      }
    }
  }
`;

const base64UrlDecode = (value: string): string => {
  const normalized = value.replace(/-/g, "+").replace(/_/g, "/");
  const padded = normalized.padEnd(Math.ceil(normalized.length / 4) * 4, "=");

  if (typeof atob === "function") {
    return atob(padded);
  }

  return Buffer.from(padded, "base64").toString("utf-8");
};

const isTokenExpired = (token: string | null | undefined): boolean => {
  if (!token) {
    return true;
  }

  try {
    const [, payloadPart] = token.split(".");
    if (!payloadPart) {
      return true;
    }

    const payload = JSON.parse(base64UrlDecode(payloadPart)) as {
      exp?: number;
    };

    if (typeof payload.exp !== "number") {
      return true;
    }

    return payload.exp * 1000 <= Date.now() + TOKEN_EXPIRY_BUFFER_MS;
  } catch {
    return true;
  }
};

const hasUnauthenticatedGraphQlError = (payload: any): boolean => {
  if (!Array.isArray(payload?.errors)) {
    return false;
  }

  return payload.errors.some((error: any) => {
    const message = String(error?.message || "").toLowerCase();
    const code = String(error?.extensions?.code || "").toUpperCase();

    return (
      code === "UNAUTHENTICATED" ||
      message.includes("not authenticated") ||
      message.includes("invalid uuid string: anonymoususer")
    );
  });
};

const isAuthGraphQlOperation = (body: any): boolean => {
  const query = String(body?.query || "");

  return (
    query.includes("login(") ||
    query.includes("register(") ||
    query.includes("refreshToken(")
  );
};

export const useApiClient = () => {
  const accessToken = useAuthStore((state) => state.accessToken);
  const refreshToken = useAuthStore((state) => state.refreshToken);

  const tryRefreshToken = useCallback(async (): Promise<string | null> => {
    if (!refreshToken) {
      return null;
    }

    try {
      const response = await fetch(`${API_URL}/graphql`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          query: REFRESH_MUTATION,
          variables: { refreshToken },
        }),
      });

      const payload = await response.json();
      const refreshed = payload?.data?.refreshToken;
      if (!response.ok || payload?.errors?.length || !refreshed?.accessToken) {
        useAuthStore.getState().clearAuth();
        return null;
      }

      useAuthStore
        .getState()
        .setAuth(
          refreshed.accessToken,
          refreshed.refreshToken,
          String(refreshed.user?.id || ""),
          String(refreshed.user?.email || ""),
        );

      return refreshed.accessToken;
    } catch {
      useAuthStore.getState().clearAuth();
      return null;
    }
  }, [refreshToken]);

  const makeRequest = useCallback(
    async (
      endpoint: string,
      method: "GET" | "POST" | "PUT" | "DELETE" = "GET",
      body?: any,
      hasRetried = false,
    ) => {
      const headers: Record<string, string> = {};
      const isProtectedGraphQlRequest =
        endpoint === "/graphql" && !isAuthGraphQlOperation(body);

      let tokenToUse = accessToken;
      const needsRefresh = !tokenToUse || isTokenExpired(tokenToUse);
      if (needsRefresh && refreshToken) {
        tokenToUse = await tryRefreshToken();
      }

      if (
        isProtectedGraphQlRequest &&
        (!tokenToUse || isTokenExpired(tokenToUse))
      ) {
        useAuthStore.getState().clearAuth();
        if (typeof window !== "undefined") {
          window.location.href = "/login";
        }
        throw new Error("Your session has expired. Please sign in again.");
      }

      if (tokenToUse) {
        headers["Authorization"] = `Bearer ${tokenToUse}`;
      }

      const hasBody = body !== undefined && body !== null;
      const requestBody =
        hasBody && body instanceof FormData
          ? body
          : hasBody
            ? JSON.stringify(body)
            : undefined;

      if (!(body instanceof FormData)) {
        headers["Content-Type"] = "application/json";
      }

      try {
        const response = await fetch(`${API_URL}${endpoint}`, {
          method,
          headers,
          body: requestBody,
        });

        const payload = await response.json();

        const shouldAttemptRefresh =
          !hasRetried &&
          !!refreshToken &&
          (response.status === 401 || hasUnauthenticatedGraphQlError(payload));

        if (shouldAttemptRefresh) {
          const refreshedAccessToken = await tryRefreshToken();
          if (refreshedAccessToken) {
            return makeRequest(endpoint, method, body, true);
          }
        }

        if (response.status === 401) {
          useAuthStore.getState().clearAuth();
          window.location.href = "/login";
          return;
        }

        return payload;
      } catch (error) {
        console.error("API request failed:", error);
        throw error;
      }
    },
    [accessToken, refreshToken, tryRefreshToken],
  );

  const get = useCallback(
    (endpoint: string) => makeRequest(endpoint, "GET"),
    [makeRequest],
  );

  const post = useCallback(
    (endpoint: string, body?: any) => makeRequest(endpoint, "POST", body),
    [makeRequest],
  );

  const put = useCallback(
    (endpoint: string, body?: any) => makeRequest(endpoint, "PUT", body),
    [makeRequest],
  );

  const del = useCallback(
    (endpoint: string, body?: any) => makeRequest(endpoint, "DELETE", body),
    [makeRequest],
  );

  return useMemo(
    () => ({
      makeRequest,
      get,
      post,
      put,
      delete: del,
      getAccessToken: () => accessToken,
    }),
    [makeRequest, get, post, put, del, accessToken],
  );
};
