import { describe, expect, it } from "vitest";
import { hashRoomPassword, verifyRoomPassword } from "../lib/roomPassword";

describe("room password hashing", () => {
  it("verifies the original password", () => {
    const secret = hashRoomPassword("1234pass");

    expect(verifyRoomPassword({
      password: "1234pass",
      ...secret
    })).toBe(true);
  });

  it("rejects a wrong password", () => {
    const secret = hashRoomPassword("1234pass");

    expect(verifyRoomPassword({
      password: "wrong-pass",
      ...secret
    })).toBe(false);
  });
});
