import { useEffect, useState, useRef, useCallback } from "react";

const AI_URL = process.env.NEXT_PUBLIC_AI_URL || "http://localhost:8080";
const MAX_RECONNECT_DELAY_MS = 30_000;

export interface SSEEvent {
  timestamp: number;
  type: string;
  data: any;
}

export function useSSE(jobId: string | null, accessToken: string | null) {
  const [events, setEvents] = useState<SSEEvent[]>([]);
  const [connected, setConnected] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState(false);

  const esRef = useRef<EventSource | null>(null);
  const reconnectDelay = useRef(1000);
  const reconnectTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const push = useCallback((type: string, data: any) => {
    setEvents((prev) => [...prev, { timestamp: Date.now(), type, data }]);
  }, []);

  const cleanup = useCallback(() => {
    if (reconnectTimer.current) clearTimeout(reconnectTimer.current);
    if (esRef.current) {
      esRef.current.close();
      esRef.current = null;
    }
    setConnected(false);
  }, []);

  const connect = useCallback(() => {
    if (!jobId || !accessToken || done) return;
    cleanup();

    const url = `${AI_URL}/ai/stream/${jobId}?token=${encodeURIComponent(accessToken)}`;
    const es = new EventSource(url);
    esRef.current = es;

    es.addEventListener("open", () => {
      setConnected(true);
      setError(null);
      reconnectDelay.current = 1000; // reset backoff on successful connect
    });

    for (const eventType of ["agent_update", "message", "heartbeat"]) {
      es.addEventListener(eventType, (e: MessageEvent) => {
        try { push(eventType, JSON.parse(e.data)); } catch { /* ignore parse errors */ }
      });
    }

    es.addEventListener("job_complete", (e: MessageEvent) => {
      try { push("job_complete", JSON.parse(e.data)); } catch { /* ignore */ }
      setDone(true);
      cleanup();
    });

    es.addEventListener("job_failed", (e: MessageEvent) => {
      try { push("job_failed", JSON.parse(e.data)); } catch { /* ignore */ }
      setDone(true);
      cleanup();
    });

    es.addEventListener("error", () => {
      cleanup();
      if (done) return;
      setError("Connection lost — reconnecting…");
      reconnectTimer.current = setTimeout(() => {
        reconnectDelay.current = Math.min(reconnectDelay.current * 2, MAX_RECONNECT_DELAY_MS);
        connect();
      }, reconnectDelay.current);
    });
  }, [jobId, accessToken, done, cleanup, push]);

  useEffect(() => {
    connect();
    return cleanup;
  }, [connect, cleanup]);

  return { events, connected, error, done };
}
