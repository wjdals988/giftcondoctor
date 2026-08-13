import { requireUser } from "@/lib/auth";
import { enforceUserRateLimit } from "@/lib/rateLimit";
import { json, jsonError } from "@/lib/http";
import { joinPushTestRoom } from "@/lib/pushTestRoom";

export const runtime = "nodejs";

export async function POST(request: Request) {
  try {
    const token = await requireUser(request);
    await enforceUserRateLimit(token.uid, { action: "push-test-room-join", limit: 3, windowSeconds: 3600 });
    return json(await joinPushTestRoom(token));
  } catch (error) {
    return jsonError(error);
  }
}
