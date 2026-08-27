import { defineConfig } from "vitest/config";

export default defineConfig({
  test: {
    environment: "node",
    include: ["test/firestoreRules.test.ts", "test/couponTrashStore.test.ts"],
    fileParallelism: false,
    testTimeout: 20_000,
    hookTimeout: 20_000
  }
});
