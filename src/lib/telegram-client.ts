/** Browser-side helpers for the admin portal. The send key lives only in this tab's session storage. */
const KEY = "vidyadhan-telegram-key";

export const getTelegramKey = () => { try { return sessionStorage.getItem(KEY) ?? ""; } catch { return ""; } };
export const setTelegramKey = (v: string) => { try { if (v) sessionStorage.setItem(KEY, v); else sessionStorage.removeItem(KEY); } catch { /* storage unavailable */ } };

export type TelegramResult = { ok: boolean; reason?: "no_key" | "not_configured" | "unauthorized" | "rate_limited" | "failed"; description?: string; url?: string };

async function post(path: string, body?: unknown): Promise<TelegramResult> {
  const key = getTelegramKey();
  if (!key) return { ok: false, reason: "no_key" };
  try {
    const res = await fetch(path, { method: "POST", headers: { "Content-Type": "application/json", "x-telegram-key": key }, body: body ? JSON.stringify(body) : undefined });
    const data = await res.json().catch(() => ({}));
    if (res.ok) return { ok: true, url: data.url };
    if (res.status === 503) return { ok: false, reason: "not_configured" };
    if (res.status === 401) return { ok: false, reason: "unauthorized" };
    if (res.status === 429) return { ok: false, reason: "rate_limited" };
    return { ok: false, reason: "failed", description: data.description, url: data.url };
  } catch {
    return { ok: false, reason: "failed" };
  }
}

export const telegramPost = (text: string) => post("/api/telegram/send", { text });
export const telegramConnect = () => post("/api/telegram/setup");
