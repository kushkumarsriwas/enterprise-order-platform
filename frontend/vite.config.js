import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// In development, /api is proxied to the gateway so the browser sees one origin (no CORS needed).
const gateway = {
  target: process.env.VITE_GATEWAY_URL || "http://localhost:8080",
  changeOrigin: true,
  configure: (proxy) => proxy.on("proxyReq", (proxyReq) => proxyReq.removeHeader("origin")),
};

// Two vendor chunks: React (needed by every page) and the chart library (recharts with its d3/redux helpers, only used by
// Seller Central, which is loaded lazily). Browsers keep both cached across releases of the app code. React gets its
// own group first, so the chart group (which takes the chart library's dependencies with it) never pulls React in.
const reactLibraries = /node_modules[\\/](react|react-dom|scheduler)[\\/]/;
const chartLibraries =
  /node_modules[\\/](recharts|d3-[a-z-]+|victory-vendor|internmap|decimal\.js-light|@reduxjs|redux|react-redux|reselect|immer|es-toolkit|eventemitter3|use-sync-external-store|clsx|tiny-invariant)[\\/]/;

export default defineConfig({
  plugins: [react()],
  server: { port: 5173, proxy: { "/api": gateway } },
  preview: { port: 4173, proxy: { "/api": gateway } },
  build: {
    rolldownOptions: {
      output: {
        codeSplitting: {
          groups: [
            { name: "react", test: reactLibraries, priority: 2 },
            { name: "charts", test: chartLibraries, priority: 1 },
          ],
        },
      },
    },
  },
});
