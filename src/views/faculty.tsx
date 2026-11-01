"use client";
import Link from "next/link";
import { useState } from "react";
import { ClipboardCheck, FileText, MonitorPlay, Send, Users, Lock } from "lucide-react";
import { useStore } from "@/lib/store";
import { useI18n } from "@/lib/i18n";
import { attendanceStats } from "@/lib/actions";
import { nowISO, today, uid } from "@/lib/utils";
import { Badge, Card, EmptyState, Field, Notice, PageHeader, SearchBox, StatCard, StatusBadge, Table, toast } from "@/components/ui";
import { NoAccess, useAccess } from "@/components/PortalShell";
import { AssignmentsManager, AttendanceManager, LiveClassManager, MaterialsManager, QuestionBank, ResultsBoard, TestsManager, type Scope } from "./ops";
import type { Module } from "@/lib/types";

function useScope(module: Module): Scope & { access: string } {
  const me = useStore((s) => s.db.staff.find((x) => x.id === s.session.userId))!;
  const access = useAccess(module);
  return { batchIds: me.batchIds, canManage: access === "manage", actorId: me.id, isAdmin: false, access };
}
function Gate({ module, children }: { module: Module; children: (s: Scope) => React.ReactNode }) {
  const s = useScope(module);
  if (s.access === "none") return <NoAccess />;
  return <>{children(s)}</>;
}

export function FacultyRouter({ slug }: { slug: string[] }) {
  const me = useStore((s) => s.db.staff.find((x) => x.id === s.session.userId));
  if (!me) return null;
  switch (slug[0]) {
    case undefined: return <Dashboard />;
    case "batches": return <Batches />;
    case "students": return <Gate module="students">{() => <Students />}</Gate>;
    case "attendance": return <Gate module="attendance">{(s) => <AttendanceManager scope={s} />}</Gate>;
    case "classes": return <Gate module="classes">{(s) => <LiveClassManager scope={s} />}</Gate>;
    case "materials": return <Gate module="materials">{(s) => <MaterialsManager scope={s} canApprove={false} canPublishDirect={me.publishWithoutApproval} />}</Gate>;
    case "tests": return <Gate module="exams">{(s) => <TestsManager scope={s} />}</Gate>;
    case "questions": return <Gate module="questionBank">{(s) => <QuestionBank canManage={s.canManage} />}</Gate>;
    case "assignments": return <Gate module="assignments">{(s) => <AssignmentsManager scope={s} />}</Gate>;
    case "results": return <Gate module="results">{(s) => <ResultsBoard scope={s} />}</Gate>;
    case "notifications": return <Notifications />;
    default: return <EmptyState title="Not found" />;
  }
}

function Dashboard() {
  const { t, l, d } = useI18n();
  const db = useStore((s) => s.db);
  const me = db.staff.find((x) => x.id === useStore.getState().session.userId)!;
  const studs = new Set(db.enrolments.filter((e) => me.batchIds.includes(e.batchId) && e.enrolmentStatus === "active").map((e) => e.studentId));
  const todayClasses = db.live.filter((c) => me.batchIds.includes(c.batchId) && c.date === today() && c.status !== "cancelled");
  const toGrade = db.submissions.filter((s) => s.status === "submitted" && db.assignments.some((a) => a.id === s.assignmentId && me.batchIds.includes(a.batchId))).length;
  const unmarked = me.batchIds.filter((b) => !db.attendance.some((a) => a.batchId === b && a.date === today()));
  return (
    <div className="space-y-5">
      <PageHeader title={`${t("Welcome", "नमस्कार")}, ${me.name}`} subtitle={t("You can access only your assigned batches and permitted modules.", "आपल्याला फक्त नियुक्त बॅचेस व परवानगी असलेले विभाग उपलब्ध आहेत.")} />
      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        <StatCard label={t("Assigned batches", "नियुक्त बॅचेस")} value={me.batchIds.length} icon={<Users className="size-5" />} />
        <StatCard label={t("Students", "विद्यार्थी")} value={studs.size} icon={<Users className="size-5" />} tone="violet" />
        <StatCard label={t("Classes today", "आजचे वर्ग")} value={todayClasses.length} icon={<MonitorPlay className="size-5" />} tone="green" />
        <StatCard label={t("Submissions to grade", "तपासायचे गृहपाठ")} value={toGrade} icon={<FileText className="size-5" />} tone="amber" />
      </div>
      {unmarked.length > 0 && <Notice tone="warn">{t("Attendance not yet marked today for", "आजची उपस्थिती अद्याप नोंदली नाही")}: {unmarked.map((b) => db.batches.find((x) => x.id === b)?.name).join(", ")} <Link href="/faculty-portal/attendance" className="font-semibold underline">{t("Mark now", "आता नोंदवा")}</Link></Notice>}
      <div className="grid gap-5 lg:grid-cols-2">
        <Card title={t("Today's classes", "आजचे वर्ग")}>{todayClasses.length ? <ul className="space-y-2 text-sm">{todayClasses.map((c) => <li key={c.id} className="flex justify-between"><span>{c.time} · {c.title}</span><StatusBadge s={c.status} /></li>)}</ul> : <p className="text-sm text-slate-500">{t("No live classes today", "आज लाईव्ह वर्ग नाहीत")}</p>}</Card>
        <Card title={t("My permissions", "माझ्या परवानग्या")}>
          <div className="flex flex-wrap gap-1.5">{Object.entries(me.permissions).filter(([, v]) => v !== "none").map(([k, v]) => <Badge key={k} tone={v === "manage" ? "green" : "blue"}>{k}: {v}</Badge>)}</div>
          <p className="mt-2 flex items-center gap-1 text-xs text-slate-500"><Lock className="size-3" />{t("Fees, admissions and settings are not available to faculty by default.", "शुल्क, प्रवेश व सेटिंग्ज प्राध्यापकांना पूर्वनिर्धारितपणे उपलब्ध नाहीत.")}</p>
        </Card>
      </div>
      <Card title={t("Upcoming", "आगामी")}><ul className="space-y-1 text-sm">{db.live.filter((c) => me.batchIds.includes(c.batchId) && c.date > today()).map((c) => <li key={c.id}>{d(c.date)} {c.time} · {c.title} · {l(db.subjects.find((s) => s.id === c.subjectId)?.name)}</li>)}</ul></Card>
    </div>
  );
}

function Batches() {
  const { t, l, d } = useI18n();
  const db = useStore((s) => s.db);
  const me = db.staff.find((x) => x.id === useStore.getState().session.userId)!;
  return (
    <div>
      <PageHeader title={t("My batches", "माझ्या बॅचेस")} />
      <div className="grid gap-4 md:grid-cols-2">
        {db.batches.filter((b) => me.batchIds.includes(b.id)).map((b) => {
          const n = db.enrolments.filter((e) => e.batchId === b.id && e.enrolmentStatus === "active").length;
          return <Card key={b.id} title={b.name} actions={<StatusBadge s={b.mode} />}><p className="text-sm text-slate-600">{l(db.courses.find((c) => c.id === b.courseId)?.name)}</p><p className="mt-1 text-sm">{b.days} · {b.time} · {db.classrooms.find((c) => c.id === b.classroomId)?.name ?? t("Online", "ऑनलाइन")}</p><p className="text-xs text-slate-500">{d(b.startDate)} – {d(b.endDate)} · {n}/{b.capacity} {t("students", "विद्यार्थी")}</p>
            <div className="mt-3 flex gap-2"><Link href="/faculty-portal/attendance" className="btn-outline btn-sm"><ClipboardCheck className="size-4" />{t("Attendance", "उपस्थिती")}</Link><Link href="/faculty-portal/classes" className="btn-outline btn-sm"><MonitorPlay className="size-4" />{t("Classes", "वर्ग")}</Link></div></Card>;
        })}
      </div>
    </div>
  );
}

function Students() {
  const { t } = useI18n();
  const db = useStore((s) => s.db);
  const me = db.staff.find((x) => x.id === useStore.getState().session.userId)!;
  const [q, setQ] = useState("");
  const rows = db.enrolments.filter((e) => me.batchIds.includes(e.batchId) && e.enrolmentStatus === "active").map((e) => ({ e, s: db.students.find((x) => x.id === e.studentId)! })).filter((r) => r.s.name.toLowerCase().includes(q.toLowerCase()));
  return (
    <div>
      <PageHeader title={t("Students (assigned batches only)", "विद्यार्थी (फक्त नियुक्त बॅचेस)")} actions={<SearchBox value={q} onChange={setQ} placeholder={t("Search", "शोधा")} />} />
      <Table head={[t("Student", "विद्यार्थी"), t("Batch", "बॅच"), t("Attendance", "उपस्थिती"), t("Tests taken", "चाचण्या")]} empty={!rows.length}>
        {rows.map(({ e, s }) => { const st = attendanceStats(db, s.id, e.batchId); return <tr key={e.id}><td><p className="font-medium">{s.name}</p><p className="text-xs text-slate-500">{s.code}</p></td><td className="text-xs">{db.batches.find((b) => b.id === e.batchId)?.name}</td><td className={st.percent < db.settings.attendance.minimumPercent ? "font-semibold text-red-600" : ""}>{st.percent.toFixed(0)}%</td><td>{db.attempts.filter((a) => a.studentId === s.id && a.submittedAt).length}</td></tr>; })}
      </Table>
      <p className="mt-2 text-xs text-slate-500">{t("Contact details and fee information are hidden from faculty.", "संपर्क तपशील व शुल्क माहिती प्राध्यापकांपासून लपवलेली आहे.")}</p>
    </div>
  );
}

function Notifications() {
  const { t, l, dt } = useI18n();
  const { db, update } = useStore();
  const me = db.staff.find((x) => x.id === useStore.getState().session.userId)!;
  const [batch, setBatch] = useState(me.batchIds[0] ?? "");
  const [msg, setMsg] = useState("");
  const send = () => {
    if (msg.trim().length < 3) return;
    update((db) => {
      const ids = db.enrolments.filter((e) => e.batchId === batch && e.enrolmentStatus === "active").map((e) => e.studentId);
      ids.forEach((sid) => db.inbox.unshift({ id: uid("in"), studentId: sid, title: { en: `Message from ${me.name}`, mr: `${me.name} यांचा संदेश` }, body: { en: msg, mr: msg }, at: nowISO(), read: false, kind: "general" }));
      db.broadcasts.unshift({ id: uid("bc"), title: `Faculty note`, body: msg, channels: ["inapp"], audience: { type: "batch", ids: [batch] }, createdAt: nowISO(), createdBy: me.id, status: "sent", delivered: ids.length, failed: 0 });
    }, { action: "notify.batch", entity: batch });
    setMsg(""); toast.success(t("Sent to batch (in-app)", "बॅचला पाठवले (अ‍ॅपमध्ये)"));
  };
  return (
    <div className="space-y-5">
      <PageHeader title={t("Notifications", "सूचना")} />
      <Card title={t("Send an in-app note to my batch", "माझ्या बॅचला अ‍ॅपमध्ये संदेश")}>
        <div className="grid gap-3 sm:grid-cols-[200px_1fr_auto]">
          <select className="input" value={batch} onChange={(e) => setBatch(e.target.value)} aria-label="Batch">{me.batchIds.map((b) => <option key={b} value={b}>{db.batches.find((x) => x.id === b)?.name}</option>)}</select>
          <Field label="" className="[&>label]:hidden">{(id) => <input id={id} className="input" value={msg} onChange={(e) => setMsg(e.target.value)} placeholder={t("e.g. Bring the practice set tomorrow", "उदा. उद्या सराव संच आणा")} />}</Field>
          <button className="btn-primary" onClick={send}><Send className="size-4" />{t("Send", "पाठवा")}</button>
        </div>
        <p className="mt-2 text-xs text-slate-500">{t("WhatsApp/SMS broadcasts are sent by the admin office.", "व्हॉट्सअ‍ॅप/SMS संदेश प्रशासकीय कार्यालयाकडून पाठवले जातात.")}</p>
      </Card>
      <Card title={t("Academy announcements", "अकॅडमी सूचना")}><ul className="space-y-2 text-sm">{db.announcements.filter((a) => a.published).map((a) => <li key={a.id}><b>{l(a.title)}</b> <span className="text-xs text-slate-500">{dt(a.date)}</span></li>)}</ul></Card>
    </div>
  );
}
