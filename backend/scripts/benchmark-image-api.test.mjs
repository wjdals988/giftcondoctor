import test from "node:test";
import assert from "node:assert/strict";
import {
  buildBenchmarkConfig,
  measureEndpoint,
  percentile,
  summarizeSamples
} from "./benchmark-image-api.mjs";

test("health mode stays read-only and needs no credential", () => {
  const config = buildBenchmarkConfig({
    IMAGE_BENCHMARK_BASE_URL: "https://example.com",
    IMAGE_BENCHMARK_RUNS: "3"
  });
  assert.equal(config.mode, "health");
  assert.equal(config.runs, 3);
  assert.equal(config.url, "https://example.com/api/health");
  assert.equal(config.headers.authorization, undefined);
});

test("image mode keeps the Firebase token in a header", () => {
  const config = buildBenchmarkConfig({
    IMAGE_BENCHMARK_BASE_URL: "https://example.com",
    IMAGE_BENCHMARK_MODE: "image",
    IMAGE_BENCHMARK_ROOM_ID: "room-1",
    IMAGE_BENCHMARK_COUPON_ID: "coupon-1",
    IMAGE_BENCHMARK_ID_TOKEN: "secret-token",
    IMAGE_BENCHMARK_VARIANT: "original"
  });
  assert.equal(config.headers.authorization, "Bearer secret-token");
  assert.equal(new URL(config.url).searchParams.has("token"), false);
  assert.equal(config.variant, "original");
});

test("remote plain HTTP and incomplete image targets are rejected", () => {
  assert.throws(
    () => buildBenchmarkConfig({ IMAGE_BENCHMARK_BASE_URL: "http://example.com" }),
    /HTTPS/
  );
  assert.throws(
    () => buildBenchmarkConfig({
      IMAGE_BENCHMARK_BASE_URL: "https://example.com",
      IMAGE_BENCHMARK_MODE: "image"
    }),
    /모두 필요/
  );
});

test("measurement counts streamed bytes and uses nearest-rank percentiles", async () => {
  const ticks = [0, 12, 30];
  const sample = await measureEndpoint(
    { url: "https://example.com/api/health", headers: {} },
    async () => new Response(new Uint8Array(5), {
      status: 200,
      headers: { "content-length": "5", "server-timing": "app;dur=7" }
    }),
    { now: () => ticks.shift() }
  );
  assert.equal(sample.ttfbMs, 12);
  assert.equal(sample.totalMs, 30);
  assert.equal(sample.responseBodyBytes, 5);
  assert.equal(percentile([10, 20, 30, 40, 50], 0.9), 50);
  assert.deepEqual(summarizeSamples([sample]).totalMs, { p50: 30, p90: 30, max: 30 });
});
