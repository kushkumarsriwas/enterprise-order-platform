import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// In development, /api is proxied to the gateway so the browser sees one origin (no CORS needed).
const gateway = {
  target: process.env.VITE_GATEWAY_URL || "http://localhost:8080",
  changeOrigin: true,
  configure: (proxy) => proxy.on("proxyReq", (proxyReq) => proxyReq.removeHeader("origin")),
};

export default defineConfig({
  plugins: [react()],
  server: { port: 5173, proxy: { "/api": gateway } },
  preview: { port: 4173, proxy: { "/api": gateway } },
});
