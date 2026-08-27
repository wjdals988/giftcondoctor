import { afterEach, describe, expect, it } from "vitest";
import { GET as notificationStatus } from "../app/api/notifications/status/route";
import { couponBlobPrefix, requireCouponBlobPath } from "../lib/blobPath";
import { ApiError, requireCronSecret, serverTiming } from "../lib/http";
import { detectSupportedImage } from "../lib/imageUpload";
import { legacyCouponThumbnailPath } from "../lib/imageThumbnail";
import { rateLimitDocumentId, rateLimitWindow } from "../lib/rateLimit";

const originalCronSecret = process.env.CRON_SECRET;

afterEach(() => {
  if (originalCronSecret === undefined) delete process.env.CRON_SECRET;
  else process.env.CRON_SECRET = originalCronSecret;
});

describe("server timing", () => {
  it("formats bounded metrics and rejects free-form names", () => {
    expect(serverTiming([
      { name: "blob-store", durationMs: 12.34 },
      { name: "cleanup", durationMs: -2 }
    ])).toBe("blob-store;dur=12.3, cleanup;dur=0.0");
    expect(() => serverTiming([{ name: "bad metric", durationMs: 1 }])).toThrow();
  });
});

describe("cron authentication", () => {
  it("fails closed when CRON_SECRET is missing", () => {
    delete process.env.CRON_SECRET;
    expect(() => requireCronSecret(new Request("https://example.test"))).toThrowError(
      expect.objectContaining({ status: 503 })
    );
  });

  it("rejects an invalid secret and accepts the configured secret", () => {
    process.env.CRON_SECRET = "configured-secret";
    expect(() => requireCronSecret(new Request("https://example.test"))).toThrowError(
      expect.objectContaining({ status: 401 })
    );
    expect(() => requireCronSecret(new Request("https://example.test", {
      headers: { authorization: "Bearer configured-secret" }
    }))).not.toThrow();
  });

  it("keeps notification metrics private before accessing Firestore", async () => {
    delete process.env.CRON_SECRET;
    const missing = await notificationStatus(new Request("https://example.test/api/notifications/status"));
    expect(missing.status).toBe(503);

    process.env.CRON_SECRET = "configured-secret";
    const invalid = await notificationStatus(new Request("https://example.test/api/notifications/status"));
    expect(invalid.status).toBe(401);
  });
});

describe("coupon blob identity", () => {
  it("accepts only the current coupon prefix", () => {
    expect(couponBlobPrefix("room-1", "coupon_1")).toBe("rooms/room-1/coupons/coupon_1/");
    expect(requireCouponBlobPath(
      "rooms/room-1/coupons/coupon_1/image.jpg",
      "room-1",
      "coupon_1"
    )).toContain("coupon_1/image.jpg");
    expect(legacyCouponThumbnailPath("room-1", "coupon_1"))
      .toBe("rooms/room-1/coupons/coupon_1/thumbnail.webp");
  });

  it("rejects cross-coupon and malformed paths", () => {
    expect(() => requireCouponBlobPath(
      "rooms/room-1/coupons/other/image.jpg",
      "room-1",
      "coupon_1"
    )).toThrowError(ApiError);
    expect(() => couponBlobPrefix("../room", "coupon")).toThrowError(ApiError);
  });
});

describe("image allowlist", () => {
  it("detects JPEG, PNG and WebP by bytes instead of client MIME", () => {
    expect(detectSupportedImage(Uint8Array.from([0xff, 0xd8, 0xff]))).toMatchObject({ extension: "jpg" });
    expect(detectSupportedImage(Uint8Array.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a])))
      .toMatchObject({ extension: "png" });
    expect(detectSupportedImage(new TextEncoder().encode("RIFFxxxxWEBP"))).toMatchObject({ extension: "webp" });
  });

  it("rejects unsupported or forged images", () => {
    expect(() => detectSupportedImage(new TextEncoder().encode("<svg></svg>"))).toThrowError(
      expect.objectContaining({ status: 415 })
    );
  });
});

describe("rate-limit windows", () => {
  const policy = { action: "room-join", limit: 10, windowSeconds: 60 };

  it("uses stable opaque IDs inside one window", () => {
    expect(rateLimitWindow(59_999, 60)).toBe(0);
    expect(rateLimitDocumentId("uid-1", policy, 1_000)).toBe(
      rateLimitDocumentId("uid-1", policy, 59_999)
    );
    expect(rateLimitDocumentId("uid-1", policy, 1_000)).not.toContain("uid-1");
  });

  it("moves to a new bucket at the next window", () => {
    expect(rateLimitDocumentId("uid-1", policy, 59_999)).not.toBe(
      rateLimitDocumentId("uid-1", policy, 60_000)
    );
  });
});
