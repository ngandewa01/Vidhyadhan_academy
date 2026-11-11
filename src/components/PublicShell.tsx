"use client";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useState } from "react";
import { ChevronDown, Menu, X, Phone, Mail, MapPin, Clock, LayoutDashboard } from "lucide-react";
import { Logo, LangSwitch, WhatsAppIcon } from "./brand";
import { useI18n } from "@/lib/i18n";
import { useStore } from "@/lib/store";
import { academy, isPlaceholder } from "@/config/academy";
import { cn } from "@/lib/utils";

type NavItem = { href: string; en: string; mr: string };
const MAIN: NavItem[] = [
  { href: "/", en: "Home", mr: "मुख्यपृष्ठ" },
  { href: "/courses", en: "Courses", mr: "अभ्यासक्रम" },
  { href: "/batches", en: "Batches", mr: "बॅचेस" },
  { href: "/fees", en: "Fees", mr: "शुल्क" },
  { href: "/library", en: "Abhyasika Kendra", mr: "अभ्यासिका केंद्र" },
  { href: "/exam-updates", en: "Exam Updates", mr: "परीक्षा अपडेट्स" },
  { href: "/contact", en: "Contact", mr: "संपर्क" },
];
const MORE: NavItem[] = [
  { href: "/about", en: "About", mr: "आमच्याविषयी" },
  { href: "/syllabus", en: "Syllabus", mr: "अभ्यासक्रम आराखडा" },
  { href: "/faculty", en: "Faculty", mr: "प्राध्यापक" },
  { href: "/results", en: "Results & Achievements", mr: "निकाल व यश" },
  { href: "/testimonials", en: "Testimonials", mr: "अभिप्राय" },
  { href: "/gallery", en: "Gallery", mr: "छायाचित्रे" },
  { href: "/announcements", en: "Announcements", mr: "सूचना" },
  { href: "/resources", en: "Important Links", mr: "महत्त्वाच्या लिंक्स" },
  { href: "/brochure", en: "Download brochure", mr: "ब्रोशर डाउनलोड" },
  { href: "/enquiry", en: "Enquiry", mr: "चौकशी" },
  { href: "/verify", en: "Verify Certificate / ID", mr: "प्रमाणपत्र / ओळखपत्र पडताळणी" },
];

export function portalHome(role: string) {
  return role === "student" ? "/student" : role === "faculty" ? "/faculty-portal" : role === "staff" || role === "owner" ? "/admin" : "/login";
}

export function PublicShell({ children }: { children: React.ReactNode }) {
  const { t, l } = useI18n();
  const path = usePathname();
  const [open, setOpen] = useState(false);
  const [more, setMore] = useState(false);
  const role = useStore((s) => s.session.role);
  useEffect(() => { setOpen(false); setMore(false); }, [path]);
  const active = (h: string) => (h === "/" ? path === "/" : path.startsWith(h));
  const wa = `https://wa.me/${academy.whatsapp}?text=${encodeURIComponent("Hello Vidyadhan Academy, I would like to know about admissions.")}`;

  return (
    <div className="flex min-h-screen flex-col">
      <div className="no-print bg-brand-950 text-xs text-brand-100">
        <div className="container-page flex h-8 items-center justify-between gap-3">
          <span className="truncate">{t("Admissions open for new batches", "नवीन बॅचेससाठी प्रवेश सुरू")} · <Link href="/admission" className="font-semibold text-saffron-200 underline-offset-2 hover:underline">{t("Apply online", "ऑनलाइन अर्ज")}</Link></span>
          <span className="hidden items-center gap-4 sm:flex"><span className="flex items-center gap-1"><Phone className="size-3" />{academy.phone}</span><span className="flex items-center gap-1"><Clock className="size-3" />{l(academy.hours)}</span></span>
        </div>
      </div>
      <header className="no-print sticky top-0 z-40 border-b border-slate-200 bg-white/95 backdrop-blur">
        <div className="container-page flex h-16 items-center justify-between gap-4">
          <Logo />
          <nav className="hidden items-center gap-1 lg:flex" aria-label="Main">
            {MAIN.map((n) => (
              <Link key={n.href} href={n.href} className={cn("rounded-md px-2.5 py-2 text-sm font-medium", active(n.href) ? "text-brand-800 bg-brand-50" : "text-slate-600 hover:text-slate-900")}>{t(n.en, n.mr)}</Link>
            ))}
            <div className="relative">
              <button onClick={() => setMore((v) => !v)} aria-expanded={more} className="flex items-center gap-1 rounded-md px-2.5 py-2 text-sm font-medium text-slate-600 hover:text-slate-900">
                {t("More", "अधिक")} <ChevronDown className="size-4" />
              </button>
              {more && (
                <div className="absolute right-0 top-full mt-1 w-64 rounded-xl border border-slate-200 bg-white p-1.5 shadow-lg">
                  {MORE.map((n) => <Link key={n.href} href={n.href} className="block rounded-md px-3 py-2 text-sm text-slate-700 hover:bg-slate-50">{t(n.en, n.mr)}</Link>)}
                </div>
              )}
            </div>
          </nav>
          <div className="flex items-center gap-2">
            <LangSwitch className="hidden sm:inline-flex" />
            {role === "visitor" ? (
              <Link href="/login" className="btn-outline hidden sm:inline-flex">{t("Login", "लॉगिन")}</Link>
            ) : (
              <Link href={portalHome(role)} className="btn-outline hidden sm:inline-flex"><LayoutDashboard className="size-4" />{t("My dashboard", "माझे डॅशबोर्ड")}</Link>
            )}
            <Link href="/admission" className="btn-accent hidden md:inline-flex">{t("Apply now", "प्रवेश घ्या")}</Link>
            <button className="btn-ghost lg:hidden" onClick={() => setOpen(true)} aria-label={t("Open menu", "मेनू उघडा")}><Menu className="size-5" /></button>
          </div>
        </div>
      </header>

      {open && (
        <div className="fixed inset-0 z-50 lg:hidden">
          <div className="absolute inset-0 bg-slate-900/40" onClick={() => setOpen(false)} />
          <div className="absolute right-0 top-0 flex h-full w-80 max-w-[85%] flex-col bg-white shadow-xl">
            <div className="flex items-center justify-between border-b p-4"><LangSwitch /><button className="btn-ghost" onClick={() => setOpen(false)} aria-label="Close"><X className="size-5" /></button></div>
            <nav className="flex-1 overflow-y-auto p-2" aria-label="Mobile">
              {[...MAIN, ...MORE].map((n) => (
                <Link key={n.href} href={n.href} className={cn("block rounded-md px-3 py-2.5 text-sm", active(n.href) ? "bg-brand-50 font-semibold text-brand-800" : "text-slate-700")}>{t(n.en, n.mr)}</Link>
              ))}
            </nav>
            <div className="grid gap-2 border-t p-4">
              <Link href="/admission" className="btn-accent">{t("Apply now", "प्रवेश घ्या")}</Link>
              <Link href={portalHome(role)} className="btn-outline">{role === "visitor" ? t("Login / Register", "लॉगिन / नोंदणी") : t("My dashboard", "माझे डॅशबोर्ड")}</Link>
            </div>
          </div>
        </div>
      )}

      <main id="main" className="flex-1">{children}</main>

      <footer className="no-print mt-16 bg-brand-950 text-brand-100">
        <div className="container-page grid gap-8 py-12 sm:grid-cols-2 lg:grid-cols-4">
          <div className="space-y-3">
            <Logo light />
            <p className="text-sm text-brand-200">{l(academy.tagline)}</p>
          </div>
          <div>
            <h3 className="mb-3 text-sm font-semibold text-white">{t("Courses", "अभ्यासक्रम")}</h3>
            <ul className="space-y-1.5 text-sm">
              {[["mpsc-rajyaseva", "MPSC Rajyaseva", "एमपीएससी राज्यसेवा"], ["psi-sti-aso", "PSI / STI / ASO", "पीएसआय / एसटीआय / एएसओ"], ["police-bharti", "Police Bharti", "पोलीस भरती"], ["talathi-bharti", "Talathi Bharti", "तलाठी भरती"]].map(([s, e, m]) => (
                <li key={s}><Link className="hover:text-white" href={`/courses/${s}`}>{t(e, m)}</Link></li>
              ))}
            </ul>
          </div>
          <div>
            <h3 className="mb-3 text-sm font-semibold text-white">{t("Quick links", "उपयुक्त दुवे")}</h3>
            <ul className="space-y-1.5 text-sm">
              {MORE.slice(0, 8).map((n) => <li key={n.href}><Link className="hover:text-white" href={n.href}>{t(n.en, n.mr)}</Link></li>)}
            </ul>
          </div>
          <div className="space-y-2 text-sm">
            <h3 className="mb-3 text-sm font-semibold text-white">{t("Contact", "संपर्क")}</h3>
            <p className="flex gap-2"><MapPin className="mt-0.5 size-4 shrink-0" />{l(academy.address)}</p>
            <p className="flex gap-2"><Phone className="mt-0.5 size-4 shrink-0" />{academy.phone}</p>
            <p className="flex gap-2"><Mail className="mt-0.5 size-4 shrink-0" />{academy.email}</p>
            {isPlaceholder(academy.phone) && <p className="text-xs text-saffron-200">{t("Contact details are placeholders until provided by the academy.", "अकॅडमीकडून माहिती मिळेपर्यंत संपर्क तपशील नमुना आहेत.")}</p>}
          </div>
        </div>
        <div className="border-t border-white/10">
          <div className="container-page flex flex-col gap-2 py-4 text-xs text-brand-300 sm:flex-row sm:justify-between">
            <span>© {new Date().getFullYear()} {l(academy.name)}. {t("All rights reserved.", "सर्व हक्क राखीव.")}</span>
            <span className="flex flex-wrap items-center gap-x-4 gap-y-1">
              <Link href="/privacy" className="hover:text-white hover:underline">{t("Privacy Policy", "गोपनीयता धोरण")}</Link>
              <Link href="/terms" className="hover:text-white hover:underline">{t("Terms", "अटी व शर्ती")}</Link>
              <span>{t("Prototype build – sample data is marked as demo.", "प्रोटोटाइप – नमुना माहिती 'डेमो' म्हणून चिन्हांकित.")}</span>
            </span>
          </div>
        </div>
      </footer>

      <a href={wa} target="_blank" rel="noopener noreferrer" aria-label="WhatsApp" className="no-print fixed bottom-5 right-5 z-30 flex size-13 items-center justify-center rounded-full bg-[#25D366] text-white shadow-lg transition-transform hover:scale-105">
        <WhatsAppIcon className="size-7" />
      </a>
    </div>
  );
}
