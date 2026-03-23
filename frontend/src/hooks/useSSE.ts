import { useEffect, useState, useRef, useCallback } from "react";

interface SSEEvent {
  timestamp: number;
  type: string;
  data: any;
}

export function useSSE(jobId: string, accessToken: string) {
  const [events, setEvents] = useState<SSEEvent[]>([]);
  const [connected, setConnected] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const eventSourceRef = useRef<EventSource | null>(null);

  const connect = useCallback(() => {
    if (!jobId || !accessToken) {
      return;
    }

    if (eventSourceRef.current) {
      return;
    }

    try {
      const url = `${process.env.NEXT_PUBLIC_API_URL || "http://localhost:4000"}/api/jobs/${jobId}/live?token=${accessToken}`;
      const eventSource = new EventSource(url);

      eventSource.addEventListener("message", (event) => {
        try {
          const data = JSON.parse(event.data);
          setEvents((prev) => [
            ...prev,
            {
              timestamp: Date.now(),
              type: "message",
              data,
            },
          ]);
          setError(null);
        } catch (e) {
          console.error("Failed to parse SSE message:", e);
        }
      });

      eventSource.addEventListener("agent_update", (event) => {
        try {
          const data = JSON.parse(event.data);
          setEvents((prev) => [
            ...prev,
            {
              timestamp: Date.now(),
              type: "agent_update",
              data,
            },
          ]);
          setError(null);
        } catch (e) {
          console.error("Failed to parse agent update:", e);
        }
      });

      eventSource.addEventListener("job_complete", (event) => {
        try {
          const data = JSON.parse(event.data);
          setEvents((prev) => [
            ...prev,
            {
              timestamp: Date.now(),
              type: "job_complete",
              data,
            },
          ]);
          eventSource.close();
          setConnected(false);
        } catch (e) {
          console.error("Failed to parse job complete event:", e);
        }
      });

      eventSource.addEventListener("error", () => {
        setError("Connection lost");
        setConnected(false);
        eventSource.close();
        eventSourceRef.current = null;
        // Attempt to reconnect after a delay
        setTimeout(connect, 5000);
      });

      eventSource.addEventListener("open", () => {
        setConnected(true);
        setError(null);
      });

      eventSourceRef.current = eventSource;
    } catch (e) {
      const errorMessage = e instanceof Error ? e.message : "Failed to connect";
      setError(errorMessage);
      setConnected(false);
    }
  }, [jobId, accessToken]);

  useEffect(() => {
    connect();

    return () => {
      if (eventSourceRef.current) {
        eventSourceRef.current.close();
        eventSourceRef.current = null;
      }
    };
  }, [connect]);

  return { events, connected, error };
}
