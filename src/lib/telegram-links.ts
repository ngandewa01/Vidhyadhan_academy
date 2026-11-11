import { academy } from "@/config/academy";

type T = (en: string, mr: string) => string;
export interface TelegramLink { href: string; label: string; join: string }

/** The Telegram community: an announcements channel and a student discussion group, whichever are set. */
export function telegramLinks(t: T): TelegramLink[] {
  const links: TelegramLink[] = [];
  if (academy.social.telegram) links.push({ href: academy.social.telegram, label: t("Channel", "चॅनेल"), join: t("Join Telegram channel", "टेलिग्राम चॅनेल जॉईन करा") });
  if (academy.social.telegramGroup) links.push({ href: academy.social.telegramGroup, label: t("Group", "ग्रुप"), join: t("Join Telegram group", "टेलिग्राम ग्रुप जॉईन करा") });
  return links;
}
