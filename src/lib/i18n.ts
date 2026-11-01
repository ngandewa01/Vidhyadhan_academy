"use client";
import { useStore } from "./store";
import type { L, Lang } from "./types";
import { fmtDate, fmtDateTime, pick } from "./utils";

/**
 * Interface language (UI chrome) and content language (study material / questions) are independent.
 * t(en, mr) – inline bilingual UI strings.  l(obj) – pick interface language.  lc(obj) – pick content language.
 */
export function useI18n() {
  const lang = useStore((s) => s.lang);
  const contentLang = useStore((s) => {
    if (s.session.role === "student") return s.db.students.find((x) => x.id === s.session.userId)?.contentLang ?? s.lang;
    return s.lang;
  });
  const t = (en: string, mr: string) => (lang === "mr" ? mr : en);
  const l = (o: L | undefined) => pick(o, lang);
  const lc = (o: L | undefined, override?: Lang) => pick(o, override ?? contentLang);
  const d = (iso?: string, opts?: Intl.DateTimeFormatOptions) => fmtDate(iso, lang, opts);
  const dt = (iso?: string) => fmtDateTime(iso, lang);
  return { lang, contentLang, t, l, lc, d, dt };
}
