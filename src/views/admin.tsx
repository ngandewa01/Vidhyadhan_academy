"use client";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
import { AlertTriangle, ArrowRightLeft, Trash2, BookOpen, Check, ClipboardCheck, CreditCard, Download, FileUp, Inbox, Library, Pencil, Phone, Plus, UserPlus, Users, X, History as HistoryIcon } from "lucide-react";
import { useStore } from "@/lib/store";
import { useI18n } from "@/lib/i18n";
import { attendanceStats, balanceFor, batchImpact, chapterImpact, deleteBatch, removeChapters, courseImpact, createEnrolment, createStudent, deleteCourse, paidFor } from "@/lib/actions";
import { PERMISSION_PRESETS } from "@/lib/seed";
import { cn, downloadFile, fmtINR, fmtMobile, isValidMobile, normMobile, nowISO, parseCSV, toCSV, today, uid, dayOffset } from "@/lib/utils";
import { Badge, BarChart, Card, EmptyState, Field, Modal, Notice, PageHeader, SearchBox, Select, StatCard, StatusBadge, Table, Tabs, Toggle, toast, statusLabel } from "@/components/ui";
import { NoAccess, useAccess } from "@/components/PortalShell";
import { AssignmentsManager, AttendanceManager, LiveClassManager, MaterialsManager, QuestionBank, ResultsBoard, TestsManager, type Scope } from "./ops";
import { Achievements, Certificates, Cms, ExamUpdatesAdmin, Fees, LibraryAdmin, NotificationsAdmin, Receipts, Referrals, Reports, SettingsAdmin, Tickets } from "./admin2";
import { MODULES, type Access, type Batch, type Chapter, type Course, type EnquiryStatus, type L, type Module, type StaffUser, type Subject } from "@/lib/types";

export function useAdmin() {
  const db = useStore((s) => s.db);
  const session = useStore((s) => s.session);
  const me = db.staff.find((x) => x.id === session.userId)!;
  return { db, me, isOwner: me?.role === "owner" };
}
function useScope(module: Module): Scope {
  const { db, me } = useAdmin();
  const access = useAccess(module);
  return { batchIds: db.batches.map((b) => b.id), canManage: access === "manage", actorId: me.id, isAdmin: true };
}
function Gate({ module, children }: { module: Module; children: (canManage: boolean) => React.ReactNode }) {
  const a = useAccess(module);
  if (a === "none") return <NoAccess />;
  return <>{children(a === "manage")}</>;
}
function S({ module, render }: { module: Module; render: (s: Scope) => React.ReactNode }) {
  const s = useScope(module);
  return <Gate module={module}>{() => render(s)}</Gate>;
}

export function AdminRouter({ slug }: { slug: string[] }) {
  const { me } = useAdmin();
  if (!me) return null;
  switch (slug[0]) {
    case undefined: return <Dashboard />;
    case "enquiries": return <Gate module="enquiries">{(m) => <Enquiries canManage={m} />}</Gate>;
    case "students": return <Gate module="students">{(m) => <Students canManage={m} />}</Gate>;
    case "admissions": return <Gate module="admissions">{(m) => <Admissions canManage={m} />}</Gate>;
    case "courses": return <Gate module="courses">{(m) => <Courses canManage={m} />}</Gate>;
    case "syllabus": return <Gate module="courses">{(m) => <Syllabus canManage={m} />}</Gate>;
    case "batches": return <Gate module="batches">{(m) => <Batches canManage={m} />}</Gate>;
    case "branches": return <Gate module="batches">{(m) => <Branches canManage={m} />}</Gate>;
    case "classes": return <S module="classes" render={(s) => <LiveClassManager scope={s} />} />;
    case "materials": return <S module="materials" render={(s) => <MaterialsManager scope={s} canApprove={s.canManage} canPublishDirect />} />;
    case "questions": return <S module="questionBank" render={(s) => <QuestionBank canManage={s.canManage} />} />;
    case "exams": return <S module="exams" render={(s) => <TestsManager scope={s} />} />;
    case "results": return <S module="exams" render={(s) => <ResultsBoard scope={s} />} />;
    case "assignments": return <S module="assignments" render={(s) => <AssignmentsManager scope={s} />} />;
    case "attendance": return <S module="attendance" render={(s) => <AttendanceManager scope={s} />} />;
    case "fees": return <Gate module="fees">{(m) => <Fees canManage={m} />}</Gate>;
    case "receipts": return <Gate module="fees">{() => <Receipts />}</Gate>;
    case "library": return <Gate module="library">{(m) => <LibraryAdmin canManage={m} />}</Gate>;
    case "exam-updates": return <Gate module="examUpdates">{(m) => <ExamUpdatesAdmin canManage={m} />}</Gate>;
    case "notifications": return <Gate module="notifications">{(m) => <NotificationsAdmin canManage={m} />}</Gate>;
    case "tickets": return <Gate module="support">{(m) => <Tickets canManage={m} />}</Gate>;
    case "achievements": return <Gate module="results">{(m) => <Achievements canManage={m} />}</Gate>;
    case "referrals": return <Gate module="referrals">{(m) => <Referrals canManage={m} />}</Gate>;
    case "certificates": return <Gate module="certificates">{(m) => <Certificates canManage={m} />}</Gate>;
    case "reports": return <Gate module="reports">{() => <Reports />}</Gate>;
    case "staff": return <Gate module="staff">{(m) => <Staff canManage={m} />}</Gate>;
    case "permissions": return <Gate module="staff">{(m) => <PermissionsMatrix canManage={m} />}</Gate>;
    case "cms": return <Gate module="cms">{(m) => <Cms canManage={m} />}</Gate>;
    case "settings": return <Gate module="settings">{(m) => <SettingsAdmin canManage={m} />}</Gate>;
    case "audit": return <Gate module="audit">{() => <Audit />}</Gate>;
    default: return <EmptyState title="Not found" />;
  }
}

/* ---------------- Dashboard ---------------- */
function Dashboard() {
  const { t, l, d, dt } = useI18n();
  const { db, me } = useAdmin();
  const finance = useAccess("fees") !== "none";
  const active = db.students.filter((s) => s.status === "active").length;
  const month = today().slice(0, 7);
  const admissionsMonth = db.enrolments.filter((e) => e.createdAt.slice(0, 7) === month).length;
  const collected = db.payments.filter((p) => p.status === "success").reduce((a, p) => a + p.amount, 0);
  const outstanding = db.enrolments.reduce((a, e) => a + balanceFor(db, e), 0);
  const overdue = db.enrolments.reduce((a, e) => { const paid = paidFor(db, e.id); let cum = 0; let due = 0; e.installments.forEach((i) => { cum += i.amount; if (i.due < today() && paid < cum) due += Math.min(i.amount, cum - paid); }); return a + due; }, 0);
  const conv = db.enquiries.length ? (db.enquiries.filter((e) => e.status === "converted").length / db.enquiries.length) * 100 : 0;
  const libActive = db.memberships.filter((m) => m.status === "active").length;
  const months = Array.from({ length: 6 }, (_, i) => { const dd = new Date(); dd.setMonth(dd.getMonth() - 5 + i); return dd.toISOString().slice(0, 7); });
  const revenue = months.map((m) => ({ label: d(m + "-01", { month: "short" }), value: db.payments.filter((p) => p.status === "success" && p.date.slice(0, 7) === m).reduce((a, p) => a + p.amount, 0) }));
  const occupancy = db.batches.map((b) => ({ label: b.name.split(" ").slice(0, 2).join(" "), value: Math.round((db.enrolments.filter((e) => e.batchId === b.id && e.enrolmentStatus === "active").length / b.capacity) * 100) }));
  const attTrend = Array.from({ length: 10 }, (_, i) => dayOffset(-10 + i)).filter((x) => new Date(x).getDay() !== 0).map((x) => { const r = db.attendance.filter((a) => a.date === x); return { label: x.slice(8), value: r.length ? Math.round((r.filter((a) => a.status !== "A").length / r.length) * 100) : 0 }; });
  const shortage = db.students.filter((s) => { const st = attendanceStats(db, s.id); return st.total > 0 && st.percent < db.settings.attendance.minimumPercent; }).length;
  const todos = [
    { n: db.enquiries.filter((e) => e.followUpDate && e.followUpDate <= today() && !["converted", "notInterested"].includes(e.status)).length, en: "Enquiry follow-ups due", mr: "आजचे पाठपुरावे", href: "/admin/enquiries" },
    { n: db.materials.filter((m) => m.status === "pending").length, en: "Materials awaiting approval", mr: "मंजुरी बाकी साहित्य", href: "/admin/materials" },
    { n: db.updates.filter((u) => u.status === "pending").length, en: "Exam updates to review", mr: "पुनरावलोकन बाकी अपडेट्स", href: "/admin/exam-updates" },
    { n: db.transfers.filter((x) => x.status === "pending").length, en: "Batch transfer requests", mr: "बॅच बदल विनंत्या", href: "/admin/admissions" },
    { n: db.tickets.filter((x) => x.status === "new").length, en: "New support tickets", mr: "नवीन तक्रारी", href: "/admin/tickets" },
    { n: db.enrolments.filter((e) => e.admissionStatus === "pending").length, en: "Admissions pending payment", mr: "पेमेंट बाकी प्रवेश", href: "/admin/admissions" },
  ].filter((x) => x.n > 0);

  return (
    <div className="space-y-5">
      <PageHeader title={t("Dashboard", "डॅशबोर्ड")} subtitle={`${me.name} · ${me.role === "owner" ? t("Full access", "पूर्ण प्रवेश") : t("Permission-based access", "परवानगीनुसार प्रवेश")} · ${d(today(), { weekday: "long", day: "numeric", month: "long" })}`} />
      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        <StatCard label={t("Students (active / total)", "विद्यार्थी (सक्रिय / एकूण)")} value={`${active} / ${db.students.length}`} hint={`${db.students.length - active} ${t("inactive", "निष्क्रिय")}`} icon={<Users className="size-5" />} />
        <StatCard label={t("Admissions this month", "या महिन्यातील प्रवेश")} value={admissionsMonth} icon={<UserPlus className="size-5" />} tone="violet" />
        {finance && <StatCard label={t("Fees collected", "जमा शुल्क")} value={fmtINR(collected)} hint={`${t("Outstanding", "थकबाकी")} ${fmtINR(outstanding)}`} icon={<CreditCard className="size-5" />} tone="green" />}
        {finance && <StatCard label={t("Overdue", "मुदत उलटलेले")} value={fmtINR(overdue)} icon={<AlertTriangle className="size-5" />} tone="red" />}
        <StatCard label={t("Enquiry conversion", "चौकशी रूपांतरण")} value={`${conv.toFixed(0)}%`} hint={`${db.enquiries.length} ${t("enquiries", "चौकशी")}`} icon={<Inbox className="size-5" />} tone="amber" />
        <StatCard label={t("Attendance shortage", "उपस्थिती कमतरता")} value={shortage} icon={<ClipboardCheck className="size-5" />} tone="red" />
        <StatCard label={t("Abhyasika Kendra members", "अभ्यासिका केंद्र सदस्य")} value={libActive} hint={`${t("Occupancy", "वापर")} ${Math.round((libActive / 80) * 100)}%`} icon={<Library className="size-5" />} tone="violet" />
        <StatCard label={t("Courses / batches", "अभ्यासक्रम / बॅचेस")} value={`${db.courses.length} / ${db.batches.length}`} icon={<BookOpen className="size-5" />} />
      </div>
      <div className="grid gap-5 lg:grid-cols-3">
        <Card title={t("Action items", "करावयाची कामे")}>
          {todos.length ? <ul className="space-y-2">{todos.map((x) => <li key={x.en}><Link href={x.href} className="flex items-center justify-between rounded-lg bg-slate-50 px-3 py-2 text-sm hover:bg-slate-100"><span>{t(x.en, x.mr)}</span><Badge tone="orange">{x.n}</Badge></Link></li>)}</ul> : <p className="text-sm text-slate-500">{t("All clear!", "सर्व कामे पूर्ण!")}</p>}
        </Card>
        {finance && <Card title={t("Monthly revenue", "मासिक उत्पन्न")} className="lg:col-span-2"><BarChart data={revenue} format={fmtINR} /></Card>}
      </div>
      <div className="grid gap-5 lg:grid-cols-2">
        <Card title={t("Batch occupancy (%)", "बॅच भरणा (%)")}><BarChart data={occupancy} format={(n) => `${n}%`} /></Card>
        <Card title={t("Attendance trend (% present)", "उपस्थिती कल (%)")}><BarChart data={attTrend} format={(n) => `${n}%`} /></Card>
      </div>
      <Card title={t("Recent activity", "अलीकडील हालचाली")} actions={<Link href="/admin/audit" className="text-xs font-semibold text-brand-700">{t("Audit log", "ऑडिट नोंदी")}</Link>}>
        <ul className="divide-y divide-slate-100 text-sm">{db.audit.slice(0, 6).map((a) => <li key={a.id} className="flex flex-wrap justify-between gap-2 py-2"><span><b>{a.actor}</b> · <code className="text-xs">{a.action}</code> · {a.entity}</span><span className="text-xs text-slate-500">{dt(a.at)}</span></li>)}</ul>
      </Card>
      <p className="text-xs text-slate-400">{l({ en: "All figures are computed from demo data.", mr: "सर्व आकडे डेमो माहितीवरून काढले आहेत." })}</p>
    </div>
  );
}

/* ---------------- Enquiries ---------------- */
const ENQ_STATUS: EnquiryStatus[] = ["new", "contacted", "followup", "converted", "notInterested"];
function Enquiries({ canManage }: { canManage: boolean }) {
  const { t, l, d, dt, lang } = useI18n();
  const { db, me } = useAdmin();
  const { update } = useStore();
  const router = useRouter();
  const [status, setStatus] = useState<string>("all");
  const [q, setQ] = useState("");
  const [open, setOpen] = useState<string | null>(null);
  const [note, setNote] = useState("");
  const [add, setAdd] = useState(false);
  const [nf, setNf] = useState({ name: "", mobile: "", courseId: db.courses[0].id, source: "walk-in" });
  const dupCount = (m: string) => db.enquiries.filter((e) => normMobile(e.mobile) === normMobile(m)).length + db.students.filter((s) => normMobile(s.mobile) === normMobile(m)).length;
  const list = db.enquiries.filter((e) => (status === "all" || e.status === status) && (e.name + e.mobile).toLowerCase().includes(q.toLowerCase())).sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  const e = db.enquiries.find((x) => x.id === open);
  const staff = db.staff.filter((s) => s.role !== "faculty");
  const set = (patch: Partial<typeof e>, log?: string) => e && update((db) => { const x = db.enquiries.find((y) => y.id === e.id)!; Object.assign(x, patch); if (log) x.notes.push({ at: nowISO(), by: me.name, text: log }); }, log ? { action: "enquiry.update", entity: e.name, detail: log } : undefined);
  const convert = () => {
    if (!e) return;
    let sid = "";
    update((db) => { sid = createStudent(db, { name: e.name, mobile: e.mobile, email: e.email }).id; const x = db.enquiries.find((y) => y.id === e.id)!; x.status = "converted"; x.notes.push({ at: nowISO(), by: me.name, text: "Converted to student admission" }); }, { action: "enquiry.convert", entity: e.name });
    toast.success(t("Student account created. Complete the admission.", "विद्यार्थी खाते तयार. प्रवेश पूर्ण करा."));
    router.push(`/admin/admissions?student=${sid}&batch=${e.batchId ?? ""}&course=${e.courseId}`);
  };
  const exportCSV = () => downloadFile("enquiries.csv", toCSV(list.map((x) => ({ Date: x.createdAt.slice(0, 10), Name: x.name, Mobile: x.mobile, Course: l(db.courses.find((c) => c.id === x.courseId)?.name), Status: x.status, Source: x.source, AssignedTo: db.staff.find((s) => s.id === x.assignedTo)?.name ?? "", FollowUp: x.followUpDate ?? "" }))));
  const counts = Object.fromEntries(ENQ_STATUS.map((s) => [s, db.enquiries.filter((x) => x.status === s).length]));
  return (
    <div>
      <PageHeader title={t("Enquiries", "चौकशी")} actions={<><button className="btn-outline" onClick={exportCSV}><Download className="size-4" />CSV</button>{canManage && <button className="btn-primary" onClick={() => setAdd(true)}><Plus className="size-4" />{t("Add enquiry", "चौकशी जोडा")}</button>}</>} />
      <div className="mb-4 grid grid-cols-2 gap-3 sm:grid-cols-5">
        {ENQ_STATUS.map((s) => <button key={s} onClick={() => setStatus(status === s ? "all" : s)} className={cn("card p-3 text-left", status === s && "ring-2 ring-brand-500")}><p className="text-xs text-slate-500">{statusLabel(s, lang)}</p><p className="text-xl font-bold">{counts[s]}</p></button>)}
      </div>
      <div className="mb-3 flex flex-wrap gap-2"><SearchBox value={q} onChange={setQ} placeholder={t("Search name / mobile", "नाव / मोबाईल शोधा")} /><span className="self-center text-sm text-slate-500">{t("Conversion", "रूपांतरण")}: <b>{db.enquiries.length ? ((counts.converted / db.enquiries.length) * 100).toFixed(0) : 0}%</b></span></div>
      <Table head={[t("Name", "नाव"), t("Course", "अभ्यासक्रम"), t("Status", "स्थिती"), t("Assigned", "नियुक्त"), t("Follow-up", "पाठपुरावा"), t("Received", "प्राप्त")]} empty={!list.length}>
        {list.map((x) => (
          <tr key={x.id} className="cursor-pointer hover:bg-slate-50" onClick={() => { setOpen(x.id); setNote(""); }}>
            <td><p className="font-medium">{x.name} {dupCount(x.mobile) > 1 && <Badge tone="red">{t("Possible duplicate", "संभाव्य डुप्लिकेट")}</Badge>}</p><p className="text-xs text-slate-500">{fmtMobile(x.mobile)} · {x.type} · {x.source}</p></td>
            <td className="text-xs">{l(db.courses.find((c) => c.id === x.courseId)?.name)}</td>
            <td><StatusBadge s={x.status} /></td>
            <td className="text-xs">{db.staff.find((s) => s.id === x.assignedTo)?.name ?? "—"}</td>
            <td className={cn("text-xs", x.followUpDate && x.followUpDate <= today() && !["converted", "notInterested"].includes(x.status) && "font-semibold text-red-600")}>{d(x.followUpDate)}</td>
            <td className="text-xs">{dt(x.createdAt)}</td>
          </tr>
        ))}
      </Table>
      {e && (
        <Modal open onClose={() => setOpen(null)} title={e.name} size="lg" footer={canManage && e.status !== "converted" ? <button className="btn-accent" onClick={convert}><UserPlus className="size-4" />{t("Convert to admission", "प्रवेशात रूपांतर करा")}</button> : undefined}>
          {dupCount(e.mobile) > 1 && <div className="mb-3"><Notice tone="warn">{t("Another enquiry or student exists with this mobile number.", "या मोबाईल क्रमांकाने दुसरी चौकशी किंवा विद्यार्थी अस्तित्वात आहे.")} {db.students.find((s) => normMobile(s.mobile) === normMobile(e.mobile))?.code}</Notice></div>}
          <dl className="grid gap-2 text-sm sm:grid-cols-3">
            <div><dt className="text-slate-500">{t("Mobile", "मोबाईल")}</dt><dd><a className="text-brand-700" href={`tel:${e.mobile}`}><Phone className="mr-1 inline size-3" />{fmtMobile(e.mobile)}</a></dd></div>
            <div><dt className="text-slate-500">Email</dt><dd>{e.email ?? "—"}</dd></div>
            <div><dt className="text-slate-500">{t("Qualification", "पात्रता")}</dt><dd>{e.qualification ?? "—"}</dd></div>
            <div><dt className="text-slate-500">{t("Course", "अभ्यासक्रम")}</dt><dd>{l(db.courses.find((c) => c.id === e.courseId)?.name)}</dd></div>
            <div><dt className="text-slate-500">{t("Preferred batch", "पसंतीची बॅच")}</dt><dd>{db.batches.find((b) => b.id === e.batchId)?.name ?? "—"}</dd></div>
            <div><dt className="text-slate-500">{t("Source", "स्रोत")}</dt><dd>{e.source}</dd></div>
          </dl>
          {e.message && <p className="mt-2 rounded-md bg-slate-50 p-2 text-sm">“{e.message}”</p>}
          {canManage && (
            <div className="mt-4 grid gap-3 sm:grid-cols-3">
              <Field label={t("Status", "स्थिती")}>{(id) => <select id={id} className="input" value={e.status} onChange={(ev) => set({ status: ev.target.value as EnquiryStatus }, `Status → ${ev.target.value}`)}>{ENQ_STATUS.map((s) => <option key={s} value={s}>{statusLabel(s, lang)}</option>)}</select>}</Field>
              <Field label={t("Assign to", "नियुक्त करा")}>{(id) => <select id={id} className="input" value={e.assignedTo ?? ""} onChange={(ev) => set({ assignedTo: ev.target.value || undefined }, `Assigned to ${db.staff.find((s) => s.id === ev.target.value)?.name ?? "nobody"}`)}><option value="">—</option>{staff.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}</select>}</Field>
              <Field label={t("Follow-up date", "पाठपुरावा दिनांक")}>{(id) => <input id={id} type="date" className="input" value={e.followUpDate ?? ""} onChange={(ev) => set({ followUpDate: ev.target.value, status: e.status === "new" ? "followup" : e.status }, `Follow-up set for ${ev.target.value}`)} />}</Field>
            </div>
          )}
          <p className="label mt-4">{t("Notes & history", "नोंदी व इतिहास")}</p>
          <ul className="space-y-1.5 text-sm">{e.notes.map((n, i) => <li key={i} className="rounded-md bg-slate-50 px-2 py-1.5"><p>{n.text}</p><p className="text-[11px] text-slate-500">{n.by} · {dt(n.at)}</p></li>)}<li className="text-[11px] text-slate-400">{t("Created", "तयार")} {dt(e.createdAt)}</li></ul>
          {canManage && <div className="mt-2 flex gap-2"><input className="input" value={note} onChange={(ev) => setNote(ev.target.value)} placeholder={t("Add a note…", "नोंद लिहा…")} aria-label="Note" /><button className="btn-outline" onClick={() => { if (note.trim()) { set({}, note.trim()); setNote(""); } }}>{t("Add", "जोडा")}</button></div>}
        </Modal>
      )}
      <Modal open={add} onClose={() => setAdd(false)} title={t("Add enquiry (walk-in / phone)", "चौकशी जोडा (प्रत्यक्ष / फोन)")} footer={<button className="btn-primary" onClick={() => { if (nf.name.length < 2 || !isValidMobile(nf.mobile)) return toast.error(t("Name and valid mobile required", "नाव व वैध मोबाईल आवश्यक")); if (dupCount(nf.mobile)) toast.info(t("Possible duplicate detected", "संभाव्य डुप्लिकेट आढळले")); update((db) => db.enquiries.unshift({ id: uid("eq"), type: "quick", name: nf.name, mobile: normMobile(nf.mobile), courseId: nf.courseId, status: "new", notes: [], createdAt: nowISO(), source: nf.source as "walk-in", assignedTo: me.id }), { action: "enquiry.create", entity: nf.name }); setAdd(false); setNf({ ...nf, name: "", mobile: "" }); }}>{t("Save", "जतन")}</button>}>
        <div className="grid gap-3 sm:grid-cols-2">
          <Field label={t("Name", "नाव")} required>{(id) => <input id={id} className="input" value={nf.name} onChange={(ev) => setNf({ ...nf, name: ev.target.value })} />}</Field>
          <Field label={t("Mobile", "मोबाईल")} required>{(id) => <input id={id} className="input" maxLength={10} value={nf.mobile} onChange={(ev) => setNf({ ...nf, mobile: ev.target.value.replace(/\D/g, "") })} />}</Field>
          <Field label={t("Course", "अभ्यासक्रम")}>{(id) => <select id={id} className="input" value={nf.courseId} onChange={(ev) => setNf({ ...nf, courseId: ev.target.value })}>{db.courses.map((c) => <option key={c.id} value={c.id}>{l(c.name)}</option>)}</select>}</Field>
          <Field label={t("Source", "स्रोत")}>{(id) => <select id={id} className="input" value={nf.source} onChange={(ev) => setNf({ ...nf, source: ev.target.value })}>{["walk-in", "phone", "whatsapp", "referral", "website"].map((s) => <option key={s}>{s}</option>)}</select>}</Field>
        </div>
      </Modal>
    </div>
  );
}

/* ---------------- Students ---------------- */
function Students({ canManage }: { canManage: boolean }) {
  const { t, l, d, dt } = useI18n();
  const { db } = useAdmin();
  const { update } = useStore();
  const finance = useAccess("fees") !== "none";
  const [q, setQ] = useState("");
  const [course, setCourse] = useState("all");
  const [status, setStatus] = useState("all");
  const [open, setOpen] = useState<string | null>(null);
  const [add, setAdd] = useState(false);
  const [nf, setNf] = useState({ name: "", mobile: "", email: "", parentMobile: "" });
  const list = db.students.filter((s) => (s.name + s.mobile + s.code).toLowerCase().includes(q.toLowerCase()) && (status === "all" || s.status === status) && (course === "all" || (course === "library" ? db.memberships.some((m) => m.studentId === s.id) && !db.enrolments.some((e) => e.studentId === s.id) : db.enrolments.some((e) => e.studentId === s.id && e.courseId === course))));
  const s = db.students.find((x) => x.id === open);
  const save = () => {
    if (nf.name.trim().length < 2 || !isValidMobile(nf.mobile)) return toast.error(t("Name and valid mobile are required", "नाव व वैध मोबाईल आवश्यक"));
    if (db.students.some((x) => normMobile(x.mobile) === normMobile(nf.mobile))) return toast.error(t("A student with this mobile already exists", "या मोबाईलचा विद्यार्थी आधीच आहे"));
    update((db) => { const st = createStudent(db, { name: nf.name.trim(), mobile: nf.mobile, email: nf.email || undefined }); st.parentMobile = nf.parentMobile || undefined; }, { action: "student.create", entity: nf.name });
    setAdd(false); setNf({ name: "", mobile: "", email: "", parentMobile: "" });
    toast.success(t("Student account created. Login via OTP on registered mobile.", "विद्यार्थी खाते तयार. नोंदणीकृत मोबाईलवर OTP द्वारे लॉगिन."));
  };
  const exp = () => downloadFile("students.csv", toCSV(list.map((x) => ({ ID: x.code, Name: x.name, Mobile: x.mobile, Email: x.email ?? "", Status: x.status, Joined: x.createdAt, Courses: db.enrolments.filter((e) => e.studentId === x.id).map((e) => l(db.courses.find((c) => c.id === e.courseId)?.name)).join("; ") }))));
  return (
    <div>
      <PageHeader title={t("Students", "विद्यार्थी")} subtitle={`${db.students.length} ${t("registered", "नोंदणीकृत")}`} actions={<><button className="btn-outline" onClick={exp}><Download className="size-4" />CSV</button>{canManage && <button className="btn-primary" onClick={() => setAdd(true)}><Plus className="size-4" />{t("Add student", "विद्यार्थी जोडा")}</button>}</>} />
      <div className="mb-3 flex flex-wrap gap-2">
        <SearchBox value={q} onChange={setQ} placeholder={t("Search name / mobile / ID", "नाव / मोबाईल / ओळख शोधा")} />
        <Select label="Course" value={course} onChange={setCourse} options={[{ value: "all", label: t("All courses", "सर्व अभ्यासक्रम") }, ...db.courses.map((c) => ({ value: c.id, label: l(c.name) })), { value: "library", label: t("Abhyasika Kendra only", "फक्त अभ्यासिका केंद्र") }]} />
        <Select label="Status" value={status} onChange={setStatus} options={[{ value: "all", label: t("All statuses", "सर्व स्थिती") }, { value: "active", label: t("Active", "सक्रिय") }, { value: "inactive", label: t("Inactive", "निष्क्रिय") }]} />
      </div>
      <Table head={[t("Student", "विद्यार्थी"), t("Mobile", "मोबाईल"), t("Courses", "अभ्यासक्रम"), t("Attendance", "उपस्थिती"), finance ? t("Balance", "शिल्लक") : "", t("Status", "स्थिती")]} empty={!list.length}>
        {list.map((x) => {
          const enr = db.enrolments.filter((e) => e.studentId === x.id);
          const bal = enr.reduce((a, e) => a + balanceFor(db, e), 0);
          const st = attendanceStats(db, x.id);
          return (
            <tr key={x.id} className="cursor-pointer hover:bg-slate-50" onClick={() => setOpen(x.id)}>
              <td><p className="font-medium">{x.name}</p><p className="text-xs text-slate-500">{x.code}</p></td>
              <td className="text-xs">{fmtMobile(x.mobile)}</td>
              <td className="text-xs">{enr.map((e) => l(db.courses.find((c) => c.id === e.courseId)?.name)).join(", ") || (db.memberships.some((m) => m.studentId === x.id) ? <Badge tone="orange">{t("Abhyasika Kendra", "अभ्यासिका केंद्र")}</Badge> : "—")}</td>
              <td className={cn("text-xs", st.total && st.percent < db.settings.attendance.minimumPercent && "font-semibold text-red-600")}>{st.total ? `${st.percent.toFixed(0)}%` : "—"}</td>
              <td className="text-xs">{finance ? (bal ? <span className="font-semibold text-red-600">{fmtINR(bal)}</span> : "—") : ""}</td>
              <td><StatusBadge s={x.status} /></td>
            </tr>
          );
        })}
      </Table>
      {s && (
        <Modal open onClose={() => setOpen(null)} title={`${s.name} · ${s.code}`} size="xl" footer={canManage ? <><button className="btn-outline" onClick={() => update((db) => { const x = db.students.find((y) => y.id === s.id)!; x.status = x.status === "active" ? "inactive" : "active"; }, { action: "student.status", entity: s.code, detail: s.status === "active" ? "deactivated" : "activated" })}>{s.status === "active" ? t("Deactivate", "निष्क्रिय करा") : t("Activate", "सक्रिय करा")}</button><Link className="btn-primary" href={`/admin/admissions?student=${s.id}`}><UserPlus className="size-4" />{t("New enrolment", "नवीन प्रवेश")}</Link></> : undefined}>
          <div className="grid gap-4 md:grid-cols-3">
            <Card title={t("Profile", "प्रोफाइल")}>
              <dl className="space-y-1.5 text-sm">
                <div><dt className="text-xs text-slate-500">{t("Mobile", "मोबाईल")}</dt><dd>{fmtMobile(s.mobile)}</dd></div>
                <div><dt className="text-xs text-slate-500">Email</dt><dd>{s.email ?? "—"}</dd></div>
                <div><dt className="text-xs text-slate-500">{t("Parent mobile", "पालक मोबाईल")}</dt><dd>{s.parentMobile ? fmtMobile(s.parentMobile) : "—"} {s.parentAlerts && <Badge tone="blue">{t("alerts on", "सूचना चालू")}</Badge>}</dd></div>
                <div><dt className="text-xs text-slate-500">{t("Content language", "साहित्य भाषा")}</dt><dd>{s.contentLang === "mr" ? "मराठी" : "English"}</dd></div>
                <div><dt className="text-xs text-slate-500">{t("Joined", "नोंदणी")}</dt><dd>{d(s.createdAt)}</dd></div>
                <div><dt className="text-xs text-slate-500">{t("Consent", "संमती")}</dt><dd className="text-xs">WA {s.consent.whatsapp ? "✓" : "✗"} · SMS {s.consent.sms ? "✓" : "✗"} · {t("Publish result", "निकाल प्रकाशन")} {s.consent.publishResults ? "✓" : "✗"}</dd></div>
              </dl>
            </Card>
            <Card title={t("Enrolments", "प्रवेश")} className="md:col-span-2">
              {db.enrolments.filter((e) => e.studentId === s.id).map((e) => <div key={e.id} className="mb-3 rounded-lg border p-3 text-sm"><p className="font-semibold">{l(db.courses.find((c) => c.id === e.courseId)?.name)} · {db.batches.find((b) => b.id === e.batchId)?.name}</p><div className="mt-1 flex flex-wrap gap-1"><StatusBadge s={e.admissionStatus} /><StatusBadge s={e.paymentStatus} /><StatusBadge s={e.enrolmentStatus} /></div>{finance && <p className="mt-1 text-xs text-slate-600">{t("Fee", "शुल्क")} {fmtINR(e.finalFee)} · {t("Paid", "भरले")} {fmtINR(paidFor(db, e.id))} · {t("Balance", "शिल्लक")} {fmtINR(balanceFor(db, e))}</p>}<p className="text-xs text-slate-500">{t("Attendance", "उपस्थिती")} {attendanceStats(db, s.id, e.batchId).percent.toFixed(0)}%</p></div>)}
              {db.memberships.filter((m) => m.studentId === s.id).map((m) => <div key={m.id} className="mb-2 rounded-lg border border-saffron-200 bg-saffron-50 p-3 text-sm"><p className="font-semibold">{l(db.libraryPlans.find((p) => p.id === m.planId)?.name)}</p><p className="text-xs">{d(m.start)} – {d(m.end)} · {t("Seat", "आसन")} {m.seat} <StatusBadge s={m.status} /></p></div>)}
              {finance && <><p className="label mt-3">{t("Payments", "पेमेंट")}</p><ul className="text-xs">{db.payments.filter((p) => p.studentId === s.id).map((p) => <li key={p.id} className="flex justify-between border-t py-1"><span className="font-mono">{p.receiptNo}</span><span>{dt(p.date)}</span><span className="font-semibold">{fmtINR(p.amount)}</span></li>)}</ul></>}
            </Card>
          </div>
        </Modal>
      )}
      <Modal open={add} onClose={() => setAdd(false)} title={t("Create student account", "विद्यार्थी खाते तयार करा")} footer={<button className="btn-primary" onClick={save}>{t("Create", "तयार करा")}</button>}>
        <div className="grid gap-3 sm:grid-cols-2">
          <Field label={t("Full name", "पूर्ण नाव")} required>{(id) => <input id={id} className="input" value={nf.name} onChange={(e) => setNf({ ...nf, name: e.target.value })} />}</Field>
          <Field label={t("Mobile", "मोबाईल")} required>{(id) => <input id={id} className="input" maxLength={10} value={nf.mobile} onChange={(e) => setNf({ ...nf, mobile: e.target.value.replace(/\D/g, "") })} />}</Field>
          <Field label={t("Email (optional)", "ईमेल (ऐच्छिक)")}>{(id) => <input id={id} className="input" value={nf.email} onChange={(e) => setNf({ ...nf, email: e.target.value })} />}</Field>
          <Field label={t("Parent mobile (optional)", "पालक मोबाईल (ऐच्छिक)")}>{(id) => <input id={id} className="input" maxLength={10} value={nf.parentMobile} onChange={(e) => setNf({ ...nf, parentMobile: e.target.value.replace(/\D/g, "") })} />}</Field>
        </div>
        <p className="mt-3 text-xs text-slate-500">{t("Only name and mobile are required. No identity documents are collected by default.", "फक्त नाव व मोबाईल आवश्यक. पूर्वनिर्धारितपणे ओळख दस्तऐवज घेतले जात नाहीत.")}</p>
      </Modal>
    </div>
  );
}

/* ---------------- Admissions, enrolments & transfers ---------------- */
function Admissions({ canManage }: { canManage: boolean }) {
  const { t, l, d } = useI18n();
  const { db, me, isOwner } = useAdmin();
  const { update } = useStore();
  const finance = useAccess("fees") !== "none";
  const params = useSearchParams();
  const [tab, setTab] = useState<"list" | "transfers">("list");
  const [filter, setFilter] = useState("all");
  const [create, setCreate] = useState(false);
  const [f, setF] = useState({ studentId: "", batchId: db.batches[0].id, discount: 0, reason: "", installments: 2 });
  useEffect(() => {
    const sid = params.get("student");
    if (!sid) return;
    setF((x) => ({ ...x, studentId: sid, batchId: params.get("batch") || db.batches.find((b) => b.courseId === params.get("course"))?.id || x.batchId }));
    setCreate(true);
  }, [params]); // eslint-disable-line react-hooks/exhaustive-deps
  const [tr, setTr] = useState<{ enrolmentId: string; toBatchId: string; reason: string; feeAdjustment: number } | null>(null);
  const list = db.enrolments.filter((e) => filter === "all" || e.admissionStatus === filter || e.paymentStatus === filter || e.enrolmentStatus === filter).sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  const APPROVAL_LIMIT = 5000;
  const save = () => {
    if (!f.studentId || !f.batchId) return toast.error(t("Select student and batch", "विद्यार्थी व बॅच निवडा"));
    if (f.discount > 0 && !f.reason.trim()) return toast.error(t("Discount reason is required", "सवलतीचे कारण आवश्यक"));
    if (f.discount > APPROVAL_LIMIT && !isOwner) return toast.error(t(`Discounts above ${fmtINR(APPROVAL_LIMIT)} need owner approval`, `${fmtINR(APPROVAL_LIMIT)} पेक्षा जास्त सवलतीसाठी मालकांची मंजुरी आवश्यक`));
    update((db) => { createEnrolment(db, { studentId: f.studentId, batchId: f.batchId, discount: f.discount, discountReason: f.reason, installments: f.installments, by: me.name, approvedBy: f.discount ? me.id : undefined }); }, { action: "admission.create", entity: db.students.find((s) => s.id === f.studentId)?.code ?? "", detail: f.discount ? `Discount ₹${f.discount}: ${f.reason}` : "" });
    setCreate(false);
    toast.success(t("Admission created (pending payment). Record payment in Fees.", "प्रवेश तयार (पेमेंट बाकी). 'शुल्क' मध्ये पेमेंट नोंदवा."));
  };
  const setStatus = (id: string, k: "admissionStatus" | "enrolmentStatus", v: string) => update((db) => { const e = db.enrolments.find((x) => x.id === id)!; (e as unknown as Record<string, string>)[k] = v; e.history.push({ at: nowISO(), text: `${k} → ${v}`, by: me.name }); }, { action: "enrolment.status", entity: id, detail: `${k} → ${v}` });
  const requestTransfer = () => {
    if (!tr || !tr.reason.trim()) return toast.error(t("Reason is required", "कारण आवश्यक"));
    const e = db.enrolments.find((x) => x.id === tr.enrolmentId)!;
    const needs = db.settings.transferNeedsApproval && !isOwner;
    update((db) => {
      const req = { id: uid("tr"), enrolmentId: e.id, fromBatchId: e.batchId, toBatchId: tr.toBatchId, reason: tr.reason, feeAdjustment: tr.feeAdjustment, status: needs ? "pending" : "approved", requestedAt: nowISO() } as const;
      db.transfers.unshift({ ...req });
      if (!needs) applyTransfer(db, req.id, me.name);
    }, { action: "batch.transfer", entity: e.id, detail: `${tr.reason}${needs ? " (pending approval)" : ""}` });
    setTr(null);
    toast.success(needs ? t("Transfer request sent for approval", "बदल विनंती मंजुरीसाठी पाठवली") : t("Student transferred", "विद्यार्थ्याची बॅच बदलली"));
  };
  return (
    <div>
      <PageHeader title={t("Admissions & enrolments", "प्रवेश व नोंदणी")} actions={canManage && <button className="btn-primary" onClick={() => setCreate(true)}><Plus className="size-4" />{t("New admission", "नवीन प्रवेश")}</button>} />
      <Tabs tabs={[{ id: "list", label: t("Enrolments", "नोंदणी"), count: db.enrolments.length }, { id: "transfers", label: t("Batch transfers", "बॅच बदल"), count: db.transfers.filter((x) => x.status === "pending").length }]} value={tab} onChange={setTab} />
      {tab === "list" && (
        <>
          <div className="mb-3 flex flex-wrap gap-1.5">{["all", "pending", "confirmed", "cancelled", "partial", "paid", "active", "suspended", "completed"].map((s) => <button key={s} onClick={() => setFilter(s)} className={filter === s ? "btn-primary btn-sm" : "btn-outline btn-sm"}>{s === "all" ? t("All", "सर्व") : <StatusBadge s={s} />}</button>)}</div>
          <Table head={[t("Student", "विद्यार्थी"), t("Course / batch", "अभ्यासक्रम / बॅच"), t("Admission", "प्रवेश"), t("Payment", "पेमेंट"), t("Enrolment", "नोंदणी"), finance ? t("Fee", "शुल्क") : "", ""]} empty={!list.length}>
            {list.map((e) => {
              const s = db.students.find((x) => x.id === e.studentId)!;
              return (
                <tr key={e.id}>
                  <td><p className="font-medium">{s.name}</p><p className="text-xs text-slate-500">{s.code} · {d(e.createdAt)}</p></td>
                  <td className="text-xs">{l(db.courses.find((c) => c.id === e.courseId)?.name)}<br />{db.batches.find((b) => b.id === e.batchId)?.name}</td>
                  <td>{canManage ? <select className="input w-auto py-1 text-xs" value={e.admissionStatus} onChange={(ev) => setStatus(e.id, "admissionStatus", ev.target.value)} aria-label="Admission status">{["pending", "confirmed", "cancelled"].map((x) => <option key={x} value={x}>{x}</option>)}</select> : <StatusBadge s={e.admissionStatus} />}</td>
                  <td><StatusBadge s={e.paymentStatus} /></td>
                  <td>{canManage ? <select className="input w-auto py-1 text-xs" value={e.enrolmentStatus} onChange={(ev) => setStatus(e.id, "enrolmentStatus", ev.target.value)} aria-label="Enrolment status">{["active", "suspended", "completed"].map((x) => <option key={x} value={x}>{x}</option>)}</select> : <StatusBadge s={e.enrolmentStatus} />}</td>
                  <td className="text-xs">{finance && <>{fmtINR(e.finalFee)}{e.discount > 0 && <p className="text-emerald-700">−{fmtINR(e.discount)}</p>}</>}</td>
                  <td>{canManage && <button className="btn-ghost btn-sm" title={t("Transfer batch", "बॅच बदला")} onClick={() => setTr({ enrolmentId: e.id, toBatchId: db.batches.find((b) => b.courseId === e.courseId && b.id !== e.batchId)?.id ?? "", reason: "", feeAdjustment: 0 })}><ArrowRightLeft className="size-4" /></button>}</td>
                </tr>
              );
            })}
          </Table>
          <p className="mt-2 text-xs text-slate-500">{t("Payment, admission and enrolment statuses are tracked separately. Every change is kept in the enrolment history and audit log.", "पेमेंट, प्रवेश व नोंदणी स्थिती स्वतंत्रपणे नोंदल्या जातात. प्रत्येक बदल इतिहास व ऑडिट नोंदीत राहतो.")}</p>
        </>
      )}
      {tab === "transfers" && (
        <Table head={[t("Student", "विद्यार्थी"), t("From → To", "पासून → कडे"), t("Reason", "कारण"), t("Fee adj.", "शुल्क फरक"), t("Status", "स्थिती"), ""]} empty={!db.transfers.length}>
          {db.transfers.map((x) => { const e = db.enrolments.find((y) => y.id === x.enrolmentId); const s = db.students.find((y) => y.id === e?.studentId); return <tr key={x.id}><td>{s?.name}</td><td className="text-xs">{db.batches.find((b) => b.id === x.fromBatchId)?.name} → {db.batches.find((b) => b.id === x.toBatchId)?.name}</td><td className="text-xs">{x.reason}</td><td>{fmtINR(x.feeAdjustment)}</td><td><StatusBadge s={x.status} /></td><td>{x.status === "pending" && isOwner && <div className="flex gap-1"><button className="btn-ghost btn-sm text-emerald-700" onClick={() => update((db) => applyTransfer(db, x.id, me.name), { action: "transfer.approve", entity: x.id })}><Check className="size-4" /></button><button className="btn-ghost btn-sm text-red-600" onClick={() => update((db) => { db.transfers.find((y) => y.id === x.id)!.status = "rejected"; }, { action: "transfer.reject", entity: x.id })}><X className="size-4" /></button></div>}{x.status === "pending" && !isOwner && <span className="text-xs text-slate-500">{t("Awaiting owner", "मालकांची प्रतीक्षा")}</span>}</td></tr>; })}
        </Table>
      )}
      <Modal open={create} onClose={() => setCreate(false)} title={t("New admission", "नवीन प्रवेश")} size="lg" footer={<button className="btn-primary" onClick={save}>{t("Create admission", "प्रवेश तयार करा")}</button>}>
        <div className="grid gap-3 sm:grid-cols-2">
          <Field label={t("Student", "विद्यार्थी")} required hint={t("Create the student first if new", "नवीन असल्यास आधी विद्यार्थी तयार करा")}>{(id) => <select id={id} className="input" value={f.studentId} onChange={(e) => setF({ ...f, studentId: e.target.value })}><option value="">—</option>{db.students.map((s) => <option key={s.id} value={s.id}>{s.name} ({s.code})</option>)}</select>}</Field>
          <Field label={t("Batch", "बॅच")} required>{(id) => <select id={id} className="input" value={f.batchId} onChange={(e) => setF({ ...f, batchId: e.target.value })}>{db.batches.map((b) => <option key={b.id} value={b.id}>{b.name} – {fmtINR(b.fee)}</option>)}</select>}</Field>
          <Field label={t("Discount / negotiated reduction (₹)", "सवलत / वाटाघाटी कपात (₹)")} hint={t(`Above ${fmtINR(APPROVAL_LIMIT)} requires owner approval`, `${fmtINR(APPROVAL_LIMIT)} पेक्षा जास्त – मालक मंजुरी`)}>{(id) => <input id={id} type="number" className="input" value={f.discount} onChange={(e) => setF({ ...f, discount: Math.max(0, Number(e.target.value)) })} />}</Field>
          <Field label={t("Discount reason", "सवलतीचे कारण")}>{(id) => <select id={id} className="input" value={f.reason} onChange={(e) => setF({ ...f, reason: e.target.value })}><option value="">—</option>{["Scholarship – merit", "Concession – financial need", "Early-bird offer", "Referral discount", "Individually negotiated fee", "Sibling / alumni"].map((r) => <option key={r}>{r}</option>)}</select>}</Field>
          <Field label={t("Instalments", "हप्ते")}>{(id) => <select id={id} className="input" value={f.installments} onChange={(e) => setF({ ...f, installments: Number(e.target.value) })}>{[1, 2, 3, 4].map((n) => <option key={n} value={n}>{n}</option>)}</select>}</Field>
          <div className="rounded-lg bg-slate-50 p-3 text-sm"><p>{t("Final fee", "अंतिम शुल्क")}: <b>{fmtINR(Math.max(0, (db.batches.find((b) => b.id === f.batchId)?.fee ?? 0) - f.discount))}</b></p><p className="text-xs text-slate-500">{f.installments} × ~{fmtINR(Math.max(0, (db.batches.find((b) => b.id === f.batchId)?.fee ?? 0) - f.discount) / f.installments)}</p></div>
        </div>
        {f.studentId && db.enrolments.some((e) => e.studentId === f.studentId && e.batchId === f.batchId) && <div className="mt-3"><Notice tone="warn">{t("This student is already enrolled in this batch.", "हा विद्यार्थी या बॅचमध्ये आधीच आहे.")}</Notice></div>}
      </Modal>
      <Modal open={!!tr} onClose={() => setTr(null)} title={t("Batch transfer", "बॅच बदल")} footer={<button className="btn-primary" onClick={requestTransfer}>{db.settings.transferNeedsApproval && !isOwner ? t("Request approval", "मंजुरी मागा") : t("Transfer", "बदला")}</button>}>
        {tr && (
          <div className="grid gap-3">
            <Field label={t("Move to batch", "या बॅचमध्ये")}>{(id) => <select id={id} className="input" value={tr.toBatchId} onChange={(e) => setTr({ ...tr, toBatchId: e.target.value })}>{db.batches.filter((b) => b.id !== db.enrolments.find((x) => x.id === tr.enrolmentId)?.batchId).map((b) => <option key={b.id} value={b.id}>{b.name} – {fmtINR(b.fee)}</option>)}</select>}</Field>
            <Field label={t("Reason", "कारण")} required>{(id) => <textarea id={id} rows={2} className="input" value={tr.reason} onChange={(e) => setTr({ ...tr, reason: e.target.value })} />}</Field>
            <Field label={t("Fee adjustment (₹, + extra / − credit)", "शुल्क फरक (₹)")} hint={`${t("Fee difference", "शुल्क फरक")}: ${fmtINR((db.batches.find((b) => b.id === tr.toBatchId)?.fee ?? 0) - (db.batches.find((b) => b.id === db.enrolments.find((x) => x.id === tr.enrolmentId)?.batchId)?.fee ?? 0))}`}>{(id) => <input id={id} type="number" className="input" value={tr.feeAdjustment} onChange={(e) => setTr({ ...tr, feeAdjustment: Number(e.target.value) })} />}</Field>
            <p className="text-xs text-slate-500">{t("History, attendance and payments are preserved.", "इतिहास, उपस्थिती व पेमेंट जतन राहतात.")}</p>
          </div>
        )}
      </Modal>
    </div>
  );
}
function applyTransfer(db: ReturnType<typeof useStore.getState>["db"], id: string, by: string) {
  const x = db.transfers.find((y) => y.id === id)!;
  const e = db.enrolments.find((y) => y.id === x.enrolmentId)!;
  x.status = "approved";
  e.batchId = x.toBatchId;
  e.finalFee += x.feeAdjustment;
  if (x.feeAdjustment) e.installments[e.installments.length - 1].amount += x.feeAdjustment;
  e.history.push({ at: nowISO(), text: `Transferred ${x.fromBatchId} → ${x.toBatchId} (${x.reason}); fee adj ₹${x.feeAdjustment}`, by });
}

/* ---------------- Courses ---------------- */
function Courses({ canManage }: { canManage: boolean }) {
  const { t, l } = useI18n();
  const { db } = useAdmin();
  const { update } = useStore();
  const [edit, setEdit] = useState<Course | null>(null);
  const blank = (): Course => ({ id: "", slug: "", category: "other", name: { en: "", mr: "" }, short: { en: "", mr: "" }, description: { en: "", mr: "" }, durationMonths: 6, mediums: ["mr"], fee: 0, admissionOpen: false, published: false, color: "from-slate-700 to-slate-500", seo: { title: "", description: "" }, documentsRequired: [], syllabusVersion: 1 });
  const save = () => {
    if (!edit || !edit.name.en || !edit.name.mr) return toast.error(t("Course name in both languages is required", "दोन्ही भाषांत नाव आवश्यक"));
    const slug = edit.slug || edit.name.en.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/(^-|-$)/g, "");
    update((db) => { if (edit.id) Object.assign(db.courses.find((c) => c.id === edit.id)!, { ...edit, slug }); else db.courses.push({ ...edit, slug, id: uid("c") }); }, { action: edit.id ? "course.update" : "course.create", entity: edit.name.en });
    setEdit(null);
  };
  const removeCourse = (c: Course) => {
    const i = courseImpact(db, c.id);
    const lines = [`${i.batches} batches`, `${i.enrolments} enrolments (${i.students} students)`, `${i.payments} payments`, `${i.enquiries} enquiries`, `${i.attempts} test attempts`, "syllabus and content"];
    const bundleNote = i.bundlePlans ? ` ${i.bundlePlans} library bundle plan(s) will be deactivated.` : "";
    if (!confirm(t(`Delete "${l(c.name)}" permanently? This removes: ${lines.join(", ")}.${bundleNote} Student accounts stay. This cannot be undone.`, `"${l(c.name)}" कायमचा हटवायचा? यामुळे हटेल: ${lines.join(", ")}.${bundleNote} विद्यार्थी खाती कायम राहतील. हे परत करता येणार नाही.`))) return;
    update((db) => deleteCourse(db, c.id), { action: "course.delete", entity: c.name.en });
    toast.success(t("Course deleted", "अभ्यासक्रम हटवला"));
  };
  return (
    <div>
      <PageHeader title={t("Courses", "अभ्यासक्रम")} actions={canManage && <button className="btn-primary" onClick={() => setEdit(blank())}><Plus className="size-4" />{t("Add course", "अभ्यासक्रम जोडा")}</button>} />
      <Table head={[t("Course", "अभ्यासक्रम"), t("Category", "प्रकार"), t("Duration", "कालावधी"), t("Fee", "शुल्क"), t("Batches", "बॅचेस"), t("Admissions", "प्रवेश"), t("Website", "संकेतस्थळ"), ""]}>
        {db.courses.map((c) => <tr key={c.id}><td><p className="font-medium">{l(c.name)}</p><p className="text-xs text-slate-500">/{c.slug} · v{c.syllabusVersion}</p></td><td className="text-xs">{c.category}</td><td>{c.durationMonths} m</td><td>{fmtINR(c.fee)}</td><td>{db.batches.filter((b) => b.courseId === c.id).length}</td><td><Toggle disabled={!canManage} checked={c.admissionOpen} onChange={(v) => update((db) => { db.courses.find((x) => x.id === c.id)!.admissionOpen = v; }, { action: "course.admission", entity: c.name.en, detail: v ? "opened" : "closed" })} /></td><td><Toggle disabled={!canManage} checked={c.published} onChange={(v) => update((db) => { db.courses.find((x) => x.id === c.id)!.published = v; }, { action: "course.publish", entity: c.name.en, detail: String(v) })} /></td><td>{canManage && <div className="flex gap-1"><button className="btn-ghost btn-sm" onClick={() => setEdit(structuredClone(c))} aria-label="Edit"><Pencil className="size-4" /></button><button className="btn-ghost btn-sm text-red-600" onClick={() => removeCourse(c)} aria-label={t("Delete", "हटवा")}><Trash2 className="size-4" /></button></div>}</td></tr>)}
      </Table>
      <Modal open={!!edit} onClose={() => setEdit(null)} title={edit?.id ? t("Edit course", "अभ्यासक्रम संपादित") : t("Add course", "अभ्यासक्रम जोडा")} size="xl" footer={<button className="btn-primary" onClick={save}>{t("Save", "जतन")}</button>}>
        {edit && (
          <div className="grid gap-3 sm:grid-cols-2">
            <Field label="Name (English)" required>{(id) => <input id={id} className="input" value={edit.name.en} onChange={(e) => setEdit({ ...edit, name: { ...edit.name, en: e.target.value } })} />}</Field>
            <Field label="नाव (मराठी)" required>{(id) => <input id={id} className="input" value={edit.name.mr} onChange={(e) => setEdit({ ...edit, name: { ...edit.name, mr: e.target.value } })} />}</Field>
            <Field label="Short description (English)">{(id) => <input id={id} className="input" value={edit.short.en} onChange={(e) => setEdit({ ...edit, short: { ...edit.short, en: e.target.value } })} />}</Field>
            <Field label="थोडक्यात वर्णन (मराठी)">{(id) => <input id={id} className="input" value={edit.short.mr} onChange={(e) => setEdit({ ...edit, short: { ...edit.short, mr: e.target.value } })} />}</Field>
            <Field label="Description (English)">{(id) => <textarea id={id} rows={3} className="input" value={edit.description.en} onChange={(e) => setEdit({ ...edit, description: { ...edit.description, en: e.target.value } })} />}</Field>
            <Field label="वर्णन (मराठी)">{(id) => <textarea id={id} rows={3} className="input" value={edit.description.mr} onChange={(e) => setEdit({ ...edit, description: { ...edit.description, mr: e.target.value } })} />}</Field>
            <Field label={t("Category", "प्रकार")}>{(id) => <select id={id} className="input" value={edit.category} onChange={(e) => setEdit({ ...edit, category: e.target.value as Course["category"] })}>{["state", "combined", "police", "revenue", "other"].map((c) => <option key={c}>{c}</option>)}</select>}</Field>
            <div className="grid grid-cols-2 gap-2"><Field label={t("Duration (months)", "कालावधी (महिने)")}>{(id) => <input id={id} type="number" className="input" value={edit.durationMonths} onChange={(e) => setEdit({ ...edit, durationMonths: Number(e.target.value) })} />}</Field><Field label={t("Base fee (₹)", "मूळ शुल्क (₹)")}>{(id) => <input id={id} type="number" className="input" value={edit.fee} onChange={(e) => setEdit({ ...edit, fee: Number(e.target.value) })} />}</Field></div>
            <div className="sm:col-span-2"><Toggle checked={edit.showFee !== false} onChange={(v) => setEdit({ ...edit, showFee: v })} label={t("Show fee on website", "वेबसाइटवर शुल्क दाखवा")} /></div>
            <div><p className="label">{t("Medium", "माध्यम")}</p><div className="flex gap-3 text-sm">{(["mr", "en"] as const).map((m) => <label key={m} className="flex items-center gap-1"><input type="checkbox" checked={edit.mediums.includes(m)} onChange={(e) => setEdit({ ...edit, mediums: e.target.checked ? [...edit.mediums, m] : edit.mediums.filter((x) => x !== m) })} />{m === "mr" ? "मराठी" : "English"}</label>)}</div></div>
            <Field label={t("Documents required (comma separated, optional)", "आवश्यक कागदपत्रे (ऐच्छिक)")}>{(id) => <input id={id} className="input" value={edit.documentsRequired.join(", ")} onChange={(e) => setEdit({ ...edit, documentsRequired: e.target.value.split(",").map((x) => x.trim()).filter(Boolean) })} />}</Field>
            <Field label={t("Course image", "अभ्यासक्रम प्रतिमा")}>{(id) => <input id={id} type="file" accept="image/*" className="input" />}</Field>
            <Field label="URL slug">{(id) => <input id={id} className="input" value={edit.slug} placeholder="auto" onChange={(e) => setEdit({ ...edit, slug: e.target.value })} />}</Field>
            <Field label="SEO title" hint={`${edit.seo.title.length}/60`}>{(id) => <input id={id} className="input" value={edit.seo.title} onChange={(e) => setEdit({ ...edit, seo: { ...edit.seo, title: e.target.value } })} />}</Field>
            <Field label="SEO description" hint={`${edit.seo.description.length}/160`}>{(id) => <input id={id} className="input" value={edit.seo.description} onChange={(e) => setEdit({ ...edit, seo: { ...edit.seo, description: e.target.value } })} />}</Field>
          </div>
        )}
      </Modal>
    </div>
  );
}

/* ---------------- Syllabus ---------------- */
function Syllabus({ canManage }: { canManage: boolean }) {
  const { t, l, dt } = useI18n();
  const { db, me } = useAdmin();
  const { update } = useStore();
  const [cid, setCid] = useState(db.courses[0].id);
  const [errors, setErrors] = useState<{ row: number; msg: string }[] | null>(null);
  const course = db.courses.find((c) => c.id === cid)!;
  const subjects = db.subjects.filter((s) => s.courseId === cid);
  const ask = (label: string, current?: L) => { const en = prompt(`${label} (English)`, current?.en); if (!en) return null; const mr = prompt(`${label} (मराठी)`, current?.mr) || en; return { en, mr }; };
  const renameSubject = (s: Subject) => { const n = ask("Subject name", s.name); if (n) update((db) => { db.subjects.find((x) => x.id === s.id)!.name = n; bump(db, `Renamed subject to ${n.en}`); }, { action: "syllabus.edit", entity: course.name.en }); };
  const removeSubject = (s: Subject) => {
    const ids = new Set(s.chapters.map((c) => c.id));
    const i = chapterImpact(db, ids);
    if (!confirm(t(`Delete subject "${l(s.name)}" with ${ids.size} chapters, ${i.questions} questions, ${i.materials} materials and ${i.recordings} recordings? This cannot be undone.`, `"${l(s.name)}" विषय हटवायचा? यात ${ids.size} प्रकरणे, ${i.questions} प्रश्न, ${i.materials} साहित्य व ${i.recordings} व्याख्याने हटतील. हे परत करता येणार नाही.`))) return;
    update((db) => { removeChapters(db, s.id, ids); db.subjects = db.subjects.filter((x) => x.id !== s.id); bump(db, `Deleted subject ${s.name.en}`); }, { action: "syllabus.delete", entity: s.name.en });
  };
  const renameChapter = (sid: string, c: Chapter) => { const n = ask("Chapter name", c.name); if (n) update((db) => { db.subjects.find((s) => s.id === sid)!.chapters.find((x) => x.id === c.id)!.name = n; bump(db, `Renamed chapter to ${n.en}`); }); };
  const removeChapter = (sid: string, c: Chapter) => {
    const i = chapterImpact(db, new Set([c.id]));
    if (!confirm(t(`Delete chapter "${l(c.name)}" with ${i.questions} questions, ${i.materials} materials and ${i.recordings} recordings? This cannot be undone.`, `"${l(c.name)}" प्रकरण हटवायचे? यात ${i.questions} प्रश्न, ${i.materials} साहित्य व ${i.recordings} व्याख्याने हटतील. हे परत करता येणार नाही.`))) return;
    update((db) => { removeChapters(db, sid, new Set([c.id])); bump(db, `Deleted chapter ${c.name.en}`); });
  };
  const renameTopic = (sid: string, cid: string, ti: number, topic: L) => { const n = ask("Topic", topic); if (n) update((db) => { db.subjects.find((s) => s.id === sid)!.chapters.find((c) => c.id === cid)!.topics[ti] = n; bump(db, `Renamed topic to ${n.en}`); }); };
  const removeTopic = (sid: string, cid: string, ti: number, topic: L) => { if (confirm(t(`Delete topic "${l(topic)}"?`, `"${l(topic)}" उपविषय हटवायचा?`))) update((db) => { db.subjects.find((s) => s.id === sid)!.chapters.find((c) => c.id === cid)!.topics.splice(ti, 1); bump(db, `Deleted topic ${topic.en}`); }); };
  const bump = (db: ReturnType<typeof useStore.getState>["db"], note: string) => { const c = db.courses.find((x) => x.id === cid)!; c.syllabusVersion++; db.syllabusHistory.unshift({ courseId: cid, version: c.syllabusVersion, at: nowISO(), by: me.name, note }); };
  const addSubject = () => { const n = ask("Subject name"); if (n) update((db) => { db.subjects.push({ id: uid("s"), courseId: cid, name: n, chapters: [] }); bump(db, `Added subject ${n.en}`); }, { action: "syllabus.edit", entity: course.name.en }); };
  const addChapter = (sid: string) => { const n = ask("Chapter name"); if (n) update((db) => { db.subjects.find((s) => s.id === sid)!.chapters.push({ id: uid("ch"), name: n, topics: [] }); bump(db, `Added chapter ${n.en}`); }); };
  const addTopic = (sid: string, chid: string) => { const n = ask("Topic"); if (n) update((db) => { db.subjects.find((s) => s.id === sid)!.chapters.find((c) => c.id === chid)!.topics.push(n); bump(db, `Added topic ${n.en}`); }); };
  const exportCSV = () => {
    const rows: Record<string, string>[] = [];
    subjects.forEach((s) => s.chapters.forEach((c) => (c.topics.length ? c.topics : [{ en: "", mr: "" }]).forEach((tp) => rows.push({ subject_en: s.name.en, subject_mr: s.name.mr, chapter_en: c.name.en, chapter_mr: c.name.mr, topic_en: tp.en, topic_mr: tp.mr }))));
    downloadFile(`syllabus-${course.slug}-v${course.syllabusVersion}.csv`, toCSV(rows));
  };
  const importCSV = async (file: File) => {
    const [head, ...rows] = parseCSV(await file.text());
    const need = ["subject_en", "subject_mr", "chapter_en", "chapter_mr", "topic_en", "topic_mr"];
    const miss = need.filter((h) => !head.includes(h));
    if (miss.length) return setErrors([{ row: 1, msg: `Missing columns: ${miss.join(", ")}` }]);
    const errs: { row: number; msg: string }[] = [];
    const g = (r: string[], k: string) => (r[head.indexOf(k)] ?? "").trim();
    rows.forEach((r, i) => { if (!g(r, "subject_en")) errs.push({ row: i + 2, msg: "subject_en is empty" }); if (!g(r, "chapter_en")) errs.push({ row: i + 2, msg: "chapter_en is empty" }); });
    if (errs.length) return setErrors(errs);
    update((db) => {
      rows.forEach((r) => {
        let s = db.subjects.find((x) => x.courseId === cid && x.name.en === g(r, "subject_en"));
        if (!s) { s = { id: uid("s"), courseId: cid, name: { en: g(r, "subject_en"), mr: g(r, "subject_mr") || g(r, "subject_en") }, chapters: [] }; db.subjects.push(s); }
        let c = s.chapters.find((x) => x.name.en === g(r, "chapter_en"));
        if (!c) { c = { id: uid("ch"), name: { en: g(r, "chapter_en"), mr: g(r, "chapter_mr") || g(r, "chapter_en") }, topics: [] }; s.chapters.push(c); }
        if (g(r, "topic_en") && !c.topics.some((x) => x.en === g(r, "topic_en"))) c.topics.push({ en: g(r, "topic_en"), mr: g(r, "topic_mr") || g(r, "topic_en") });
      });
      bump(db, `Imported ${rows.length} rows from ${file.name}`);
    }, { action: "syllabus.import", entity: course.name.en, detail: `${rows.length} rows` });
    setErrors([]);
    toast.success(t("Syllabus imported – new version saved", "अभ्यासक्रम आयात – नवीन आवृत्ती जतन"));
  };
  return (
    <div>
      <PageHeader title={t("Subjects & syllabus", "विषय व अभ्यासक्रम")} subtitle={t("Course → Subject → Chapter → Topic", "अभ्यासक्रम → विषय → प्रकरण → उपविषय")} actions={<><button className="btn-outline" onClick={exportCSV}><Download className="size-4" />{t("Export", "निर्यात")}</button>{canManage && <><label className="btn-outline cursor-pointer"><FileUp className="size-4" />{t("Import CSV", "CSV आयात")}<input type="file" accept=".csv" className="sr-only" onChange={(e) => e.target.files?.[0] && importCSV(e.target.files[0])} /></label><button className="btn-primary" onClick={addSubject}><Plus className="size-4" />{t("Subject", "विषय")}</button></>}</>} />
      <div className="mb-4 flex flex-wrap items-center gap-2"><Select label="Course" value={cid} onChange={setCid} options={db.courses.map((c) => ({ value: c.id, label: l(c.name) }))} /><Badge tone="blue">{t("Version", "आवृत्ती")} {course.syllabusVersion}</Badge></div>
      {errors && errors.length > 0 && <div className="mb-4"><Notice tone="danger"><p className="font-semibold">{t("Import rejected – fix these rows:", "आयात नाकारली – या ओळी दुरुस्त करा:")}</p><ul className="list-disc pl-5">{errors.map((e, i) => <li key={i}>Row {e.row}: {e.msg}</li>)}</ul></Notice></div>}
      <div className="grid gap-5 lg:grid-cols-[1fr_300px]">
        <div className="space-y-3">
          {subjects.map((s) => (
            <Card key={s.id} title={l(s.name)} actions={canManage && <div className="flex items-center gap-1"><button className="btn-ghost btn-sm" onClick={() => addChapter(s.id)}><Plus className="size-4" />{t("Chapter", "प्रकरण")}</button><button className="btn-ghost btn-sm" onClick={() => renameSubject(s)} aria-label={t("Edit subject", "विषय संपादित")}><Pencil className="size-4" /></button><button className="btn-ghost btn-sm text-red-600" onClick={() => removeSubject(s)} aria-label={t("Delete subject", "विषय हटवा")}><Trash2 className="size-4" /></button></div>}>
              <ol className="space-y-2 text-sm">{s.chapters.map((c) => <li key={c.id}><div className="flex items-center justify-between gap-2"><span className="font-medium">{l(c.name)}</span>{canManage && <span className="flex items-center gap-1"><button className="btn-ghost btn-sm" onClick={() => addTopic(s.id, c.id)}>+ {t("topic", "उपविषय")}</button><button className="btn-ghost btn-sm" onClick={() => renameChapter(s.id, c)} aria-label={t("Edit chapter", "प्रकरण संपादित")}><Pencil className="size-3.5" /></button><button className="btn-ghost btn-sm text-red-600" onClick={() => removeChapter(s.id, c)} aria-label={t("Delete chapter", "प्रकरण हटवा")}><Trash2 className="size-3.5" /></button></span>}</div><ul className="mt-1 space-y-0.5 text-xs text-slate-500">{c.topics.map((x, ti) => <li key={ti} className="flex items-center justify-between gap-2"><span>{l(x)}</span>{canManage && <span className="flex items-center gap-0.5"><button className="btn-ghost btn-sm" onClick={() => renameTopic(s.id, c.id, ti, x)} aria-label={t("Edit topic", "उपविषय संपादित")}><Pencil className="size-3" /></button><button className="btn-ghost btn-sm text-red-600" onClick={() => removeTopic(s.id, c.id, ti, x)} aria-label={t("Delete topic", "उपविषय हटवा")}><Trash2 className="size-3" /></button></span>}</li>)}{!c.topics.length && <li>—</li>}</ul><p className="text-[11px] text-slate-400">{db.questions.filter((q) => q.chapterId === c.id).length} {t("questions", "प्रश्न")} · {db.materials.filter((m) => m.chapterId === c.id).length} {t("materials", "साहित्य")}</p></li>)}</ol>
              {!s.chapters.length && <p className="text-sm text-slate-400">{t("No chapters yet", "प्रकरणे नाहीत")}</p>}
            </Card>
          ))}
          {!subjects.length && <EmptyState title={t("No subjects yet", "विषय नाहीत")} />}
        </div>
        <Card title={<span className="flex items-center gap-1"><HistoryIcon className="size-4" />{t("Version history", "आवृत्ती इतिहास")}</span>}>
          <ul className="space-y-2 text-xs">{db.syllabusHistory.filter((h) => h.courseId === cid).map((h, i) => <li key={i}><b>v{h.version}</b> · {h.note}<br /><span className="text-slate-500">{h.by} · {dt(h.at)}</span></li>)}<li className="text-slate-400">v1 · {t("initial", "प्रारंभिक")}</li></ul>
          <p className="mt-3 text-[11px] text-slate-500">{t("Earlier versions stay linked to tests and materials created against them.", "जुन्या आवृत्त्या त्यावर आधारित चाचण्या व साहित्याशी जोडलेल्या राहतात.")}</p>
        </Card>
      </div>
    </div>
  );
}

/* ---------------- Batches ---------------- */
function Batches({ canManage }: { canManage: boolean }) {
  const { t, l, d } = useI18n();
  const { db } = useAdmin();
  const { update } = useStore();
  const [edit, setEdit] = useState<Batch | null>(null);
  const removeBatch = (b: Batch) => {
    const i = batchImpact(db, b.id);
    if (!confirm(t(`Delete batch "${b.name}" permanently? This removes ${i.enrolments} enrolments (${i.students} students), ${i.payments} payments, and its attendance, classes and materials. Student accounts stay. This cannot be undone.`, `"${b.name}" बॅच कायमचा हटवायचा? यामुळे ${i.enrolments} प्रवेश (${i.students} विद्यार्थी), ${i.payments} पेमेंट, आणि त्याची उपस्थिती, वर्ग व साहित्य हटतील. विद्यार्थी खाती कायम राहतील. हे परत करता येणार नाही.`))) return;
    update((db) => deleteBatch(db, b.id), { action: "batch.delete", entity: b.name });
    toast.success(t("Batch deleted", "बॅच हटवला"));
  };
  const blank = (): Batch => ({ id: "", courseId: db.courses[0].id, branchId: "br_main", name: "", mode: "offline", startDate: today(), endDate: dayOffset(180), days: "Mon–Sat", time: "08:00 – 10:00", capacity: 40, facultyIds: [], classroomId: db.classrooms[0]?.id, fee: db.courses[0].fee, admissionOpen: true, admissionCloses: dayOffset(15) });
  const save = () => {
    if (!edit?.name) return toast.error(t("Batch name required", "बॅच नाव आवश्यक"));
    update((db) => {
      if (edit.id) Object.assign(db.batches.find((b) => b.id === edit.id)!, edit); else { const id = uid("b"); db.batches.push({ ...edit, id }); edit.facultyIds.forEach((f) => db.staff.find((s) => s.id === f)?.batchIds.push(id)); }
      if (edit.id) db.staff.forEach((s) => { if (s.role !== "faculty") return; const has = s.batchIds.includes(edit.id); const should = edit.facultyIds.includes(s.id); if (should && !has) s.batchIds.push(edit.id); if (!should && has) s.batchIds = s.batchIds.filter((x) => x !== edit.id); });
    }, { action: edit.id ? "batch.update" : "batch.create", entity: edit.name });
    setEdit(null);
  };
  return (
    <div>
      <PageHeader title={t("Batches", "बॅचेस")} actions={canManage && <button className="btn-primary" onClick={() => setEdit(blank())}><Plus className="size-4" />{t("Add batch", "बॅच जोडा")}</button>} />
      <Table head={[t("Batch", "बॅच"), t("Mode", "पद्धत"), t("Schedule", "वेळापत्रक"), t("Faculty", "प्राध्यापक"), t("Occupancy", "भरणा"), t("Fee", "शुल्क"), t("Admission", "प्रवेश"), ""]}>
        {db.batches.map((b) => { const n = db.enrolments.filter((e) => e.batchId === b.id && e.enrolmentStatus === "active").length; return <tr key={b.id}><td><p className="font-medium">{b.name}</p><p className="text-xs text-slate-500">{l(db.courses.find((c) => c.id === b.courseId)?.name)} · {db.classrooms.find((c) => c.id === b.classroomId)?.name ?? "Online"}</p></td><td><StatusBadge s={b.mode} /></td><td className="text-xs">{b.days}<br />{b.time}<br />{d(b.startDate)} – {d(b.endDate)}</td><td className="text-xs">{b.facultyIds.map((f) => db.staff.find((s) => s.id === f)?.name).join(", ")}</td><td><span className={n >= b.capacity ? "font-semibold text-red-600" : ""}>{n}/{b.capacity}</span></td><td>{fmtINR(b.fee)}</td><td className="text-xs">{b.admissionOpen ? <>{t("till", "पर्यंत")} {d(b.admissionCloses)}</> : <Badge>{t("Closed", "बंद")}</Badge>}</td><td>{canManage && <div className="flex gap-1"><button className="btn-ghost btn-sm" onClick={() => setEdit(structuredClone(b))} aria-label="Edit"><Pencil className="size-4" /></button><button className="btn-ghost btn-sm text-red-600" onClick={() => removeBatch(b)} aria-label={t("Delete", "हटवा")}><Trash2 className="size-4" /></button></div>}</td></tr>; })}
      </Table>
      <Modal open={!!edit} onClose={() => setEdit(null)} title={edit?.id ? t("Edit batch", "बॅच संपादित") : t("Add batch", "बॅच जोडा")} size="xl" footer={<button className="btn-primary" onClick={save}>{t("Save", "जतन")}</button>}>
        {edit && (
          <div className="grid gap-3 sm:grid-cols-3">
            <Field label={t("Name", "नाव")} required className="sm:col-span-2">{(id) => <input id={id} className="input" value={edit.name} onChange={(e) => setEdit({ ...edit, name: e.target.value })} />}</Field>
            <Field label={t("Course", "अभ्यासक्रम")}>{(id) => <select id={id} className="input" value={edit.courseId} onChange={(e) => setEdit({ ...edit, courseId: e.target.value })}>{db.courses.map((c) => <option key={c.id} value={c.id}>{l(c.name)}</option>)}</select>}</Field>
            <Field label={t("Mode", "पद्धत")}>{(id) => <select id={id} className="input" value={edit.mode} onChange={(e) => setEdit({ ...edit, mode: e.target.value as Batch["mode"] })}><option value="offline">Offline</option><option value="online">Online</option><option value="hybrid">Hybrid</option></select>}</Field>
            <Field label={t("Branch", "शाखा")}>{(id) => <select id={id} className="input" value={edit.branchId} onChange={(e) => setEdit({ ...edit, branchId: e.target.value })}>{db.branches.map((b) => <option key={b.id} value={b.id}>{l(b.name)}</option>)}</select>}</Field>
            <Field label={t("Classroom", "वर्गखोली")}>{(id) => <select id={id} className="input" value={edit.classroomId ?? ""} onChange={(e) => setEdit({ ...edit, classroomId: e.target.value || undefined })}><option value="">— Online</option>{db.classrooms.filter((c) => c.branchId === edit.branchId).map((c) => <option key={c.id} value={c.id}>{c.name} ({c.capacity})</option>)}</select>}</Field>
            <Field label={t("Start", "सुरुवात")}>{(id) => <input id={id} type="date" className="input" value={edit.startDate} onChange={(e) => setEdit({ ...edit, startDate: e.target.value })} />}</Field>
            <Field label={t("End", "समाप्ती")}>{(id) => <input id={id} type="date" className="input" value={edit.endDate} onChange={(e) => setEdit({ ...edit, endDate: e.target.value })} />}</Field>
            <Field label={t("Capacity", "क्षमता")}>{(id) => <input id={id} type="number" className="input" value={edit.capacity} onChange={(e) => setEdit({ ...edit, capacity: Number(e.target.value) })} />}</Field>
            <Field label={t("Class days", "वर्ग दिवस")}>{(id) => <input id={id} className="input" value={edit.days} onChange={(e) => setEdit({ ...edit, days: e.target.value })} />}</Field>
            <Field label={t("Timing", "वेळ")}>{(id) => <input id={id} className="input" value={edit.time} onChange={(e) => setEdit({ ...edit, time: e.target.value })} />}</Field>
            <Field label={t("Batch fee (₹)", "बॅच शुल्क (₹)")}>{(id) => <input id={id} type="number" className="input" value={edit.fee} onChange={(e) => setEdit({ ...edit, fee: Number(e.target.value) })} />}</Field>
            <Field label={t("Online link", "ऑनलाइन लिंक")} className="sm:col-span-2">{(id) => <input id={id} className="input" value={edit.onlineLink ?? ""} onChange={(e) => setEdit({ ...edit, onlineLink: e.target.value })} />}</Field>
            <Field label={t("Admission closes", "प्रवेश अंतिम")}>{(id) => <input id={id} type="date" className="input" value={edit.admissionCloses} onChange={(e) => setEdit({ ...edit, admissionCloses: e.target.value })} />}</Field>
            <div className="sm:col-span-3"><p className="label">{t("Faculty", "प्राध्यापक")}</p><div className="flex flex-wrap gap-2">{db.staff.filter((s) => s.role === "faculty").map((s) => <label key={s.id} className="flex items-center gap-1.5 rounded-md border px-2 py-1 text-sm"><input type="checkbox" checked={edit.facultyIds.includes(s.id)} onChange={(e) => setEdit({ ...edit, facultyIds: e.target.checked ? [...edit.facultyIds, s.id] : edit.facultyIds.filter((x) => x !== s.id) })} />{s.name}</label>)}</div></div>
            <Toggle checked={edit.admissionOpen} onChange={(v) => setEdit({ ...edit, admissionOpen: v })} label={t("Admissions open", "प्रवेश सुरू")} />
          </div>
        )}
      </Modal>
    </div>
  );
}

/* ---------------- Branches & classrooms ---------------- */
function Branches({ canManage }: { canManage: boolean }) {
  const { t, l } = useI18n();
  const { db } = useAdmin();
  const { update } = useStore();
  const [room, setRoom] = useState({ name: "", capacity: 30 });
  return (
    <div className="space-y-5">
      <PageHeader title={t("Branches & classrooms", "शाखा व वर्गखोल्या")} actions={canManage && <button className="btn-outline" onClick={() => { const n = prompt("Branch name"); if (n) update((db) => db.branches.push({ id: uid("br"), name: { en: n, mr: n }, city: "", active: false }), { action: "branch.create", entity: n }); }}><Plus className="size-4" />{t("Add branch (inactive)", "शाखा जोडा (निष्क्रिय)")}</button>} />
      <Notice>{t("Only one branch is active initially. All records carry a branch reference so more branches can be added later without data migration.", "सुरुवातीला एकच शाखा सक्रिय. सर्व नोंदींमध्ये शाखेचा संदर्भ असल्याने नंतर शाखा जोडणे सोपे आहे.")}</Notice>
      {db.branches.map((b) => (
        <Card key={b.id} title={<span className="flex items-center gap-2">{l(b.name)} <StatusBadge s={b.active ? "active" : "inactive"} /></span>} actions={canManage && <Toggle checked={b.active} onChange={(v) => update((db) => { db.branches.find((x) => x.id === b.id)!.active = v; }, { action: "branch.toggle", entity: b.name.en })} />}>
          <div className="grid gap-3 sm:grid-cols-3">{db.classrooms.filter((c) => c.branchId === b.id).map((c) => <div key={c.id} className="rounded-lg border p-3 text-sm"><p className="font-semibold">{c.name}</p><p className="text-xs text-slate-500">{t("Capacity", "क्षमता")} {c.capacity} · {db.batches.filter((x) => x.classroomId === c.id).length} {t("batches", "बॅचेस")}</p></div>)}</div>
          {canManage && <div className="mt-3 flex flex-wrap gap-2"><input className="input w-48" placeholder={t("Classroom name", "वर्गखोली नाव")} value={room.name} onChange={(e) => setRoom({ ...room, name: e.target.value })} aria-label="Classroom name" /><input type="number" className="input w-24" value={room.capacity} onChange={(e) => setRoom({ ...room, capacity: Number(e.target.value) })} aria-label="Capacity" /><button className="btn-outline" onClick={() => { if (room.name) { update((db) => db.classrooms.push({ id: uid("cr"), branchId: b.id, ...room }), { action: "classroom.create", entity: room.name }); setRoom({ name: "", capacity: 30 }); } }}><Plus className="size-4" />{t("Classroom", "वर्गखोली")}</button></div>}
        </Card>
      ))}
    </div>
  );
}

/* ---------------- Staff ---------------- */
function Staff({ canManage }: { canManage: boolean }) {
  const { t } = useI18n();
  const { db } = useAdmin();
  const { update } = useStore();
  const [add, setAdd] = useState(false);
  const [f, setF] = useState({ name: "", mobile: "", role: "faculty" as StaffUser["role"], designation: "" });
  const save = () => {
    if (!f.name || !isValidMobile(f.mobile)) return toast.error(t("Name and valid mobile required", "नाव व वैध मोबाईल आवश्यक"));
    update((db) => db.staff.push({ id: uid("u"), name: f.name, mobile: normMobile(f.mobile), role: f.role, designation: f.designation, subjects: [], batchIds: [], permissions: { ...PERMISSION_PRESETS[f.role === "owner" ? "owner" : f.role] }, publishWithoutApproval: false, active: true }), { action: "staff.create", entity: f.name, detail: f.role });
    setAdd(false);
  };
  return (
    <div>
      <PageHeader title={t("Faculty & staff", "प्राध्यापक व कर्मचारी")} actions={canManage && <button className="btn-primary" onClick={() => setAdd(true)}><Plus className="size-4" />{t("Add", "जोडा")}</button>} />
      <Table head={[t("Name", "नाव"), t("Role", "भूमिका"), t("Mobile", "मोबाईल"), t("Assigned batches", "नियुक्त बॅचेस"), t("Active", "सक्रिय"), ""]}>
        {db.staff.map((s) => <tr key={s.id}><td><p className="font-medium">{s.name}</p><p className="text-xs text-slate-500">{s.designation}</p></td><td><Badge tone={s.role === "owner" ? "violet" : s.role === "staff" ? "blue" : "slate"}>{s.role}</Badge></td><td className="text-xs">{fmtMobile(s.mobile)}</td><td className="text-xs">{s.batchIds.map((b) => db.batches.find((x) => x.id === b)?.name).join(", ") || "—"}</td><td><Toggle disabled={!canManage || s.role === "owner"} checked={s.active} onChange={(v) => update((db) => { db.staff.find((x) => x.id === s.id)!.active = v; }, { action: "staff.active", entity: s.name, detail: String(v) })} /></td><td><Link href="/admin/permissions" className="btn-ghost btn-sm">{t("Permissions", "परवानग्या")}</Link></td></tr>)}
      </Table>
      <Modal open={add} onClose={() => setAdd(false)} title={t("Add faculty / staff", "प्राध्यापक / कर्मचारी जोडा")} footer={<button className="btn-primary" onClick={save}>{t("Save", "जतन")}</button>}>
        <div className="grid gap-3 sm:grid-cols-2">
          <Field label={t("Name", "नाव")} required>{(id) => <input id={id} className="input" value={f.name} onChange={(e) => setF({ ...f, name: e.target.value })} />}</Field>
          <Field label={t("Mobile (login)", "मोबाईल (लॉगिन)")} required>{(id) => <input id={id} className="input" maxLength={10} value={f.mobile} onChange={(e) => setF({ ...f, mobile: e.target.value.replace(/\D/g, "") })} />}</Field>
          <Field label={t("Role", "भूमिका")}>{(id) => <select id={id} className="input" value={f.role} onChange={(e) => setF({ ...f, role: e.target.value as StaffUser["role"] })}><option value="faculty">Faculty</option><option value="staff">Admin staff</option></select>}</Field>
          <Field label={t("Designation", "पद")}>{(id) => <input id={id} className="input" value={f.designation} onChange={(e) => setF({ ...f, designation: e.target.value })} />}</Field>
        </div>
        <p className="mt-3 text-xs text-slate-500">{t("Default permission preset is applied; adjust in Permissions. Faculty never get finance/admission/system access by default.", "पूर्वनिर्धारित परवानग्या लागू होतील; 'परवानग्या' मध्ये बदला. प्राध्यापकांना वित्त/प्रवेश/प्रणाली प्रवेश पूर्वनिर्धारितपणे नसतो.")}</p>
      </Modal>
    </div>
  );
}

/* ---------------- Permissions ---------------- */
const MOD_LABEL: Record<Module, [string, string]> = {
  enquiries: ["Enquiries", "चौकशी"], students: ["Students", "विद्यार्थी"], admissions: ["Admissions", "प्रवेश"], courses: ["Courses & syllabus", "अभ्यासक्रम"], batches: ["Batches & classrooms", "बॅचेस"], fees: ["Fees & payments", "शुल्क"], library: ["Abhyasika Kendra", "अभ्यासिका केंद्र"], materials: ["Study materials", "साहित्य"], classes: ["Live/recorded classes", "वर्ग"], questionBank: ["Question bank", "प्रश्नपेढी"], exams: ["Examinations", "परीक्षा"], assignments: ["Assignments", "गृहपाठ"], attendance: ["Attendance", "उपस्थिती"], examUpdates: ["Exam updates", "परीक्षा अपडेट्स"], notifications: ["Notifications", "सूचना"], support: ["Support", "मदत"], results: ["Results & achievements", "निकाल"], referrals: ["Referrals", "रेफरल"], certificates: ["ID cards & certificates", "प्रमाणपत्रे"], reports: ["Reports", "अहवाल"], cms: ["Website CMS", "वेबसाइट"], settings: ["Settings", "सेटिंग्ज"], staff: ["Staff & permissions", "कर्मचारी"], audit: ["Audit logs", "ऑडिट"],
};
const SENSITIVE: Module[] = ["fees", "admissions", "settings", "staff", "audit", "enquiries"];
function PermissionsMatrix({ canManage }: { canManage: boolean }) {
  const { t, lang } = useI18n();
  const { db, me, isOwner } = useAdmin();
  const { update } = useStore();
  const [uidSel, setUidSel] = useState(db.staff.find((s) => s.role !== "owner")?.id ?? "");
  const u = db.staff.find((s) => s.id === uidSel)!;
  const editable = canManage && isOwner && u?.role !== "owner";
  const setP = (m: Module, a: Access) => {
    if (u.role === "faculty" && SENSITIVE.includes(m) && a !== "none" && !confirm(t(`Grant "${MOD_LABEL[m][0]}" to a faculty member? This is not recommended by default.`, `प्राध्यापकाला "${MOD_LABEL[m][1]}" परवानगी द्यायची? पूर्वनिर्धारितपणे शिफारस नाही.`))) return;
    update((db) => { db.staff.find((s) => s.id === u.id)!.permissions[m] = a; }, { action: "permission.update", entity: u.name, detail: `${m}: ${a}` });
  };
  return (
    <div>
      <PageHeader title={t("Permissions", "परवानग्या")} subtitle={t("Per-user, per-module access: No access · View only · View & manage. Faculty are additionally limited to their assigned batches.", "प्रत्येक वापरकर्ता व विभागनिहाय प्रवेश: प्रवेश नाही · फक्त पाहणे · पाहणे व व्यवस्थापन. प्राध्यापक फक्त नियुक्त बॅचेसपुरते मर्यादित.")} />
      {!isOwner && <div className="mb-4"><Notice tone="warn">{t("Only the owner can change permissions.", "फक्त मालक परवानग्या बदलू शकतात.")}</Notice></div>}
      <div className="grid gap-5 lg:grid-cols-[240px_1fr]">
        <Card bodyClass="p-2"><ul>{db.staff.map((s) => <li key={s.id}><button onClick={() => setUidSel(s.id)} className={cn("w-full rounded-md px-3 py-2 text-left text-sm", uidSel === s.id ? "bg-brand-700 text-white" : "hover:bg-slate-100")}>{s.name}<span className="block text-[11px] opacity-70">{s.role}</span></button></li>)}</ul></Card>
        {u && (
          <div className="space-y-4">
            {u.role === "owner" ? <Notice>{t("Owner / super admin has full access to all modules.", "मालक / सुपर अ‍ॅडमिनला सर्व विभागांचा पूर्ण प्रवेश.")}</Notice> : (
              <>
                <div className="flex flex-wrap gap-2">{(["faculty", "staff"] as const).map((p) => <button key={p} disabled={!editable} className="btn-outline btn-sm" onClick={() => update((db) => { db.staff.find((s) => s.id === u.id)!.permissions = { ...PERMISSION_PRESETS[p] }; }, { action: "permission.preset", entity: u.name, detail: p })}>{t("Apply preset", "प्रीसेट लागू")}: {p}</button>)}</div>
                <div className="card overflow-x-auto">
                  <table className="table-base"><thead><tr><th>{t("Module", "विभाग")}</th><th>{t("No access", "प्रवेश नाही")}</th><th>{t("View only", "फक्त पाहणे")}</th><th>{t("View & manage", "व्यवस्थापन")}</th></tr></thead>
                    <tbody>{MODULES.map((m) => <tr key={m}><td className="font-medium">{MOD_LABEL[m][lang === "mr" ? 1 : 0]}{u.role === "faculty" && SENSITIVE.includes(m) && <AlertTriangle className="ml-1 inline size-3.5 text-amber-500" />}</td>{(["none", "view", "manage"] as Access[]).map((a) => <td key={a}><input type="radio" disabled={!editable} name={`p-${m}`} checked={u.permissions[m] === a} onChange={() => setP(m, a)} aria-label={`${m} ${a}`} className="size-4 accent-brand-700" /></td>)}</tr>)}</tbody></table>
                </div>
                {u.role === "faculty" && (
                  <Card title={t("Faculty scope", "प्राध्यापक मर्यादा")}>
                    <p className="label">{t("Assigned batches", "नियुक्त बॅचेस")}</p>
                    <div className="flex flex-wrap gap-2">{db.batches.map((b) => <label key={b.id} className="flex items-center gap-1.5 rounded-md border px-2 py-1 text-sm"><input type="checkbox" disabled={!editable} checked={u.batchIds.includes(b.id)} onChange={(e) => update((db) => { const s = db.staff.find((x) => x.id === u.id)!; s.batchIds = e.target.checked ? [...s.batchIds, b.id] : s.batchIds.filter((x) => x !== b.id); }, { action: "permission.batches", entity: u.name, detail: `${b.name}: ${e.target.checked}` })} />{b.name}</label>)}</div>
                    <div className="mt-3"><Toggle disabled={!editable} checked={u.publishWithoutApproval} onChange={(v) => update((db) => { db.staff.find((x) => x.id === u.id)!.publishWithoutApproval = v; }, { action: "permission.publishDirect", entity: u.name, detail: String(v) })} label={t("Can publish study material without approval", "मंजुरीशिवाय साहित्य प्रकाशित करू शकतात")} /></div>
                  </Card>
                )}
              </>
            )}
            <p className="text-xs text-slate-500">{t("All permission changes are recorded in the audit log.", "सर्व परवानगी बदल ऑडिट नोंदीत नोंदवले जातात.")} ({me.name})</p>
          </div>
        )}
      </div>
    </div>
  );
}

/* ---------------- Audit ---------------- */
function Audit() {
  const { t, dt } = useI18n();
  const { db } = useAdmin();
  const [q, setQ] = useState("");
  const list = useMemo(() => db.audit.filter((a) => (a.actor + a.action + a.entity + a.detail).toLowerCase().includes(q.toLowerCase())), [db.audit, q]);
  return (
    <div>
      <PageHeader title={t("Audit logs", "ऑडिट नोंदी")} subtitle={t("Important administrative and financial actions. Records are append-only.", "महत्त्वाच्या प्रशासकीय व आर्थिक कृती. नोंदी फक्त जोडल्या जातात.")} actions={<><SearchBox value={q} onChange={setQ} placeholder={t("Filter", "फिल्टर")} /><button className="btn-outline" onClick={() => downloadFile("audit-log.csv", toCSV(list.map((a) => ({ At: a.at, Actor: a.actor, Action: a.action, Entity: a.entity, Detail: a.detail }))))}><Download className="size-4" />CSV</button></>} />
      <Table head={[t("When", "केव्हा"), t("Who", "कोण"), t("Action", "कृती"), t("Record", "नोंद"), t("Detail", "तपशील")]} empty={!list.length}>
        {list.map((a) => <tr key={a.id}><td className="whitespace-nowrap text-xs">{dt(a.at)}</td><td className="text-sm">{a.actor}</td><td><code className="rounded bg-slate-100 px-1.5 py-0.5 text-xs">{a.action}</code></td><td className="text-sm">{a.entity}</td><td className="text-xs text-slate-600">{a.detail}</td></tr>)}
      </Table>
    </div>
  );
}

