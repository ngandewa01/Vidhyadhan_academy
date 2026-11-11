import { academy } from "@/config/academy";
import { sendMessage, webhookSecretOk, webhookSecretSet } from "@/lib/telegram-server";

export const dynamic = "force-dynamic";

const channel = academy.social.telegram ? `\n${academy.social.telegram}` : "";

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
      channel ? `चॅनेल / Channel:${channel}` : "",
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
  let update: { message?: { text?: unknown; chat?: { id?: number; type?: string } } };
  try { update = await req.json(); } catch { return Response.json({ ok: true }); }
  const chat = update.message?.chat;
  if (!chat?.id || chat.type !== "private") return Response.json({ ok: true });
  const text = typeof update.message?.text === "string" ? update.message.text.trim() : "";
  const command = text.split(/[\s@]/)[0].toLowerCase();
  await sendMessage(chat.id, reply(command, chat.id));
  return Response.json({ ok: true });
}
