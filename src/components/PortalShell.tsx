"use client";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState, type ComponentType } from "react";
import { Bell, LogOut, Menu, X, ExternalLink, Wifi, WifiOff, ShieldAlert } from "lucide-react";
import { Logo, LangSwitch } from "./brand";
import { Avatar, Badge, EmptyState } from "./ui";
import { useStore } from "@/lib/store";
import { useI18n } from "@/lib/i18n";
import { cn } from "@/lib/utils";
import type { Access, Module, Role } from "@/lib/types";

export type NavLink = { href: string; en: string; mr: string; icon: ComponentType<{ className?: string }>; module?: Module; badge?: number };
export type NavGroup = { en: string; mr: string; items: NavLink[] };

/** Access level of the logged-in staff member for a module. Owner always has full access. */
export function useAccess(module: Module): Access {
  return useStore((s) => {
    const u = s.db.staff.find((x) => x.id === s.session.userId);
    if (!u) return "none";
    if (u.role === "owner") return "manage";
    return u.permissions[module] ?? "none";
  });
}

export function useOnline() {
  const [online, setOnline] = useState(true);
  useEffect(() => {
    setOnline(navigator.onLine);
    const on = () => setOnline(true), off = () => setOnline(false);
    window.addEventListener("online", on); window.addEventListener("offline", off);
    return () => { window.removeEventListener("online", on); window.removeEventListener("offline", off); };
  }, []);
  return online;
}

export function PortalShell({ roles, groups, title, children, notifHref }: { roles: Role[]; groups: NavGroup[]; title: { en: string; mr: string }; children: React.ReactNode; notifHref?: string }) {
  const { t } = useI18n();
  const path = usePathname();
  const router = useRouter();
  const { session, db, hydrated, logout } = useStore();
  const [open, setOpen] = useState(false);
  const online = useOnline();
  useEffect(() => setOpen(false), [path]);

  const staff = db.staff.find((s) => s.id === session.userId);
  const student = db.students.find((s) => s.id === session.userId);
  const name = student?.name ?? staff?.name ?? "";
  const perms = staff?.role === "owner" ? null : staff?.permissions;
  const unread = student ? db.inbox.filter((n) => (n.studentId === student.id || n.studentId === "*") && !n.read).length : 0;

  if (!hydrated) return <div className="flex min-h-screen items-center justify-center text-sm text-slate-400">{t("Loading…", "लोड होत आहे…")}</div>;
  if (!roles.includes(session.role))
    return (
      <div className="flex min-h-screen items-center justify-center p-4">
        <div className="max-w-md">
          <EmptyState icon={<ShieldAlert className="size-6" />} title={t("Please log in to continue", "पुढे जाण्यासाठी लॉगिन करा")} hint={t("This area is available only to authorised users.", "हा विभाग फक्त अधिकृत वापरकर्त्यांसाठी आहे.")}
            action={<div className="flex gap-2"><Link className="btn-primary" href={`/login?next=${encodeURIComponent(path)}`}>{t("Login", "लॉगिन")}</Link><Link className="btn-outline" href="/">{t("Home", "मुख्यपृष्ठ")}</Link></div>} />
        </div>
      </div>
    );

  const visible = groups
    .map((g) => ({ ...g, items: g.items.filter((i) => !i.module || !perms || (perms[i.module] ?? "none") !== "none") }))
    .filter((g) => g.items.length);
  const isActive = (href: string) => path === href || (href.split("/").length > 2 && path.startsWith(href + "/"));

  const nav = (
    <nav className="flex-1 space-y-5 overflow-y-auto px-3 py-4" aria-label="Portal">
      {visible.map((g) => (
        <div key={g.en}>
          {visible.length > 1 && <p className="mb-1 px-2 text-[11px] font-semibold uppercase tracking-wider text-slate-400">{t(g.en, g.mr)}</p>}
          <ul className="space-y-0.5">
            {g.items.map((i) => (
              <li key={i.href}>
                <Link href={i.href} className={cn("flex items-center gap-2.5 rounded-lg px-2.5 py-2 text-sm", isActive(i.href) ? "bg-brand-700 font-semibold text-white" : "text-slate-600 hover:bg-slate-100 hover:text-slate-900")} aria-current={isActive(i.href) ? "page" : undefined}>
                  <i.icon className="size-4 shrink-0" /><span className="flex-1 truncate">{t(i.en, i.mr)}</span>
                  {!!i.badge && <span className={cn("rounded-full px-1.5 text-[11px] font-semibold", isActive(i.href) ? "bg-white/20" : "bg-saffron-100 text-saffron-700")}>{i.badge}</span>}
                </Link>
              </li>
            ))}
          </ul>
        </div>
      ))}
    </nav>
  );

  return (
    <div className="flex min-h-screen bg-slate-50">
      <aside className="no-print sticky top-0 hidden h-screen w-64 shrink-0 flex-col border-r border-slate-200 bg-white lg:flex">
        <div className="flex h-16 items-center border-b border-slate-200 px-4"><Logo /></div>
        {nav}
        <div className="border-t border-slate-200 p-3 text-xs text-slate-400"><Badge tone="orange">{t("Prototype · demo data", "प्रोटोटाइप · डेमो माहिती")}</Badge></div>
      </aside>
      {open && (
        <div className="fixed inset-0 z-50 lg:hidden">
          <div className="absolute inset-0 bg-slate-900/40" onClick={() => setOpen(false)} />
          <aside className="absolute left-0 top-0 flex h-full w-72 max-w-[85%] flex-col bg-white shadow-xl">
            <div className="flex h-16 items-center justify-between border-b px-4"><Logo /><button className="btn-ghost" onClick={() => setOpen(false)} aria-label="Close"><X className="size-5" /></button></div>
            {nav}
          </aside>
        </div>
      )}
      <div className="flex min-w-0 flex-1 flex-col">
        <header className="no-print sticky top-0 z-30 flex h-16 items-center gap-2 border-b border-slate-200 bg-white/95 px-4 backdrop-blur sm:px-6">
          <button className="btn-ghost -ml-2 lg:hidden" onClick={() => setOpen(true)} aria-label={t("Open menu", "मेनू उघडा")}><Menu className="size-5" /></button>
          <p className="truncate text-sm font-semibold text-slate-700">{t(title.en, title.mr)}</p>
          <div className="ml-auto flex items-center gap-2">
            <span className={cn("hidden items-center gap-1 text-xs sm:flex", online ? "text-emerald-600" : "text-red-600")} title={online ? "Online" : "Offline"}>{online ? <Wifi className="size-3.5" /> : <WifiOff className="size-3.5" />}{online ? t("Synced", "समक्रमित") : t("Offline", "ऑफलाइन")}</span>
            <LangSwitch />
            {notifHref && (
              <Link href={notifHref} className="btn-ghost relative px-2" aria-label={t("Notifications", "सूचना")}>
                <Bell className="size-5" />{unread > 0 && <span className="absolute right-0.5 top-0.5 flex size-4 items-center justify-center rounded-full bg-red-600 text-[10px] font-bold text-white">{unread}</span>}
              </Link>
            )}
            <Link href="/" className="btn-ghost hidden px-2 sm:inline-flex" title={t("Public website", "सार्वजनिक संकेतस्थळ")}><ExternalLink className="size-4" /></Link>
            <div className="flex items-center gap-2 border-l border-slate-200 pl-2">
              <Avatar name={name} size={32} />
              <div className="hidden leading-tight md:block"><p className="max-w-36 truncate text-sm font-medium">{name}</p><p className="text-[11px] capitalize text-slate-500">{session.role}</p></div>
              <button className="btn-ghost px-2" onClick={() => { logout(); router.push("/login"); }} aria-label={t("Log out", "बाहेर पडा")} title={t("Log out", "बाहेर पडा")}><LogOut className="size-4" /></button>
            </div>
          </div>
        </header>
        {!online && <div className="bg-red-600 px-4 py-1.5 text-center text-xs text-white">{t("You are offline. Changes are saved on this device and will sync when you reconnect.", "आपण ऑफलाइन आहात. बदल या उपकरणावर जतन होतील व पुन्हा जोडल्यावर समक्रमित होतील.")}</div>}
        <main id="main" className="min-w-0 flex-1 p-4 sm:p-6">{children}</main>
      </div>
    </div>
  );
}

export function NoAccess() {
  const { t } = useI18n();
  return <EmptyState icon={<ShieldAlert className="size-6" />} title={t("You don't have access to this module", "या विभागासाठी आपल्याला परवानगी नाही")} hint={t("Ask the owner/admin to grant permission from Permissions.", "मालक/प्रशासकाला 'परवानग्या' मधून परवानगी देण्यास सांगा.")} />;
}
