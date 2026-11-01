import type { L, Lang } from "./types";

export const cn = (...c: (string | number | false | null | undefined)[]) => c.filter(Boolean).join(" ");

export const uid = (p = "id") => `${p}_${Math.random().toString(36).slice(2, 8)}${Date.now().toString(36).slice(-4)}`;

/** ISO date (YYYY-MM-DD) offset from today. */
export function dayOffset(days: number, base = new Date()): string {
  const d = new Date(base);
  d.setDate(d.getDate() + days);
  return toISODate(d);
}
export function toISODate(d: Date) {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${y}-${m}-${day}`;
}
export const today = () => toISODate(new Date());
export const nowISO = () => new Date().toISOString();

export function addMonths(iso: string, months: number) {
  const d = new Date(iso);
  d.setMonth(d.getMonth() + months);
  d.setDate(d.getDate() - 1);
  return toISODate(d);
}
export function daysBetween(a: string, b: string) {
  return Math.round((new Date(b).getTime() - new Date(a).getTime()) / 86400000);
}

const loc = (lang: Lang) => (lang === "mr" ? "mr-IN-u-nu-latn" : "en-IN");

export function fmtDate(iso: string | undefined, lang: Lang = "en", opts?: Intl.DateTimeFormatOptions) {
  if (!iso) return "—";
  const d = new Date(iso.length === 10 ? iso + "T00:00:00" : iso);
  if (isNaN(d.getTime())) return iso;
  return d.toLocaleDateString(loc(lang), opts ?? { day: "2-digit", month: "short", year: "numeric" });
}
export function fmtDateTime(iso: string | undefined, lang: Lang = "en") {
  if (!iso) return "—";
  const d = new Date(iso);
  return d.toLocaleString(loc(lang), { day: "2-digit", month: "short", year: "numeric", hour: "2-digit", minute: "2-digit" });
}
export function fmtINR(n: number) {
  return "₹" + Math.round(n).toLocaleString("en-IN");
}
export function fmtNum(n: number) {
  return n.toLocaleString("en-IN");
}
export function fmtMobile(m: string) {
  const d = m.replace(/\D/g, "").slice(-10);
  if (d.length !== 10) return m;
  return `+91 ${d.slice(0, 5)} ${d.slice(5)}`;
}
export function fmtSize(kb: number) {
  return kb >= 1024 ? `${(kb / 1024).toFixed(1)} MB` : `${kb} KB`;
}
export const isValidMobile = (m: string) => /^[6-9]\d{9}$/.test(m.replace(/\D/g, "").slice(-10)) && m.replace(/\D/g, "").length >= 10;
export const normMobile = (m: string) => m.replace(/\D/g, "").slice(-10);
export const isValidEmail = (e: string) => !e || /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(e);

export const pick = (l: L | undefined, lang: Lang) => (l ? l[lang] || l.en : "");

export function downloadFile(filename: string, content: string, mime = "text/csv;charset=utf-8") {
  // BOM so Excel opens Marathi (UTF-8) correctly
  const blob = new Blob(["﻿" + content], { type: mime });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
export function toCSV(rows: Record<string, unknown>[]) {
  if (!rows.length) return "";
  const keys = Object.keys(rows[0]);
  const esc = (v: unknown) => {
    const s = v == null ? "" : String(v);
    return /[",\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
  };
  return [keys.join(","), ...rows.map((r) => keys.map((k) => esc(r[k])).join(","))].join("\n");
}
/** Minimal CSV parser (handles quotes). */
export function parseCSV(text: string): string[][] {
  const rows: string[][] = [];
  let row: string[] = [];
  let cur = "";
  let q = false;
  for (let i = 0; i < text.length; i++) {
    const c = text[i];
    if (q) {
      if (c === '"' && text[i + 1] === '"') { cur += '"'; i++; }
      else if (c === '"') q = false;
      else cur += c;
    } else if (c === '"') q = true;
    else if (c === ",") { row.push(cur); cur = ""; }
    else if (c === "\n" || c === "\r") {
      if (c === "\r" && text[i + 1] === "\n") i++;
      row.push(cur); cur = "";
      if (row.some((x) => x.trim() !== "")) rows.push(row);
      row = [];
    } else cur += c;
  }
  row.push(cur);
  if (row.some((x) => x.trim() !== "")) rows.push(row);
  return rows.map((r) => r.map((x) => x.replace(/^﻿/, "")));
}

export function initials(name: string) {
  return name.split(/\s+/).filter(Boolean).slice(0, 2).map((p) => p[0]).join("").toUpperCase();
}

/** Deterministic PRNG for seed data. */
export function seeded(seed: number) {
  let s = seed;
  return () => {
    s = (s * 1664525 + 1013904223) % 4294967296;
    return s / 4294967296;
  };
}
