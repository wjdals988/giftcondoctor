import { ApiError } from "./http";

export const MAX_IMAGE_SIZE = 10 * 1024 * 1024;

type SupportedImage = {
  contentType: "image/jpeg" | "image/png" | "image/webp";
  extension: "jpg" | "png" | "webp";
};

const SUPPORTED_IMAGES: Array<{
  definition: SupportedImage;
  matches: (bytes: Uint8Array) => boolean;
}> = [
  {
    definition: { contentType: "image/jpeg", extension: "jpg" },
    matches: (bytes) => bytes.length >= 3 && bytes[0] === 0xff && bytes[1] === 0xd8 && bytes[2] === 0xff
  },
  {
    definition: { contentType: "image/png", extension: "png" },
    matches: (bytes) =>
      bytes.length >= 8 &&
      bytes[0] === 0x89 && bytes[1] === 0x50 && bytes[2] === 0x4e && bytes[3] === 0x47 &&
      bytes[4] === 0x0d && bytes[5] === 0x0a && bytes[6] === 0x1a && bytes[7] === 0x0a
  },
  {
    definition: { contentType: "image/webp", extension: "webp" },
    matches: (bytes) =>
      bytes.length >= 12 &&
      ascii(bytes, 0, 4) === "RIFF" && ascii(bytes, 8, 12) === "WEBP"
  }
];

function ascii(bytes: Uint8Array, start: number, end: number): string {
  return String.fromCharCode(...bytes.slice(start, end));
}

export function detectSupportedImage(bytes: Uint8Array): SupportedImage {
  const supported = SUPPORTED_IMAGES.find((candidate) => candidate.matches(bytes));
  if (!supported) {
    throw new ApiError(415, "JPEG, PNG, WebP 이미지만 업로드할 수 있습니다.");
  }
  return supported.definition;
}
