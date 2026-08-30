import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// Dev server runs on 5173; the backend is proxied so relative /api calls work
// without CORS during local `npm run dev`. In the Docker/nginx build we bake
// VITE_API_BASE_URL instead (see Dockerfile) and call the backend directly.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      "/api": {
        target: "http://localhost:8080",
        changeOrigin: true,
      },
    },
  },
});
