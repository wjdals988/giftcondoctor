import { pbkdf2Sync, randomBytes, timingSafeEqual } from "crypto";

const ITERATIONS = 120_000;
const KEY_LENGTH = 32;
const DIGEST = "sha256";

export function hashRoomPassword(password: string) {
  const salt = randomBytes(16).toString("base64url");
  const hash = pbkdf2Sync(password, salt, ITERATIONS, KEY_LENGTH, DIGEST).toString("base64url");
  return {
    passwordHash: hash,
    passwordSalt: salt,
    passwordIterations: ITERATIONS
  };
}

export function verifyRoomPassword(params: {
  password: string;
  passwordHash: unknown;
  passwordSalt: unknown;
  passwordIterations: unknown;
}) {
  if (
    typeof params.passwordHash !== "string" ||
    typeof params.passwordSalt !== "string" ||
    typeof params.passwordIterations !== "number"
  ) {
    return false;
  }

  const candidate = pbkdf2Sync(
    params.password,
    params.passwordSalt,
    params.passwordIterations,
    KEY_LENGTH,
    DIGEST
  );
  const expected = Buffer.from(params.passwordHash, "base64url");
  return expected.length === candidate.length && timingSafeEqual(expected, candidate);
}
