import { keyOk, sendMessage, targetId, tokenSet, type Target } from "@/lib/telegram-server";
import { clientKey, rateLimited } from "@/lib/rate-limit";

export const dynamic = "force-dynamic";
const noStore = { "Cache-Control": "no-store" };

/**
 * Posts a text message to the academy's Telegram channel, group or both. "auto" means the channel if it is set, else the group.
 * Only callers who know TELEGRAM_SEND_KEY may use it, and only the configured chats can be reached.
 */
export async function POST(req: Request) {
  let body: { text?: unknown; target?: unknown };
  try { body = await req.json(); } catch { return Response.json({ error: "bad_request" }, { status: 400, headers: noStore }); }
  const want = body.target === "channel" || body.target === "group" || body.target === "both" ? body.target : "auto";
  const targets: Target[] = want === "both" ? ["channel", "group"] : want === "auto" ? [targetId("channel") ? "channel" : "group"] : [want];
  const configured = targets.filter((x) => targetId(x));
  if (!tokenSet() || !configured.length) return Response.json({ error: "not_configured" }, { status: 503, headers: noStore });
  if (!keyOk(req.headers.get("x-telegram-key"))) return Response.json({ error: "unauthorized" }, { status: 401, headers: noStore });
  if (rateLimited(`tg-send:${clientKey(req)}`, 30)) return Response.json({ error: "rate_limited" }, { status: 429, headers: noStore });
  const text = typeof body.text === "string" ? body.text.trim() : "";
  if (!text || text.length > 3500) return Response.json({ error: "invalid_text" }, { status: 400, headers: noStore });

  const results: Partial<Record<Target, { ok: boolean; messageId?: number; description?: string }>> = {};
  for (const x of configured) {
    const r = await sendMessage(targetId(x), text);
    results[x] = r.ok ? { ok: true, messageId: r.result?.message_id } : { ok: false, description: r.description };
  }
  const failed = [...configured.filter((x) => !results[x]?.ok), ...targets.filter((x) => !configured.includes(x))];
  const delivered = configured.filter((x) => results[x]?.ok);
  if (!delivered.length) return Response.json({ error: "telegram_error", description: results[configured[0]]?.description, results, failed }, { status: 502, headers: noStore });
  return Response.json({ ok: true, delivered, failed, results }, { headers: noStore });
}
