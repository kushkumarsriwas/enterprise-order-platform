// Free Render instances sleep after 15 minutes without visitors, and only a request from outside Render wakes
// one up: when the API gateway calls a sleeping service, Render answers 502 and the service stays asleep.
// So while the page is open and visible, the browser itself pings every service of the deployment
// (VITE_WAKE_URLS, set only on Render): they all start together and stay awake for as long as someone is here.
// Locally and in Docker the variable is empty and nothing is sent.
const URLS = (import.meta.env.VITE_WAKE_URLS || "")
  .split(",")
  .map((url) => url.trim().replace(/\/$/, ""))
  .filter(Boolean);
const EVERY_MS = 4 * 60 * 1000;
let lastPing = 0;

function ping() {
  lastPing = Date.now();
  // the answer is not needed (and not readable from another origin): the request alone wakes the service
  URLS.forEach((url) => fetch(`${url}/actuator/health`, { mode: "no-cors", cache: "no-store" }).catch(() => {}));
}

export function keepServicesAwake() {
  if (URLS.length === 0) return;
  ping();
  setInterval(() => {
    if (document.visibilityState === "visible") ping();
  }, EVERY_MS);
  document.addEventListener("visibilitychange", () => {
    if (document.visibilityState === "visible" && Date.now() - lastPing > EVERY_MS) ping();
  });
}
