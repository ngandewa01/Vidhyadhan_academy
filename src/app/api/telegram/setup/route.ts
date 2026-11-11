import { keyOk, setWebhook, tokenSet, webhookSecretSet } from "@/lib/telegram-server";
import { clientKey, rateLimited } from "@/lib/rate-limit";

export const dynamic = "force-dynamic";
const noStore = { "Cache-Control": "no-store" };

/** Points the bot at this website's webhook. Needs TELEGRAM_SEND_KEY, like sending does. */
export async function POST(req: Request) {
  if (!tokenSet() || !webhookSecretSet()) return Response.json({ error: "not_configured" }, { status: 503, headers: noStore });
  if (!keyOk(req.headers.get("x-telegram-key"))) return Response.json({ error: "unauthorized" }, { status: 401, headers: noStore });
  if (rateLimited(`tg-setup:${clientKey(req)}`, 10)) return Response.json({ error: "rate_limited" }, { status: 429, headers: noStore });
  const host = req.headers.get("x-forwarded-host") || req.headers.get("host") || "";
  const proto = req.headers.get("x-forwarded-proto") || "https";
  const url = `${proto}://${host}/api/telegram/webhook`;
  const r = await setWebhook(url);
  if (!r.ok) return Response.json({ error: "telegram_error", description: r.description, url }, { status: 502, headers: noStore });
  return Response.json({ ok: true, url }, { headers: noStore });
}
