import { randomInt } from "crypto";
import { Timestamp } from "firebase-admin/firestore";
import { getAdminDb } from "./firebaseAdmin";

const CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

export function createInviteCode(length = 6) {
  let code = "";
  for (let i = 0; i < length; i += 1) {
    code += CHARS[randomInt(CHARS.length)];
  }
  return code;
}

export function inviteExpiresAt(hours = 24) {
  return Timestamp.fromDate(new Date(Date.now() + hours * 60 * 60 * 1000));
}

export async function createUniqueInviteCode() {
  const db = getAdminDb();
  const now = Timestamp.now();

  for (let i = 0; i < 20; i += 1) {
    const code = createInviteCode();
    const existing = await db
      .collection("rooms")
      .where("inviteCode", "==", code)
      .where("inviteExpiresAt", ">", now)
      .limit(1)
      .get();
    if (existing.empty) return code;
  }

  throw new Error("초대코드를 생성하지 못했습니다.");
}
