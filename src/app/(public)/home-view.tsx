"use client";
import { Fragment, type ReactNode, useEffect, useRef } from "react";
import Link from "next/link";
import { ArrowRight, BookOpenCheck, CalendarDays, CheckCircle2, GraduationCap, Library, Megaphone, Phone, Trophy, Users, BellRing } from "lucide-react";
import { useI18n } from "@/lib/i18n";
import { useStore } from "@/lib/store";
import { CourseCard, DemoNote, EnquiryForm, SectionHead } from "@/components/public";
import { Badge, StatusBadge } from "@/components/ui";
import { WhatsAppIcon } from "@/components/brand";
import { GoogleReviews, ResultCard } from "@/components/GoogleReviews";
import { academy } from "@/config/academy";
import { fmtINR } from "@/lib/utils";
import { resolveHomeSections } from "@/lib/seed";
import type { HomeSectionKey } from "@/lib/types";

export default function HomeView() {
  const { t, l, d } = useI18n();
  const db = useStore((s) => s.db);
  const cms = db.settings.cms;
  const courses = db.courses.filter((c) => c.published);
  const upcoming = [...db.batches].filter((b) => b.admissionOpen).sort((a, b) => a.startDate.localeCompare(b.startDate)).slice(0, 4);
  const anns = db.announcements.filter((a) => a.published).sort((a, b) => Number(b.pinned) - Number(a.pinned) || b.date.localeCompare(a.date)).slice(0, 3);
  const results = db.achievements.filter((a) => a.published && a.consent).sort((a, b) => Number(!!b.featured) - Number(!!a.featured)).slice(0, 12);
  const updates = db.updates.filter((u) => u.status === "published").slice(0, 3);
  const minLib = Math.min(...db.libraryPlans.filter((p) => p.kind === "library").map((p) => p.fee));

  const sections = resolveHomeSections(cms.homeSections);
  const blocks: Record<HomeSectionKey, ReactNode> = {
    courses: (
      <>
      <section className="container-page py-14">
        <SectionHead title={t("Courses we offer", "आमचे अभ्यासक्रम")} subtitle={t("Choose your target examination", "आपली लक्ष्य परीक्षा निवडा")} action={<Link href="/courses" className="btn-outline">{t("All courses", "सर्व अभ्यासक्रम")}<ArrowRight className="size-4" /></Link>} />
        <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">{courses.slice(0, 6).map((c) => <CourseCard key={c.id} c={c} />)}</div>
        <DemoNote>{t("Fees shown are demo values, not actual academy prices.", "दाखवलेले शुल्क डेमो असून प्रत्यक्ष अकॅडमीचे दर नाहीत.")}</DemoNote>
      </section>
      </>
    ),
    why: (
      <>
      <section className="bg-white py-14">
        <div className="container-page">
          <SectionHead title={t("Why Vidyadhan Academy", "विद्याधन अकॅडमीच का?")} />
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {cms.whyUs.map((w, i) => {
              const I = [BookOpenCheck, Trophy, GraduationCap, Users, Library, CheckCircle2][i % 6];
              return (
                <div key={i} className="flex items-start gap-3 rounded-xl border border-slate-200 p-4">
                  <span className="rounded-lg bg-brand-50 p-2 text-brand-700"><I className="size-5" /></span>
                  <p className="pt-1 font-medium">{l(w)}</p>
                </div>
              );
            })}
          </div>
        </div>
      </section>
      </>
    ),
    batches: (
      <>
      <section className="container-page grid gap-8 py-14 lg:grid-cols-[1.4fr_1fr]">
        <div>
          <SectionHead title={t("Upcoming & open batches", "आगामी व सुरू बॅचेस")} action={<Link href="/batches" className="text-sm font-semibold text-brand-700 hover:underline">{t("View schedule", "वेळापत्रक पहा")} →</Link>} />
          <div className="space-y-3">
            {upcoming.map((b) => {
              const c = db.courses.find((x) => x.id === b.courseId)!;
              return (
                <div key={b.id} className="card flex flex-col gap-3 p-4 sm:flex-row sm:items-center sm:justify-between">
                  <div>
                    <p className="text-xs font-medium text-brand-700">{l(c.name)}</p>
                    <p className="font-semibold">{b.name}</p>
                    <p className="mt-1 flex flex-wrap items-center gap-2 text-sm text-slate-500"><CalendarDays className="size-4" />{t("Starts", "सुरुवात")} {d(b.startDate)} · {b.days} · {b.time} <StatusBadge s={b.mode} /></p>
                  </div>
                  <Link href={`/admission?batch=${b.id}`} className="btn-primary btn-sm self-start sm:self-center">{t("Enrol", "प्रवेश")}</Link>
                </div>
              );
            })}
          </div>
        </div>
        <div>
          <SectionHead title={t("Latest updates", "ताज्या सूचना")} action={<Link href="/announcements" className="text-sm font-semibold text-brand-700 hover:underline">{t("All", "सर्व")} →</Link>} />
          <ul className="card divide-y divide-slate-100">
            {anns.map((a) => (
              <li key={a.id} className="flex gap-3 p-4">
                <Megaphone className="mt-0.5 size-5 shrink-0 text-saffron-600" />
                <div><p className="font-medium">{l(a.title)}</p><p className="text-xs text-slate-500">{d(a.date)}</p></div>
              </li>
            ))}
            {updates.map((u) => (
              <li key={u.id} className="flex gap-3 p-4">
                <BellRing className="mt-0.5 size-5 shrink-0 text-brand-600" />
                <div><p className="font-medium">{l(u.title)}</p><Link href="/exam-updates" className="text-xs text-brand-700 hover:underline">{t("Official exam update", "अधिकृत परीक्षा अपडेट")}</Link></div>
              </li>
            ))}
          </ul>
        </div>
      </section>
      </>
    ),
    results: (
      <>
      <section className="bg-white py-14">
        <div className="container-page">
          <SectionHead title={t("Our successful students", "आमचे यशस्वी विद्यार्थी")} subtitle={t("Proud moments of the Vidyadhan family", "विद्याधन परिवाराचे अभिमानाचे क्षण")} action={<Link href="/results" className="btn-outline">{t("View all", "सर्व पहा")}</Link>} />
          {results.length ? (
            <ResultsCarousel items={results.map((r) => <ResultCard key={r.id} a={r} courseName={l(db.courses.find((c) => c.id === r.courseId)?.name)} />)} />
          ) :  <p className="text-sm text-slate-500">{t("Results will be published here.", "निकाल येथे प्रसिद्ध होतील.")}</p>}
        </div>
      </section>
      </>
    ),
    reviews: (
      <>
      <section className="container-page py-14">
        <SectionHead title={t("What students say", "विद्यार्थी काय म्हणतात")} />
        <GoogleReviews limit={3} />
      </section>
      </>
    ),
    library: (
      <>
      <section className="container-page grid gap-8 py-14 lg:grid-cols-2">
        <div>
          <SectionHead title={t("Our faculty", "आमचे प्राध्यापक")} action={<Link href="/faculty" className="text-sm font-semibold text-brand-700 hover:underline">{t("Meet the team", "टीमला भेटा")} →</Link>} />
          <div className="grid gap-3 sm:grid-cols-3">
            {db.facultyProfiles.filter((f) => f.published).map((f) => (
              <div key={f.id} className="card p-4 text-center">
                <div className="mx-auto mb-2 flex size-14 items-center justify-center rounded-full bg-brand-50 text-brand-400"><GraduationCap className="size-6" /></div>
                <p className="text-sm font-semibold">{f.name}</p>
                <p className="text-xs text-slate-500">{l(f.subject)}</p>
              </div>
            ))}
          </div>
        </div>
        <div className="card overflow-hidden">
          <div className="bg-gradient-to-br from-saffron-600 to-saffron-500 p-6 text-white">
            <Library className="mb-2 size-8" />
            <h2 className="text-2xl font-bold !text-white">{t("Abhyasika Kendra", "अभ्यासिका केंद्र")}</h2>
            <p className="mt-2 text-saffron-50">{l(cms.libraryBlurb)}</p>
          </div>
          <div className="flex flex-wrap items-center justify-between gap-3 p-5">
            <p className="text-sm text-slate-600">{t("Plans from", "योजना")} <b className="text-slate-900">{fmtINR(minLib)}</b>{t("/month", "/महिना पासून")} <span className="text-xs text-saffron-700">{t("(demo)", "(डेमो)")}</span></p>
            <Link href="/library" className="btn-primary">{t("View plans", "योजना पहा")}</Link>
          </div>
        </div>
      </section>
      </>
    ),
    cta: (
      <>
      <section className="container-page">
        <div className="flex flex-col items-start justify-between gap-6 rounded-2xl bg-brand-900 p-8 text-white sm:flex-row sm:items-center">
          <div>
            <h2 className="text-2xl font-bold !text-white">{t("Talk to our admissions team", "प्रवेश विभागाशी बोला")}</h2>
            <p className="mt-1 text-brand-200">{t("Call or WhatsApp us for batch availability and fee details.", "बॅच उपलब्धता व शुल्क माहितीसाठी कॉल किंवा व्हॉट्सअ‍ॅप करा.")}</p>
          </div>
          <div className="flex flex-wrap gap-3">
            <a href={`tel:${academy.phone.replace(/\s/g, "")}`} className="btn bg-white text-brand-900 hover:bg-brand-50"><Phone className="size-4" />{t("Call", "कॉल करा")}</a>
            <a href={`https://wa.me/${academy.whatsapp}`} target="_blank" rel="noopener noreferrer" className="btn bg-[#25D366] text-white hover:bg-[#1ebe5a]"><WhatsAppIcon className="size-4" />WhatsApp</a>
          </div>
        </div>
      </section>
      </>
    ),
  };

  return (
    <>
      {/* Hero */}
      <section className="relative overflow-hidden bg-gradient-to-br from-brand-950 via-brand-900 to-brand-700 text-white">
        <div className="absolute -right-24 -top-24 size-96 rounded-full bg-saffron-500/20 blur-3xl" aria-hidden />
        <div className="container-page relative grid gap-6 py-8 md:grid-cols-[1.2fr_1fr] md:gap-8 md:py-16 lg:gap-10 lg:py-20">
          <div>
            <div className="mb-5 flex items-center gap-3">
              <img src={academy.logoUrl} alt="" width={64} height={64} className="size-16 rounded-full bg-white shadow-lg ring-2 ring-saffron-400/60" />
              <div>
                <p className="text-sm text-brand-200">{t("Nikhil Rudrawar's Vidyadhan Academy", "निखिल रुद्रवार यांची विद्याधन अकॅडमी")}</p>
                <p className="font-semibold text-saffron-200">“{l(academy.tagline)}”</p>
              </div>
            </div>
            <Badge tone="orange" className="mb-4">{academy.focus}</Badge>
            <h1 className="text-3xl font-bold leading-tight !text-white sm:text-5xl">{l(cms.heroTitle)}</h1>
            <p className="mt-3 sm:mt-4 max-w-xl text-base text-brand-100 sm:text-lg">{l(cms.heroSubtitle)}</p>
            <div className="mt-7 flex flex-wrap gap-3">
              <Link href="/admission" className="btn-accent px-5 py-3 text-base">{t("Apply for admission", "प्रवेशासाठी अर्ज करा")}<ArrowRight className="size-4" /></Link>
              <Link href="/courses" className="btn px-5 py-3 text-base bg-white/10 text-white hover:bg-white/20">{t("Explore courses", "अभ्यासक्रम पहा")}</Link>
            </div>
            <dl className="mt-10 hidden max-w-lg grid-cols-3 gap-4 text-center sm:grid">
              {[[String(courses.length), t("Courses", "अभ्यासक्रम")], [String(db.batches.length), t("Active batches", "सक्रिय बॅचेस")], ["2", t("Mediums", "माध्यमे")]].map(([v, k]) => (
                <div key={k} className="rounded-xl bg-white/5 p-3 ring-1 ring-white/10"><dt className="text-xs text-brand-200">{k}</dt><dd className="text-2xl font-bold">{v}</dd></div>
              ))}
            </dl>
          </div>
          <div className="w-full justify-self-end rounded-2xl bg-white p-4 text-slate-800 shadow-2xl sm:p-6 md:max-w-md">
            <h2 className="text-lg font-semibold">{t("Quick enquiry", "त्वरित चौकशी")}</h2>
            <p className="mb-3 hidden text-sm text-slate-500 sm:block">{t("Leave your number — we'll call you back.", "आपला क्रमांक द्या — आम्ही परत कॉल करू.")}</p>
            <EnquiryForm variant="quick" />
          </div>
        </div>
      </section>

      {sections.filter((s) => s.enabled).map((s) => <Fragment key={s.key}>{blocks[s.key]}</Fragment>)}
    </>
  );
}

function ResultsCarousel({ items }: { items: ReactNode[] }) {
  const ref = useRef<HTMLDivElement>(null);
  const holdUntil = useRef(0);
  useEffect(() => {
    const el = ref.current;
    if (!el || items.length < 2) return;
    const id = setInterval(() => {
      if (Date.now() < holdUntil.current || document.hidden) return;
      const before = el.scrollLeft;
      const atEnd = before + el.clientWidth >= el.scrollWidth - 4;
      const next = ([...el.children] as HTMLElement[]).find((c) => c.offsetLeft > before + 24);
      const target = atEnd || !next ? 0 : next.offsetLeft;
      el.scrollTo({ left: target, behavior: "smooth" });
      // some mobile browsers ignore smooth programmatic scrolls inside snap containers
      setTimeout(() => { if (Math.abs(el.scrollLeft - before) < 2) el.scrollLeft = target; }, 900);
    }, 3000);
    return () => clearInterval(id);
  }, [items.length]);
  const hold = (ms: number) => () => { holdUntil.current = Date.now() + ms; };
  return (
    <div
      ref={ref}
      onTouchStart={hold(6000)}
      onTouchEnd={hold(3000)}
      onPointerDown={hold(6000)}
      onWheel={hold(4000)}
      className="relative -mx-4 flex snap-x snap-mandatory gap-4 overflow-x-auto px-4 pb-4 [scrollbar-width:none] [&::-webkit-scrollbar]:hidden"
    >
      {items.map((item, i) => (
        <div key={i} className="w-[85%] shrink-0 snap-start sm:w-[320px]">{item}</div>
      ))}
    </div>
  );
}
