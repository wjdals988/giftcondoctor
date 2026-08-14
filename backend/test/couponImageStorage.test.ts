import { describe, expect, it, vi } from "vitest";
import {
  couponUploadCandidatePaths,
  requireCouponUploadId,
  storeCouponImage
} from "../lib/couponImageStorage";
import { MAX_IMAGE_SIZE } from "../lib/imageUpload";

describe("coupon image storage boundaries", () => {
  it("rejects an empty file before contacting blob storage", async () => {
    const image = new File([], "empty.png", { type: "image/png" });
    await expect(storeCouponImage("room-1", "coupon-1", image)).rejects.toMatchObject({ status: 400 });
  });

  it("rejects a file over 10MB before contacting blob storage", async () => {
    const image = new File([new Uint8Array(MAX_IMAGE_SIZE + 1)], "large.jpg", { type: "image/jpeg" });
    await expect(storeCouponImage("room-1", "coupon-1", image)).rejects.toMatchObject({ status: 413 });
  });

  it("rejects content whose bytes do not match the supported image allowlist", async () => {
    const image = new File(["<svg></svg>"], "forged.jpg", { type: "image/jpeg" });
    await expect(storeCouponImage("room-1", "coupon-1", image)).rejects.toMatchObject({ status: 415 });
  });

  it("starts the original upload before thumbnail processing finishes", async () => {
    let resolveThumbnail: ((value: {
      data: Buffer;
      width: number;
      height: number;
      sourceWidth: number;
      sourceHeight: number;
    }) => void) | undefined;
    const thumbnail = new Promise<{
      data: Buffer;
      width: number;
      height: number;
      sourceWidth: number;
      sourceHeight: number;
    }>((resolve) => { resolveThumbnail = resolve; });
    const uploaded: string[] = [];
    const operation = storeCouponImage(
      "room-1",
      "coupon-1",
      new File([new Uint8Array([0xff, 0xd8, 0xff, 0x00])], "image.jpg", { type: "image/jpeg" }),
      { dependencies: {
        putImage: async (path) => {
          uploaded.push(path);
          return { pathname: path };
        },
        createThumbnail: async () => thumbnail,
        deleteImages: async () => undefined
      } }
    );

    await vi.waitFor(() => expect(uploaded).toHaveLength(1));
    expect(uploaded[0]).not.toContain("thumbnail");
    resolveThumbnail?.({
      data: Buffer.from("thumbnail"),
      width: 256,
      height: 128,
      sourceWidth: 1024,
      sourceHeight: 512
    });
    const result = await operation;
    expect(uploaded).toHaveLength(2);
    expect(result.thumbnailWidth).toBe(256);
    expect(result.imageWidth).toBe(1024);
  });

  it("compensates the original upload when thumbnail processing fails", async () => {
    const deleted: string[][] = [];
    await expect(storeCouponImage(
      "room-1",
      "coupon-1",
      new File([new Uint8Array([0xff, 0xd8, 0xff, 0x00])], "image.jpg", { type: "image/jpeg" }),
      { dependencies: {
        putImage: async (path) => ({ pathname: path }),
        createThumbnail: async () => { throw new Error("thumbnail failed"); },
        deleteImages: async (paths) => { deleted.push(paths); }
      } }
    )).rejects.toThrow("thumbnail failed");
    expect(deleted).toHaveLength(1);
    expect(deleted[0]).toHaveLength(1);
    expect(deleted[0][0]).toContain("rooms/room-1/coupons/coupon-1/");
  });

  it("uses deterministic session paths without a storage-added suffix", async () => {
    const uploaded: Array<{ path: string; addRandomSuffix: boolean }> = [];
    const uploadId = "12345678-1234-1234-1234-123456789012";
    const result = await storeCouponImage(
      "room-1",
      "coupon-1",
      new File([new Uint8Array([0xff, 0xd8, 0xff, 0x00])], "image.jpg", { type: "image/jpeg" }),
      {
        uploadId,
        dependencies: {
          putImage: async (path, _data, options) => {
            uploaded.push({ path, addRandomSuffix: options.addRandomSuffix });
            return { pathname: path };
          },
          createThumbnail: async () => ({
            data: Buffer.from("thumbnail"),
            width: 256,
            height: 128,
            sourceWidth: 1024,
            sourceHeight: 512
          }),
          deleteImages: async () => undefined
        }
      }
    );
    expect(result.uploadId).toBe(uploadId);
    expect(uploaded.map((item) => item.path)).toEqual([
      `rooms/room-1/coupons/coupon-1/${uploadId}-original.jpg`,
      `rooms/room-1/coupons/coupon-1/${uploadId}-thumbnail.webp`
    ]);
    expect(uploaded.every((item) => item.addRandomSuffix === false)).toBe(true);
  });

  it("enumerates only bounded candidate paths for a validated session", () => {
    const uploadId = "12345678-1234-1234-1234-123456789012";
    expect(couponUploadCandidatePaths("room-1", "coupon-1", uploadId)).toHaveLength(4);
    expect(() => requireCouponUploadId("../escape")).toThrow("uploadId 형식이 올바르지 않습니다.");
  });
});
