import sharp from "sharp";
import { describe, expect, it } from "vitest";
import { createCouponThumbnail } from "../lib/imageThumbnail";

describe("coupon thumbnail", () => {
  it("creates a bounded WebP thumbnail without enlarging the source", async () => {
    const source = await sharp({
      create: {
        width: 1_200,
        height: 800,
        channels: 3,
        background: "#7c3aed"
      }
    }).jpeg({ quality: 95 }).toBuffer();

    const thumbnail = await createCouponThumbnail(source);
    const metadata = await sharp(thumbnail.data).metadata();

    expect(thumbnail.sourceWidth).toBe(1_200);
    expect(thumbnail.sourceHeight).toBe(800);
    expect(thumbnail.width).toBe(512);
    expect(thumbnail.height).toBe(341);
    expect(metadata.format).toBe("webp");
    expect(thumbnail.data.byteLength).toBeLessThan(source.byteLength);
  });

  it("keeps a small image at its original dimensions", async () => {
    const source = await sharp({
      create: {
        width: 120,
        height: 80,
        channels: 3,
        background: "#ffffff"
      }
    }).png().toBuffer();

    const thumbnail = await createCouponThumbnail(source);

    expect(thumbnail.width).toBe(120);
    expect(thumbnail.height).toBe(80);
  });
});
