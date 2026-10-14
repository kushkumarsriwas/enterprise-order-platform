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
  get: () => {
    try {
      return localStorage.getItem(TOKEN_KEY);
    } catch {
      return null;
    }
  },
  set: (token) => {
    try {
      localStorage.setItem(TOKEN_KEY, token);
    } catch {
      /* storage unavailable */
    }
  },
  clear: () => {
    try {
      localStorage.removeItem(TOKEN_KEY);
    } catch {
      /* storage unavailable */
    }
  },
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
        throw new ApiError(0, "Cannot reach the server. Check your connection and try again.");
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

/** Downloads a generated report (PDF / Excel) with the user's token and saves it under the server's file name. */
export async function download(path) {
  const token = tokenStore.get();
  const response = await fetch(`${BASE}${path}`, { headers: token ? { Authorization: `Bearer ${token}` } : {} });
  if (!response.ok) {
    let message = `Download failed (${response.status})`;
    try {
      message = (await response.json()).message || message;
    } catch {
      /* not JSON */
    }
    throw new ApiError(response.status, message);
  }
  const blob = await response.blob();
  const disposition = response.headers.get("Content-Disposition") || "";
  const match = /filename\*?=(?:UTF-8'')?"?([^";]+)"?/i.exec(disposition);
  const name = match ? decodeURIComponent(match[1]) : path.split("/").pop();
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = name;
  document.body.appendChild(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 2000);
  return name;
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
    if (value !== undefined && value !== null && value !== "" && value !== false) search.set(key, value);
  });
  const result = search.toString();
  return result ? `?${result}` : "";
};
