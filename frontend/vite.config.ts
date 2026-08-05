import { defineConfig } from "vite";

export default defineConfig({
  server: {
    port: 3000,
    proxy: {
      "/api": "http://localhost:18082",
      "/mock": "http://localhost:18082",
    },
  },
});
