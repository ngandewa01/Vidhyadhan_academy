import { createHash, timingSafeEqual } from "node:crypto";

/** Server-only Telegram helpers. The bot token never leaves the server. */

const api = () => (process.env.TELEGRAM_API_BASE || "https://api.telegram.org").replace(/\/$/, "");
const token = () => process.env.TELEGRAM_BOT_TOKEN || "";
const sendKey = () => process.env.TELEGRAM_SEND_KEY || "";
const hookSecret = () => process.env.TELEGRAM_WEBHOOK_SECRET || "";

export const tokenSet = () => token().length > 0;
/** Ids look like -1001234567890, or @username for a public channel or group. */
export const channelId = () => process.env.TELEGRAM_CHANNEL_ID || "";
export const groupId = () => process.env.TELEGRAM_GROUP_ID || process.env.TELEGRAM_CHAT_ID || "";
export type Target = "channel" | "group";
export const targetId = (t: Target) => (t === "channel" ? channelId() : groupId());
export const keySet = () => sendKey().length >= 16;
export const webhookSecretSet = () => hookSecret().length >= 16;

type Reply<T> = { ok: boolean; result?: T; description?: string };

async function call<T>(method: string, body?: unknown): Promise<Reply<T>> {
  try {
    const res = await fetch(`${api()}/bot${token()}/${method}`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body ?? {}), cache: "no-store" });
    return (await res.json()) as Reply<T>;
  } catch {
    return { ok: false, description: "Could not reach Telegram" };
  }
}

export const getMe = () => call<{ username: string; first_name: string }>("getMe");
export const getWebhookInfo = () => call<{ url: string; pending_update_count: number; last_error_message?: string }>("getWebhookInfo");
export const sendMessage = (chat_id: string | number, text: string) => call<{ message_id: number }>("sendMessage", { chat_id, text });
export const setWebhook = (url: string) => call<boolean>("setWebhook", { url, secret_token: hookSecret(), allowed_updates: ["message", "channel_post"] });

const digest = (s: string) => createHash("sha256").update(s).digest();
const same = (given: string | null, expected: string) => !!given && expected.length >= 16 && timingSafeEqual(digest(given), digest(expected));
export const keyOk = (given: string | null) => same(given, sendKey());
export const webhookSecretOk = (given: string | null) => same(given, hookSecret());
