"use client";
import { create } from "zustand";
import { useEffect, useId, useRef, useState, type ReactNode } from "react";
import { X, Inbox, CheckCircle2, AlertTriangle, Info, Search } from "lucide-react";
import { cn } from "@/lib/utils";
import { useI18n } from "@/lib/i18n";

/* ---------------- Toasts ---------------- */
type Toast = { id: number; text: string; tone: "success" | "error" | "info" };
const useToasts = create<{ items: Toast[]; push: (t: Omit<Toast, "id">) => void; drop: (id: number) => void }>((set) => ({
  items: [],
  push: (t) => {
    const id = Date.now() + Math.random();
    set((s) => ({ items: [...s.items, { ...t, id }] }));
    setTimeout(() => set((s) => ({ items: s.items.filter((x) => x.id !== id) })), 3800);
  },
  drop: (id) => set((s) => ({ items: s.items.filter((x) => x.id !== id) })),
}));
export const toast = {
  success: (text: string) => useToasts.getState().push({ text, tone: "success" }),
  error: (text: string) => useToasts.getState().push({ text, tone: "error" }),
  info: (text: string) => useToasts.getState().push({ text, tone: "info" }),
};
export function Toaster() {
  const { items, drop } = useToasts();
  return (
    <div className="pointer-events-none fixed inset-x-0 bottom-4 z-[100] flex flex-col items-center gap-2 px-4 sm:items-end sm:pr-6" aria-live="polite">
      {items.map((t) => (
        <div key={t.id} role="status" className={cn("pointer-events-auto flex w-full max-w-sm items-start gap-2 rounded-lg px-4 py-3 text-sm text-white shadow-lg", t.tone === "success" ? "bg-emerald-700" : t.tone === "error" ? "bg-red-700" : "bg-slate-800")}>
          {t.tone === "success" ? <CheckCircle2 className="mt-0.5 size-4 shrink-0" /> : t.tone === "error" ? <AlertTriangle className="mt-0.5 size-4 shrink-0" /> : <Info className="mt-0.5 size-4 shrink-0" />}
          <span className="flex-1">{t.text}</span>
          <button onClick={() => drop(t.id)} aria-label="Dismiss" className="opacity-70 hover:opacity-100"><X className="size-4" /></button>
        </div>
      ))}
    </div>
  );
}

/* ---------------- Modal ---------------- */
export function Modal({ open, onClose, title, children, footer, size = "md" }: { open: boolean; onClose: () => void; title: string; children: ReactNode; footer?: ReactNode; size?: "sm" | "md" | "lg" | "xl" }) {
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (!open) return;
    const prev = document.activeElement as HTMLElement | null;
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && onClose();
    document.addEventListener("keydown", onKey);
    document.body.style.overflow = "hidden";
    setTimeout(() => ref.current?.querySelector<HTMLElement>("input,select,textarea,button:not([data-close])")?.focus(), 10);
    return () => {
      document.removeEventListener("keydown", onKey);
      document.body.style.overflow = "";
      prev?.focus?.();
    };
  }, [open, onClose]);
  if (!open) return null;
  const w = { sm: "max-w-md", md: "max-w-lg", lg: "max-w-2xl", xl: "max-w-4xl" }[size];
  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center bg-slate-900/50 p-0 sm:items-center sm:p-4" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div ref={ref} role="dialog" aria-modal="true" aria-label={title} className={cn("flex max-h-[92vh] w-full flex-col rounded-t-2xl bg-white shadow-xl sm:rounded-2xl", w)}>
        <div className="flex items-center justify-between border-b border-slate-200 px-5 py-3">
          <h2 className="text-base font-semibold">{title}</h2>
          <button data-close onClick={onClose} className="btn-ghost btn-sm" aria-label="Close"><X className="size-4" /></button>
        </div>
        <div className="overflow-y-auto px-5 py-4">{children}</div>
        {footer && <div className="flex flex-wrap justify-end gap-2 border-t border-slate-200 px-5 py-3">{footer}</div>}
      </div>
    </div>
  );
}

/* ---------------- Badges ---------------- */
const TONES: Record<string, string> = {
  green: "bg-emerald-50 text-emerald-700 ring-emerald-600/20",
  red: "bg-red-50 text-red-700 ring-red-600/20",
  amber: "bg-amber-50 text-amber-800 ring-amber-600/20",
  blue: "bg-brand-50 text-brand-700 ring-brand-600/20",
  slate: "bg-slate-100 text-slate-700 ring-slate-500/20",
  violet: "bg-violet-50 text-violet-700 ring-violet-600/20",
  orange: "bg-saffron-50 text-saffron-700 ring-saffron-600/20",
};
export function Badge({ tone = "slate", children, className }: { tone?: keyof typeof TONES | string; children: ReactNode; className?: string }) {
  return <span className={cn("inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-xs font-medium ring-1 ring-inset whitespace-nowrap", TONES[tone] ?? TONES.slate, className)}>{children}</span>;
}

const STATUS: Record<string, [string, string, string]> = {
  // key: [tone, en, mr]
  pending: ["amber", "Pending", "प्रलंबित"], partial: ["orange", "Partial", "अंशतः"], paid: ["green", "Paid", "भरले"], failed: ["red", "Failed", "अयशस्वी"], refunded: ["violet", "Refunded", "परत केले"],
  confirmed: ["green", "Confirmed", "निश्चित"], cancelled: ["red", "Cancelled", "रद्द"], active: ["green", "Active", "सक्रिय"], suspended: ["amber", "Suspended", "निलंबित"], completed: ["slate", "Completed", "पूर्ण"],
  inactive: ["slate", "Inactive", "निष्क्रिय"], expired: ["red", "Expired", "मुदत संपली"],
  new: ["blue", "New", "नवीन"], contacted: ["violet", "Contacted", "संपर्क झाला"], followup: ["amber", "Follow-up", "पाठपुरावा"], converted: ["green", "Converted", "प्रवेश झाला"], notInterested: ["slate", "Not interested", "स्वारस्य नाही"],
  approved: ["green", "Approved", "मंजूर"], rejected: ["red", "Rejected", "नाकारले"], published: ["green", "Published", "प्रकाशित"], draft: ["slate", "Draft", "मसुदा"], archived: ["slate", "Archived", "संग्रहित"],
  scheduled: ["blue", "Scheduled", "नियोजित"], rescheduled: ["amber", "Rescheduled", "पुनर्नियोजित"], sent: ["green", "Sent", "पाठवले"],
  open: ["blue", "Open", "खुले"], inProgress: ["amber", "In progress", "प्रगतीत"], resolved: ["green", "Resolved", "सोडवले"], closed: ["slate", "Closed", "बंद"],
  low: ["slate", "Low", "कमी"], medium: ["blue", "Medium", "मध्यम"], high: ["orange", "High", "जास्त"], urgent: ["red", "Urgent", "तातडीचे"],
  submitted: ["blue", "Submitted", "जमा केले"], graded: ["green", "Graded", "गुण दिले"], correction: ["amber", "Correction requested", "दुरुस्ती आवश्यक"],
  success: ["green", "Success", "यशस्वी"], P: ["green", "Present", "उपस्थित"], A: ["red", "Absent", "अनुपस्थित"], L: ["amber", "Late", "उशीरा"],
  offline: ["slate", "Offline", "ऑफलाइन"], online: ["blue", "Online", "ऑनलाइन"], hybrid: ["violet", "Hybrid", "हायब्रिड"],
  easy: ["green", "Easy", "सोपे"], hard: ["red", "Hard", "कठीण"],
  none: ["slate", "No access", "प्रवेश नाही"], view: ["blue", "View only", "फक्त पाहणे"], manage: ["green", "View & manage", "पाहणे व व्यवस्थापन"],
};
export function StatusBadge({ s }: { s: string }) {
  const { lang } = useI18n();
  const v = STATUS[s];
  if (!v) return <Badge>{s}</Badge>;
  return <Badge tone={v[0]}>{lang === "mr" ? v[2] : v[1]}</Badge>;
}
export function statusLabel(s: string, lang: "en" | "mr") {
  const v = STATUS[s];
  return v ? (lang === "mr" ? v[2] : v[1]) : s;
}

export function DemoBadge({ className }: { className?: string }) {
  const { t } = useI18n();
  return <Badge tone="orange" className={className}>{t("Demo data", "डेमो माहिती")}</Badge>;
}

/* ---------------- Layout bits ---------------- */
export function PageHeader({ title, subtitle, actions }: { title: string; subtitle?: string; actions?: ReactNode }) {
  return (
    <div className="mb-5 flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
      <div>
        <h1 className="text-xl font-bold sm:text-2xl">{title}</h1>
        {subtitle && <p className="mt-1 text-sm text-slate-500">{subtitle}</p>}
      </div>
      {actions && <div className="flex flex-wrap gap-2">{actions}</div>}
    </div>
  );
}

export function StatCard({ label, value, hint, icon, tone = "brand" }: { label: string; value: ReactNode; hint?: ReactNode; icon?: ReactNode; tone?: "brand" | "green" | "amber" | "red" | "violet" }) {
  const bg = { brand: "bg-brand-50 text-brand-700", green: "bg-emerald-50 text-emerald-700", amber: "bg-amber-50 text-amber-700", red: "bg-red-50 text-red-700", violet: "bg-violet-50 text-violet-700" }[tone];
  return (
    <div className="card flex items-start gap-3 p-4">
      {icon && <div className={cn("rounded-lg p-2", bg)}>{icon}</div>}
      <div className="min-w-0">
        <p className="text-xs font-medium text-slate-500">{label}</p>
        <p className="mt-0.5 text-xl font-bold text-slate-900">{value}</p>
        {hint && <p className="mt-0.5 text-xs text-slate-500">{hint}</p>}
      </div>
    </div>
  );
}

export function EmptyState({ title, hint, action, icon }: { title: string; hint?: string; action?: ReactNode; icon?: ReactNode }) {
  return (
    <div className="flex flex-col items-center justify-center rounded-xl border border-dashed border-slate-300 bg-white px-6 py-10 text-center">
      <div className="mb-3 rounded-full bg-slate-100 p-3 text-slate-400">{icon ?? <Inbox className="size-6" />}</div>
      <p className="font-semibold text-slate-700">{title}</p>
      {hint && <p className="mt-1 max-w-sm text-sm text-slate-500">{hint}</p>}
      {action && <div className="mt-4">{action}</div>}
    </div>
  );
}

export function Tabs<T extends string>({ tabs, value, onChange }: { tabs: { id: T; label: string; count?: number }[]; value: T; onChange: (v: T) => void }) {
  return (
    <div role="tablist" className="mb-4 flex gap-1 overflow-x-auto border-b border-slate-200">
      {tabs.map((t) => (
        <button key={t.id} role="tab" aria-selected={value === t.id} onClick={() => onChange(t.id)}
          className={cn("-mb-px whitespace-nowrap border-b-2 px-3 py-2 text-sm font-medium transition-colors", value === t.id ? "border-brand-700 text-brand-800" : "border-transparent text-slate-500 hover:text-slate-800")}>
          {t.label}
          {t.count !== undefined && <span className="ml-1.5 rounded-full bg-slate-100 px-1.5 text-xs text-slate-600">{t.count}</span>}
        </button>
      ))}
    </div>
  );
}

/* ---------------- Forms ---------------- */
export function Field({ label, error, hint, required, children, className }: { label: string; error?: string; hint?: string; required?: boolean; children: (id: string) => ReactNode; className?: string }) {
  const id = useId();
  return (
    <div className={className}>
      <label htmlFor={id} className="label">{label}{required && <span className="text-red-600"> *</span>}</label>
      {children(id)}
      {hint && !error && <p className="mt-1 text-xs text-slate-500">{hint}</p>}
      {error && <p className="mt-1 text-xs text-red-600" role="alert">{error}</p>}
    </div>
  );
}

export function Toggle({ checked, onChange, label, disabled }: { checked: boolean; onChange: (v: boolean) => void; label?: string; disabled?: boolean }) {
  return (
    <button type="button" role="switch" aria-checked={checked} aria-label={label} disabled={disabled} onClick={() => onChange(!checked)}
      className={cn("inline-flex items-center gap-2 text-sm disabled:opacity-50")}>
      <span className={cn("relative inline-flex h-5 w-9 shrink-0 rounded-full transition-colors", checked ? "bg-brand-600" : "bg-slate-300")}>
        <span className={cn("absolute top-0.5 size-4 rounded-full bg-white shadow transition-all", checked ? "left-4.5" : "left-0.5")} />
      </span>
      {label && <span className="text-slate-700">{label}</span>}
    </button>
  );
}

export function SearchBox({ value, onChange, placeholder }: { value: string; onChange: (v: string) => void; placeholder?: string }) {
  return (
    <div className="relative w-full sm:w-64">
      <Search className="pointer-events-none absolute left-2.5 top-2.5 size-4 text-slate-400" />
      <input className="input pl-8" value={value} onChange={(e) => onChange(e.target.value)} placeholder={placeholder} aria-label={placeholder ?? "Search"} />
    </div>
  );
}

export function Select({ value, onChange, options, className, label }: { value: string; onChange: (v: string) => void; options: { value: string; label: string }[]; className?: string; label?: string }) {
  return (
    <select aria-label={label} className={cn("input w-auto", className)} value={value} onChange={(e) => onChange(e.target.value)}>
      {options.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
    </select>
  );
}

/* ---------------- Data display ---------------- */
export function Table({ head, children, empty }: { head: ReactNode[]; children: ReactNode; empty?: boolean }) {
  const { t } = useI18n();
  return (
    <div className="card overflow-hidden">
      <div className="overflow-x-auto">
        <table className="table-base">
          <thead><tr>{head.map((h, i) => <th key={i}>{h}</th>)}</tr></thead>
          <tbody>{children}</tbody>
        </table>
      </div>
      {empty && <p className="px-4 py-8 text-center text-sm text-slate-500">{t("No records match the current filters.", "निवडलेल्या फिल्टरनुसार नोंदी आढळल्या नाहीत.")}</p>}
    </div>
  );
}

export function Progress({ value, tone = "brand" }: { value: number; tone?: "brand" | "green" | "amber" | "red" }) {
  const c = { brand: "bg-brand-600", green: "bg-emerald-600", amber: "bg-amber-500", red: "bg-red-600" }[tone];
  return (
    <div className="h-2 w-full overflow-hidden rounded-full bg-slate-100" role="progressbar" aria-valuenow={Math.round(value)} aria-valuemin={0} aria-valuemax={100}>
      <div className={cn("h-full rounded-full transition-all", c)} style={{ width: `${Math.max(0, Math.min(100, value))}%` }} />
    </div>
  );
}

/** Simple accessible bar chart (no external chart library). */
export function BarChart({ data, format = (n) => String(n), height = 160 }: { data: { label: string; value: number; secondary?: number }[]; format?: (n: number) => string; height?: number }) {
  const max = Math.max(1, ...data.map((d) => Math.max(d.value, d.secondary ?? 0)));
  return (
    <div className="flex items-end gap-2" style={{ height }} role="img" aria-label={data.map((d) => `${d.label}: ${format(d.value)}`).join(", ")}>
      {data.map((d) => (
        <div key={d.label} className="group flex h-full min-w-0 flex-1 flex-col items-center justify-end gap-1">
          <span className="text-[10px] font-medium text-slate-500 opacity-0 transition-opacity group-hover:opacity-100">{format(d.value)}</span>
          <div className="flex w-full items-end justify-center gap-0.5" style={{ height: "100%" }}>
            <div className="w-full max-w-8 rounded-t bg-brand-600" style={{ height: `${(d.value / max) * 100}%`, minHeight: d.value ? 2 : 0 }} />
            {d.secondary !== undefined && <div className="w-full max-w-8 rounded-t bg-saffron-400" style={{ height: `${(d.secondary / max) * 100}%`, minHeight: d.secondary ? 2 : 0 }} />}
          </div>
          <span className="w-full truncate text-center text-[11px] text-slate-500">{d.label}</span>
        </div>
      ))}
    </div>
  );
}

export function Donut({ value, label, size = 96, tone = "#3442b8" }: { value: number; label?: string; size?: number; tone?: string }) {
  const r = 40, c = 2 * Math.PI * r;
  return (
    <div className="relative inline-flex items-center justify-center" style={{ width: size, height: size }}>
      <svg viewBox="0 0 100 100" className="-rotate-90" width={size} height={size} aria-hidden>
        <circle cx="50" cy="50" r={r} fill="none" stroke="#e2e8f0" strokeWidth="10" />
        <circle cx="50" cy="50" r={r} fill="none" stroke={tone} strokeWidth="10" strokeDasharray={c} strokeDashoffset={c - (c * Math.min(100, value)) / 100} strokeLinecap="round" />
      </svg>
      <div className="absolute text-center">
        <p className="text-lg font-bold">{Math.round(value)}%</p>
        {label && <p className="text-[10px] text-slate-500">{label}</p>}
      </div>
    </div>
  );
}

export function Avatar({ name, size = 36 }: { name: string; size?: number }) {
  const ini = name.split(/\s+/).filter((p) => /[A-Za-zऀ-ॿ]/.test(p[0] ?? "")).slice(0, 2).map((p) => p[0]).join("").toUpperCase();
  return <span className="inline-flex shrink-0 items-center justify-center rounded-full bg-brand-100 font-semibold text-brand-800" style={{ width: size, height: size, fontSize: size * 0.38 }}>{ini || "?"}</span>;
}

export function Card({ title, actions, children, className, bodyClass }: { title?: ReactNode; actions?: ReactNode; children: ReactNode; className?: string; bodyClass?: string }) {
  return (
    <section className={cn("card", className)}>
      {(title || actions) && (
        <div className="flex items-center justify-between gap-2 border-b border-slate-100 px-4 py-3">
          {title && <h2 className="text-sm font-semibold text-slate-800">{title}</h2>}
          {actions}
        </div>
      )}
      <div className={cn("p-4", bodyClass)}>{children}</div>
    </section>
  );
}

export function useDisclosure(init = false) {
  const [open, setOpen] = useState(init);
  return { open, onOpen: () => setOpen(true), onClose: () => setOpen(false), setOpen };
}

export function Notice({ tone = "info", children }: { tone?: "info" | "warn" | "danger" | "success"; children: ReactNode }) {
  const c = { info: "border-brand-200 bg-brand-50 text-brand-900", warn: "border-amber-200 bg-amber-50 text-amber-900", danger: "border-red-200 bg-red-50 text-red-900", success: "border-emerald-200 bg-emerald-50 text-emerald-900" }[tone];
  const I = tone === "warn" || tone === "danger" ? AlertTriangle : tone === "success" ? CheckCircle2 : Info;
  return <div className={cn("flex gap-2 rounded-lg border px-3 py-2.5 text-sm", c)}><I className="mt-0.5 size-4 shrink-0" /><div>{children}</div></div>;
}
