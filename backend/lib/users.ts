import type { DecodedIdToken } from "firebase-admin/auth";
import { FieldValue } from "firebase-admin/firestore";
import { getAdminDb } from "./firebaseAdmin";
import { userProfile } from "./auth";

export async function upsertUserProfile(token: DecodedIdToken) {
  const db = getAdminDb();
  const userRef = db.doc(`users/${token.uid}`);
  const user = await userRef.get();

  const data: Record<string, unknown> = {
    ...userProfile(token),
    updatedAt: FieldValue.serverTimestamp()
  };

  if (!user.exists) {
    data.defaultNotificationMode = "basic";
    data.defaultNotificationDays = [7, 3, 1, 0];
    data.pushEnabled = true;
    data.createdAt = FieldValue.serverTimestamp();
  }

  await userRef.set(data, { merge: true });
}
