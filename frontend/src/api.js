// Thin fetch wrapper around the API gateway.
// - adds the JWT and turns the uniform error body of the services into an ApiError
// - never shows raw HTML: proxies (Render, nginx) answer 502/503/504 with an HTML page while a service starts
// - retries while the free Render instances wake up (~1 minute after a period without traffic)
const BASE = (import.meta.env.VITE_API_BASE_URL || "").replace(/\/$/, "");
const TOKEN_KEY = "nexora_token";
const WAKE_UP_MS = 100000;

export class ApiError extends Error {
  constructor(status, message, fieldErrors) {
    super(message);
    this.status = status;
    this.fieldErrors = fieldErrors || null;
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
      /* storage unavailable: the session lasts until the tab closes */
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

const GATEWAY_MESSAGES = {
  502: "Nexora is starting up. Please try again in a moment.",
  503: "This part of Nexora is temporarily unavailable. Please try again in a moment.",
  504: "Nexora took too long to answer. Please try again.",
};

/** Reads an error body: our services send JSON; proxies may send HTML, which must never reach the screen. */
async function errorFrom(response) {
  const text = await response.text().catch(() => "");
  let data = null;
  if (text && /^\s*[{[]/.test(text)) {
    try {
      data = JSON.parse(text);
    } catch {
      data = null;
    }
  }
  const message =
    data?.message ||
    GATEWAY_MESSAGES[response.status] ||
    (response.status === 401 ? "Please sign in again." : null) ||
    (response.status === 403 ? "You do not have access to this." : null) ||
    (response.status === 404 ? "We could not find what you were looking for." : null) ||
    `Something went wrong (error ${response.status}). Please try again.`;
  return new ApiError(response.status, message, data?.fieldErrors);
}

/**
 * GET requests are retried while the platform wakes up. Other methods are retried only on 502/503 - the
 * request never reached a service - never on 504, where it may have been processed (no duplicate orders).
 */
async function send(path, init, { auth = true, retryWrites = true } = {}) {
  const token = tokenStore.get();
  const headers = { Accept: "application/json", ...(init.headers || {}) };
  if (auth && token) headers.Authorization = `Bearer ${token}`;
  const method = init.method || "GET";
  const started = Date.now();
  const slowTimer = setTimeout(() => onSlowStart(true), 3500);
  try {
    for (let attempt = 0; ; attempt++) {
      let response;
      try {
        response = await fetch(`${BASE}${path}`, { ...init, headers });
      } catch {
        if (method === "GET" && Date.now() - started < WAKE_UP_MS) {
          await sleep(2500);
          continue;
        }
        throw new ApiError(0, "Cannot reach Nexora. Check your internet connection and try again.");
      }
      const waking = response.status === 502 || response.status === 503 || (method === "GET" && response.status === 504);
      const mayRetry = method === "GET" || (retryWrites && attempt < 3);
      if (waking && mayRetry && Date.now() - started < WAKE_UP_MS) {
        await sleep(method === "GET" ? 2500 : 3000);
        continue;
      }
      if (!response.ok) {
        const error = await errorFrom(response);
        if (response.status === 401 && auth && token) onUnauthorized();
        throw error;
      }
      return response;
    }
  } finally {
    clearTimeout(slowTimer);
    onSlowStart(false);
  }
}

export async function api(path, { method = "GET", body, auth = true } = {}) {
  const init = { method };
  if (body !== undefined) {
    init.headers = { "Content-Type": "application/json" };
    init.body = JSON.stringify(body);
  }
  const response = await send(path, init, { auth });
  if (response.status === 204) return null;
  const text = await response.text();
  if (!text) return null;
  try {
    return JSON.parse(text);
  } catch {
    throw new ApiError(response.status, "Nexora returned an unexpected answer. Please try again.");
  }
}

/** multipart/form-data upload (product photos, Excel imports). */
export async function upload(path, formData, { method = "POST" } = {}) {
  const response = await send(path, { method, body: formData }, { retryWrites: false });
  const text = await response.text();
  return text ? JSON.parse(text) : null;
}

/** Downloads a generated document (PDF / Excel) and saves it under the server's file name. */
export async function download(path) {
  const response = await send(path, { method: "GET" });
  const blob = await response.blob();
  const disposition = response.headers.get("Content-Disposition") || "";
  const match = /filename\*?=(?:UTF-8'')?"?([^";]+)"?/i.exec(disposition);
  const name = match ? decodeURIComponent(match[1]) : path.split("?")[0].split("/").pop();
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = name;
  document.body.appendChild(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 4000);
  return name;
}

/** Query string from an object; skips empty values; arrays become repeated parameters (?status=A&status=B). */
export const qs = (params = {}) => {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (Array.isArray(value)) value.filter((v) => v !== "" && v != null).forEach((v) => search.append(key, v));
    else if (value !== undefined && value !== null && value !== "" && value !== false) search.set(key, value);
  });
  const result = search.toString();
  return result ? `?${result}` : "";
};

export const imageUrl = (product) => {
  if (!product) return null;
  if (product.imageUrl && /^https?:\/\//.test(product.imageUrl)) return product.imageUrl;
  if (product.imageUrl) return `${BASE}${product.imageUrl}`;
  return null;
};
