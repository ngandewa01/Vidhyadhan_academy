"use client";
import Link from "next/link";
import { useMemo, useState } from "react";
import { ArrowRight, BookOpen, CalendarDays, ExternalLink, GraduationCap, ImageIcon, Library, Mail, MapPin, Megaphone, Phone, Clock, Quote, Users, Armchair, Check, Bell } from "lucide-react";
import { useI18n } from "@/lib/i18n";
import { useStore } from "@/lib/store";
import { CourseCard, DemoNote, EnquiryForm, PageHero, SectionHead } from "@/components/public";
import { Badge, Card, EmptyState, Notice, StatusBadge, Tabs } from "@/components/ui";
import { WhatsAppIcon } from "@/components/brand";
import { academy, isPlaceholder } from "@/config/academy";
import { fmtINR } from "@/lib/utils";
import type { ImportantDateKind } from "@/lib/types";

export function AboutView() {
  const { t, l } = useI18n();
  const cms = useStore((s) => s.db.settings.cms);
  return (
    <>
      <PageHero title={t("About the Academy", "अकॅडमीविषयी")} subtitle={l(academy.tagline)} />
      <section className="container-page grid gap-8 py-12 lg:grid-cols-[2fr_1fr]">
        <div className="prose max-w-none space-y-4 text-slate-700">
          <p className="text-lg">{l(cms.aboutBody)}</p>
          <h2 className="text-xl font-semibold">{t("Our approach", "आमची पद्धत")}</h2>
          <ul className="grid gap-2 sm:grid-cols-2">
            {cms.whyUs.map((w, i) => <li key={i} className="flex gap-2"><Check className="mt-1 size-4 shrink-0 text-emerald-600" />{l(w)}</li>)}
          </ul>
          <Notice tone="warn">{t("Founding year, affiliations, director's message and photographs will be added once supplied by the academy. No affiliations are claimed in this prototype.", "स्थापना वर्ष, संलग्नता, संचालकांचा संदेश व छायाचित्रे अकॅडमीकडून मिळाल्यानंतर जोडली जातील. या प्रोटोटाइपमध्ये कोणत्याही संलग्नतेचा दावा केलेला नाही.")}</Notice>
        </div>
        <Card title={t("At a glance", "थोडक्यात")}>
          <dl className="space-y-3 text-sm">
            <div><dt className="text-slate-500">{t("Location", "स्थान")}</dt><dd className="font-medium">{l(academy.address)}</dd></div>
            <div><dt className="text-slate-500">{t("Mode", "पद्धत")}</dt><dd className="font-medium">{t("Classroom, online and hybrid", "वर्ग, ऑनलाइन व हायब्रिड")}</dd></div>
            <div><dt className="text-slate-500">{t("Medium", "माध्यम")}</dt><dd className="font-medium">{t("Marathi and English", "मराठी व इंग्रजी")}</dd></div>
            <div><dt className="text-slate-500">{t("Facilities", "सुविधा")}</dt><dd className="font-medium">{t("Multiple classrooms, self-study library", "अनेक वर्गखोल्या, अभ्यासिका")}</dd></div>
          </dl>
        </Card>
      </section>
    </>
  );
}

export function CoursesView() {
  const { t } = useI18n();
  const courses = useStore((s) => s.db.courses).filter((c) => c.published);
  const [cat, setCat] = useState("all");
  const cats = [
    { id: "all", label: t("All", "सर्व") }, { id: "state", label: t("State services", "राज्यसेवा") }, { id: "combined", label: t("Combined", "संयुक्त") },
    { id: "police", label: t("Police", "पोलीस") }, { id: "revenue", label: t("Revenue", "महसूल") }, { id: "other", label: t("Other", "इतर") },
  ];
  const list = courses.filter((c) => cat === "all" || c.category === cat);
  return (
    <>
      <PageHero title={t("Courses", "अभ्यासक्रम")} subtitle={t("Coaching programmes for Maharashtra and other competitive examinations.", "महाराष्ट्र व इतर स्पर्धा परीक्षांसाठी मार्गदर्शन कार्यक्रम.")} />
      <section className="container-page py-10">
        <Tabs tabs={cats.map((c) => ({ ...c, count: courses.filter((x) => c.id === "all" || x.category === c.id).length }))} value={cat} onChange={setCat} />
        {list.length ? <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">{list.map((c) => <CourseCard key={c.id} c={c} />)}</div> : <EmptyState title={t("No courses in this category yet", "या प्रकारात अद्याप अभ्यासक्रम नाहीत")} />}
        <DemoNote />
      </section>
    </>
  );
}

export function CourseDetailView({ slug }: { slug: string }) {
  const { t, l, d } = useI18n();
  const db = useStore((s) => s.db);
  const c = db.courses.find((x) => x.slug === slug);
  const [tab, setTab] = useState<"overview" | "syllabus" | "batches" | "fees">("overview");
  if (!c) return <div className="container-page py-20"><EmptyState title={t("Course not found", "अभ्यासक्रम सापडला नाही")} action={<Link className="btn-primary" href="/courses">{t("All courses", "सर्व अभ्यासक्रम")}</Link>} /></div>;
  const subjects = db.subjects.filter((s) => s.courseId === c.id);
  const batches = db.batches.filter((b) => b.courseId === c.id);
  const tests = db.tests.filter((x) => x.courseId === c.id && x.status === "published");
  return (
    <>
      <section className={`bg-gradient-to-br ${c.color} text-white`}>
        <div className="container-page py-12">
          <Link href="/courses" className="text-sm text-white/80 hover:text-white">← {t("All courses", "सर्व अभ्यासक्रम")}</Link>
          <h1 className="mt-2 text-3xl font-bold !text-white sm:text-4xl">{l(c.name)}</h1>
          <p className="mt-3 max-w-2xl text-white/90">{l(c.description)}</p>
          <div className="mt-5 flex flex-wrap gap-2 text-sm">
            <span className="rounded-full bg-white/15 px-3 py-1">{c.durationMonths} {t("months", "महिने")}</span>
            <span className="rounded-full bg-white/15 px-3 py-1">{c.mediums.map((m) => (m === "mr" ? t("Marathi", "मराठी") : t("English", "इंग्रजी"))).join(" + ")} {t("medium", "माध्यम")}</span>
            <span className="rounded-full bg-white/15 px-3 py-1">{subjects.length} {t("subjects", "विषय")}</span>
            <span className="rounded-full bg-white/15 px-3 py-1">{tests.length} {t("tests", "चाचण्या")}</span>
          </div>
          <div className="mt-6 flex flex-wrap gap-3">
            {c.admissionOpen ? <Link href={`/admission?course=${c.id}`} className="btn bg-white text-slate-900 hover:bg-slate-100">{t("Apply now", "प्रवेश घ्या")}<ArrowRight className="size-4" /></Link> : <span className="btn bg-white/20 text-white">{t("Admissions closed", "प्रवेश बंद")}</span>}
            <Link href={`/enquiry?course=${c.id}`} className="btn bg-white/15 text-white hover:bg-white/25">{t("Ask a question", "प्रश्न विचारा")}</Link>
          </div>
        </div>
      </section>
      <section className="container-page py-10">
        <Tabs tabs={[{ id: "overview", label: t("Overview", "आढावा") }, { id: "syllabus", label: t("Syllabus", "अभ्यासक्रम") }, { id: "batches", label: t("Batches", "बॅचेस"), count: batches.length }, { id: "fees", label: t("Fees", "शुल्क") }]} value={tab} onChange={setTab} />
        {tab === "overview" && (
          <div className="grid gap-6 lg:grid-cols-3">
            <div className="lg:col-span-2 space-y-4">
              <p className="text-slate-700">{l(c.description)}</p>
              <h2 className="text-lg font-semibold">{t("Subjects covered", "समाविष्ट विषय")}</h2>
              <div className="flex flex-wrap gap-2">{subjects.map((s) => <Badge key={s.id} tone="blue">{l(s.name)}</Badge>)}</div>
              <h2 className="text-lg font-semibold">{t("What you get", "आपल्याला काय मिळेल")}</h2>
              <ul className="grid gap-2 sm:grid-cols-2 text-sm">
                {[t("Live and recorded lectures", "लाईव्ह व रेकॉर्डेड व्याख्याने"), t("Bilingual notes and PDFs", "द्विभाषिक नोट्स व PDF"), t("Chapter tests & full mocks", "प्रकरण चाचण्या व पूर्ण सराव परीक्षा"), t("Rank & performance analysis", "क्रमांक व कामगिरी विश्लेषण"), t("Assignments with feedback", "अभिप्रायासह गृहपाठ"), t("Attendance tracking", "उपस्थिती नोंद")].map((x) => <li key={x} className="flex gap-2"><Check className="mt-0.5 size-4 text-emerald-600" />{x}</li>)}
              </ul>
            </div>
            <Card title={t("Documents", "कागदपत्रे")}>
              <p className="text-sm text-slate-600">{c.documentsRequired.length ? c.documentsRequired.join(", ") : t("No documents required at admission. Only name and mobile number are mandatory.", "प्रवेशावेळी कोणतीही कागदपत्रे आवश्यक नाहीत. फक्त नाव व मोबाईल क्रमांक अनिवार्य.")}</p>
            </Card>
          </div>
        )}
        {tab === "syllabus" && <SyllabusTree courseId={c.id} />}
        {tab === "batches" && <BatchList courseId={c.id} />}
        {tab === "fees" && (
          <div className="max-w-xl space-y-3">
            <Card>
              <div className="flex items-baseline justify-between"><span className="text-slate-600">{t("Course fee", "अभ्यासक्रम शुल्क")}</span><span className="text-2xl font-bold">{fmtINR(c.fee)}</span></div>
              <p className="mt-2 text-sm text-slate-500">{t("Batch-specific fees, instalments, scholarships and offers apply. Final fee is shown during admission.", "बॅचनिहाय शुल्क, हप्ते, शिष्यवृत्ती व सवलती लागू. अंतिम शुल्क प्रवेशावेळी दिसेल.")}</p>
              {batches.map((b) => <div key={b.id} className="mt-2 flex justify-between border-t pt-2 text-sm"><span>{b.name}</span><span className="font-semibold">{fmtINR(b.fee)}</span></div>)}
            </Card>
            <DemoNote>{t("Demo fee values – replace with actual fees.", "डेमो शुल्क – प्रत्यक्ष शुल्काने बदला.")}</DemoNote>
          </div>
        )}
        <p className="mt-6 text-xs text-slate-400">{t("Next session starts", "पुढील सत्र")} {d(batches[0]?.startDate)}</p>
      </section>
    </>
  );
}

export function SyllabusTree({ courseId }: { courseId: string }) {
  const { t, lc, contentLang } = useI18n();
  const [lang, setLang] = useState(contentLang);
  const subjects = useStore((s) => s.db.subjects).filter((x) => x.courseId === courseId);
  if (!subjects.length) return <EmptyState title={t("Syllabus will be published soon", "अभ्यासक्रम लवकरच प्रकाशित होईल")} />;
  return (
    <div>
      <div className="mb-3 flex items-center gap-2 text-sm"><span className="text-slate-500">{t("Content language", "मजकुराची भाषा")}:</span>
        {(["mr", "en"] as const).map((x) => <button key={x} onClick={() => setLang(x)} className={x === lang ? "btn-primary btn-sm" : "btn-outline btn-sm"}>{x === "mr" ? "मराठी" : "English"}</button>)}
      </div>
      <div className="grid gap-4 md:grid-cols-2">
        {subjects.map((s) => (
          <Card key={s.id} title={<span className="flex items-center gap-2"><BookOpen className="size-4 text-brand-600" />{lc(s.name, lang)}</span>}>
            <ol className="space-y-2 text-sm">
              {s.chapters.map((c, i) => (
                <li key={c.id}>
                  <p className="font-medium">{i + 1}. {lc(c.name, lang)}</p>
                  <ul className="ml-5 list-disc text-slate-600">{c.topics.map((tp, j) => <li key={j}>{lc(tp, lang)}</li>)}</ul>
                </li>
              ))}
            </ol>
          </Card>
        ))}
      </div>
    </div>
  );
}

export function SyllabusView() {
  const { t, l } = useI18n();
  const courses = useStore((s) => s.db.courses).filter((c) => c.published);
  const [cid, setCid] = useState(courses[0]?.id ?? "");
  return (
    <>
      <PageHero title={t("Syllabus", "अभ्यासक्रम आराखडा")} subtitle={t("Subject → Chapter → Topic structure for each course. Always cross-check with the official notification.", "प्रत्येक अभ्यासक्रमाची विषय → प्रकरण → उपविषय रचना. अधिकृत जाहिरातीशी नेहमी पडताळा.")} />
      <section className="container-page py-10">
        <div className="mb-5 flex flex-wrap gap-2">{courses.map((c) => <button key={c.id} onClick={() => setCid(c.id)} className={c.id === cid ? "btn-primary btn-sm" : "btn-outline btn-sm"}>{l(c.name)}</button>)}</div>
        <SyllabusTree courseId={cid} />
      </section>
    </>
  );
}

function BatchList({ courseId }: { courseId?: string }) {
  const { t, l, d } = useI18n();
  const db = useStore((s) => s.db);
  const [mode, setMode] = useState("all");
  const list = db.batches.filter((b) => (!courseId || b.courseId === courseId) && (mode === "all" || b.mode === mode));
  const enrolled = (bid: string) => db.enrolments.filter((e) => e.batchId === bid && e.enrolmentStatus === "active").length;
  return (
    <div>
      {!courseId && (
        <div className="mb-4 flex flex-wrap gap-2">
          {["all", "offline", "online", "hybrid"].map((m) => <button key={m} onClick={() => setMode(m)} className={m === mode ? "btn-primary btn-sm" : "btn-outline btn-sm"}>{m === "all" ? t("All modes", "सर्व पद्धती") : <StatusBadge s={m} />}</button>)}
        </div>
      )}
      <div className="grid gap-4 md:grid-cols-2">
        {list.map((b) => {
          const c = db.courses.find((x) => x.id === b.courseId)!;
          const room = db.classrooms.find((r) => r.id === b.classroomId);
          const seats = Math.max(0, b.capacity - enrolled(b.id));
          return (
            <div key={b.id} className="card p-5">
              <div className="flex items-start justify-between gap-2">
                <div><p className="text-xs font-semibold text-brand-700">{l(c.name)}</p><h3 className="font-semibold">{b.name}</h3></div>
                <StatusBadge s={b.mode} />
              </div>
              <dl className="mt-3 grid grid-cols-2 gap-2 text-sm">
                <div><dt className="text-slate-500">{t("Start", "सुरुवात")}</dt><dd>{d(b.startDate)}</dd></div>
                <div><dt className="text-slate-500">{t("End", "समाप्ती")}</dt><dd>{d(b.endDate)}</dd></div>
                <div><dt className="text-slate-500">{t("Days", "दिवस")}</dt><dd>{b.days}</dd></div>
                <div><dt className="text-slate-500">{t("Time", "वेळ")}</dt><dd>{b.time}</dd></div>
                <div><dt className="text-slate-500">{t("Venue", "ठिकाण")}</dt><dd>{room?.name ?? t("Online", "ऑनलाइन")}</dd></div>
                <div><dt className="text-slate-500">{t("Seats left", "उपलब्ध जागा")}</dt><dd className={seats < 10 ? "font-semibold text-saffron-700" : ""}>{seats} / {b.capacity}</dd></div>
              </dl>
              <div className="mt-4 flex items-center justify-between">
                <span className="font-semibold">{fmtINR(b.fee)} <span className="text-[10px] font-normal text-saffron-700">{t("demo", "डेमो")}</span></span>
                {b.admissionOpen ? <Link href={`/admission?batch=${b.id}`} className="btn-primary btn-sm">{t("Enrol", "प्रवेश")} · {t("till", "पर्यंत")} {d(b.admissionCloses, { day: "numeric", month: "short" })}</Link> : <Badge>{t("Closed", "बंद")}</Badge>}
              </div>
            </div>
          );
        })}
      </div>
      {!list.length && <EmptyState title={t("No batches found", "बॅचेस आढळल्या नाहीत")} />}
    </div>
  );
}

export function BatchesView() {
  const { t } = useI18n();
  return (
    <>
      <PageHero title={t("Batches & class schedules", "बॅचेस व वर्ग वेळापत्रक")} subtitle={t("Offline, online and hybrid batches with timings and seat availability.", "वेळा व जागा उपलब्धतेसह ऑफलाइन, ऑनलाइन व हायब्रिड बॅचेस.")} />
      <section className="container-page py-10"><BatchList /><DemoNote /></section>
    </>
  );
}

export function FeesView() {
  const { t, l } = useI18n();
  const db = useStore((s) => s.db);
  return (
    <>
      <PageHero title={t("Fees & plans", "शुल्क व योजना")} subtitle={t("Transparent fee structure with instalments, scholarships and offers.", "हप्ते, शिष्यवृत्ती व सवलतींसह पारदर्शक शुल्क रचना.")} />
      <section className="container-page space-y-8 py-10">
        <Notice tone="warn">{t("All amounts on this page are DEMO values and not the academy's actual fees.", "या पानावरील सर्व रकमा डेमो असून अकॅडमीचे प्रत्यक्ष शुल्क नाही.")}</Notice>
        <div className="card overflow-x-auto">
          <table className="table-base">
            <thead><tr><th>{t("Course", "अभ्यासक्रम")}</th><th>{t("Batch", "बॅच")}</th><th>{t("Mode", "पद्धत")}</th><th>{t("Duration", "कालावधी")}</th><th className="text-right">{t("Fee", "शुल्क")}</th></tr></thead>
            <tbody>
              {db.batches.map((b) => {
                const c = db.courses.find((x) => x.id === b.courseId)!;
                return <tr key={b.id}><td className="font-medium">{l(c.name)}</td><td>{b.name}</td><td><StatusBadge s={b.mode} /></td><td>{c.durationMonths} {t("months", "महिने")}</td><td className="text-right font-semibold">{fmtINR(b.fee)}</td></tr>;
              })}
            </tbody>
          </table>
        </div>
        <div className="grid gap-4 md:grid-cols-3">
          <Card title={t("Instalments", "हप्ते")}><p className="text-sm text-slate-600">{t("Pay in 2–3 instalments. The schedule is shown before payment and reminders are sent before each due date.", "२–३ हप्त्यांत भरा. भरण्यापूर्वी वेळापत्रक दिसेल व प्रत्येक देय तारखेपूर्वी स्मरणपत्र मिळेल.")}</p></Card>
          <Card title={t("Scholarships & concessions", "शिष्यवृत्ती व सवलती")}><p className="text-sm text-slate-600">{t("Merit and need-based concessions are approved by the academy office.", "गुणवत्ता व गरजेनुसार सवलती अकॅडमी कार्यालयाकडून मंजूर केल्या जातात.")}</p></Card>
          <Card title={t("Offers", "ऑफर्स")}>
            <ul className="space-y-1 text-sm">{db.promos.filter((p) => p.active && p.kind !== "scholarship").map((p) => <li key={p.id} className="flex justify-between"><code className="font-semibold text-brand-800">{p.code}</code><span>{p.type === "flat" ? fmtINR(p.value) : `${p.value}%`} {t("off", "सूट")}</span></li>)}</ul>
          </Card>
        </div>
        <div>
          <h2 className="mb-3 text-xl font-semibold">{t("Library plans", "ग्रंथालय योजना")}</h2>
          <LibraryPlans />
        </div>
        <p className="text-sm text-slate-500">{t("Payment methods: Cash, UPI/QR, online gateway and bank transfer. A detailed receipt is issued for every payment. GST, if applicable, is configured by the academy.", "पेमेंट पद्धती: रोख, UPI/QR, ऑनलाइन गेटवे व बँक ट्रान्सफर. प्रत्येक पेमेंटची सविस्तर पावती दिली जाते. GST लागू असल्यास अकॅडमी निश्चित करेल.")}</p>
      </section>
    </>
  );
}

function LibraryPlans() {
  const { t, l } = useI18n();
  const plans = useStore((s) => s.db.libraryPlans).filter((p) => p.active);
  return (
    <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-5">
      {plans.map((p) => (
        <div key={p.id} className={`card flex flex-col p-5 ${p.kind === "bundle" ? "ring-2 ring-saffron-400" : ""}`}>
          {p.kind === "bundle" && <Badge tone="orange" className="mb-2 self-start">{t("Bundle – save more", "संच – अधिक बचत")}</Badge>}
          <h3 className="font-semibold">{l(p.name)}</h3>
          <p className="mt-2 text-2xl font-bold">{fmtINR(p.fee)}</p>
          <p className="text-xs text-slate-500">{p.months} {t("month(s)", "महिने")} · <span className="text-saffron-700">{t("demo price", "डेमो किंमत")}</span></p>
          <Link href={`/admission?library=${p.id}`} className="btn-outline btn-sm mt-4">{t("Choose plan", "योजना निवडा")}</Link>
        </div>
      ))}
    </div>
  );
}

export function LibraryView() {
  const { t, l } = useI18n();
  const db = useStore((s) => s.db);
  const capacity = 80;
  const active = db.memberships.filter((m) => m.status === "active").length;
  return (
    <>
      <PageHero title={t("Library", "ग्रंथालय / अभ्यासिका")} subtitle={l(db.settings.cms.libraryBlurb)}>
        <div className="mt-6 flex flex-wrap gap-3">
          <Link href="/admission?library=lp_m" className="btn-accent">{t("Get membership", "सदस्यत्व घ्या")}</Link>
          <Link href="/contact" className="btn-outline">{t("Visit the library", "ग्रंथालयाला भेट द्या")}</Link>
        </div>
      </PageHero>
      <section className="container-page space-y-10 py-10">
        <div className="grid gap-4 sm:grid-cols-3">
          {[
            [Armchair, t("Seat allocation", "आसन वाटप"), t("Fixed or flexible seats as per plan", "योजनेनुसार निश्चित किंवा लवचिक आसन")],
            [Clock, t("Long hours", "दीर्घ वेळ"), l(academy.hours)],
            [Users, t("Available seats", "उपलब्ध आसने"), `${capacity - active} / ${capacity} ${t("(demo)", "(डेमो)")}`],
          ].map(([I, a, b], i) => {
            const Icon = I as typeof Clock;
            return <div key={i} className="card flex gap-3 p-4"><Icon className="size-6 text-saffron-600" /><div><p className="font-semibold">{a as string}</p><p className="text-sm text-slate-500">{b as string}</p></div></div>;
          })}
        </div>
        <div>
          <SectionHead title={t("Membership plans", "सदस्यत्व योजना")} subtitle={t("Library-only membership — no coaching enrolment required — or a discounted course + library bundle.", "फक्त ग्रंथालय सदस्यत्व (कोचिंग प्रवेश आवश्यक नाही) किंवा सवलतीचा अभ्यासक्रम + ग्रंथालय संच.")} />
          <LibraryPlans />
        </div>
        <Card title={t("Library rules (sample)", "ग्रंथालय नियम (नमुना)")}>
          <ul className="list-disc space-y-1 pl-5 text-sm text-slate-600">
            <li>{t("Check in at the desk or by QR on arrival.", "आगमनावेळी डेस्कवर किंवा QR द्वारे नोंद करा.")}</li>
            <li>{t("Maintain silence; phones on silent.", "शांतता राखा; फोन सायलेंट ठेवा.")}</li>
            <li>{t("Renew before expiry to retain your seat.", "आसन टिकवण्यासाठी मुदत संपण्यापूर्वी नूतनीकरण करा.")}</li>
          </ul>
        </Card>
      </section>
    </>
  );
}

export function FacultyView() {
  const { t, l } = useI18n();
  const list = useStore((s) => s.db.facultyProfiles).filter((f) => f.published);
  return (
    <>
      <PageHero title={t("Faculty", "प्राध्यापक")} subtitle={t("Experienced subject teachers for every paper.", "प्रत्येक पेपरसाठी अनुभवी विषय शिक्षक.")} />
      <section className="container-page py-10">
        <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
          {list.map((f) => (
            <div key={f.id} className="card p-6 text-center">
              <div className="mx-auto mb-3 flex size-20 items-center justify-center rounded-full bg-brand-50 text-brand-400"><GraduationCap className="size-8" /></div>
              <h3 className="font-semibold">{f.name}</h3>
              <p className="text-sm text-brand-700">{l(f.subject)}</p>
              <p className="mt-2 text-sm text-slate-500">{l(f.bio)}</p>
            </div>
          ))}
        </div>
        <DemoNote>{t("Faculty names and photos are placeholders until provided by the academy.", "अकॅडमीकडून माहिती मिळेपर्यंत प्राध्यापकांची नावे व फोटो नमुना आहेत.")}</DemoNote>
      </section>
    </>
  );
}

export function ResultsView() {
  const { t, l } = useI18n();
  const db = useStore((s) => s.db);
  const list = db.achievements.filter((a) => a.published && a.consent);
  const years = [...new Set(list.map((a) => a.year))].sort((a, b) => b - a);
  const [year, setYear] = useState<number | "all">("all");
  return (
    <>
      <PageHero title={t("Results & achievements", "निकाल व यश")} subtitle={t("Year-wise and course-wise achievements, published only with student consent.", "वर्षनिहाय व अभ्यासक्रमनिहाय यश — केवळ विद्यार्थ्यांच्या संमतीने प्रकाशित.")} />
      <section className="container-page py-10">
        <div className="mb-5 flex flex-wrap gap-2">
          <button onClick={() => setYear("all")} className={year === "all" ? "btn-primary btn-sm" : "btn-outline btn-sm"}>{t("All years", "सर्व वर्षे")}</button>
          {years.map((y) => <button key={y} onClick={() => setYear(y)} className={year === y ? "btn-primary btn-sm" : "btn-outline btn-sm"}>{y}</button>)}
        </div>
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {list.filter((a) => year === "all" || a.year === year).map((a) => (
            <div key={a.id} className="card p-5 text-center">
              <div className="mx-auto mb-3 flex size-16 items-center justify-center rounded-full bg-slate-100 text-xs text-slate-400">{t("Photo", "फोटो")}</div>
              <p className="font-semibold">{a.name}</p>
              <p className="text-sm text-slate-500">{a.exam} · {a.year}</p>
              <p className="text-xs text-slate-500">{l(db.courses.find((c) => c.id === a.courseId)?.name)}</p>
              <Badge tone="green" className="mt-2">{a.result}</Badge>
              {a.demo && <Badge tone="orange" className="ml-1 mt-2">{t("Demo", "डेमो")}</Badge>}
            </div>
          ))}
        </div>
        <DemoNote>{t("No real results are shown. The academy will publish genuine results after obtaining consent.", "कोणतेही खरे निकाल दाखवलेले नाहीत. संमती मिळाल्यानंतर अकॅडमी खरे निकाल प्रकाशित करेल.")}</DemoNote>
      </section>
    </>
  );
}

export function TestimonialsView() {
  const { t, l } = useI18n();
  const list = useStore((s) => s.db.testimonials).filter((x) => x.published && x.consent);
  return (
    <>
      <PageHero title={t("Student testimonials", "विद्यार्थ्यांचे अभिप्राय")} />
      <section className="container-page grid gap-5 py-10 md:grid-cols-2">
        {list.map((x) => (
          <figure key={x.id} className="card p-6">
            <Quote className="size-6 text-saffron-500" />
            <blockquote className="mt-2 text-slate-700">{l(x.text)}</blockquote>
            <figcaption className="mt-3 text-sm font-semibold">{x.name} <span className="font-normal text-slate-500">· {x.course}</span> {x.demo && <Badge tone="orange">{t("Demo", "डेमो")}</Badge>}</figcaption>
          </figure>
        ))}
      </section>
    </>
  );
}

export function GalleryView() {
  const { t } = useI18n();
  const items = [t("Classroom", "वर्गखोली"), t("Library", "ग्रंथालय"), t("Seminar", "चर्चासत्र"), t("Felicitation", "सत्कार"), t("Mock test", "सराव परीक्षा"), t("Campus", "परिसर")];
  return (
    <>
      <PageHero title={t("Gallery", "छायाचित्रे")} subtitle={t("Photos will be added by the academy through the Website CMS.", "अकॅडमी वेबसाइट CMS मधून छायाचित्रे जोडेल.")} />
      <section className="container-page grid grid-cols-2 gap-4 py-10 md:grid-cols-3">
        {items.map((x) => (
          <div key={x} className="flex aspect-[4/3] flex-col items-center justify-center rounded-xl border border-dashed border-slate-300 bg-white text-slate-400">
            <ImageIcon className="size-8" /><span className="mt-2 text-sm">{x}</span><span className="text-[10px]">{t("placeholder", "नमुना")}</span>
          </div>
        ))}
      </section>
    </>
  );
}

export function AnnouncementsView() {
  const { t, l, d } = useI18n();
  const list = useStore((s) => s.db.announcements).filter((a) => a.published).sort((a, b) => Number(b.pinned) - Number(a.pinned) || b.date.localeCompare(a.date));
  return (
    <>
      <PageHero title={t("Announcements", "सूचना")} />
      <section className="container-page max-w-3xl space-y-4 py-10">
        {list.map((a) => (
          <article key={a.id} className="card p-5">
            <div className="flex items-center gap-2 text-xs text-slate-500"><Megaphone className="size-4 text-saffron-600" />{d(a.date)}{a.pinned && <Badge tone="orange">{t("Pinned", "महत्त्वाचे")}</Badge>}</div>
            <h2 className="mt-1 text-lg font-semibold">{l(a.title)}</h2>
            <p className="mt-1 text-slate-600">{l(a.body)}</p>
          </article>
        ))}
      </section>
    </>
  );
}

export const DATE_LABEL: Record<ImportantDateKind, [string, string]> = {
  appStart: ["Application starts", "अर्ज सुरू"], appEnd: ["Last date to apply", "अर्जाची शेवटची तारीख"], correction: ["Correction window", "दुरुस्ती कालावधी"],
  admitCard: ["Admit card", "प्रवेशपत्र"], exam: ["Exam date", "परीक्षा तारीख"], answerKey: ["Answer key", "उत्तरतालिका"], result: ["Result", "निकाल"],
};

export function ExamUpdatesView() {
  const { t, l, d, lang } = useI18n();
  const db = useStore((s) => s.db);
  const [auth, setAuth] = useState("all");
  const list = db.updates.filter((u) => u.status === "published" && (auth === "all" || u.authorityId === auth)).sort((a, b) => Number(b.urgent) - Number(a.urgent) || b.createdAt.localeCompare(a.createdAt));
  return (
    <>
      <PageHero title={t("Official exam updates", "अधिकृत परीक्षा अपडेट्स")} subtitle={t("Curated updates for MPSC, Police Bharti, UPSC, SSC and banking exams — reviewed by our team. Always confirm on the official website.", "एमपीएससी, पोलीस भरती, यूपीएससी, एसएससी व बँकिंग परीक्षांसाठी आमच्या टीमने तपासलेल्या सूचना. अधिकृत संकेतस्थळावर नेहमी खात्री करा.")}>
        <a href="https://x.com/mpsc_office" target="_blank" rel="noopener noreferrer" className="btn-outline mt-5">{t("Follow MPSC on X", "MPSC ला X वर फॉलो करा")} @mpsc_office <ExternalLink className="size-4" /></a>
      </PageHero>
      <section className="container-page grid gap-8 py-10 lg:grid-cols-[1fr_300px]">
        <div>
          <div className="mb-4 flex flex-wrap gap-2">
            <button onClick={() => setAuth("all")} className={auth === "all" ? "btn-primary btn-sm" : "btn-outline btn-sm"}>{t("All", "सर्व")}</button>
            {db.authorities.map((a) => <button key={a.id} onClick={() => setAuth(a.id)} className={auth === a.id ? "btn-primary btn-sm" : "btn-outline btn-sm"}>{l(a.name)}</button>)}
          </div>
          <div className="space-y-3">
            {list.map((u) => {
              const a = db.authorities.find((x) => x.id === u.authorityId);
              return (
                <article key={u.id} className={`card p-5 ${u.urgent ? "border-red-300" : ""}`}>
                  <div className="flex flex-wrap items-center gap-2 text-xs"><Badge tone="blue">{l(a?.name)}</Badge>{u.urgent && <Badge tone="red">{t("Urgent", "तातडीचे")}</Badge>}<span className="text-slate-500">{d(u.createdAt)}</span></div>
                  <h2 className="mt-2 font-semibold">{l(u.title)}</h2>
                  <p className="mt-1 text-sm text-slate-600">{l(u.summary)}</p>
                  {u.dates.length > 0 && <div className="mt-3 flex flex-wrap gap-2">{u.dates.map((x, i) => <span key={i} className="rounded-md bg-slate-100 px-2 py-1 text-xs"><b>{DATE_LABEL[x.kind][lang === "mr" ? 1 : 0]}:</b> {d(x.date)}</span>)}</div>}
                  {u.sourceUrl && <a href={u.sourceUrl} target="_blank" rel="noopener noreferrer" className="mt-3 inline-flex items-center gap-1 text-sm font-medium text-brand-700 hover:underline">{t("Official source", "अधिकृत स्रोत")}<ExternalLink className="size-3.5" /></a>}
                </article>
              );
            })}
            {!list.length && <EmptyState title={t("No updates yet", "अद्याप अपडेट्स नाहीत")} />}
          </div>
        </div>
        <aside className="space-y-4">
          <Card title={<span className="flex items-center gap-2"><Bell className="size-4" />{t("Get personalised alerts", "वैयक्तिक सूचना मिळवा")}</span>}>
            <p className="text-sm text-slate-600">{t("Students can follow exams and authorities, bookmark updates and receive reminders 7, 3 and 1 day before deadlines.", "विद्यार्थी परीक्षा व संस्था फॉलो करू शकतात, अपडेट्स बुकमार्क करू शकतात आणि शेवटच्या तारखेपूर्वी ७, ३ व १ दिवस आधी स्मरणपत्रे मिळवू शकतात.")}</p>
            <Link href="/login" className="btn-primary btn-sm mt-3 w-full">{t("Login to follow", "फॉलो करण्यासाठी लॉगिन")}</Link>
          </Card>
          <Notice>{t("Live API integrations (e.g. X) are not active in this prototype. Updates are curated by the admin from official sources.", "या प्रोटोटाइपमध्ये थेट API जोडणी (उदा. X) सक्रिय नाही. अधिकृत स्रोतांतून प्रशासक अपडेट्स निवडतात.")}</Notice>
        </aside>
      </section>
    </>
  );
}

export function ResourcesView() {
  const { t, l } = useI18n();
  const auths = useStore((s) => s.db.authorities);
  const groups = useMemo(() => [...new Set(auths.map((a) => a.group))], [auths]);
  return (
    <>
      <PageHero title={t("Important examination links", "महत्त्वाच्या परीक्षा लिंक्स")} subtitle={t("Official websites of recruitment authorities.", "भरती संस्थांची अधिकृत संकेतस्थळे.")} />
      <section className="container-page grid gap-6 py-10 md:grid-cols-3">
        {groups.map((g) => (
          <Card key={g} title={g}>
            <ul className="space-y-2">
              {auths.filter((a) => a.group === g).map((a) => (
                <li key={a.id}><a href={a.url} target="_blank" rel="noopener noreferrer" className="flex items-center justify-between rounded-md px-2 py-1.5 text-sm hover:bg-slate-50"><span className="font-medium">{l(a.name)}</span><ExternalLink className="size-4 text-slate-400" /></a></li>
              ))}
              {g === "Maharashtra" && <li><a href="https://x.com/mpsc_office" target="_blank" rel="noopener noreferrer" className="flex items-center justify-between rounded-md px-2 py-1.5 text-sm hover:bg-slate-50"><span className="font-medium">MPSC on X – @mpsc_office</span><ExternalLink className="size-4 text-slate-400" /></a></li>}
            </ul>
          </Card>
        ))}
      </section>
    </>
  );
}

export function ContactView() {
  const { t, l } = useI18n();
  return (
    <>
      <PageHero title={t("Contact us", "संपर्क")} subtitle={t("Visit, call or WhatsApp the admissions office.", "प्रवेश कार्यालयाला भेट द्या, कॉल किंवा व्हॉट्सअ‍ॅप करा.")} />
      <section className="container-page grid gap-8 py-10 lg:grid-cols-2">
        <div className="space-y-4">
          <div className="card space-y-4 p-6">
            {[[MapPin, t("Address", "पत्ता"), l(academy.address)], [Phone, t("Phone", "फोन"), academy.phone], [Mail, t("Email", "ईमेल"), academy.email], [Clock, t("Office hours", "कार्यालयीन वेळ"), l(academy.hours)]].map(([I, k, v], i) => {
              const Icon = I as typeof Phone;
              return <div key={i} className="flex gap-3"><Icon className="mt-0.5 size-5 text-brand-600" /><div><p className="text-xs text-slate-500">{k as string}</p><p className="font-medium">{v as string}</p></div></div>;
            })}
            <div className="flex flex-wrap gap-2 pt-2">
              <a href={`tel:${academy.phone.replace(/\s/g, "")}`} className="btn-primary"><Phone className="size-4" />{t("Call", "कॉल")}</a>
              <a href={`https://wa.me/${academy.whatsapp}`} target="_blank" rel="noopener noreferrer" className="btn bg-[#25D366] text-white"><WhatsAppIcon className="size-4" />WhatsApp</a>
            </div>
            {isPlaceholder(academy.phone) && <Notice tone="warn">{t("These are placeholders. Update src/config/academy.ts with real details.", "ही नमुना माहिती आहे. src/config/academy.ts मध्ये खरी माहिती भरा.")}</Notice>}
          </div>
          <div className="flex aspect-video items-center justify-center rounded-xl border border-dashed border-slate-300 bg-white text-sm text-slate-400">
            {academy.mapEmbedUrl ? <iframe title="Map" src={academy.mapEmbedUrl} className="size-full rounded-xl" loading="lazy" /> : <span className="flex items-center gap-2"><MapPin className="size-5" />{t("Map embed placeholder", "नकाशा जागा")}</span>}
          </div>
        </div>
        <div className="card p-6">
          <h2 className="mb-4 text-lg font-semibold">{t("Send a detailed enquiry", "सविस्तर चौकशी पाठवा")}</h2>
          <EnquiryForm variant="detailed" />
        </div>
      </section>
    </>
  );
}

export function EnquiryView({ course }: { course?: string }) {
  const { t } = useI18n();
  return (
    <>
      <PageHero title={t("Enquiry", "चौकशी")} subtitle={t("Tell us what you're preparing for. We'll guide you to the right batch.", "आपण कोणत्या परीक्षेची तयारी करत आहात ते सांगा. योग्य बॅच निवडण्यास आम्ही मदत करू.")} />
      <section className="container-page grid gap-8 py-10 lg:grid-cols-[1fr_2fr]">
        <Card title={t("Quick enquiry", "त्वरित चौकशी")}><EnquiryForm variant="quick" defaultCourse={course} /></Card>
        <Card title={t("Detailed enquiry", "सविस्तर चौकशी")}><EnquiryForm variant="detailed" defaultCourse={course} /></Card>
      </section>
    </>
  );
}
