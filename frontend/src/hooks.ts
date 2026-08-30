/**
 * The two hooks every screen needs: load-once-and-reload, and a debounced value
 * for search boxes.
 *
 * There is no react-query here on purpose — one small, readable hook is easier to
 * justify in a code review than a caching library used at five percent of its
 * surface area. What it does have to get right is staleness: a user who types
 * "pump", then "pumps", then clears the box fires three overlapping requests, and
 * without the sequence check below the slowest one wins and the screen shows
 * results for a query that is no longer on screen.
 */

import { useCallback, useEffect, useRef, useState } from "react";
import { errorMessage } from "./api";

export interface AsyncState<T> {
  data: T | null;
  loading: boolean;
  error: string | null;
  /** Re-run the fetcher, e.g. after a write. */
  reload: () => void;
  /**
   * Replace the loaded value without a round trip.
   *
   * Every work-order mutation returns the whole updated detail, so the page can
   * swap it in directly instead of firing a second GET.
   */
  setData: (value: T) => void;
}

/**
 * Run `fetcher` on mount and whenever `deps` change.
 *
 * `fetcher` is intentionally *not* in the dependency list — an inline arrow
 * function is a new object on every render, so including it would loop forever.
 * `deps` is the honest declaration of what the request actually varies on.
 */
export function useApi<T>(fetcher: () => Promise<T>, deps: unknown[] = []): AsyncState<T> {
  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [tick, setTick] = useState(0);

  // Monotonic request id: only the newest response is allowed to write state.
  const latest = useRef(0);
  // Guards against setting state after the component has gone.
  const mounted = useRef(true);

  const fetcherRef = useRef(fetcher);
  fetcherRef.current = fetcher;

  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);

  useEffect(() => {
    const id = ++latest.current;
    setLoading(true);
    setError(null);

    fetcherRef
      .current()
      .then((result) => {
        if (mounted.current && id === latest.current) {
          setData(result);
          setLoading(false);
        }
      })
      .catch((err: unknown) => {
        if (mounted.current && id === latest.current) {
          setError(errorMessage(err));
          setLoading(false);
        }
      });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, tick]);

  const reload = useCallback(() => setTick((n) => n + 1), []);

  return { data, loading, error, reload, setData };
}

/**
 * The value, but only after it has stopped changing for `delay` ms.
 *
 * Used on search inputs so that typing a SKU is one request instead of eight.
 */
export function useDebounced<T>(value: T, delay = 350): T {
  const [settled, setSettled] = useState(value);

  useEffect(() => {
    const timer = window.setTimeout(() => setSettled(value), delay);
    return () => window.clearTimeout(timer);
  }, [value, delay]);

  return settled;
}

/**
 * A one-shot async action with its own pending and error state.
 *
 * Every write on the detail page needs the same three things: disable the button
 * while it is in flight, show the server's message if it is refused, and hand the
 * result back on success. Without this they would each grow their own copy.
 */
export interface Action {
  run: (task: () => Promise<void>) => Promise<void>;
  busy: boolean;
  error: string | null;
  clearError: () => void;
}

export function useAction(): Action {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const run = useCallback(async (task: () => Promise<void>) => {
    setBusy(true);
    setError(null);
    try {
      await task();
    } catch (err: unknown) {
      // A refusal is expected traffic here — a 409 for a rule the user broke, or
      // a 403 for one they were never allowed to break. Both are shown verbatim.
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  }, []);

  const clearError = useCallback(() => setError(null), []);

  return { run, busy, error, clearError };
}
