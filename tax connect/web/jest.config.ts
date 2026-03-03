import type { Config } from "jest";

const config: Config = {
  preset: "ts-jest/presets/js-with-ts",
  testEnvironment: "jsdom",
  setupFilesAfterEnv: ["<rootDir>/jest.setup.ts"],
  moduleNameMapper: {
    "\\.(css|less|scss)$": "identity-obj-proxy"
  },
  transform: {
    "^.+\\.(ts|tsx)$": [
      "ts-jest",
      {
        useESM: true,
        tsconfig: "<rootDir>/tsconfig.app.json"
      }
    ]
  },
  extensionsToTreatAsEsm: [".ts", ".tsx"],
  collectCoverageFrom: [
    "src/components/chat/**/*.{ts,tsx}",
    "!src/components/chat/**/*.stories.*",
    "!src/components/chat/**/*.test.*"
  ],
  testPathIgnorePatterns: ["<rootDir>/src/components/CallControlBar.test.tsx"],
  coverageThreshold: {
    global: {
      branches: 0.65,
      functions: 0.65,
      lines: 0.65,
      statements: 0.65
    }
  }
};

export default config;
