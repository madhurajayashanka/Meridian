import { useCallback } from "react";
import { useAuthStore } from "./useAuth";

const API_URL = process.env.NEXT_PUBLIC_API_URL;

export const useApiClient = () => {
  const { accessToken, refreshToken } = useAuthStore();

  const makeRequest = useCallback(
    async (
      endpoint: string,
      method: "GET" | "POST" | "PUT" | "DELETE" = "GET",
      body?: any,
    ) => {
      const headers: Record<string, string> = {};

      if (accessToken) {
        headers["Authorization"] = `Bearer ${accessToken}`;
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

        if (response.status === 401) {
          // Token might be expired, could implement refresh here
          useAuthStore.getState().clearAuth();
          window.location.href = "/login";
          return;
        }

        return await response.json();
      } catch (error) {
        console.error("API request failed:", error);
        throw error;
      }
    },
    [accessToken, refreshToken],
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

  return {
    makeRequest,
    get,
    post,
    put,
    delete: del,
    getAccessToken: () => accessToken,
  };
};
