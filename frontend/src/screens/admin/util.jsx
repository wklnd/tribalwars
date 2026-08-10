import { useCallback, useEffect, useRef, useState } from "react";

// load once (and again on reload()); `deps` restart the load
export function useLoad(fn, deps) {
  const [state, setState] = useState({ data: null, error: null, loading: true });
  const alive = useRef(true);
  const fnRef = useRef(fn);
  fnRef.current = fn;
  const load = useCallback(() => {
    setState((s) => ({ ...s, loading: true }));
    return fnRef.current().then(
      (data) => alive.current && setState({ data, error: null, loading: false }),
      (e) => alive.current && setState({ data: null, error: e.message || String(e), loading: false }),
    );
  }, []);
  useEffect(() => {
    alive.current = true;
    load();
    return () => { alive.current = false; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);
  return { ...state, reload: load };
}

export function fmtBytes(n) {
  if (n == null) return "-";
  if (n < 1024) return n + " B";
  if (n < 1024 * 1024) return (n / 1024).toFixed(1) + " KB";
  if (n < 1024 * 1024 * 1024) return (n / 1024 / 1024).toFixed(1) + " MB";
  return (n / 1024 / 1024 / 1024).toFixed(2) + " GB";
}

export function fmtUptime(sec) {
  if (sec == null) return "-";
  const d = Math.floor(sec / 86400), h = Math.floor((sec % 86400) / 3600), m = Math.floor((sec % 3600) / 60), s = Math.floor(sec % 60);
  return (d ? d + "d " : "") + (d || h ? h + "h " : "") + m + "m " + (d || h ? "" : s + "s");
}

export function fmtDate(v) {
  if (!v) return "-";
  const d = new Date(v);
  return isNaN(d) ? String(v) : d.toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" });
}

export const fmtInt = (n) => (n == null ? "-" : Math.round(Number(n)).toLocaleString("en-US"));

// "1,5" / " 3 " -> number, "" -> undefined, anything else -> NaN
export function toNum(s) {
  const t = String(s ?? "").trim().replace(",", ".");
  if (t === "") return undefined;
  return /^-?\d+(\.\d+)?$/.test(t) ? Number(t) : NaN;
}

export const plural = (n, one, many) => `${fmtInt(n)} ${n === 1 ? one : many ?? one + "s"}`;
