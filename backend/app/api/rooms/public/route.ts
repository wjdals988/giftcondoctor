import { requireUser } from "@/lib/auth";
import { getAdminDb } from "@/lib/firebaseAdmin";
import { json, jsonError } from "@/lib/http";

export const runtime = "nodejs";

export async function GET(request: Request) {
  try {
    const token = await requireUser(request);
    const db = getAdminDb();
    const rooms = await db
      .collection("rooms")
      .where("isPublic", "==", true)
      .limit(50)
      .get();

    const data = await Promise.all(
      rooms.docs.map(async (room) => {
        const member = await room.ref.collection("members").doc(token.uid).get();
        return {
          roomId: room.id,
          name: room.get("name") ?? "이름 없는 방",
          memberCount: room.get("memberCount") ?? 0,
          alreadyJoined: member.exists
        };
      })
    );

    return json({
      rooms: data.sort((a, b) => {
        if (a.alreadyJoined !== b.alreadyJoined) return a.alreadyJoined ? 1 : -1;
        return String(a.name).localeCompare(String(b.name), "ko");
      })
    }, {
      headers: {
        "Cache-Control": "private, no-store"
      }
    });
  } catch (error) {
    return jsonError(error);
  }
}
