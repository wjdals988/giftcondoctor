import { getAdminDb, getAdminMessaging } from "@/lib/firebaseAdmin";
import { requireUser } from "@/lib/auth";
import { ApiError, json, jsonError } from "@/lib/http";
import { isInvalidFcmTokenCode } from "@/lib/reminders";

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
    const tokenDocs = await tokenDocsForUid(token.uid);
    if (tokenDocs.length === 0) {
      throw new ApiError(404, "저장된 푸시 토큰이 없습니다. 앱을 다시 실행한 뒤 시도해 주세요.");
    }

    const response = await getAdminMessaging().sendEachForMulticast({
      tokens: tokenDocs.map((doc) => doc.token),
      notification: {
        title: "테스트 푸시가 도착했어요",
        body: "기프티콘닥터 알림 설정이 정상입니다."
      },
      data: {
        title: "테스트 푸시가 도착했어요",
        body: "기프티콘닥터 알림 설정이 정상입니다.",
        type: "test_push"
      },
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
