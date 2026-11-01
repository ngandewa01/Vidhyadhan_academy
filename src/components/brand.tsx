"use client";
import Link from "next/link";
import { Languages } from "lucide-react";
import { academy } from "@/config/academy";
import { useStore } from "@/lib/store";
import { useI18n } from "@/lib/i18n";
import { cn } from "@/lib/utils";

export function Logo({ compact, light }: { compact?: boolean; light?: boolean }) {
  const { l } = useI18n();
  return (
    <Link href="/" className="flex items-center gap-2" aria-label={academy.name.en}>
      {academy.logoUrl ? (
        // eslint-disable-next-line @next/next/no-img-element
        <img src={academy.logoUrl} alt="" width={44} height={44} className="size-11 rounded-full bg-white shadow-sm" />
      ) : (
        <span className="flex size-9 items-center justify-center rounded-lg bg-gradient-to-br from-brand-700 to-brand-500 text-lg font-bold text-white shadow-sm" title="Logo placeholder">वि</span>
      )}
      {!compact && (
        <span className="leading-tight">
          <span className={cn("block text-[15px] font-bold", light ? "text-white" : "text-brand-900")}>{l(academy.name)}</span>
          <span className={cn("block text-[11px] font-medium tracking-wide", light ? "text-brand-200" : "text-saffron-700")}>{academy.focus}</span>
        </span>
      )}
    </Link>
  );
}

export function LangSwitch({ className, light }: { className?: string; light?: boolean }) {
  const lang = useStore((s) => s.lang);
  const setLang = useStore((s) => s.setLang);
  return (
    <div className={cn("inline-flex items-center gap-1 rounded-lg p-0.5 text-xs font-semibold", light ? "bg-white/10" : "bg-slate-100", className)} role="group" aria-label="Interface language / भाषा">
      <Languages className={cn("ml-1 size-3.5", light ? "text-brand-200" : "text-slate-500")} aria-hidden />
      {(["mr", "en"] as const).map((l) => (
        <button key={l} onClick={() => setLang(l)} aria-pressed={lang === l}
          className={cn("rounded-md px-2 py-1 transition-colors", lang === l ? (light ? "bg-white text-brand-800" : "bg-white text-brand-800 shadow-sm") : light ? "text-brand-100 hover:text-white" : "text-slate-500 hover:text-slate-800")}>
          {l === "mr" ? "मराठी" : "English"}
        </button>
      ))}
    </div>
  );
}

export function WhatsAppIcon({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 24 24" className={className} fill="currentColor" aria-hidden>
      <path d="M17.47 14.38c-.3-.15-1.75-.86-2.02-.96-.27-.1-.47-.15-.67.15-.2.3-.77.96-.94 1.16-.17.2-.35.22-.64.07-.3-.15-1.25-.46-2.38-1.47-.88-.79-1.47-1.76-1.65-2.06-.17-.3-.02-.46.13-.6.13-.14.3-.35.45-.52.15-.17.2-.3.3-.5.1-.2.05-.37-.03-.52-.07-.15-.67-1.6-.92-2.2-.24-.58-.49-.5-.67-.5h-.57c-.2 0-.52.07-.79.37-.27.3-1.04 1.02-1.04 2.48s1.07 2.88 1.21 3.08c.15.2 2.1 3.2 5.08 4.49.71.31 1.26.49 1.7.63.71.22 1.36.19 1.87.12.57-.09 1.75-.72 2-1.41.25-.7.25-1.29.17-1.41-.07-.13-.27-.2-.57-.35M12.04 21.8h-.01a9.8 9.8 0 0 1-5-1.37l-.36-.21-3.72.97 1-3.62-.24-.37a9.8 9.8 0 0 1-1.5-5.23c0-5.42 4.41-9.83 9.84-9.83 2.63 0 5.1 1.03 6.95 2.88a9.77 9.77 0 0 1 2.88 6.96c0 5.42-4.41 9.83-9.84 9.83M20.4 3.6A11.8 11.8 0 0 0 12.04.13C5.5.13.17 5.45.17 12c0 2.1.55 4.13 1.59 5.93L.07 24l6.22-1.63a11.84 11.84 0 0 0 5.75 1.46h.01c6.54 0 11.87-5.32 11.87-11.86 0-3.17-1.23-6.15-3.47-8.39" />
    </svg>
  );
}
