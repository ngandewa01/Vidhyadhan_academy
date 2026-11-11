"use client";
import Link from "next/link";
import { useEffect, useMemo, useRef, useState } from "react";
import { QRCodeSVG } from "qrcode.react";
import {
  AlertTriangle, Bookmark, BookmarkCheck, BookOpen, CalendarDays, ChevronLeft, ChevronRight, ClipboardCheck, CreditCard, Download, Eye, FileText, Lock,
  MonitorPlay, Pause, Play, PlayCircle, Plus, Printer, QrCode, Send, Upload, Video, Library, Bell, Clock, Trophy, RotateCcw, ExternalLink,
} from "lucide-react";
import { useStore } from "@/lib/store";
import { useI18n } from "@/lib/i18n";
import { attendanceStats, balanceFor, createMembership, paidFor, recordPayment, PAY_METHOD_LABEL, membershipPaid } from "@/lib/actions";
import { cn, dayOffset, downloadFile, fmtINR, fmtSize, nowISO, today, uid, daysBetween } from "@/lib/utils";
import { Badge, Card, Donut, EmptyState, Field, Modal, Notice, PageHeader, Progress, StatCard, StatusBadge, Tabs, Toggle, toast, statusLabel } from "@/components/ui";
import { ReceiptModal } from "@/components/Receipt";
import { DATE_LABEL } from "./public-pages";
import { TestsView, TestEngine, ResultsView } from "./exam";
import { academy } from "@/config/academy";
import { telegramLinks } from "@/lib/telegram-links";
import type { Lang, Ticket } from "@/lib/types";

export function useMe() {
  const db = useStore((s) => s.db);
  const session = useStore((s) => s.session);
  const me = db.students.find((s) => s.id === session.userId)!;
  const enrolments = db.enrolments.filter((e) => e.studentId === me?.id);
  const active = enrolments.filter((e) => e.enrolmentStatus === "active" && e.admissionStatus === "confirmed");
  return { db, me, enrolments, active, batchIds: active.map((e) => e.batchId), courseIds: active.map((e) => e.courseId) };
}

export function StudentRouter({ slug }: { slug: string[] }) {
  const { me } = useMe();
  if (!me) return null;
  const [a, b, c] = slug;
  switch (a) {
    case undefined: return <Dashboard />;
    case "courses": return b ? <CourseContent id={b} /> : <MyCourses />;
    case "live": return <LiveClasses />;
    case "recorded": return <Recorded />;
    case "materials": return <Materials />;
    case "tests": return b ? <TestEngine testId={b} review={c === "review"} /> : <TestsView />;
    case "results": return <ResultsView />;
    case "assignments": return <Assignments />;
    case "attendance": return <Attendance />;
    case "fees": return <Fees />;
    case "library": return <LibraryMembership />;
    case "updates": return <Updates />;
    case "calendar": return <CalendarView />;
    case "bookmarks": return <Updates bookmarksOnly />;
    case "notifications": return <Notifications />;
    case "support": return <Support />;
    case "id-card": return <IdCard />;
    case "profile": return <Profile />;
    default: return <EmptyState title="Page not found" />;
  }
}

/* ---------------- Dashboard ---------------- */
function Dashboard() {
  const { t, l, lc, d } = useI18n();
  const { db, me, enrolments, active, batchIds, courseIds } = useMe();
  const att = attendanceStats(db, me.id);
  const balance = enrolments.reduce((a, e) => a + balanceFor(db, e), 0);
  const nextClass = db.live.filter((c) => batchIds.includes(c.batchId) && c.status !== "cancelled" && c.date >= today()).sort((a, b) => (a.date + a.time).localeCompare(b.date + b.time))[0];
  const pendingAs = db.assignments.filter((a) => batchIds.includes(a.batchId) && !db.submissions.some((s) => s.assignmentId === a.id && s.studentId === me.id));
  const tests = db.tests.filter((x) => x.status === "published" && x.batchIds.some((b) => batchIds.includes(b)) && x.availableTo >= today());
  const updates = db.updates.filter((u) => u.status === "published" && (u.urgent || me.follows.includes(u.authorityId))).slice(0, 4);
  const lastAccessed = Object.entries(db.progress).filter(([k]) => k.startsWith(me.id + ":")).sort((a, b) => b[1].at.localeCompare(a[1].at)).slice(0, 2);
  const mem = db.memberships.filter((m) => m.studentId === me.id).sort((a, b) => b.end.localeCompare(a.end))[0];
  const pending = enrolments.filter((e) => e.admissionStatus === "pending");
  const warn = db.settings.attendance.warningPercent;

  return (
    <div className="space-y-6">
      <PageHeader title={`${t("Welcome", "नमस्कार")}, ${me.name.split(" ")[0]} 👋`} subtitle={`${me.code} · ${active.map((e) => l(db.courses.find((c) => c.id === e.courseId)?.name)).join(", ") || t("No active course", "सक्रिय अभ्यासक्रम नाही")}`} />
      {pending.length > 0 && <Notice tone="warn">{t("Your admission is pending payment verification. Course content unlocks after confirmation.", "आपला प्रवेश पेमेंट पडताळणीसाठी प्रलंबित आहे. पुष्टीनंतर साहित्य उपलब्ध होईल.")} <Link href="/student/fees" className="font-semibold underline">{t("Go to fees", "शुल्क पहा")}</Link></Notice>}
      {att.total > 0 && att.percent < warn && <Notice tone="danger">{t(`Attendance warning: ${att.percent.toFixed(0)}% (minimum ${db.settings.attendance.minimumPercent}%).`, `उपस्थिती इशारा: ${att.percent.toFixed(0)}% (किमान ${db.settings.attendance.minimumPercent}%).`)}</Notice>}
      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        <StatCard label={t("Attendance", "उपस्थिती")} value={`${att.percent.toFixed(0)}%`} hint={`${att.present + att.late}/${att.total} ${t("days", "दिवस")}`} icon={<ClipboardCheck className="size-5" />} tone={att.percent < warn ? "red" : "green"} />
        <StatCard label={t("Pending assignments", "बाकी गृहपाठ")} value={pendingAs.length} icon={<FileText className="size-5" />} tone="amber" />
        <StatCard label={t("Open tests", "खुल्या चाचण्या")} value={tests.length} icon={<Trophy className="size-5" />} tone="violet" />
        <StatCard label={t("Fee balance", "शिल्लक शुल्क")} value={fmtINR(balance)} icon={<CreditCard className="size-5" />} tone={balance ? "red" : "green"} />
      </div>
      <div className="grid gap-5 lg:grid-cols-3">
        <Card title={t("Next live class", "पुढील लाईव्ह वर्ग")} className="lg:col-span-1">
          {nextClass ? (
            <div>
              <p className="font-semibold">{nextClass.title}</p>
              <p className="text-sm text-slate-500">{d(nextClass.date)} · {nextClass.time} · {nextClass.durationMin} min</p>
              <div className="mt-2"><StatusBadge s={nextClass.status} /></div>
              <Link href="/student/live" className="btn-primary btn-sm mt-3"><MonitorPlay className="size-4" />{t("Open", "उघडा")}</Link>
            </div>
          ) : <p className="text-sm text-slate-500">{t("No upcoming classes", "आगामी वर्ग नाहीत")}</p>}
        </Card>
        <Card title={t("Continue learning", "अभ्यास सुरू ठेवा")} className="lg:col-span-2">
          {lastAccessed.length ? (
            <ul className="space-y-3">
              {lastAccessed.map(([k, v]) => {
                const r = db.recordings.find((x) => x.id === k.split(":")[1]);
                if (!r) return null;
                return (
                  <li key={k} className="flex items-center gap-3">
                    <PlayCircle className="size-8 shrink-0 text-brand-600" />
                    <div className="min-w-0 flex-1"><p className="truncate text-sm font-medium">{lc(r.title)}</p><Progress value={(v.sec / (r.durationMin * 60)) * 100} /></div>
                    <Link href="/student/recorded" className="btn-outline btn-sm">{t("Resume", "पुढे पहा")}</Link>
                  </li>
                );
              })}
            </ul>
          ) : <p className="text-sm text-slate-500">{t("Start a recorded lecture to see it here.", "रेकॉर्डेड व्याख्यान सुरू करा, ते येथे दिसेल.")}</p>}
        </Card>
      </div>
      <div className="grid gap-5 lg:grid-cols-2">
        <Card title={t("Upcoming tests", "आगामी चाचण्या")} actions={<Link href="/student/tests" className="text-xs font-semibold text-brand-700">{t("All", "सर्व")}</Link>}>
          {tests.length ? <ul className="divide-y divide-slate-100">{tests.slice(0, 4).map((x) => <li key={x.id} className="flex items-center justify-between py-2 text-sm"><span><span className="font-medium">{lc(x.title)}</span><span className="block text-xs text-slate-500">{x.durationMin} min · {t("till", "पर्यंत")} {d(x.availableTo)}</span></span><Link href={`/student/tests/${x.id}`} className="btn-outline btn-sm">{t("Start", "सुरू")}</Link></li>)}</ul> : <p className="text-sm text-slate-500">{t("No tests right now", "सध्या चाचण्या नाहीत")}</p>}
        </Card>
        <Card title={t("Official exam updates", "अधिकृत परीक्षा अपडेट्स")} actions={<Link href="/student/updates" className="text-xs font-semibold text-brand-700">{t("All", "सर्व")}</Link>}>
          <ul className="space-y-2">{updates.map((u) => <li key={u.id} className="text-sm"><p className="font-medium">{u.urgent && <Badge tone="red" className="mr-1">!</Badge>}{l(u.title)}</p>{u.dates[0] && <p className="text-xs text-slate-500">{t(...DATE_LABEL[u.dates[0].kind])}: {d(u.dates[0].date)}</p>}</li>)}</ul>
        </Card>
      </div>
      {mem && (
        <Card title={t("Abhyasika Kendra membership", "अभ्यासिका केंद्र सदस्यत्व")}>
          <div className="flex flex-wrap items-center justify-between gap-3 text-sm">
            <span>{l(db.libraryPlans.find((p) => p.id === mem.planId)?.name)} · {t("Seat", "आसन")} {mem.seat} · {t("expires", "मुदत")} {d(mem.end)}</span>
            <StatusBadge s={mem.status} />
          </div>
        </Card>
      )}
      {courseIds.length === 0 && !mem && <EmptyState title={t("You are not enrolled yet", "आपला अद्याप प्रवेश नाही")} action={<Link className="btn-accent" href="/admission">{t("Enrol in a course", "अभ्यासक्रमात प्रवेश घ्या")}</Link>} />}
    </div>
  );
}

/* ---------------- Courses & content hierarchy ---------------- */
function MyCourses() {
  const { t, l, d } = useI18n();
  const { db, enrolments } = useMe();
  return (
    <div>
      <PageHeader title={t("My courses", "माझे अभ्यासक्रम")} actions={<Link href="/admission" className="btn-outline"><Plus className="size-4" />{t("Add course", "अभ्यासक्रम जोडा")}</Link>} />
      {!enrolments.length && <EmptyState title={t("No enrolments", "प्रवेश नाहीत")} action={<Link className="btn-accent" href="/admission">{t("Enrol now", "प्रवेश घ्या")}</Link>} />}
      <div className="grid gap-4 md:grid-cols-2">
        {enrolments.map((e) => {
          const c = db.courses.find((x) => x.id === e.courseId)!;
          const b = db.batches.find((x) => x.id === e.batchId)!;
          const total = daysBetween(b.startDate, b.endDate), done = Math.max(0, daysBetween(b.startDate, today()));
          const unlocked = e.admissionStatus === "confirmed" && e.enrolmentStatus === "active";
          return (
            <div key={e.id} className="card overflow-hidden">
              <div className={`h-2 bg-gradient-to-r ${c.color}`} />
              <div className="p-5">
                <div className="flex items-start justify-between gap-2"><div><h2 className="font-semibold">{l(c.name)}</h2><p className="text-sm text-slate-500">{b.name}</p></div><StatusBadge s={b.mode} /></div>
                <div className="mt-3 flex flex-wrap gap-1.5 text-xs"><span className="text-slate-500">{t("Admission", "प्रवेश")}:</span><StatusBadge s={e.admissionStatus} /><span className="text-slate-500">{t("Payment", "पेमेंट")}:</span><StatusBadge s={e.paymentStatus} /><span className="text-slate-500">{t("Enrolment", "नोंदणी")}:</span><StatusBadge s={e.enrolmentStatus} /></div>
                <div className="mt-4"><div className="mb-1 flex justify-between text-xs text-slate-500"><span>{d(b.startDate)}</span><span>{d(b.endDate)}</span></div><Progress value={(done / total) * 100} /></div>
                {unlocked ? <Link href={`/student/courses/${e.courseId}`} className="btn-primary mt-4 w-full">{t("Open course", "अभ्यासक्रम उघडा")}</Link> : <p className="mt-4 flex items-center gap-1 text-sm text-amber-700"><Lock className="size-4" />{t("Unlocks after admission is confirmed", "प्रवेश निश्चित झाल्यावर उपलब्ध")}</p>}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}

function CourseContent({ id }: { id: string }) {
  const { t, l, lc } = useI18n();
  const { db, courseIds, batchIds } = useMe();
  const c = db.courses.find((x) => x.id === id);
  if (!c || !courseIds.includes(id)) return <EmptyState title={t("Course not available", "अभ्यासक्रम उपलब्ध नाही")} />;
  const subjects = db.subjects.filter((s) => s.courseId === id);
  return (
    <div>
      <Link href="/student/courses" className="text-sm text-brand-700">← {t("My courses", "माझे अभ्यासक्रम")}</Link>
      <PageHeader title={l(c.name)} subtitle={t("Course → Subject → Chapter → Lectures / Notes / Tests", "अभ्यासक्रम → विषय → प्रकरण → व्याख्याने / नोट्स / चाचण्या")} />
      <div className="space-y-4">
        {subjects.map((s) => (
          <details key={s.id} className="card group" open>
            <summary className="flex cursor-pointer items-center justify-between px-4 py-3 font-semibold"><span className="flex items-center gap-2"><BookOpen className="size-4 text-brand-600" />{lc(s.name)}</span><span className="text-xs font-normal text-slate-500">{s.chapters.length} {t("chapters", "प्रकरणे")}</span></summary>
            <div className="divide-y divide-slate-100 border-t border-slate-100">
              {s.chapters.map((ch) => {
                const recs = db.recordings.filter((r) => r.chapterId === ch.id && r.published && r.batchIds.some((b) => batchIds.includes(b)));
                const mats = db.materials.filter((m) => m.chapterId === ch.id && m.status === "approved" && m.batchIds.some((b) => batchIds.includes(b)));
                const qs = db.questions.filter((q) => q.chapterId === ch.id).length;
                return (
                  <div key={ch.id} className="px-4 py-3">
                    <p className="text-sm font-medium">{lc(ch.name)}</p>
                    <p className="text-xs text-slate-500">{ch.topics.map((tp) => lc(tp)).join(" · ")}</p>
                    <div className="mt-2 flex flex-wrap gap-2 text-xs">
                      {recs.map((r) => <Link key={r.id} href="/student/recorded" className="inline-flex items-center gap-1 rounded-md bg-brand-50 px-2 py-1 text-brand-800"><Video className="size-3" />{lc(r.title)}</Link>)}
                      {mats.map((m) => <Link key={m.id} href="/student/materials" className="inline-flex items-center gap-1 rounded-md bg-saffron-50 px-2 py-1 text-saffron-800"><FileText className="size-3" />{lc(m.title)}</Link>)}
                      {qs > 0 && <span className="inline-flex items-center gap-1 rounded-md bg-violet-50 px-2 py-1 text-violet-800"><Trophy className="size-3" />{qs} {t("practice questions", "सराव प्रश्न")}</span>}
                      {!recs.length && !mats.length && !qs && <span className="text-slate-400">{t("Content coming soon", "साहित्य लवकरच")}</span>}
                    </div>
                  </div>
                );
              })}
            </div>
          </details>
        ))}
      </div>
    </div>
  );
}

/* ---------------- Live classes ---------------- */
function LiveClasses() {
  const { t, d, l } = useI18n();
  const { db, me, batchIds } = useMe();
  const { update } = useStore();
  const [tab, setTab] = useState<"upcoming" | "past">("upcoming");
  const list = db.live.filter((c) => batchIds.includes(c.batchId) && (tab === "upcoming" ? c.date >= today() : c.date < today())).sort((a, b) => (a.date + a.time).localeCompare(b.date + b.time) * (tab === "upcoming" ? 1 : -1));
  const join = (id: string, link: string) => {
    update((db) => { const c = db.live.find((x) => x.id === id); if (c && !c.attendees.includes(me.id)) c.attendees.push(me.id); });
    window.open(link, "_blank", "noopener");
    toast.success(t("Joining… attendance recorded for this class.", "जोडत आहे… या वर्गाची उपस्थिती नोंदवली."));
  };
  return (
    <div>
      <PageHeader title={t("Live classes", "लाईव्ह वर्ग")} subtitle={t("Join via Zoom / Google Meet links shared by the academy.", "अकॅडमीने दिलेल्या Zoom / Google Meet लिंकद्वारे सहभागी व्हा.")} />
      <Tabs tabs={[{ id: "upcoming", label: t("Upcoming", "आगामी") }, { id: "past", label: t("History", "इतिहास") }]} value={tab} onChange={setTab} />
      <div className="space-y-3">
        {list.map((c) => {
          const s = db.subjects.find((x) => x.id === c.subjectId);
          const f = db.staff.find((x) => x.id === c.facultyId);
          const isToday = c.date === today();
          return (
            <div key={c.id} className="card flex flex-col gap-3 p-4 sm:flex-row sm:items-center">
              <div className="flex size-14 shrink-0 flex-col items-center justify-center rounded-lg bg-brand-50 text-brand-800"><span className="text-lg font-bold leading-none">{c.date.slice(8)}</span><span className="text-[10px] uppercase">{d(c.date, { month: "short" })}</span></div>
              <div className="flex-1">
                <p className="font-semibold">{c.title}</p>
                <p className="text-sm text-slate-500">{l(s?.name)} · {c.time} · {c.durationMin} min · {f?.name} · {c.provider === "zoom" ? "Zoom" : "Google Meet"}</p>
              </div>
              <div className="flex items-center gap-2">
                <StatusBadge s={c.status} />
                {tab === "past" ? <Badge tone={c.attendees.includes(me.id) ? "green" : "slate"}>{c.attendees.includes(me.id) ? t("Attended", "उपस्थित") : t("Missed", "चुकले")}</Badge>
                  : c.status === "cancelled" ? null : <button className="btn-primary btn-sm" disabled={!isToday} onClick={() => join(c.id, c.link)} title={!isToday ? t("Join opens on the class day", "वर्गाच्या दिवशी उघडेल") : ""}><ExternalLink className="size-4" />{t("Join", "सहभागी व्हा")}</button>}
              </div>
            </div>
          );
        })}
        {!list.length && <EmptyState title={t("No classes", "वर्ग नाहीत")} />}
      </div>
      <p className="mt-4 text-xs text-slate-500">{t("Reminders are sent before each class via enabled channels. Integrated live-class providers can be added later.", "प्रत्येक वर्गापूर्वी सक्षम माध्यमांतून स्मरणपत्र पाठवले जाते. एकात्मिक लाईव्ह-क्लास प्रदाते नंतर जोडता येतील.")}</p>
    </div>
  );
}

/* ---------------- Recorded lectures ---------------- */
function Recorded() {
  const { t, lc, l, d } = useI18n();
  const { db, me, batchIds } = useMe();
  const { update } = useStore();
  const [playing, setPlaying] = useState<string | null>(null);
  const list = db.recordings.filter((r) => r.published && r.batchIds.some((b) => batchIds.includes(b)) && r.availableFrom <= today());
  const rec = db.recordings.find((r) => r.id === playing);
  return (
    <div>
      <PageHeader title={t("Recorded lectures", "रेकॉर्डेड व्याख्याने")} subtitle={me.dataSaver ? t("Data saver is ON – videos start at low quality.", "डेटा सेव्हर चालू – व्हिडिओ कमी गुणवत्तेत सुरू होतील.") : undefined} />
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {list.map((r) => {
          const p = db.progress[`${me.id}:${r.id}`];
          const pct = p ? (p.sec / (r.durationMin * 60)) * 100 : 0;
          return (
            <div key={r.id} className="card overflow-hidden">
              <button onClick={() => setPlaying(r.id)} className="relative flex aspect-video w-full items-center justify-center bg-gradient-to-br from-slate-800 to-brand-900 text-white" aria-label={`Play ${lc(r.title)}`}>
                <PlayCircle className="size-12 opacity-90" /><span className="absolute bottom-2 right-2 rounded bg-black/60 px-1.5 text-xs">{r.durationMin}:00</span>
              </button>
              <div className="p-3">
                <p className="font-medium">{lc(r.title)}</p>
                <p className="text-xs text-slate-500">{l(db.subjects.find((s) => s.id === r.subjectId)?.name)} · {r.source === "hosted" ? t("Secure stream", "सुरक्षित स्ट्रीम") : t("External link", "बाह्य लिंक")}</p>
                {pct > 0 && <div className="mt-2"><Progress value={pct} tone={pct >= 95 ? "green" : "brand"} /><p className="mt-1 text-[11px] text-slate-500">{pct >= 95 ? t("Completed", "पूर्ण") : `${Math.round(pct)}% · ${t("last watched", "शेवटचे पाहिले")} ${d(p!.at)}`}</p></div>}
              </div>
            </div>
          );
        })}
      </div>
      {!list.length && <EmptyState title={t("No recordings yet", "अद्याप रेकॉर्डिंग नाही")} />}
      {rec && <Player rec={rec} onClose={() => setPlaying(null)} initial={db.progress[`${me.id}:${rec.id}`]?.sec ?? 0} save={(sec) => update((db) => { db.progress[`${me.id}:${rec.id}`] = { sec, at: nowISO() }; })} dataSaver={me.dataSaver} />}
    </div>
  );
}

function Player({ rec, onClose, initial, save, dataSaver }: { rec: { id: string; title: { en: string; mr: string }; durationMin: number; source: string; url: string }; onClose: () => void; initial: number; save: (s: number) => void; dataSaver: boolean }) {
  const { t, lc } = useI18n();
  const total = rec.durationMin * 60;
  const [pos, setPos] = useState(initial >= total - 5 ? 0 : initial);
  const [on, setOn] = useState(false);
  const [q, setQ] = useState(dataSaver ? "240p" : "Auto");
  const posRef = useRef(pos);
  posRef.current = pos;
  useEffect(() => { if (!on) return; const i = setInterval(() => setPos((p) => Math.min(total, p + 30)), 500); return () => clearInterval(i); }, [on, total]);
  useEffect(() => { const i = setInterval(() => save(posRef.current), 3000); return () => { clearInterval(i); save(posRef.current); }; }, []); // eslint-disable-line react-hooks/exhaustive-deps
  const fmt = (s: number) => `${Math.floor(s / 60)}:${String(Math.floor(s % 60)).padStart(2, "0")}`;
  return (
    <Modal open onClose={onClose} title={lc(rec.title)} size="xl">
      <div className="relative flex aspect-video items-center justify-center overflow-hidden rounded-lg bg-slate-900 text-white">
        <div className="absolute left-3 top-3 text-[10px] opacity-40">{t("Watermark: student ID", "वॉटरमार्क: विद्यार्थी ओळख")}</div>
        <div className="text-center"><Video className="mx-auto size-10 opacity-50" /><p className="mt-2 text-sm opacity-70">{rec.source === "hosted" ? t("Secure streaming placeholder (simulated playback)", "सुरक्षित स्ट्रीमिंग जागा (सिम्युलेटेड प्लेबॅक)") : t("External video link (simulated)", "बाह्य व्हिडिओ लिंक (सिम्युलेटेड)")}</p></div>
      </div>
      <div className="mt-3 flex items-center gap-3">
        <button className="btn-primary btn-sm" onClick={() => setOn((v) => !v)}>{on ? <Pause className="size-4" /> : <Play className="size-4" />}{on ? t("Pause", "थांबवा") : initial > 0 && pos === initial ? t("Resume", "पुढे सुरू") : t("Play", "सुरू")}</button>
        <input type="range" min={0} max={total} value={pos} onChange={(e) => setPos(Number(e.target.value))} className="flex-1 accent-brand-700" aria-label="Seek" />
        <span className="w-24 text-right font-mono text-xs">{fmt(pos)} / {fmt(total)}</span>
        <select className="input w-auto py-1 text-xs" value={q} onChange={(e) => setQ(e.target.value)} aria-label="Quality">{["Auto", "240p", "360p", "480p", "720p"].map((x) => <option key={x}>{x}</option>)}</select>
      </div>
      <p className="mt-2 text-xs text-slate-500">{t("Progress is saved automatically; you'll resume from where you left off.", "प्रगती आपोआप जतन होते; आपण जिथे थांबलात तिथून पुढे सुरू होईल.")}</p>
    </Modal>
  );
}

/* ---------------- Study materials ---------------- */
function Materials() {
  const { t, lc, l, d } = useI18n();
  const { db, me, batchIds, courseIds } = useMe();
  const { update } = useStore();
  const [sub, setSub] = useState("all");
  const [viewing, setViewing] = useState<string | null>(null);
  const subjects = db.subjects.filter((s) => courseIds.includes(s.courseId));
  const list = db.materials.filter((m) => m.status === "approved" && m.batchIds.some((b) => batchIds.includes(b)) && (sub === "all" || m.subjectId === sub));
  const dl = (id: string) => {
    update((db) => { const m = db.materials.find((x) => x.id === id); if (m) m.downloads++; db.downloads.push({ materialId: id, studentId: me.id, at: nowISO() }); });
    toast.success(t("Download started (simulated). Download is tracked.", "डाउनलोड सुरू (सिम्युलेटेड). डाउनलोडची नोंद होते."));
  };
  const viewed = db.materials.find((m) => m.id === viewing);
  return (
    <div>
      <PageHeader title={t("Study materials", "अभ्यास साहित्य")} />
      <div className="mb-4 flex flex-wrap gap-2">
        <button onClick={() => setSub("all")} className={sub === "all" ? "btn-primary btn-sm" : "btn-outline btn-sm"}>{t("All", "सर्व")}</button>
        {subjects.map((s) => <button key={s.id} onClick={() => setSub(s.id)} className={sub === s.id ? "btn-primary btn-sm" : "btn-outline btn-sm"}>{lc(s.name)}</button>)}
      </div>
      <div className="card divide-y divide-slate-100">
        {list.map((m) => {
          const avail = m.availableFrom <= today();
          const canView = avail && (m.access === "view" || m.access === "both");
          const canDl = avail && (m.access === "download" || m.access === "both");
          return (
            <div key={m.id} className="flex flex-col gap-2 p-4 sm:flex-row sm:items-center">
              <FileText className="size-8 shrink-0 text-saffron-600" />
              <div className="min-w-0 flex-1">
                <p className="font-medium">{lc(m.title)}</p>
                <p className="text-xs text-slate-500">{l(db.subjects.find((s) => s.id === m.subjectId)?.name)} · {m.kind.toUpperCase()} · {fmtSize(m.sizeKb)} · {m.lang === "both" ? "मराठी + English" : m.lang === "mr" ? "मराठी" : "English"}{m.watermark && ` · ${t("watermarked", "वॉटरमार्क")}`}</p>
              </div>
              <div className="flex gap-2">
                {!avail ? <Badge tone="amber"><Clock className="size-3" />{t("Available from", "उपलब्ध")} {d(m.availableFrom)}</Badge>
                  : m.access === "restricted" ? <Badge><Lock className="size-3" />{t("Restricted", "मर्यादित")}</Badge> : (
                    <>
                      {canView && <button className="btn-outline btn-sm" onClick={() => setViewing(m.id)}><Eye className="size-4" />{t("View", "पहा")}</button>}
                      {canDl && <button className="btn-outline btn-sm" onClick={() => dl(m.id)}><Download className="size-4" />{fmtSize(m.sizeKb)}</button>}
                    </>
                  )}
              </div>
            </div>
          );
        })}
        {!list.length && <p className="p-6 text-center text-sm text-slate-500">{t("No materials yet", "अद्याप साहित्य नाही")}</p>}
      </div>
      <p className="mt-3 text-xs text-slate-500">{t("Files are never downloaded automatically. Watermarking discourages sharing; screenshots cannot be fully prevented.", "फायली आपोआप डाउनलोड होत नाहीत. वॉटरमार्कमुळे शेअरिंगला आळा बसतो; स्क्रीनशॉट पूर्णपणे थांबवता येत नाहीत.")}</p>
      {viewed && (
        <Modal open onClose={() => setViewing(null)} title={lc(viewed.title)} size="lg">
          <div className="relative flex aspect-[3/4] max-h-[60vh] w-full items-center justify-center overflow-hidden rounded-lg border bg-slate-50">
            <div className="pointer-events-none absolute inset-0 flex rotate-[-30deg] flex-wrap content-center justify-center gap-8 text-sm font-semibold text-slate-300/70" aria-hidden>{Array.from({ length: 12 }).map((_, i) => <span key={i}>{me.code} · {me.mobile.slice(-4)}</span>)}</div>
            <p className="relative text-sm text-slate-500">{t("In-browser document viewer placeholder", "ब्राउझरमधील दस्तऐवज दर्शक जागा")}</p>
          </div>
        </Modal>
      )}
    </div>
  );
}

/* ---------------- Assignments ---------------- */
function Assignments() {
  const { t, l, d, dt } = useI18n();
  const { db, me, batchIds } = useMe();
  const { update } = useStore();
  const [open, setOpen] = useState<string | null>(null);
  const [files, setFiles] = useState<{ name: string; sizeKb: number }[]>([]);
  const list = db.assignments.filter((a) => batchIds.includes(a.batchId)).sort((a, b) => b.due.localeCompare(a.due));
  const a = db.assignments.find((x) => x.id === open);
  const sub = a && db.submissions.find((s) => s.assignmentId === a.id && s.studentId === me.id);
  const submit = () => {
    if (!a || !files.length) return toast.error(t("Attach at least one PDF or image", "किमान एक PDF किंवा प्रतिमा जोडा"));
    const late = today() > a.due;
    update((db) => {
      const ex = db.submissions.find((s) => s.assignmentId === a.id && s.studentId === me.id);
      if (ex) Object.assign(ex, { files, submittedAt: nowISO(), late, status: "submitted", marks: undefined });
      else db.submissions.push({ id: uid("sb"), assignmentId: a.id, studentId: me.id, files, submittedAt: nowISO(), late, status: "submitted" });
    });
    toast.success(late ? t("Submitted (late)", "जमा केले (उशिरा)") : t("Submitted", "जमा केले"));
    setOpen(null); setFiles([]);
  };
  return (
    <div>
      <PageHeader title={t("Assignments", "गृहपाठ")} />
      <div className="space-y-3">
        {list.map((x) => {
          const s = db.submissions.find((y) => y.assignmentId === x.id && y.studentId === me.id);
          const overdue = !s && today() > x.due;
          return (
            <div key={x.id} className="card flex flex-col gap-3 p-4 sm:flex-row sm:items-center">
              <div className="flex-1">
                <p className="font-semibold">{x.title}</p>
                <p className="text-sm text-slate-500">{l(db.subjects.find((y) => y.id === x.subjectId)?.name)} · {t("Due", "अंतिम")} {d(x.due)} · {x.maxMarks} {t("marks", "गुण")}</p>
              </div>
              <div className="flex flex-wrap items-center gap-2">
                {s ? <StatusBadge s={s.status} /> : overdue ? <Badge tone="red">{t("Overdue", "मुदत संपली")}</Badge> : <Badge tone="amber">{t("Pending", "बाकी")}</Badge>}
                {s?.late && <Badge tone="orange">{t("Late", "उशिरा")}</Badge>}
                {s?.marks !== undefined && <Badge tone="green">{s.marks}/{x.maxMarks}</Badge>}
                <button className="btn-outline btn-sm" onClick={() => { setOpen(x.id); setFiles([]); }}>{t("Open", "उघडा")}</button>
              </div>
            </div>
          );
        })}
        {!list.length && <EmptyState title={t("No assignments", "गृहपाठ नाही")} />}
      </div>
      {a && (
        <Modal open onClose={() => setOpen(null)} title={a.title} size="lg" footer={(!sub || (a.allowResubmit && sub.status !== "graded") || sub.status === "correction") && (today() <= a.due || a.allowLate) ? <button className="btn-primary" onClick={submit}><Upload className="size-4" />{sub ? t("Resubmit", "पुन्हा जमा करा") : t("Submit", "जमा करा")}</button> : undefined}>
          <p className="text-sm text-slate-700">{a.instructions}</p>
          <dl className="mt-3 grid grid-cols-2 gap-2 text-sm sm:grid-cols-4">
            <div><dt className="text-slate-500">{t("Issued", "दिले")}</dt><dd>{d(a.issued)}</dd></div><div><dt className="text-slate-500">{t("Due", "अंतिम")}</dt><dd>{d(a.due)}</dd></div>
            <div><dt className="text-slate-500">{t("Max / pass", "कमाल / उत्तीर्ण")}</dt><dd>{a.maxMarks} / {a.passMarks}</dd></div><div><dt className="text-slate-500">{t("Late allowed", "उशिरा चालेल")}</dt><dd>{a.allowLate ? "✓" : "✗"}</dd></div>
          </dl>
          {a.attachments.length > 0 && <p className="mt-3 text-sm">{t("Attachments", "जोडण्या")}: {a.attachments.map((x) => <span key={x} className="mr-2 inline-flex items-center gap-1 rounded bg-slate-100 px-2 py-0.5 text-xs"><FileText className="size-3" />{x}</span>)}</p>}
          {sub && (
            <div className="mt-4 rounded-lg bg-slate-50 p-3 text-sm">
              <p className="font-medium">{t("Your submission", "आपले उत्तर")} · {dt(sub.submittedAt)} <StatusBadge s={sub.status} /></p>
              <ul className="mt-1 text-xs text-slate-600">{sub.files.map((f) => <li key={f.name}>{f.name} ({fmtSize(f.sizeKb)})</li>)}</ul>
              {sub.feedback && <p className="mt-2"><b>{t("Feedback", "अभिप्राय")}:</b> {sub.feedback}</p>}
            </div>
          )}
          {(!sub || a.allowResubmit || sub.status === "correction") && (
            <div className="mt-4">
              <label className="label" htmlFor="as-files">{t("Upload PDF or photos of handwritten answers (multiple allowed)", "PDF किंवा हस्तलिखित उत्तरांचे फोटो जोडा (अनेक चालतील)")}</label>
              <input id="as-files" type="file" multiple accept="application/pdf,image/*" className="input" onChange={(e) => setFiles(Array.from(e.target.files ?? []).map((f) => ({ name: f.name, sizeKb: Math.max(1, Math.round(f.size / 1024)) })))} />
              {today() > a.due && <p className="mt-2 text-xs text-amber-700">{a.allowLate ? t("Deadline passed – this will be marked late.", "मुदत संपली – उशिरा म्हणून नोंद होईल.") : t("Deadline passed – submissions closed.", "मुदत संपली – जमा करणे बंद.")}</p>}
            </div>
          )}
        </Modal>
      )}
    </div>
  );
}

/* ---------------- Attendance (incl. QR) ---------------- */
function Attendance() {
  const { t, d, lang } = useI18n();
  const { db, me, active } = useMe();
  const { update } = useStore();
  const [batch, setBatch] = useState(active[0]?.batchId ?? "");
  const [month, setMonth] = useState(today().slice(0, 7));
  const [scan, setScan] = useState(false);
  const [code, setCode] = useState("");
  const st = attendanceStats(db, me.id, batch || undefined);
  const recs = db.attendance.filter((a) => a.studentId === me.id && (!batch || a.batchId === batch));
  const byDate = new Map(recs.map((r) => [r.date, r]));
  const [y, m] = month.split("-").map(Number);
  const first = new Date(y, m - 1, 1), days = new Date(y, m, 0).getDate();
  const min = db.settings.attendance.minimumPercent;

  const submitQR = () => {
    const now = Date.now();
    const s = db.qrSessions.find((q) => q.code === code.trim().toUpperCase());
    if (!s) return toast.error(t("Invalid QR code", "अवैध QR कोड"));
    if (s.expiresAt < now) return toast.error(t("QR code expired – ask faculty to refresh", "QR कोडची मुदत संपली – प्राध्यापकांना रिफ्रेश करण्यास सांगा"));
    if (!active.some((e) => e.batchId === s.batchId)) return toast.error(t("This QR is for a different batch", "हा QR दुसऱ्या बॅचसाठी आहे"));
    if (db.attendance.some((a) => a.studentId === me.id && a.batchId === s.batchId && a.date === today())) return toast.info(t("Already marked for today", "आजची उपस्थिती आधीच नोंदली"));
    update((db) => { db.attendance.push({ id: uid("at"), batchId: s.batchId, date: today(), studentId: me.id, status: "P", method: "qr", markedBy: "qr" }); });
    toast.success(t("Attendance marked: Present", "उपस्थिती नोंदली: उपस्थित"));
    setScan(false); setCode("");
  };

  return (
    <div>
      <PageHeader title={t("Attendance", "उपस्थिती")} actions={<button className="btn-primary" onClick={() => setScan(true)}><QrCode className="size-4" />{t("Scan class QR", "वर्ग QR स्कॅन करा")}</button>} />
      <div className="mb-4 flex flex-wrap gap-2">{active.map((e) => <button key={e.id} onClick={() => setBatch(e.batchId)} className={batch === e.batchId ? "btn-primary btn-sm" : "btn-outline btn-sm"}>{db.batches.find((b) => b.id === e.batchId)?.name}</button>)}</div>
      {st.total > 0 && st.percent < min && <div className="mb-4"><Notice tone="danger">{t(`Shortage: your attendance is below the required ${min}%.`, `कमतरता: आपली उपस्थिती आवश्यक ${min}% पेक्षा कमी आहे.`)}</Notice></div>}
      <div className="grid gap-5 lg:grid-cols-[280px_1fr]">
        <Card>
          <div className="flex flex-col items-center gap-3">
            <Donut value={st.percent} label={t("overall", "एकूण")} size={120} tone={st.percent < min ? "#dc2626" : "#059669"} />
            <div className="grid w-full grid-cols-3 text-center text-sm">
              <div><p className="text-lg font-bold text-emerald-700">{st.present}</p><p className="text-xs text-slate-500">{statusLabel("P", lang)}</p></div>
              <div><p className="text-lg font-bold text-amber-600">{st.late}</p><p className="text-xs text-slate-500">{statusLabel("L", lang)}</p></div>
              <div><p className="text-lg font-bold text-red-600">{st.absent}</p><p className="text-xs text-slate-500">{statusLabel("A", lang)}</p></div>
            </div>
            <p className="text-xs text-slate-500">{t(`Minimum required: ${min}%`, `किमान आवश्यक: ${min}%`)}</p>
          </div>
        </Card>
        <Card title={<span className="flex items-center gap-2"><button className="btn-ghost btn-sm" aria-label="Previous month" onClick={() => { const dd = new Date(y, m - 2, 1); setMonth(`${dd.getFullYear()}-${String(dd.getMonth() + 1).padStart(2, "0")}`); }}><ChevronLeft className="size-4" /></button>{d(month + "-01", { month: "long", year: "numeric" })}<button className="btn-ghost btn-sm" aria-label="Next month" onClick={() => { const dd = new Date(y, m, 1); setMonth(`${dd.getFullYear()}-${String(dd.getMonth() + 1).padStart(2, "0")}`); }}><ChevronRight className="size-4" /></button></span>}>
          <div className="grid grid-cols-7 gap-1 text-center text-xs">
            {["S", "M", "T", "W", "T", "F", "S"].map((x, i) => <span key={i} className="py-1 font-semibold text-slate-400">{x}</span>)}
            {Array.from({ length: first.getDay() }).map((_, i) => <span key={"e" + i} />)}
            {Array.from({ length: days }).map((_, i) => {
              const iso = `${month}-${String(i + 1).padStart(2, "0")}`;
              const r = byDate.get(iso);
              return <span key={iso} title={r ? `${statusLabel(r.status, lang)} · ${r.method.toUpperCase()}` : ""} className={cn("rounded-md py-2 font-medium", r?.status === "P" ? "bg-emerald-100 text-emerald-800" : r?.status === "A" ? "bg-red-100 text-red-800" : r?.status === "L" ? "bg-amber-100 text-amber-800" : "text-slate-400", iso === today() && "ring-2 ring-brand-500")}>{i + 1}</span>;
            })}
          </div>
        </Card>
      </div>
      <Modal open={scan} onClose={() => setScan(false)} title={t("Scan class QR", "वर्ग QR स्कॅन करा")} footer={<button className="btn-primary" onClick={submitQR}>{t("Mark attendance", "उपस्थिती नोंदवा")}</button>}>
        <div className="flex aspect-square max-h-56 w-full items-center justify-center rounded-lg bg-slate-900 text-sm text-slate-300">{t("Camera viewfinder (simulated)", "कॅमेरा (सिम्युलेटेड)")}</div>
        <Field label={t("Or enter the code shown under the QR", "किंवा QR खालील कोड लिहा")} className="mt-3" hint={t("Faculty → Attendance → Generate QR shows a time-limited code.", "प्राध्यापक → उपस्थिती → QR तयार करा येथे वेळ-मर्यादित कोड दिसतो.")}>{(id) => <input id={id} className="input font-mono uppercase" value={code} onChange={(e) => setCode(e.target.value)} />}</Field>
        <p className="mt-2 text-xs text-slate-500">{t("You must be logged in. The server validates batch, expiry and duplicate scans. Codes rotate every few seconds to reduce screenshot reuse.", "लॉगिन आवश्यक. सर्व्हर बॅच, मुदत व दुहेरी स्कॅन तपासतो. स्क्रीनशॉटचा गैरवापर टाळण्यासाठी कोड काही सेकंदांनी बदलतो.")}</p>
      </Modal>
    </div>
  );
}

/* ---------------- Fees ---------------- */
function Fees() {
  const { t, l, d, dt, lang } = useI18n();
  const { db, me, enrolments } = useMe();
  const { update } = useStore();
  const [receipt, setReceipt] = useState<string | null>(null);
  const [payFor, setPayFor] = useState<string | null>(null);
  const [amt, setAmt] = useState(0);
  const payments = db.payments.filter((p) => p.studentId === me.id).sort((a, b) => b.date.localeCompare(a.date));
  const pay = () => {
    const e = db.enrolments.find((x) => x.id === payFor);
    if (!e || amt <= 0 || amt > balanceFor(db, e)) return toast.error(t("Enter a valid amount", "वैध रक्कम लिहा"));
    let pid = "";
    update((db) => { pid = recordPayment(db, { studentId: me.id, enrolmentId: e.id, amount: amt, method: "upi", reference: `DEMO${Date.now().toString().slice(-8)}`, by: "online" }).id; }, { action: "payment.online", entity: me.code, detail: `₹${amt} (simulated)` });
    setPayFor(null); setReceipt(pid);
    toast.success(t("Payment successful (simulated)", "पेमेंट यशस्वी (सिम्युलेटेड)"));
  };
  return (
    <div className="space-y-5">
      <PageHeader title={t("Fees & receipts", "शुल्क व पावत्या")} />
      {enrolments.map((e) => {
        const paid = paidFor(db, e.id), bal = balanceFor(db, e);
        let cum = 0;
        return (
          <Card key={e.id} title={`${l(db.courses.find((c) => c.id === e.courseId)?.name)} – ${db.batches.find((b) => b.id === e.batchId)?.name}`} actions={<StatusBadge s={e.paymentStatus} />}>
            <div className="grid gap-3 text-sm sm:grid-cols-5">
              {[[t("Original fee", "मूळ शुल्क"), fmtINR(e.originalFee)], [t("Discount", "सवलत"), `− ${fmtINR(e.discount)}`], [t("Final fee", "अंतिम शुल्क"), fmtINR(e.finalFee)], [t("Paid", "भरले"), fmtINR(paid)], [t("Balance", "शिल्लक"), fmtINR(bal)]].map(([k, v]) => <div key={k}><p className="text-xs text-slate-500">{k}</p><p className="font-semibold">{v}</p></div>)}
            </div>
            {e.discountReason && <p className="mt-2 text-xs text-slate-500">{t("Discount reason", "सवलतीचे कारण")}: {e.discountReason}</p>}
            <p className="label mt-4">{t("Instalment schedule", "हप्ते वेळापत्रक")}</p>
            <ul className="divide-y divide-slate-100 text-sm">
              {e.installments.map((i, k) => { cum += i.amount; const done = paid >= cum; const overdue = !done && i.due < today(); return <li key={k} className="flex justify-between py-1.5"><span>{t("Instalment", "हप्ता")} {k + 1} · {d(i.due)}</span><span className="flex items-center gap-2">{fmtINR(i.amount)} {done ? <Badge tone="green">{t("Paid", "भरले")}</Badge> : overdue ? <Badge tone="red">{t("Overdue", "थकीत")}</Badge> : <Badge tone="amber">{t("Due", "देय")}</Badge>}</span></li>; })}
            </ul>
            {bal > 0 && <button className="btn-accent mt-3" onClick={() => { setPayFor(e.id); setAmt(Math.min(bal, e.installments.find((_, k) => paid < e.installments.slice(0, k + 1).reduce((a, x) => a + x.amount, 0))?.amount ?? bal)); }}><CreditCard className="size-4" />{t("Pay now", "आता भरा")}</button>}
          </Card>
        );
      })}
      <Card title={t("Payment history", "पेमेंट इतिहास")} bodyClass="p-0">
        <div className="overflow-x-auto">
          <table className="table-base">
            <thead><tr><th>{t("Receipt", "पावती")}</th><th>{t("Date", "दिनांक")}</th><th>{t("Method", "पद्धत")}</th><th>{t("Status", "स्थिती")}</th><th className="text-right">{t("Amount", "रक्कम")}</th><th /></tr></thead>
            <tbody>
              {payments.map((p) => <tr key={p.id}><td className="font-mono text-xs">{p.receiptNo}</td><td>{dt(p.date)}</td><td>{PAY_METHOD_LABEL[p.method][lang === "mr" ? 1 : 0]}</td><td><StatusBadge s={p.status} /></td><td className="text-right font-semibold">{fmtINR(p.amount)}</td><td>{p.status === "success" && <button className="btn-ghost btn-sm" onClick={() => setReceipt(p.id)}><Printer className="size-4" />{t("Receipt", "पावती")}</button>}</td></tr>)}
            </tbody>
          </table>
          {!payments.length && <p className="p-6 text-center text-sm text-slate-500">{t("No payments yet", "अद्याप पेमेंट नाही")}</p>}
        </div>
      </Card>
      <Modal open={!!payFor} onClose={() => setPayFor(null)} title={t("Pay fees", "शुल्क भरा")} footer={<button className="btn-accent" onClick={pay}>{t("Pay", "भरा")} {fmtINR(amt)}</button>}>
        <Field label={t("Amount (partial payments allowed)", "रक्कम (अंशतः भरणे चालेल)")}>{(id) => <input id={id} type="number" className="input" value={amt} onChange={(e) => setAmt(Number(e.target.value))} />}</Field>
        <p className="mt-2 text-xs text-slate-500">{t("Simulated UPI payment. No money is charged.", "सिम्युलेटेड UPI पेमेंट. कोणतीही रक्कम आकारली जात नाही.")}</p>
      </Modal>
      <ReceiptModal paymentId={receipt} onClose={() => setReceipt(null)} />
    </div>
  );
}

/* ---------------- Library ---------------- */
function LibraryMembership() {
  const { t, l, d } = useI18n();
  const { db, me } = useMe();
  const { update } = useStore();
  const [receipt, setReceipt] = useState<string | null>(null);
  const mems = db.memberships.filter((m) => m.studentId === me.id).sort((a, b) => b.end.localeCompare(a.end));
  const cur = mems[0];
  const renew = (planId: string) => {
    let pid = "";
    update((db) => {
      const start = cur && cur.end >= today() ? dayOffset(1, new Date(cur.end)) : today();
      const m = createMembership(db, { studentId: me.id, planId, start, discount: 0, renewal: true });
      if (cur?.seat) m.seat = cur.seat;
      pid = recordPayment(db, { studentId: me.id, membershipId: m.id, amount: m.fee, method: "upi", reference: `DEMO${Date.now().toString().slice(-6)}`, by: "online" }).id;
    }, { action: "library.renew", entity: me.code, detail: planId });
    setReceipt(pid);
    toast.success(t("Membership renewed (simulated payment)", "सदस्यत्व नूतनीकरण झाले (सिम्युलेटेड पेमेंट)"));
  };
  const checkins = db.checkins.filter((c) => mems.some((m) => m.id === c.membershipId));
  const daysLeft = cur ? daysBetween(today(), cur.end) : 0;
  return (
    <div className="space-y-5">
      <PageHeader title={t("Abhyasika Kendra membership", "अभ्यासिका केंद्र सदस्यत्व")} />
      {!cur ? <EmptyState icon={<Library className="size-6" />} title={t("No Abhyasika Kendra membership", "अभ्यासिका केंद्र सदस्यत्व नाही")} hint={t("Abhyasika Kendra only plans don't require course enrolment.", "फक्त अभ्यासिका केंद्र योजनांसाठी अभ्यासक्रम प्रवेश आवश्यक नाही.")} action={<Link href="/admission?library=lp_m" className="btn-accent">{t("Get membership", "सदस्यत्व घ्या")}</Link>} /> : (
        <>
          {cur.status === "active" && daysLeft <= 7 && <Notice tone="warn">{t(`Your membership expires in ${daysLeft} day(s). Renew to keep your seat.`, `आपले सदस्यत्व ${daysLeft} दिवसांत संपेल. आसन टिकवण्यासाठी नूतनीकरण करा.`)}</Notice>}
          <div className="grid gap-4 sm:grid-cols-4">
            <StatCard label={t("Status", "स्थिती")} value={<StatusBadge s={cur.status} />} />
            <StatCard label={t("Plan", "योजना")} value={<span className="text-base">{l(db.libraryPlans.find((p) => p.id === cur.planId)?.name)}</span>} />
            <StatCard label={t("Seat", "आसन")} value={cur.seat ?? "—"} />
            <StatCard label={t("Valid till", "मुदत")} value={<span className="text-base">{d(cur.end)}</span>} hint={cur.status === "active" ? `${daysLeft} ${t("days left", "दिवस बाकी")}` : undefined} tone={daysLeft <= 7 ? "red" : "green"} />
          </div>
          <Card title={t("Renew / change plan", "नूतनीकरण / योजना बदला")}>
            <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-4">
              {db.libraryPlans.filter((p) => p.active && p.kind === "library").map((p) => <button key={p.id} onClick={() => renew(p.id)} className="rounded-lg border border-slate-200 p-3 text-left text-sm hover:border-brand-500"><p className="font-semibold">{l(p.name)}</p><p className="text-brand-800">{fmtINR(p.fee)} <span className="text-[10px] text-saffron-700">demo</span></p></button>)}
            </div>
          </Card>
          <Card title={t("History", "इतिहास")} bodyClass="p-0">
            <table className="table-base"><thead><tr><th>{t("Plan", "योजना")}</th><th>{t("Period", "कालावधी")}</th><th>{t("Paid", "भरले")}</th><th>{t("Status", "स्थिती")}</th></tr></thead>
              <tbody>{mems.map((m) => <tr key={m.id}><td>{l(db.libraryPlans.find((p) => p.id === m.planId)?.name)}</td><td>{d(m.start)} – {d(m.end)}</td><td>{fmtINR(membershipPaid(db, m.id))}</td><td><StatusBadge s={m.status} /></td></tr>)}</tbody></table>
          </Card>
          <Card title={t("Recent check-ins", "अलीकडील नोंदी")}>{checkins.length ? <ul className="text-sm">{checkins.map((c) => <li key={c.id}>{d(c.date)} · {c.inTime}{c.outTime ? ` – ${c.outTime}` : ""}</li>)}</ul> : <p className="text-sm text-slate-500">{t("No check-ins yet", "अद्याप नोंद नाही")}</p>}</Card>
        </>
      )}
      <ReceiptModal paymentId={receipt} onClose={() => setReceipt(null)} />
    </div>
  );
}

/* ---------------- Exam updates & bookmarks ---------------- */
function Updates({ bookmarksOnly }: { bookmarksOnly?: boolean }) {
  const { t, l, d, lang } = useI18n();
  const { db, me } = useMe();
  const { update } = useStore();
  const toggleFollow = (id: string) => update((db) => { const s = db.students.find((x) => x.id === me.id)!; s.follows = s.follows.includes(id) ? s.follows.filter((x) => x !== id) : [...s.follows, id]; });
  const toggleBm = (id: string) => update((db) => { const s = db.students.find((x) => x.id === me.id)!; s.bookmarks = s.bookmarks.includes(id) ? s.bookmarks.filter((x) => x !== id) : [...s.bookmarks, id]; });
  const list = db.updates.filter((u) => u.status === "published" && (bookmarksOnly ? me.bookmarks.includes(u.id) : u.urgent || me.follows.includes(u.authorityId))).sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  return (
    <div>
      <PageHeader title={bookmarksOnly ? t("Bookmarks", "बुकमार्क्स") : t("Official exam updates", "अधिकृत परीक्षा अपडेट्स")} subtitle={!bookmarksOnly ? t("You see updates only for exams you follow, plus urgent academy notices.", "आपण फॉलो केलेल्या परीक्षांचे अपडेट्स व तातडीच्या अकॅडमी सूचना दिसतात.") : undefined} />
      {!bookmarksOnly && (
        <Card title={t("Exams & authorities I follow", "मी फॉलो करत असलेल्या परीक्षा")} className="mb-5">
          <div className="flex flex-wrap gap-2">{db.authorities.map((a) => <button key={a.id} onClick={() => toggleFollow(a.id)} aria-pressed={me.follows.includes(a.id)} className={me.follows.includes(a.id) ? "btn-primary btn-sm" : "btn-outline btn-sm"}>{me.follows.includes(a.id) ? "✓ " : "+ "}{l(a.name)}</button>)}</div>
          <p className="mt-3 text-xs text-slate-500">{t(`Reminders: ${db.settings.reminderDays.join(", ")} day(s) before important dates.`, `स्मरणपत्रे: महत्त्वाच्या तारखांपूर्वी ${db.settings.reminderDays.join(", ")} दिवस.`)}</p>
        </Card>
      )}
      <div className="space-y-3">
        {list.map((u) => (
          <article key={u.id} className={cn("card p-4", u.urgent && "border-red-300")}>
            <div className="flex items-start justify-between gap-2">
              <div>
                <div className="flex flex-wrap items-center gap-2 text-xs"><Badge tone="blue">{l(db.authorities.find((a) => a.id === u.authorityId)?.name)}</Badge>{u.urgent && <Badge tone="red">{t("Urgent", "तातडीचे")}</Badge>}<span className="text-slate-500">{d(u.createdAt)}</span></div>
                <h2 className="mt-1 font-semibold">{l(u.title)}</h2>
                <p className="text-sm text-slate-600">{l(u.summary)}</p>
              </div>
              <button className="btn-ghost px-2" onClick={() => toggleBm(u.id)} aria-label="Bookmark" aria-pressed={me.bookmarks.includes(u.id)}>{me.bookmarks.includes(u.id) ? <BookmarkCheck className="size-5 text-brand-700" /> : <Bookmark className="size-5" />}</button>
            </div>
            {u.dates.length > 0 && <div className="mt-2 flex flex-wrap gap-2">{u.dates.map((x, i) => <span key={i} className={cn("rounded-md px-2 py-1 text-xs", x.date >= today() && daysBetween(today(), x.date) <= 7 ? "bg-red-50 text-red-800" : "bg-slate-100")}><b>{DATE_LABEL[x.kind][lang === "mr" ? 1 : 0]}:</b> {d(x.date)}</span>)}</div>}
            {u.sourceUrl && <a href={u.sourceUrl} target="_blank" rel="noopener noreferrer" className="mt-2 inline-flex items-center gap-1 text-sm text-brand-700 hover:underline">{t("Official source", "अधिकृत स्रोत")}<ExternalLink className="size-3.5" /></a>}
          </article>
        ))}
        {!list.length && <EmptyState title={bookmarksOnly ? t("No bookmarks yet", "अद्याप बुकमार्क नाहीत") : t("No updates for the exams you follow", "फॉलो केलेल्या परीक्षांसाठी अपडेट्स नाहीत")} />}
      </div>
    </div>
  );
}

/* ---------------- Calendar ---------------- */
function CalendarView() {
  const { t, l, lc, d, lang } = useI18n();
  const { db, me, batchIds, enrolments } = useMe();
  const [month, setMonth] = useState(today().slice(0, 7));
  const [sel, setSel] = useState(today());
  const events = useMemo(() => {
    const ev: { date: string; label: string; tone: string }[] = [];
    db.live.filter((c) => batchIds.includes(c.batchId) && c.status !== "cancelled").forEach((c) => ev.push({ date: c.date, label: `${c.time} ${c.title}`, tone: "bg-brand-600" }));
    db.tests.filter((x) => x.status === "published" && x.batchIds.some((b) => batchIds.includes(b))).forEach((x) => ev.push({ date: x.availableTo, label: `${t("Test closes", "चाचणी बंद")}: ${lc(x.title)}`, tone: "bg-violet-600" }));
    db.assignments.filter((a) => batchIds.includes(a.batchId)).forEach((a) => ev.push({ date: a.due, label: `${t("Assignment due", "गृहपाठ अंतिम")}: ${a.title}`, tone: "bg-amber-500" }));
    db.updates.filter((u) => u.status === "published" && me.follows.includes(u.authorityId)).forEach((u) => u.dates.forEach((x) => ev.push({ date: x.date, label: `${DATE_LABEL[x.kind][lang === "mr" ? 1 : 0]} – ${l(db.authorities.find((a) => a.id === u.authorityId)?.name)}`, tone: "bg-red-600" })));
    enrolments.forEach((e) => e.installments.forEach((i) => ev.push({ date: i.due, label: `${t("Instalment due", "हप्ता देय")} ${fmtINR(i.amount)}`, tone: "bg-emerald-600" })));
    db.memberships.filter((m) => m.studentId === me.id && m.status === "active").forEach((m) => ev.push({ date: m.end, label: t("Abhyasika Kendra membership expires", "अभ्यासिका केंद्र सदस्यत्व संपते"), tone: "bg-saffron-600" }));
    return ev;
  }, [db, batchIds, me, enrolments, t, l, lc, lang]);
  const [y, m] = month.split("-").map(Number);
  const first = new Date(y, m - 1, 1), days = new Date(y, m, 0).getDate();
  const selEv = events.filter((e) => e.date === sel);
  return (
    <div>
      <PageHeader title={t("Calendar", "दिनदर्शिका")} />
      <div className="grid gap-5 lg:grid-cols-[1fr_320px]">
        <Card title={<span className="flex items-center gap-2"><button className="btn-ghost btn-sm" aria-label="Previous" onClick={() => { const dd = new Date(y, m - 2, 1); setMonth(`${dd.getFullYear()}-${String(dd.getMonth() + 1).padStart(2, "0")}`); }}><ChevronLeft className="size-4" /></button>{d(month + "-01", { month: "long", year: "numeric" })}<button className="btn-ghost btn-sm" aria-label="Next" onClick={() => { const dd = new Date(y, m, 1); setMonth(`${dd.getFullYear()}-${String(dd.getMonth() + 1).padStart(2, "0")}`); }}><ChevronRight className="size-4" /></button></span>}>
          <div className="grid grid-cols-7 gap-1 text-xs">
            {["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"].map((x) => <span key={x} className="py-1 text-center font-semibold text-slate-400">{x}</span>)}
            {Array.from({ length: first.getDay() }).map((_, i) => <span key={"e" + i} />)}
            {Array.from({ length: days }).map((_, i) => {
              const iso = `${month}-${String(i + 1).padStart(2, "0")}`;
              const ev = events.filter((e) => e.date === iso);
              return (
                <button key={iso} onClick={() => setSel(iso)} className={cn("flex min-h-16 flex-col items-start rounded-md border p-1 text-left", sel === iso ? "border-brand-600 bg-brand-50" : "border-slate-100 hover:bg-slate-50", iso === today() && "ring-1 ring-brand-400")}>
                  <span className="font-semibold">{i + 1}</span>
                  <span className="mt-auto flex flex-wrap gap-0.5">{ev.slice(0, 4).map((e, k) => <span key={k} className={cn("size-1.5 rounded-full", e.tone)} />)}</span>
                </button>
              );
            })}
          </div>
        </Card>
        <Card title={d(sel, { weekday: "long", day: "numeric", month: "long" })}>
          {selEv.length ? <ul className="space-y-2 text-sm">{selEv.map((e, i) => <li key={i} className="flex gap-2"><span className={cn("mt-1.5 size-2 shrink-0 rounded-full", e.tone)} />{e.label}</li>)}</ul> : <p className="text-sm text-slate-500">{t("Nothing scheduled", "काहीही नियोजित नाही")}</p>}
          <div className="mt-4 space-y-1 border-t pt-3 text-xs text-slate-500">
            {[["bg-brand-600", t("Live class", "लाईव्ह वर्ग")], ["bg-violet-600", t("Test", "चाचणी")], ["bg-amber-500", t("Assignment", "गृहपाठ")], ["bg-red-600", t("Exam date (official)", "परीक्षा तारीख (अधिकृत)")], ["bg-emerald-600", t("Fee", "शुल्क")], ["bg-saffron-600", t("Abhyasika Kendra", "अभ्यासिका केंद्र")]].map(([c, x]) => <p key={x} className="flex items-center gap-2"><span className={cn("size-2 rounded-full", c)} />{x}</p>)}
          </div>
        </Card>
      </div>
    </div>
  );
}

/* ---------------- Notifications ---------------- */
function Notifications() {
  const { t, l, dt } = useI18n();
  const { db, me } = useMe();
  const { update } = useStore();
  const list = db.inbox.filter((n) => n.studentId === me.id || n.studentId === "*").sort((a, b) => b.at.localeCompare(a.at));
  return (
    <div>
      <PageHeader title={t("Notifications", "सूचना")} actions={<button className="btn-outline" onClick={() => update((db) => db.inbox.forEach((n) => { if (n.studentId === me.id || n.studentId === "*") n.read = true; }))}>{t("Mark all read", "सर्व वाचले")}</button>} />
      <div className="card divide-y divide-slate-100">
        {list.map((n) => (
          <button key={n.id} onClick={() => update((db) => { const x = db.inbox.find((y) => y.id === n.id); if (x) x.read = true; })} className={cn("flex w-full gap-3 p-4 text-left", !n.read && "bg-brand-50/50")}>
            <Bell className={cn("mt-0.5 size-5 shrink-0", n.read ? "text-slate-300" : "text-brand-600")} />
            <div className="flex-1"><p className={cn("text-sm", !n.read && "font-semibold")}>{l(n.title)}</p><p className="text-sm text-slate-600">{l(n.body)}</p><p className="mt-1 text-xs text-slate-400">{dt(n.at)} · {n.kind}</p></div>
            {!n.read && <span className="mt-2 size-2 rounded-full bg-brand-600" />}
          </button>
        ))}
        {!list.length && <p className="p-6 text-center text-sm text-slate-500">{t("You're all caught up", "नवीन सूचना नाहीत")}</p>}
      </div>
    </div>
  );
}

/* ---------------- Support ---------------- */
const CATS: Record<Ticket["category"], [string, string]> = { admissions: ["Admissions", "प्रवेश"], fees: ["Fees", "शुल्क"], course: ["Course", "अभ्यासक्रम"], lectures: ["Live/recorded lectures", "लाईव्ह/रेकॉर्डेड व्याख्याने"], exams: ["Exams", "परीक्षा"], attendance: ["Attendance", "उपस्थिती"], technical: ["Technical", "तांत्रिक"], complaint: ["General complaint", "सामान्य तक्रार"] };
export { CATS as TICKET_CATS };
function Support() {
  const { t, l, dt, lang } = useI18n();
  const { db, me } = useMe();
  const { update } = useStore();
  const [newOpen, setNewOpen] = useState(false);
  const [view, setView] = useState<string | null>(null);
  const [form, setForm] = useState({ category: "course" as string, subject: "", text: "", priority: "medium" });
  const [reply, setReply] = useState("");
  const mine = db.tickets.filter((x) => x.studentId === me.id).sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  const tk = db.tickets.find((x) => x.id === view);
  const create = () => {
    if (form.subject.trim().length < 4 || form.text.trim().length < 4) return toast.error(t("Add a subject and description", "विषय व तपशील लिहा"));
    update((db) => { db.tickets.unshift({ id: uid("tk"), code: `TKT-${1000 + db.tickets.length + 1}`, studentId: me.id, category: form.category as Ticket["category"], subject: form.subject, priority: form.priority as Ticket["priority"], status: "new", messages: [{ by: me.id, text: form.text, at: nowISO() }], createdAt: nowISO() }); });
    setNewOpen(false); setForm({ category: "course", subject: "", text: "", priority: "medium" });
    toast.success(t("Ticket created", "तक्रार नोंदवली"));
  };
  const send = (reopen = false) => {
    if (!tk || (!reply.trim() && !reopen)) return;
    update((db) => { const x = db.tickets.find((y) => y.id === tk.id)!; if (reply.trim()) x.messages.push({ by: me.id, text: reply, at: nowISO() }); if (reopen || x.status === "resolved") x.status = "open"; });
    setReply("");
  };
  return (
    <div className="space-y-5">
      <PageHeader title={t("Support", "मदत")} actions={<button className="btn-primary" onClick={() => setNewOpen(true)}><Plus className="size-4" />{t("New ticket", "नवीन तक्रार")}</button>} />
      <div className="grid gap-5 lg:grid-cols-[1fr_340px]">
        <Card title={t("My tickets", "माझ्या तक्रारी")} bodyClass="p-0">
          <ul className="divide-y divide-slate-100">{mine.map((x) => <li key={x.id}><button onClick={() => setView(x.id)} className="flex w-full items-center gap-3 p-4 text-left hover:bg-slate-50"><div className="flex-1"><p className="font-medium">{x.subject}</p><p className="text-xs text-slate-500">{x.code} · {CATS[x.category][lang === "mr" ? 1 : 0]} · {dt(x.createdAt)}</p></div><StatusBadge s={x.priority} /><StatusBadge s={x.status} /></button></li>)}</ul>
          {!mine.length && <p className="p-6 text-center text-sm text-slate-500">{t("No tickets", "तक्रारी नाहीत")}</p>}
        </Card>
        <div className="space-y-4">
          <Card title={t("Contact the office", "कार्यालयाशी संपर्क")}><p className="text-sm">{academy.phone}</p><a className="text-sm text-brand-700" href={`https://wa.me/${academy.whatsapp}`} target="_blank" rel="noopener noreferrer">WhatsApp</a></Card>
          <Card title="FAQ">
            <div className="space-y-2">{db.faqs.map((f) => <details key={f.id} className="rounded-md border border-slate-200 p-2 text-sm"><summary className="cursor-pointer font-medium">{l(f.q)}</summary><p className="mt-1 text-slate-600">{l(f.a)}</p></details>)}</div>
          </Card>
        </div>
      </div>
      <Modal open={newOpen} onClose={() => setNewOpen(false)} title={t("New support ticket", "नवीन तक्रार")} footer={<button className="btn-primary" onClick={create}>{t("Submit", "पाठवा")}</button>}>
        <div className="grid gap-3 sm:grid-cols-2">
          <Field label={t("Category", "प्रकार")}>{(id) => <select id={id} className="input" value={form.category} onChange={(e) => setForm({ ...form, category: e.target.value })}>{Object.entries(CATS).map(([k, v]) => <option key={k} value={k}>{v[lang === "mr" ? 1 : 0]}</option>)}</select>}</Field>
          <Field label={t("Priority", "प्राधान्य")}>{(id) => <select id={id} className="input" value={form.priority} onChange={(e) => setForm({ ...form, priority: e.target.value })}>{["low", "medium", "high", "urgent"].map((p) => <option key={p} value={p}>{statusLabel(p, lang)}</option>)}</select>}</Field>
          <Field label={t("Subject", "विषय")} className="sm:col-span-2">{(id) => <input id={id} className="input" value={form.subject} onChange={(e) => setForm({ ...form, subject: e.target.value })} />}</Field>
          <Field label={t("Describe the issue", "समस्या सांगा")} className="sm:col-span-2">{(id) => <textarea id={id} rows={4} className="input" value={form.text} onChange={(e) => setForm({ ...form, text: e.target.value })} />}</Field>
          <Field label={t("Attachment (optional)", "जोडणी (ऐच्छिक)")} className="sm:col-span-2">{(id) => <input id={id} type="file" className="input" />}</Field>
        </div>
      </Modal>
      {tk && (
        <Modal open onClose={() => setView(null)} title={`${tk.code} · ${tk.subject}`} size="lg" footer={tk.status === "closed" ? undefined : <>{tk.status === "resolved" && <button className="btn-outline" onClick={() => send(true)}><RotateCcw className="size-4" />{t("Reopen", "पुन्हा उघडा")}</button>}<button className="btn-primary" onClick={() => send()}><Send className="size-4" />{t("Send", "पाठवा")}</button></>}>
          <div className="mb-3 flex gap-2"><StatusBadge s={tk.status} /><StatusBadge s={tk.priority} /></div>
          <div className="space-y-2">{tk.messages.map((m, i) => { const mineMsg = m.by === me.id; return <div key={i} className={cn("max-w-[85%] rounded-lg p-3 text-sm", mineMsg ? "ml-auto bg-brand-50" : "bg-slate-100")}><p>{m.text}</p><p className="mt-1 text-[11px] text-slate-500">{mineMsg ? t("You", "आपण") : db.staff.find((s) => s.id === m.by)?.name ?? "Staff"} · {dt(m.at)}</p></div>; })}</div>
          {tk.status !== "closed" && <textarea className="input mt-3" rows={2} placeholder={t("Write a reply…", "उत्तर लिहा…")} value={reply} onChange={(e) => setReply(e.target.value)} aria-label="Reply" />}
        </Modal>
      )}
    </div>
  );
}

/* ---------------- ID card ---------------- */
export function IdCardFace({ studentId }: { studentId: string }) {
  const { t, l } = useI18n();
  const db = useStore((s) => s.db);
  const s = db.students.find((x) => x.id === studentId)!;
  const e = db.enrolments.filter((x) => x.studentId === s.id && x.enrolmentStatus === "active");
  const m = db.memberships.find((x) => x.studentId === s.id && x.status === "active");
  const validTill = e.map((x) => db.batches.find((b) => b.id === x.batchId)?.endDate ?? "").concat(m ? [m.end] : []).sort().pop();
  const url = typeof window !== "undefined" ? `${window.location.origin}/verify?code=${s.code}` : `/verify?code=${s.code}`;
  return (
    <div className="print-area mx-auto w-[340px] overflow-hidden rounded-xl border border-slate-200 bg-white shadow-md">
      <div className="flex items-center gap-2 bg-gradient-to-r from-brand-800 to-brand-600 px-4 py-3 text-white">
        <img src={academy.logoUrl} alt="" className="size-10 rounded-full bg-white" />
        <div className="leading-tight"><p className="text-sm font-bold">{academy.name.en}</p><p className="text-[11px] text-brand-100">{academy.name.mr} · {t("Student ID", "विद्यार्थी ओळखपत्र")}</p></div>
      </div>
      <div className="flex gap-3 p-4">
        <div className="flex h-24 w-20 shrink-0 items-center justify-center rounded-md border border-dashed border-slate-300 bg-slate-50 text-[10px] text-slate-400">{t("Photo", "फोटो")}</div>
        <div className="min-w-0 flex-1 text-xs">
          <p className="text-sm font-bold">{s.name}</p>
          <p className="font-mono text-brand-800">{s.code}</p>
          <p className="mt-1 text-slate-600">{e.map((x) => `${l(db.courses.find((c) => c.id === x.courseId)?.name)} · ${db.batches.find((b) => b.id === x.batchId)?.name}`).join("; ") || (m ? t("Abhyasika Kendra member", "अभ्यासिका केंद्र सदस्य") : "—")}</p>
          <p className="mt-1">{t("Valid till", "वैधता")}: <b>{validTill || "—"}</b></p>
          <p>{t("Status", "स्थिती")}: <b className={s.status === "active" ? "text-emerald-700" : "text-red-700"}>{s.status.toUpperCase()}</b></p>
        </div>
        <QRCodeSVG value={url} size={72} level="M" />
      </div>
      <p className="border-t px-4 py-1.5 text-center text-[10px] text-slate-400">{t("Scan QR to verify", "पडताळणीसाठी QR स्कॅन करा")} · {academy.phone}</p>
    </div>
  );
}
function IdCard() {
  const { t } = useI18n();
  const { me } = useMe();
  return (
    <div>
      <PageHeader title={t("Digital ID card", "डिजिटल ओळखपत्र")} actions={<button className="btn-primary" onClick={() => window.print()}><Printer className="size-4" />{t("Print / Save PDF", "प्रिंट / PDF")}</button>} />
      <IdCardFace studentId={me.id} />
    </div>
  );
}

/* ---------------- Profile ---------------- */
function Profile() {
  const { t } = useI18n();
  const { db, me } = useMe();
  const { update } = useStore();
  const [f, setF] = useState({ ...me });
  const save = () => { update((db) => { Object.assign(db.students.find((x) => x.id === me.id)!, { ...f, name: f.name.trim() || me.name }); }, { action: "profile.update", entity: me.code }); toast.success(t("Profile saved", "प्रोफाइल जतन केले")); };
  const exportData = () => {
    const data = { profile: me, enrolments: db.enrolments.filter((e) => e.studentId === me.id), payments: db.payments.filter((p) => p.studentId === me.id), attendance: db.attendance.filter((a) => a.studentId === me.id), attempts: db.attempts.filter((a) => a.studentId === me.id) };
    downloadFile(`${me.code}-my-data.json`, JSON.stringify(data, null, 2), "application/json");
  };
  const set = <K extends keyof typeof f>(k: K, v: (typeof f)[K]) => setF((p) => ({ ...p, [k]: v }));
  return (
    <div className="space-y-5">
      <PageHeader title={t("Profile", "प्रोफाइल")} actions={<button className="btn-primary" onClick={save}>{t("Save changes", "बदल जतन करा")}</button>} />
      <div className="grid gap-5 lg:grid-cols-2">
        <Card title={t("Personal details", "वैयक्तिक माहिती")}>
          <div className="grid gap-3 sm:grid-cols-2">
            <Field label={t("Full name", "पूर्ण नाव")} required>{(id) => <input id={id} className="input" value={f.name} onChange={(e) => set("name", e.target.value)} />}</Field>
            <Field label={t("Mobile", "मोबाईल")} required hint={t("Change via OTP verification", "OTP पडताळणीने बदला")}>{(id) => <input id={id} className="input" value={f.mobile} disabled />}</Field>
            <Field label={t("Email (optional)", "ईमेल (ऐच्छिक)")}>{(id) => <input id={id} className="input" value={f.email ?? ""} onChange={(e) => set("email", e.target.value)} />}</Field>
            <Field label={t("Date of birth (optional)", "जन्मतारीख (ऐच्छिक)")}>{(id) => <input id={id} type="date" className="input" value={f.dob ?? ""} onChange={(e) => set("dob", e.target.value)} />}</Field>
            <Field label={t("Gender (optional)", "लिंग (ऐच्छिक)")}>{(id) => <select id={id} className="input" value={f.gender ?? ""} onChange={(e) => set("gender", e.target.value as typeof f.gender)}><option value="">—</option><option value="female">{t("Female", "स्त्री")}</option><option value="male">{t("Male", "पुरुष")}</option><option value="other">{t("Other", "इतर")}</option></select>}</Field>
            <Field label={t("Profile photo (optional)", "फोटो (ऐच्छिक)")}>{(id) => <input id={id} type="file" accept="image/*" className="input" />}</Field>
            <Field label={t("Address (optional)", "पत्ता (ऐच्छिक)")} className="sm:col-span-2">{(id) => <textarea id={id} rows={2} className="input" value={f.address ?? ""} onChange={(e) => set("address", e.target.value)} />}</Field>
            <Field label={t("Parent mobile (optional)", "पालक मोबाईल (ऐच्छिक)")}>{(id) => <input id={id} className="input" inputMode="numeric" maxLength={10} value={f.parentMobile ?? ""} onChange={(e) => set("parentMobile", e.target.value.replace(/\D/g, ""))} />}</Field>
            <div className="flex items-end pb-2"><Toggle checked={f.parentAlerts} onChange={(v) => set("parentAlerts", v)} label={t("Send attendance alerts to parent", "पालकांना उपस्थिती सूचना")} /></div>
          </div>
          <p className="mt-3 text-xs text-slate-500">{t("Identity documents are not required. If a course needs a document, the office will request it separately.", "ओळख दस्तऐवज आवश्यक नाहीत. एखाद्या अभ्यासक्रमासाठी आवश्यक असल्यास कार्यालय स्वतंत्रपणे मागेल.")}</p>
        </Card>
        <div className="space-y-5">
          <Card title={t("Language & data", "भाषा व डेटा")}>
            <p className="label">{t("Study content language", "अभ्यास साहित्याची भाषा")}</p>
            <div className="flex gap-2">{(["mr", "en"] as Lang[]).map((x) => <button key={x} onClick={() => set("contentLang", x)} className={f.contentLang === x ? "btn-primary btn-sm" : "btn-outline btn-sm"}>{x === "mr" ? "मराठी" : "English"}</button>)}</div>
            <p className="mt-1 text-xs text-slate-500">{t("Independent of the website language (top bar).", "संकेतस्थळाच्या भाषेपेक्षा स्वतंत्र (वरची पट्टी).")}</p>
            <div className="mt-4"><Toggle checked={f.dataSaver} onChange={(v) => set("dataSaver", v)} label={t("Data-saver mode (low video quality, no previews)", "डेटा सेव्हर (कमी व्हिडिओ गुणवत्ता)")} /></div>
          </Card>
          <Card title={t("Communication consent", "संवाद संमती")}>
            <div className="space-y-2">
              {(["whatsapp", "sms", "email"] as const).map((k) => <div key={k}><Toggle checked={f.consent[k]} onChange={(v) => set("consent", { ...f.consent, [k]: v })} label={k === "whatsapp" ? "WhatsApp" : k.toUpperCase()} /></div>)}
              <div><Toggle checked={f.consent.publishResults} onChange={(v) => set("consent", { ...f.consent, publishResults: v })} label={t("Allow academy to publish my result/photo", "माझा निकाल/फोटो प्रकाशित करण्यास परवानगी")} /></div>
            </div>
            <div className="mt-4 flex flex-wrap items-center justify-between gap-2 rounded-lg bg-slate-50 p-3 text-sm">
              <span>Telegram {f.telegramLinked ? <Badge tone="green">{t("Joined", "सहभागी")}</Badge> : <Badge>{t("Not joined", "सहभागी नाही")}</Badge>}</span>
              <span className="flex flex-wrap gap-2">
                {telegramLinks(t).map((x) => <a key={x.href} href={x.href} target="_blank" rel="noopener noreferrer" className="btn-outline btn-sm"><Send className="size-4" aria-hidden />{x.join}</a>)}
                <button className="btn-outline btn-sm" onClick={() => set("telegramLinked", !f.telegramLinked)}>{f.telegramLinked ? t("Mark as not joined", "सहभागी नाही म्हणून नोंदवा") : t("I have joined", "मी सहभागी झालो")}</button>
              </span>
            </div>
          </Card>
          {db.settings.referral.enabled && (
            <Card title={t("Refer a friend", "मित्राला सुचवा")}>
              <p className="text-sm">{t("Your code", "आपला कोड")}: <code className="font-bold text-brand-800">{me.referralCode}</code></p>
              <a className="btn-outline btn-sm mt-2" target="_blank" rel="noopener noreferrer" href={`https://wa.me/?text=${encodeURIComponent(`Join Vidyadhan Academy with my code ${me.referralCode}`)}`}>WhatsApp</a>
            </Card>
          )}
          <Card title={t("Privacy", "गोपनीयता")}>
            <div className="flex flex-wrap gap-2">
              <button className="btn-outline btn-sm" onClick={exportData}><Download className="size-4" />{t("Export my data", "माझी माहिती डाउनलोड")}</button>
              <button className="btn-outline btn-sm text-red-600" onClick={() => toast.info(t("Deactivation request sent to the office (demo).", "खाते बंद करण्याची विनंती कार्यालयाला पाठवली (डेमो)."))}><AlertTriangle className="size-4" />{t("Request deactivation", "खाते बंद विनंती")}</button>
            </div>
          </Card>
        </div>
      </div>
    </div>
  );
}
