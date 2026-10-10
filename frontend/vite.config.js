import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  server: {
    host: "0.0.0.0",
    proxy: {
      "/api": {
        target: process.env.VITE_GATEWAY_URL || "http://localhost:8080",
        changeOrigin: true
      },
      "/actuator": {
        target: process.env.VITE_GATEWAY_URL || "http://localhost:8080",
        changeOrigin: true
      }
    }
  },
  preview: { host: "0.0.0.0" }
});
