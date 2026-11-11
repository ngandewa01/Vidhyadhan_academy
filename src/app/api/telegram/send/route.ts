import { channelId, keyOk, sendMessage, tokenSet } from "@/lib/telegram-server";
import { clientKey, rateLimited } from "@/lib/rate-limit";

export const dynamic = "force-dynamic";
const noStore = { "Cache-Control": "no-store" };

/** Posts a text message to the academy channel. Only callers who know TELEGRAM_SEND_KEY may use it. */
export async function POST(req: Request) {
  if (!tokenSet() || !channelId()) return Response.json({ error: "not_configured" }, { status: 503, headers: noStore });
  if (!keyOk(req.headers.get("x-telegram-key"))) return Response.json({ error: "unauthorized" }, { status: 401, headers: noStore });
  if (rateLimited(`tg-send:${clientKey(req)}`, 30)) return Response.json({ error: "rate_limited" }, { status: 429, headers: noStore });
  let body: { text?: unknown };
  try { body = await req.json(); } catch { return Response.json({ error: "bad_request" }, { status: 400, headers: noStore }); }
  const text = typeof body.text === "string" ? body.text.trim() : "";
  if (!text || text.length > 3500) return Response.json({ error: "invalid_text" }, { status: 400, headers: noStore });
  const r = await sendMessage(channelId(), text);
  if (!r.ok) return Response.json({ error: "telegram_error", description: r.description }, { status: 502, headers: noStore });
  return Response.json({ ok: true, messageId: r.result?.message_id }, { headers: noStore });
}
