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
      const headers: any = {
        "Content-Type": "application/json",
      };

      if (accessToken) {
        headers["Authorization"] = `Bearer ${accessToken}`;
      }

      try {
        const response = await fetch(`${API_URL}${endpoint}`, {
          method,
          headers,
          body: body ? JSON.stringify(body) : undefined,
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

  return { makeRequest };
};
