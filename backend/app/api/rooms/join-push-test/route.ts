import { requireUser } from "@/lib/auth";
import { json, jsonError } from "@/lib/http";
import { joinPushTestRoom } from "@/lib/pushTestRoom";

export const runtime = "nodejs";

export async function POST(request: Request) {
  try {
    const token = await requireUser(request);
    return json(await joinPushTestRoom(token));
  } catch (error) {
    return jsonError(error);
  }
}
