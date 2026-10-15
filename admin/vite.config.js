import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// The Control Center is its own web application (port 5174 in development, 3001 in Docker), separate from the
// marketplace. In development /api is proxied to the gateway, so the browser sees one origin (no CORS needed).
const gateway = {
  target: process.env.VITE_GATEWAY_URL || "http://localhost:8080",
  changeOrigin: true,
  configure: (proxy) => proxy.on("proxyReq", (proxyReq) => proxyReq.removeHeader("origin")),
};

// Vendor chunks (React, then the chart library) stay cached in the browser across releases of the console itself.
const reactLibraries = /node_modules[\\/](react|react-dom|scheduler)[\\/]/;
const chartLibraries =
  /node_modules[\\/](recharts|d3-[a-z-]+|victory-vendor|internmap|decimal\.js-light|@reduxjs|redux|react-redux|reselect|immer|es-toolkit|eventemitter3|use-sync-external-store|clsx|tiny-invariant)[\\/]/;

export default defineConfig({
  plugins: [react()],
  server: { port: 5174, strictPort: true, proxy: { "/api": gateway } },
  preview: { port: 4174, proxy: { "/api": gateway } },
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
