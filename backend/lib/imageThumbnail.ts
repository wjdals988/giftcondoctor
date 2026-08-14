import sharp from "sharp";
import { couponBlobPrefix } from "./blobPath";

const MAX_INPUT_PIXELS = 40_000_000;
const THUMBNAIL_EDGE = 512;

export function legacyCouponThumbnailPath(roomId: string, couponId: string): string {
  return `${couponBlobPrefix(roomId, couponId)}thumbnail.webp`;
}

export type CouponThumbnail = {
  data: Buffer;
  width: number;
  height: number;
  sourceWidth: number | null;
  sourceHeight: number | null;
};

export async function createCouponThumbnail(buffer: Buffer): Promise<CouponThumbnail> {
  const source = sharp(buffer, {
    failOn: "error",
    limitInputPixels: MAX_INPUT_PIXELS
  });
  const metadata = await source.metadata();
  const processed = await source
    .clone()
    .rotate()
    .resize({
      width: THUMBNAIL_EDGE,
      height: THUMBNAIL_EDGE,
      fit: "inside",
      withoutEnlargement: true
    })
    .webp({ quality: 82, effort: 4 })
    .toBuffer({ resolveWithObject: true });

  return {
    data: processed.data,
    width: processed.info.width,
    height: processed.info.height,
    sourceWidth: metadata.width ?? null,
    sourceHeight: metadata.height ?? null
  };
}
