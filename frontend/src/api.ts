import axios, { AxiosError } from "axios";
import type { ApiError } from "./types";

// Base URL:
//   - Docker/nginx build: VITE_API_BASE_URL is baked in (e.g. http://localhost:8080/api)
//   - Local `npm run dev`: falls back to "/api", which Vite proxies to :8080
const baseURL = import.meta.env.VITE_API_BASE_URL ?? "/api";

const TOKEN_KEY = "keystone.token";

export function getToken(): string | null {
  return window.localStorage.getItem(TOKEN_KEY);
}

export function setToken(token: string | null): void {
  if (token) {
    window.localStorage.setItem(TOKEN_KEY, token);
  } else {
    window.localStorage.removeItem(TOKEN_KEY);
  }
}

export const api = axios.create({ baseURL });

// Attach the JWT to every request if we have one.
api.interceptors.request.use((config) => {
  const token = getToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// On 401, drop the (now invalid) token so the app returns to the login screen.
api.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ApiError>) => {
    if (error.response?.status === 401) {
      setToken(null);
    }
    return Promise.reject(error);
  }
);

/** Extract a human-readable message from an axios error. */
export function errorMessage(error: unknown): string {
  const err = error as AxiosError<ApiError>;
  if (err.response?.data?.message) {
    return err.response.data.message;
  }
  if (err.message) {
    return err.message;
  }
  return "Something went wrong. Please try again.";
}

/**
 * The per-field complaints from a 400, keyed by field name.
 *
 * Bean Validation failures come back as a map so a form can put each message
 * next to the input that caused it instead of dumping "Validation failed" at the
 * top and leaving the user to guess which field it meant.
 */
export function fieldErrors(error: unknown): Record<string, string> {
  const err = error as AxiosError<ApiError>;
  return err.response?.data?.fieldErrors ?? {};
}

/** The HTTP status of a failed call, or 0 if the request never reached the server. */
export function errorStatus(error: unknown): number {
  const err = error as AxiosError<ApiError>;
  return err.response?.status ?? 0;
}
