import { academy } from "@/config/academy";
import { sendMessage, webhookSecretOk, webhookSecretSet } from "@/lib/telegram-server";

export const dynamic = "force-dynamic";

const links = [academy.social.telegram && `चॅनेल / Channel: ${academy.social.telegram}`, academy.social.telegramGroup && `ग्रुप / Group: ${academy.social.telegramGroup}`].filter(Boolean).join("\n");

function reply(command: string, chatId: number) {
  if (command === "/id") return `आपला टेलिग्राम चॅट आयडी / Your Telegram chat ID: ${chatId}`;
  if (command === "/start" || command === "/help") {
    return [
      `नमस्कार! ${academy.name.mr}च्या अधिकृत टेलिग्राम बॉटमध्ये आपले स्वागत आहे.`,
      `Welcome to the official Telegram bot of ${academy.name.en}.`,
      "",
      `${academy.tagline.mr} / ${academy.tagline.en}`,
      "",
      `फोन / Phone: ${academy.phone}`,
      `पत्ता / Address: ${academy.address.mr}`,
      links,
      "",
      "/id – आपला चॅट आयडी / your chat ID",
      "/help – मदत / help",
    ].filter((line, i, all) => line !== "" || all[i - 1] !== "").join("\n");
  }
  return `धन्यवाद! प्रवेश व इतर प्रश्नांसाठी कृपया ${academy.phone} वर संपर्क करा.\nThank you! For admissions and other questions, please call ${academy.phone}.`;
}

/** Telegram calls this with every message sent to the bot. */
export async function POST(req: Request) {
  if (!webhookSecretSet()) return new Response("not configured", { status: 503 });
  if (!webhookSecretOk(req.headers.get("x-telegram-bot-api-secret-token"))) return new Response("forbidden", { status: 401 });
  type Msg = { text?: unknown; chat?: { id?: number; type?: string } };
  let update: { message?: Msg; channel_post?: Msg };
  try { update = await req.json(); } catch { return Response.json({ ok: true }); }
  const msg = update.message ?? update.channel_post;
  const chat = msg?.chat;
  if (!chat?.id) return Response.json({ ok: true });
  const text = typeof msg?.text === "string" ? msg.text.trim() : "";
  const command = text.split(/[\s@]/)[0].toLowerCase();
  // In groups and channels the bot stays quiet except for /id, which tells the admin the id to put in TELEGRAM_GROUP_ID or TELEGRAM_CHANNEL_ID.
  if (chat.type !== "private" && command !== "/id") return Response.json({ ok: true });
  await sendMessage(chat.id, reply(command, chat.id));
  return Response.json({ ok: true });
}
