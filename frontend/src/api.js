// Thin fetch wrapper around the API gateway.
// - adds the JWT, parses the uniform error body of the services
// - retries 502/503/504 for a while: free Render instances need ~1 minute to wake up
const BASE = (import.meta.env.VITE_API_BASE_URL || "").replace(/\/$/, "");
const TOKEN_KEY = "nexora_token";

export class ApiError extends Error {
  constructor(status, message, details) {
    super(message);
    this.status = status;
    this.details = details;
  }
}

let onUnauthorized = () => {};
let onSlowStart = () => {};
export const setUnauthorizedHandler = (fn) => (onUnauthorized = fn);
export const setSlowStartHandler = (fn) => (onSlowStart = fn);

export const tokenStore = {
  get: () => localStorage.getItem(TOKEN_KEY),
  set: (token) => localStorage.setItem(TOKEN_KEY, token),
  clear: () => localStorage.removeItem(TOKEN_KEY),
};

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

export async function api(path, { method = "GET", body, auth = true } = {}) {
  const headers = { Accept: "application/json" };
  if (body !== undefined) headers["Content-Type"] = "application/json";
  const token = tokenStore.get();
  if (auth && token) headers.Authorization = `Bearer ${token}`;

  const started = Date.now();
  const slowTimer = setTimeout(() => onSlowStart(true), 4000);
  try {
    for (let attempt = 0; ; attempt++) {
      let response;
      try {
        response = await fetch(`${BASE}${path}`, {
          method,
          headers,
          body: body === undefined ? undefined : JSON.stringify(body),
        });
      } catch {
        if (Date.now() - started < 90000 && method === "GET") {
          await sleep(3000);
          continue;
        }
        throw new ApiError(0, "Cannot reach the server. Please check your connection and retry.");
      }
      const retryable = [502, 503, 504].includes(response.status);
      if (retryable && Date.now() - started < 90000 && (method === "GET" || attempt < 2)) {
        await sleep(3000);
        continue;
      }
      if (response.status === 204) return null;
      const text = await response.text();
      const data = text ? safeJson(text) : null;
      if (!response.ok) {
        if (response.status === 401 && auth && token && !path.startsWith("/api/auth/")) onUnauthorized();
        throw new ApiError(response.status, data?.message || `Request failed (${response.status})`, data?.fieldErrors);
      }
      return data;
    }
  } finally {
    clearTimeout(slowTimer);
    onSlowStart(false);
  }
}

function safeJson(text) {
  try {
    return JSON.parse(text);
  } catch {
    return { message: text };
  }
}

export const qs = (params) => {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== "") search.set(key, value);
  });
  const result = search.toString();
  return result ? `?${result}` : "";
};
