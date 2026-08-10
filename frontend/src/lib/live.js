// Live updates: the server pushes small "something changed" events (village, reports, map, market, tribe, achievements) over
// one server-sent-events stream (GET /api/events) and the app refetches the affected endpoint. Nothing here carries game data,
// so a missed event costs at most one slow poll. fetch() is used instead of EventSource because every request of this app sends
// its login token and world/village as headers, which EventSource cannot do.
import { useEffect, useRef, useSyncExternalStore } from "react";
import { openStream } from "../api";

/**
 * Turns text chunks of a text/event-stream into events: `feed(chunk)` calls `onEvent({ event, data })` for every complete
 * event. Handles lines split across chunks, CR / LF / CRLF endings, ": comment" lines (the server's heartbeat) and multi-line data.
 */
export function createParser(onEvent) {
  let buf = "";
  let event = "message";
  let data = [];
  return {
    feed(chunk) {
      buf += chunk;
      for (;;) {
        const m = /\r\n|\n|\r/.exec(buf);
        if (!m) break;
        if (m[0] === "\r" && m.index === buf.length - 1) break; // may be the first half of a CRLF: wait for the next chunk
        const line = buf.slice(0, m.index);
        buf = buf.slice(m.index + m[0].length);
        if (line === "") {
          if (data.length) onEvent({ event, data: data.join("\n") });
          event = "message";
          data = [];
        } else if (!line.startsWith(":")) {
          const c = line.indexOf(":");
          const field = c < 0 ? line : line.slice(0, c);
          let value = c < 0 ? "" : line.slice(c + 1);
          if (value.startsWith(" ")) value = value.slice(1);
          if (field === "event") event = value;
          else if (field === "data") data.push(value);
        }
      }
    },
  };
}

/** Delay before reconnect attempt number `attempt` (0-based): 1 s, 2 s, 4 s, ... at most 10 s. */
export const backoff = (attempt) => Math.min(10000, 1000 * 2 ** attempt);

/** How long the stream may be silent before it is considered dead (the server pings every 20 s). */
export const SILENCE_MS = 60000;

/**
 * Keeps one stream open until the returned function is called: reconnects with backoff, and reports `onConnection(true/false)`.
 * `onEvent({ event, data })` gets every event, including the server's "hello" right after each (re)connect.
 */
export function openLive(onEvent, onConnection = () => {}) {
  const stop = new AbortController();
  const sleep = (ms) => new Promise((resolve) => {
    const t = setTimeout(resolve, ms);
    stop.signal.addEventListener("abort", () => { clearTimeout(t); resolve(); }, { once: true });
  });
  (async () => {
    let attempt = 0;
    while (!stop.signal.aborted) {
      const conn = new AbortController();
      const onStop = () => conn.abort();
      stop.signal.addEventListener("abort", onStop, { once: true });
      let watchdog = null;
      const armWatchdog = () => {
        clearTimeout(watchdog);
        watchdog = setTimeout(() => conn.abort(), SILENCE_MS);
      };
      try {
        const res = await openStream("/events", conn.signal);
        if (!res.ok || !res.body) throw new Error("stream refused: " + res.status);
        attempt = 0;
        onConnection(true);
        armWatchdog();
        const reader = res.body.getReader();
        const decoder = new TextDecoder();
        const parser = createParser(onEvent);
        for (;;) {
          const { done, value } = await reader.read();
          if (done) break;
          armWatchdog();
          parser.feed(decoder.decode(value, { stream: true }));
        }
      } catch {
        /* dropped, refused or silent for too long: fall through and reconnect */
      } finally {
        clearTimeout(watchdog);
        stop.signal.removeEventListener("abort", onStop);
      }
      onConnection(false);
      if (stop.signal.aborted) return;
      await sleep(backoff(attempt++));
    }
  })();
  return () => stop.abort();
}

const flights = new Map();
/**
 * Runs `fn`; calls with the same `key` made while it runs collapse into ONE more run of the newest `fn` afterwards, so
 * refetches never pile up and an older answer can never overwrite a newer one.
 */
export async function runCoalesced(key, fn) {
  let flight = flights.get(key);
  if (flight) {
    flight.again = true;
    flight.fn = fn;
    return;
  }
  flight = { again: false, fn };
  flights.set(key, flight);
  try {
    do {
      flight.again = false;
      await flight.fn();
    } while (flight.again);
  } finally {
    flights.delete(key);
  }
}

// screens subscribe to topics they care about (market, achievements, ...); App forwards every event here
const listeners = new Set();
export function emitLive(topic) {
  for (const l of [...listeners]) l(topic);
}

/** Calls `fn()` whenever the server reports a change of `topic` (always with the latest `fn`). */
export function useLiveTopic(topic, fn) {
  const latest = useRef(fn);
  useEffect(() => {
    latest.current = fn;
  });
  useEffect(() => {
    const l = (t) => t === topic && latest.current?.();
    listeners.add(l);
    return () => listeners.delete(l);
  }, [topic]);
}

// whether the stream is up: screens that poll on their own poll slowly meanwhile (the events tell them when to refetch)
let connected = false;
const connectionListeners = new Set();
export function setConnected(value) {
  if (connected === value) return;
  connected = value;
  connectionListeners.forEach((l) => l());
}

export function useConnected() {
  return useSyncExternalStore(
    (cb) => {
      connectionListeners.add(cb);
      return () => connectionListeners.delete(cb);
    },
    () => connected
  );
}

/** The polling interval to use: `fastMs` normally, `slowMs` while the live stream is connected. */
export function usePollMs(fastMs, slowMs) {
  return useConnected() ? slowMs : fastMs;
}
