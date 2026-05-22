import { getAdminDb, getAdminMessaging } from "@/lib/firebaseAdmin";
import { requireUser } from "@/lib/auth";
import { ApiError, json, jsonError } from "@/lib/http";
import { PUSH_TEST_ROOM_ID, joinPushTestRoom } from "@/lib/pushTestRoom";
import { isInvalidFcmTokenCode, notificationBody, notificationTitle, shouldNotify } from "@/lib/reminders";

export const runtime = "nodejs";

type TokenDoc = {
  id: string;
  token: string;
};

async function tokenDocsForUid(uid: string): Promise<TokenDoc[]> {
  const tokens = await getAdminDb().collection(`users/${uid}/pushTokens`).get();
  return tokens.docs
    .map((doc) => ({ id: doc.id, token: doc.get("token") }))
    .filter((doc): doc is TokenDoc => typeof doc.token === "string" && doc.token.length > 0);
}

export async function POST(request: Request) {
  try {
    const token = await requireUser(request);
    const payload = await request.json().catch(() => ({}));
    const kind = typeof payload?.kind === "string" ? payload.kind : "device";
    const expiryTest = kind === "expiryReminder";

    if (expiryTest) {
      const user = await getAdminDb().doc(`users/${token.uid}`).get();
      if (!user.exists || user.get("pushEnabled") === false) {
        throw new ApiError(400, "전체 푸시 알림이 꺼져 있습니다. 알림 설정에서 푸시 알림 사용을 켠 뒤 다시 시도해 주세요.");
      }
      if (!shouldNotify(0, user.data())) {
        throw new ApiError(400, "현재 알림 설정은 당일 만료 알림을 받지 않도록 되어 있습니다.");
      }
      await joinPushTestRoom(token);
    }

    const tokenDocs = await tokenDocsForUid(token.uid);
    if (tokenDocs.length === 0) {
      throw new ApiError(404, "저장된 푸시 토큰이 없습니다. 앱을 다시 실행한 뒤 시도해 주세요.");
    }

    const title = expiryTest ? notificationTitle("기프티콘닥터", 0) : "테스트 푸시가 도착했어요";
    const body = expiryTest ? notificationBody("테스트 만료 쿠폰", "오늘", 0) : "기프티콘닥터 알림 설정이 정상입니다.";
    const data: Record<string, string> = expiryTest
      ? {
          title,
          body,
          roomId: PUSH_TEST_ROOM_ID,
          couponId: "expiry-reminder-test",
          daysBefore: "0",
          deepLink: `giftcondoctor://rooms/${PUSH_TEST_ROOM_ID}`,
          type: "expiry_reminder_test",
          delaySeconds: "10"
        }
      : {
          title,
          body,
          type: "test_push"
        };

    const response = await getAdminMessaging().sendEachForMulticast({
      tokens: tokenDocs.map((doc) => doc.token),
      notification: { title, body },
      data,
      android: {
        priority: "high",
        notification: {
          channelId: "coupon_expiry",
          clickAction: "OPEN_COUPON_DETAIL",
          color: "#00B4A6",
          icon: "ic_stat_gd_notification"
        }
      }
    });

    const db = getAdminDb();
    const batch = db.batch();
    let removed = 0;
    response.responses.forEach((item, index) => {
      if (!item.success && isInvalidFcmTokenCode(item.error?.code)) {
        batch.delete(db.doc(`users/${token.uid}/pushTokens/${tokenDocs[index].id}`));
        removed += 1;
      }
    });
    if (removed > 0) await batch.commit();

    return json({
      sent: response.successCount,
      failed: response.failureCount,
      removedInvalidTokens: removed
    });
  } catch (error) {
    return jsonError(error);
  }
}
