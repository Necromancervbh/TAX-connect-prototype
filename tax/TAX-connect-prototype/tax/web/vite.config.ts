import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  test: {
    globals: true,
    environment: "jsdom",
    setupFiles: "./src/setupTests.ts",
    coverage: {
      provider: "v8",
      reporter: ["text", "html"],
      include: ["src/components/CallControlBar.tsx"],
      exclude: ["**/*.stories.*"],
      thresholds: {
        lines: 100,
        branches: 100,
        functions: 100,
        statements: 100
      }
    }
  }
});
