import { getAdminApp } from "@/lib/firebaseAdmin";
import { json } from "@/lib/http";

export const runtime = "nodejs";

export async function GET() {
  let firebaseAdmin = "configured";
  try {
    getAdminApp();
  } catch {
    firebaseAdmin = "not_configured";
  }

  return json({
    ok: firebaseAdmin === "configured",
    service: "giftcondoctor-api",
    firebaseAdmin,
    blob: process.env.BLOB_READ_WRITE_TOKEN ? "configured" : "not_configured",
    timeZone: "Asia/Seoul",
    timestamp: new Date().toISOString()
  }, {
    headers: {
      "Cache-Control": "no-store"
    }
  });
}
