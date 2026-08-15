import { pathToFileURL } from "node:url";
import { performance } from "node:perf_hooks";

const DEFAULT_BASE_URL = "http://127.0.0.1:3000";
const DEFAULT_RUNS = 5;
const VALID_VARIANTS = new Set(["thumbnail", "original"]);

export function percentile(values, ratio) {
  if (values.length === 0) throw new Error("측정값이 없습니다.");
  const sorted = [...values].sort((left, right) => left - right);
  const index = Math.max(0, Math.ceil(sorted.length * ratio) - 1);
  return sorted[index];
}

function positiveInteger(value, fallback) {
  if (value === undefined || value === "") return fallback;
  const parsed = Number(value);
  if (!Number.isInteger(parsed) || parsed < 1 || parsed > 100) {
    throw new Error("IMAGE_BENCHMARK_RUNS는 1~100 사이 정수여야 합니다.");
  }
  return parsed;
}

export function buildBenchmarkConfig(environment = process.env) {
  const baseUrl = new URL(environment.IMAGE_BENCHMARK_BASE_URL || DEFAULT_BASE_URL);
  const isLocal = baseUrl.hostname === "127.0.0.1" || baseUrl.hostname === "localhost";
  if (baseUrl.protocol !== "https:" && !(isLocal && baseUrl.protocol === "http:")) {
    throw new Error("원격 API는 HTTPS만 사용할 수 있습니다.");
  }

  const mode = environment.IMAGE_BENCHMARK_MODE || "health";
  const runs = positiveInteger(environment.IMAGE_BENCHMARK_RUNS, DEFAULT_RUNS);
  if (mode === "health") {
    return {
      mode,
      runs,
      url: new URL("/api/health", baseUrl).toString(),
      headers: { accept: "application/json" }
    };
  }
  if (mode !== "image") throw new Error("IMAGE_BENCHMARK_MODE는 health 또는 image여야 합니다.");

  const roomId = environment.IMAGE_BENCHMARK_ROOM_ID;
  const couponId = environment.IMAGE_BENCHMARK_COUPON_ID;
  const idToken = environment.IMAGE_BENCHMARK_ID_TOKEN;
  const variant = environment.IMAGE_BENCHMARK_VARIANT || "thumbnail";
  if (!roomId || !couponId || !idToken) {
    throw new Error("image 모드에는 ROOM_ID, COUPON_ID, ID_TOKEN이 모두 필요합니다.");
  }
  if (!VALID_VARIANTS.has(variant)) {
    throw new Error("IMAGE_BENCHMARK_VARIANT는 thumbnail 또는 original이어야 합니다.");
  }

  const target = new URL("/api/coupons/image", baseUrl);
  target.searchParams.set("roomId", roomId);
  target.searchParams.set("couponId", couponId);
  target.searchParams.set("variant", variant);
  return {
    mode,
    variant,
    runs,
    url: target.toString(),
    headers: { accept: "image/*", authorization: `Bearer ${idToken}` }
  };
}

async function countResponseBytes(response) {
  if (!response.body) return 0;
  const reader = response.body.getReader();
  let bytes = 0;
  while (true) {
    const { done, value } = await reader.read();
    if (done) return bytes;
    bytes += value.byteLength;
  }
}

export async function measureEndpoint(config, fetchImpl = fetch, clock = performance) {
  const startedAt = clock.now();
  const response = await fetchImpl(config.url, {
    method: "GET",
    headers: config.headers,
    cache: "no-store"
  });
  const headersAt = clock.now();
  const responseBodyBytes = await countResponseBytes(response);
  const finishedAt = clock.now();
  if (!response.ok) {
    throw new Error(`API 응답 실패: HTTP ${response.status}`);
  }
  return {
    status: response.status,
    ttfbMs: headersAt - startedAt,
    totalMs: finishedAt - startedAt,
    responseBodyBytes,
    contentLength: Number(response.headers.get("content-length")) || null,
    serverTiming: response.headers.get("server-timing")
  };
}

export function summarizeSamples(samples) {
  const ttfb = samples.map((sample) => sample.ttfbMs);
  const total = samples.map((sample) => sample.totalMs);
  const round = (value) => Number(value.toFixed(3));
  return {
    runs: samples.length,
    ttfbMs: {
      p50: round(percentile(ttfb, 0.5)),
      p90: round(percentile(ttfb, 0.9)),
      max: round(Math.max(...ttfb))
    },
    totalMs: {
      p50: round(percentile(total, 0.5)),
      p90: round(percentile(total, 0.9)),
      max: round(Math.max(...total))
    },
    responseBodyBytes: [...new Set(samples.map((sample) => sample.responseBodyBytes))],
    contentLength: [...new Set(samples.map((sample) => sample.contentLength))],
    serverTiming: [...new Set(samples.map((sample) => sample.serverTiming).filter(Boolean))]
  };
}

export async function main() {
  const config = buildBenchmarkConfig();
  const samples = [];
  for (let index = 0; index < config.runs; index += 1) {
    samples.push(await measureEndpoint(config));
  }
  const safeTarget = new URL(config.url);
  safeTarget.searchParams.delete("roomId");
  safeTarget.searchParams.delete("couponId");
  console.log(JSON.stringify({
    mode: config.mode,
    variant: config.variant,
    target: safeTarget.toString(),
    ...summarizeSamples(samples)
  }, null, 2));
}

const invokedPath = process.argv[1] ? pathToFileURL(process.argv[1]).href : "";
if (import.meta.url === invokedPath) {
  main().catch((error) => {
    console.error(error instanceof Error ? error.message : String(error));
    process.exitCode = 1;
  });
}
