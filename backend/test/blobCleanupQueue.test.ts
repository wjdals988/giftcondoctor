import { describe, expect, it } from "vitest";
import {
  blobCleanupDeletablePaths,
  blobCleanupHasLiveReference,
  blobCleanupHealth,
  blobCleanupJobId,
  blobCleanupRetryDelayMs,
  isDueBlobCleanup,
  parseBlobCleanupData
} from "../lib/blobCleanupQueue";

const imagePath = "rooms/room-1/coupons/coupon-1/image.jpg";
const thumbnailPath = "rooms/room-1/coupons/coupon-1/thumbnail.webp";

describe("Blob cleanup queue", () => {
  it("creates a stable identity regardless of path order or duplicates", () => {
    const first = blobCleanupJobId("room-1", "coupon-1", [imagePath, thumbnailPath]);
    expect(blobCleanupJobId("room-1", "coupon-1", [thumbnailPath, imagePath, imagePath])).toBe(first);
  });

  it("accepts only paths owned by the queued coupon", () => {
    expect(parseBlobCleanupData({
      roomId: "room-1",
      couponId: "coupon-1",
      paths: [thumbnailPath, imagePath, imagePath],
      attempts: 1
    })).toEqual({
      roomId: "room-1",
      couponId: "coupon-1",
      paths: [imagePath, thumbnailPath],
      attempts: 1
    });
    expect(parseBlobCleanupData({
      roomId: "room-1",
      couponId: "coupon-1",
      paths: ["rooms/room-1/coupons/other/image.jpg"],
      attempts: 0
    })).toBeNull();
  });

  it("does not allow cleanup while any queued path is still referenced", () => {
    expect(blobCleanupHasLiveReference([imagePath], imagePath, thumbnailPath)).toBe(true);
    expect(blobCleanupHasLiveReference([thumbnailPath], imagePath, thumbnailPath)).toBe(true);
    expect(blobCleanupHasLiveReference(
      ["rooms/room-1/coupons/coupon-1/old.jpg"],
      imagePath,
      thumbnailPath
    )).toBe(false);
  });

  it("filters live paths while allowing abandoned session candidates to be deleted", () => {
    const abandoned = "rooms/room-1/coupons/coupon-1/session-original.png";
    expect(blobCleanupDeletablePaths(
      [imagePath, thumbnailPath, abandoned],
      imagePath,
      thumbnailPath
    )).toEqual([abandoned]);
  });

  it("claims pending work only after it is due and expired leases", () => {
    expect(isDueBlobCleanup({ status: "pending", nextAttemptAtMs: 999, nowMs: 1_000 })).toBe(true);
    expect(isDueBlobCleanup({ status: "retry", nextAttemptAtMs: 1_001, nowMs: 1_000 })).toBe(false);
    expect(isDueBlobCleanup({ status: "deleting", leaseUntilMs: 999, nowMs: 1_000 })).toBe(true);
  });

  it("uses capped exponential retry delay with jitter", () => {
    expect(blobCleanupRetryDelayMs(1, () => 0.5)).toBe(300_000);
    expect(blobCleanupRetryDelayMs(2, () => 0.5)).toBe(600_000);
    expect(blobCleanupRetryDelayMs(20, () => 0.5)).toBe(86_400_000);
    expect(blobCleanupRetryDelayMs(1, () => 0)).toBe(240_000);
    expect(blobCleanupRetryDelayMs(1, () => 1)).toBe(360_000);
  });

  it("escalates retry work to warning and dead letters or stale leases to critical", () => {
    const healthy = {
      pending: 0,
      deleting: 0,
      retry: 0,
      deleted: 4,
      deadLetter: 0,
      due: 0,
      staleDeleting: 0
    };
    expect(blobCleanupHealth(healthy)).toBe("healthy");
    expect(blobCleanupHealth({ ...healthy, retry: 1 })).toBe("warning");
    expect(blobCleanupHealth({ ...healthy, due: 1 })).toBe("warning");
    expect(blobCleanupHealth({ ...healthy, deadLetter: 1 })).toBe("critical");
    expect(blobCleanupHealth({ ...healthy, staleDeleting: 1 })).toBe("critical");
  });
});
