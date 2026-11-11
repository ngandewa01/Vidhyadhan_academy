import { channelId, getMe, getWebhookInfo, groupId, keySet, tokenSet, webhookSecretSet } from "@/lib/telegram-server";
import { clientKey, rateLimited } from "@/lib/rate-limit";

export const dynamic = "force-dynamic";
const noStore = { "Cache-Control": "no-store" };

export async function GET(req: Request) {
  if (rateLimited(`tg-status:${clientKey(req)}`, 30)) return Response.json({ error: "rate_limited" }, { status: 429, headers: noStore });
  const out: { tokenSet: boolean; channelSet: boolean; groupSet: boolean; keySet: boolean; webhookSecretSet: boolean; bot: { username: string; name: string } | null; botError?: string; webhookConnected: boolean; webhookError?: string } = {
    tokenSet: tokenSet(), channelSet: !!channelId(), groupSet: !!groupId(), keySet: keySet(), webhookSecretSet: webhookSecretSet(), bot: null, webhookConnected: false,
  };
  if (out.tokenSet) {
    const me = await getMe();
    if (me.ok && me.result) out.bot = { username: me.result.username, name: me.result.first_name };
    else out.botError = me.description ?? "unknown";
    const info = await getWebhookInfo();
    if (info.ok && info.result) {
      out.webhookConnected = info.result.url.endsWith("/api/telegram/webhook");
      if (info.result.last_error_message) out.webhookError = info.result.last_error_message;
    }
  }
  return Response.json(out, { headers: noStore });
}
