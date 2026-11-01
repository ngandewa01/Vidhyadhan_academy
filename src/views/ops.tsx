"use client";
/** Operational modules shared by Faculty (scoped to assigned batches) and Admin (all batches). */
import { useEffect, useMemo, useState } from "react";
import { QRCodeSVG } from "qrcode.react";
import { Check, CheckCircle2, Download, FileText, FileUp, Pencil, Plus, QrCode, Upload, X, XCircle } from "lucide-react";
import { useStore } from "@/lib/store";
import { useI18n } from "@/lib/i18n";
import { attendanceStats } from "@/lib/actions";
import { cn, downloadFile, fmtSize, nowISO, parseCSV, toCSV, today, uid } from "@/lib/utils";
import { Badge, Card, EmptyState, Field, Modal, Notice, PageHeader, SearchBox, Select, StatusBadge, Table, Tabs, Toggle, toast, statusLabel } from "@/components/ui";
import type { AttendanceMark, LiveClass, Material, MaterialAccess, Question, Test, TestType } from "@/lib/types";
import { TEST_TYPE } from "./exam";

export type Scope = { batchIds: string[]; canManage: boolean; actorId: string; isAdmin: boolean };

function BatchPicker({ scope, value, onChange }: { scope: Scope; value: string; onChange: (v: string) => void }) {
  const batches = useStore((s) => s.db.batches);
  return <Select label="Batch" value={value} onChange={onChange} options={batches.filter((b) => scope.batchIds.includes(b.id)).map((b) => ({ value: b.id, label: b.name }))} />;
}
function studentsIn(db: ReturnType<typeof useStore.getState>["db"], batchId: string) {
  const ids = db.enrolments.filter((e) => e.batchId === batchId && e.enrolmentStatus === "active").map((e) => e.studentId);
  return db.students.filter((s) => ids.includes(s.id));
}

/* ---------------- Attendance ---------------- */
export function AttendanceManager({ scope }: { scope: Scope }) {
  const { t, lang, d } = useI18n();
  const { db, update } = useStore();
  const [batch, setBatch] = useState(scope.batchIds[0] ?? "");
  const [date, setDate] = useState(today());
  const [tab, setTab] = useState<"mark" | "report" | "shortage">("mark");
  const [qr, setQr] = useState(false);
  const [fix, setFix] = useState<{ id: string; to: AttendanceMark } | null>(null);
  const [reason, setReason] = useState("");
  const studs = studentsIn(db, batch);
  const recs = db.attendance.filter((a) => a.batchId === batch && a.date === date);
  const settings = db.settings.attendance;

  const mark = (sid: string, status: AttendanceMark) => {
    const ex = recs.find((r) => r.studentId === sid);
    if (ex && date !== today() && !scope.isAdmin) return toast.error(t("Only admin can correct past attendance", "मागील उपस्थिती फक्त प्रशासक दुरुस्त करू शकतात"));
    if (ex && ex.status !== status && (date !== today() || ex.method === "qr")) { setFix({ id: ex.id, to: status }); setReason(""); return; }
    update((db) => {
      const r = db.attendance.find((a) => a.batchId === batch && a.date === date && a.studentId === sid);
      if (r) r.status = status; else db.attendance.push({ id: uid("at"), batchId: batch, date, studentId: sid, status, method: "manual", markedBy: scope.actorId });
      if (status === "A") { const s = db.students.find((x) => x.id === sid); if (s) db.inbox.unshift({ id: uid("in"), studentId: sid, title: { en: "Marked absent", mr: "अनुपस्थित नोंद" }, body: { en: `You were marked absent on ${date}.${s.parentAlerts ? " Parent notified." : ""}`, mr: `${date} रोजी अनुपस्थित नोंद.${s.parentAlerts ? " पालकांना कळवले." : ""}` }, at: nowISO(), read: false, kind: "attendance" }); }
    });
  };
  const all = (status: AttendanceMark) => studs.forEach((s) => !recs.some((r) => r.studentId === s.id) && mark(s.id, status));
  const applyFix = () => {
    if (!fix || reason.trim().length < 3) return toast.error(t("A reason is mandatory", "कारण आवश्यक आहे"));
    update((db) => { const r = db.attendance.find((a) => a.id === fix.id)!; r.correction = { reason, by: scope.actorId, at: nowISO(), from: r.status }; r.status = fix.to; }, { action: "attendance.correct", entity: fix.id, detail: reason });
    setFix(null);
    toast.success(t("Corrected and logged", "दुरुस्त करून नोंदवले"));
  };
  const report = studs.map((s) => ({ s, st: attendanceStats(db, s.id, batch) }));
  const exportCSV = () => downloadFile(`attendance-${batch}-${date.slice(0, 7)}.csv`, toCSV(report.map(({ s, st }) => ({ StudentID: s.code, Name: s.name, Present: st.present, Late: st.late, Absent: st.absent, Total: st.total, Percent: st.percent.toFixed(1) }))));

  return (
    <div>
      <PageHeader title={t("Attendance", "उपस्थिती")} actions={<>{scope.canManage && <button className="btn-primary" onClick={() => setQr(true)}><QrCode className="size-4" />{t("Generate QR", "QR तयार करा")}</button>}<button className="btn-outline" onClick={exportCSV}><Download className="size-4" />CSV / Excel</button><button className="btn-outline" onClick={() => window.print()}>PDF</button></>} />
      <div className="mb-4 flex flex-wrap items-center gap-2">
        <BatchPicker scope={scope} value={batch} onChange={setBatch} />
        <input type="date" className="input w-auto" value={date} max={today()} onChange={(e) => setDate(e.target.value)} aria-label="Date" />
      </div>
      <Tabs tabs={[{ id: "mark", label: t("Daily register", "दैनिक हजेरी") }, { id: "report", label: t("Student-wise report", "विद्यार्थीनिहाय अहवाल") }, { id: "shortage", label: t("Shortage", "कमतरता"), count: report.filter((r) => r.st.total && r.st.percent < settings.minimumPercent).length }]} value={tab} onChange={setTab} />
      {tab === "mark" && (
        <>
          {scope.canManage && <div className="mb-3 flex flex-wrap gap-2 text-sm"><button className="btn-outline btn-sm" onClick={() => all("P")}>{t("Mark remaining Present", "उर्वरित उपस्थित")}</button><span className="self-center text-slate-500">{recs.length}/{studs.length} {t("marked", "नोंदले")}</span></div>}
          <Table head={[t("Student", "विद्यार्थी"), t("Status", "स्थिती"), t("Method", "पद्धत"), ""]} empty={!studs.length}>
            {studs.map((s) => {
              const r = recs.find((x) => x.studentId === s.id);
              return (
                <tr key={s.id}>
                  <td><p className="font-medium">{s.name}</p><p className="text-xs text-slate-500">{s.code}</p></td>
                  <td>{r ? <StatusBadge s={r.status} /> : <span className="text-xs text-slate-400">—</span>}{r?.correction && <Badge tone="violet" className="ml-1">{t("corrected", "दुरुस्त")}</Badge>}</td>
                  <td className="text-xs uppercase text-slate-500">{r?.method ?? ""}</td>
                  <td>{scope.canManage && <div className="flex gap-1">{(["P", "L", "A"] as AttendanceMark[]).map((m) => <button key={m} onClick={() => mark(s.id, m)} className={cn("size-8 rounded-md text-xs font-bold", r?.status === m ? (m === "P" ? "bg-emerald-600 text-white" : m === "A" ? "bg-red-600 text-white" : "bg-amber-500 text-white") : "bg-slate-100 text-slate-600 hover:bg-slate-200")} aria-label={statusLabel(m, lang)}>{m}</button>)}</div>}</td>
                </tr>
              );
            })}
          </Table>
        </>
      )}
      {(tab === "report" || tab === "shortage") && (
        <Table head={[t("Student", "विद्यार्थी"), "P", "L", "A", "%", ""]} empty={!report.length}>
          {report.filter((r) => tab === "report" || (r.st.total && r.st.percent < settings.minimumPercent)).map(({ s, st }) => (
            <tr key={s.id}><td className="font-medium">{s.name}</td><td>{st.present}</td><td>{st.late}</td><td>{st.absent}</td><td className={cn("font-semibold", st.percent < settings.minimumPercent ? "text-red-600" : st.percent < settings.warningPercent ? "text-amber-600" : "text-emerald-700")}>{st.percent.toFixed(0)}%</td>
              <td>{tab === "shortage" && <button className="btn-ghost btn-sm" onClick={() => { update((db) => { db.inbox.unshift({ id: uid("in"), studentId: s.id, title: { en: "Attendance shortage warning", mr: "उपस्थिती कमतरता इशारा" }, body: { en: `Your attendance is ${st.percent.toFixed(0)}%. Minimum required is ${settings.minimumPercent}%.`, mr: `आपली उपस्थिती ${st.percent.toFixed(0)}% आहे. किमान ${settings.minimumPercent}% आवश्यक.` }, at: nowISO(), read: false, kind: "attendance" }); }); toast.success(t("Warning sent", "इशारा पाठवला")); }}>{t("Send warning", "इशारा पाठवा")}</button>}</td></tr>
          ))}
        </Table>
      )}
      <p className="mt-3 text-xs text-slate-500">{t(`Minimum ${settings.minimumPercent}% · warning below ${settings.warningPercent}% · corrections require a reason and are audit-logged · last updated ${d(today())}`, `किमान ${settings.minimumPercent}% · ${settings.warningPercent}% खाली इशारा · दुरुस्तीसाठी कारण आवश्यक व नोंद होते`)}</p>
      {qr && <QRSession batchId={batch} actorId={scope.actorId} onClose={() => setQr(false)} />}
      <Modal open={!!fix} onClose={() => setFix(null)} title={t("Correct attendance", "उपस्थिती दुरुस्ती")} footer={<button className="btn-primary" onClick={applyFix}>{t("Save correction", "दुरुस्ती जतन")}</button>}>
        <Field label={t("Reason (mandatory)", "कारण (आवश्यक)")} required>{(id) => <textarea id={id} className="input" rows={3} value={reason} onChange={(e) => setReason(e.target.value)} />}</Field>
      </Modal>
    </div>
  );
}

function QRSession({ batchId, actorId, onClose }: { batchId: string; actorId: string; onClose: () => void }) {
  const { t } = useI18n();
  const { db, update } = useStore();
  const ttl = db.settings.attendance.qrValiditySec;
  const [code, setCode] = useState("");
  const [left, setLeft] = useState(ttl);
  useEffect(() => {
    const rotate = () => {
      const c = Math.random().toString(36).slice(2, 8).toUpperCase();
      update((db) => { db.qrSessions = db.qrSessions.filter((q) => q.expiresAt > Date.now()); db.qrSessions.push({ id: uid("qr"), batchId, code: c, expiresAt: Date.now() + ttl * 1000, createdBy: actorId }); });
      setCode(c); setLeft(ttl);
    };
    rotate();
    const r = setInterval(rotate, ttl * 1000);
    const tick = setInterval(() => setLeft((x) => Math.max(0, x - 1)), 1000);
    return () => { clearInterval(r); clearInterval(tick); };
  }, [batchId, ttl, actorId, update]);
  const marked = db.attendance.filter((a) => a.batchId === batchId && a.date === today() && a.method === "qr").length;
  return (
    <Modal open onClose={onClose} title={t("Class QR attendance", "वर्ग QR उपस्थिती")} size="md">
      <div className="flex flex-col items-center gap-3 text-center">
        <p className="text-sm text-slate-600">{db.batches.find((b) => b.id === batchId)?.name}</p>
        {code && <QRCodeSVG value={`VA-ATT:${batchId}:${code}`} size={220} />}
        <p className="font-mono text-3xl font-bold tracking-widest">{code}</p>
        <p className="text-sm text-slate-500">{t(`Refreshes in ${left}s`, `${left} सेकंदांत बदलेल`)} · {marked} {t("scanned today", "आज स्कॅन")}</p>
        <Notice>{t("Students must be logged in. Codes expire quickly and duplicate scans are rejected, which reduces screenshot sharing.", "विद्यार्थ्यांना लॉगिन आवश्यक. कोड लवकर कालबाह्य होतात व दुहेरी स्कॅन नाकारले जातात, त्यामुळे स्क्रीनशॉट शेअरिंग कमी होते.")}</Notice>
      </div>
    </Modal>
  );
}

/* ---------------- Live classes ---------------- */
export function LiveClassManager({ scope }: { scope: Scope }) {
  const { t, l, d } = useI18n();
  const { db, update } = useStore();
  const [edit, setEdit] = useState<Partial<LiveClass> | null>(null);
  const list = db.live.filter((c) => scope.batchIds.includes(c.batchId)).sort((a, b) => (b.date + b.time).localeCompare(a.date + a.time));
  const save = () => {
    if (!edit?.batchId || !edit.title || !edit.date || !edit.time || !edit.link) return toast.error(t("Fill all required fields", "सर्व आवश्यक माहिती भरा"));
    update((db) => {
      if (edit.id) { const c = db.live.find((x) => x.id === edit.id)!; const re = c.date !== edit.date || c.time !== edit.time; Object.assign(c, edit, re && c.status === "scheduled" ? { status: "rescheduled" } : {}); }
      else db.live.push({ id: uid("lc"), batchId: edit.batchId!, subjectId: edit.subjectId ?? "", facultyId: edit.facultyId ?? scope.actorId, title: edit.title!, date: edit.date!, time: edit.time!, durationMin: edit.durationMin ?? 60, provider: edit.provider ?? "meet", link: edit.link!, status: "scheduled", attendees: [] });
      studentsIn(db, edit.batchId!).forEach((s) => db.inbox.unshift({ id: uid("in"), studentId: s.id, title: { en: edit.id ? "Class rescheduled" : "New live class scheduled", mr: edit.id ? "वर्ग पुनर्नियोजित" : "नवीन लाईव्ह वर्ग" }, body: { en: `${edit.title} – ${edit.date} ${edit.time}`, mr: `${edit.title} – ${edit.date} ${edit.time}` }, at: nowISO(), read: false, kind: "class" }));
    }, { action: edit.id ? "live.update" : "live.create", entity: edit.title });
    setEdit(null);
    toast.success(t("Saved. Students notified.", "जतन केले. विद्यार्थ्यांना कळवले."));
  };
  const cancel = (id: string) => update((db) => { const c = db.live.find((x) => x.id === id)!; c.status = "cancelled"; studentsIn(db, c.batchId).forEach((s) => db.inbox.unshift({ id: uid("in"), studentId: s.id, title: { en: "Class cancelled", mr: "वर्ग रद्द" }, body: { en: c.title, mr: c.title }, at: nowISO(), read: false, kind: "class" })); }, { action: "live.cancel", entity: id });
  const subjects = db.subjects.filter((s) => db.batches.some((b) => b.id === edit?.batchId && b.courseId === s.courseId));
  return (
    <div>
      <PageHeader title={t("Live & recorded classes", "लाईव्ह व रेकॉर्डेड वर्ग")} actions={scope.canManage && <button className="btn-primary" onClick={() => setEdit({ batchId: scope.batchIds[0], date: today(), time: "18:00", durationMin: 60, provider: "meet" })}><Plus className="size-4" />{t("Schedule class", "वर्ग नियोजित करा")}</button>} />
      <Table head={[t("Class", "वर्ग"), t("Batch", "बॅच"), t("When", "केव्हा"), t("Status", "स्थिती"), t("Attended", "उपस्थित"), ""]} empty={!list.length}>
        {list.map((c) => (
          <tr key={c.id}>
            <td><p className="font-medium">{c.title}</p><p className="text-xs text-slate-500">{l(db.subjects.find((s) => s.id === c.subjectId)?.name)} · {db.staff.find((s) => s.id === c.facultyId)?.name}</p></td>
            <td className="text-xs">{db.batches.find((b) => b.id === c.batchId)?.name}</td>
            <td className="whitespace-nowrap text-xs">{d(c.date)} {c.time}<br />{c.durationMin} min · {c.provider}</td>
            <td><StatusBadge s={c.status} /></td>
            <td>{c.attendees.length}</td>
            <td>{scope.canManage && c.status !== "cancelled" && c.status !== "completed" && <div className="flex gap-1"><button className="btn-ghost btn-sm" onClick={() => setEdit(c)} aria-label="Edit"><Pencil className="size-4" /></button><button className="btn-ghost btn-sm text-red-600" onClick={() => cancel(c.id)}>{t("Cancel", "रद्द")}</button></div>}</td>
          </tr>
        ))}
      </Table>
      <RecordingManager scope={scope} />
      <Modal open={!!edit} onClose={() => setEdit(null)} title={edit?.id ? t("Edit / reschedule class", "वर्ग संपादित / पुनर्नियोजित") : t("Schedule live class", "लाईव्ह वर्ग नियोजित करा")} footer={<button className="btn-primary" onClick={save}>{t("Save & notify", "जतन करा व कळवा")}</button>}>
        {edit && (
          <div className="grid gap-3 sm:grid-cols-2">
            <Field label={t("Title", "शीर्षक")} required className="sm:col-span-2">{(id) => <input id={id} className="input" value={edit.title ?? ""} onChange={(e) => setEdit({ ...edit, title: e.target.value })} />}</Field>
            <Field label={t("Batch", "बॅच")} required>{(id) => <select id={id} className="input" value={edit.batchId} onChange={(e) => setEdit({ ...edit, batchId: e.target.value })}>{db.batches.filter((b) => scope.batchIds.includes(b.id)).map((b) => <option key={b.id} value={b.id}>{b.name}</option>)}</select>}</Field>
            <Field label={t("Subject", "विषय")}>{(id) => <select id={id} className="input" value={edit.subjectId ?? ""} onChange={(e) => setEdit({ ...edit, subjectId: e.target.value })}><option value="">—</option>{subjects.map((s) => <option key={s.id} value={s.id}>{l(s.name)}</option>)}</select>}</Field>
            <Field label={t("Date", "दिनांक")} required>{(id) => <input id={id} type="date" className="input" value={edit.date} onChange={(e) => setEdit({ ...edit, date: e.target.value })} />}</Field>
            <Field label={t("Time", "वेळ")} required>{(id) => <input id={id} type="time" className="input" value={edit.time} onChange={(e) => setEdit({ ...edit, time: e.target.value })} />}</Field>
            <Field label={t("Duration (min)", "कालावधी (मिनिटे)")}>{(id) => <input id={id} type="number" className="input" value={edit.durationMin} onChange={(e) => setEdit({ ...edit, durationMin: Number(e.target.value) })} />}</Field>
            <Field label={t("Provider", "माध्यम")}>{(id) => <select id={id} className="input" value={edit.provider} onChange={(e) => setEdit({ ...edit, provider: e.target.value as LiveClass["provider"] })}><option value="meet">Google Meet</option><option value="zoom">Zoom</option><option value="other">Other</option></select>}</Field>
            <Field label={t("Joining link", "लिंक")} required className="sm:col-span-2">{(id) => <input id={id} className="input" placeholder="https://" value={edit.link ?? ""} onChange={(e) => setEdit({ ...edit, link: e.target.value })} />}</Field>
          </div>
        )}
      </Modal>
    </div>
  );
}

function RecordingManager({ scope }: { scope: Scope }) {
  const { t, l } = useI18n();
  const { db, update } = useStore();
  const courseIds = [...new Set(db.batches.filter((b) => scope.batchIds.includes(b.id)).map((b) => b.courseId))];
  const list = db.recordings.filter((r) => courseIds.includes(r.courseId));
  const [add, setAdd] = useState(false);
  const [f, setF] = useState({ title: "", url: "", subjectId: "", durationMin: 45, source: "external" as "external" | "hosted" });
  const save = () => {
    const s = db.subjects.find((x) => x.id === f.subjectId);
    if (!f.title || !s) return toast.error(t("Title and subject are required", "शीर्षक व विषय आवश्यक"));
    update((db) => db.recordings.push({ id: uid("rec"), courseId: s.courseId, subjectId: s.id, title: { en: f.title, mr: f.title }, durationMin: f.durationMin, source: f.source, url: f.url, published: false, availableFrom: today(), batchIds: scope.batchIds.filter((b) => db.batches.find((x) => x.id === b)?.courseId === s.courseId) }), { action: "recording.add", entity: f.title });
    setAdd(false);
  };
  return (
    <Card className="mt-6" title={t("Recorded lectures", "रेकॉर्डेड व्याख्याने")} actions={scope.canManage && <button className="btn-outline btn-sm" onClick={() => setAdd(true)}><Plus className="size-4" />{t("Add", "जोडा")}</button>} bodyClass="p-0">
      <table className="table-base"><thead><tr><th>{t("Title", "शीर्षक")}</th><th>{t("Subject", "विषय")}</th><th>{t("Source", "स्रोत")}</th><th>{t("Published", "प्रकाशित")}</th></tr></thead>
        <tbody>{list.map((r) => <tr key={r.id}><td className="font-medium">{l(r.title)}</td><td>{l(db.subjects.find((s) => s.id === r.subjectId)?.name)}</td><td>{r.source === "hosted" ? t("Secure hosted", "सुरक्षित होस्टेड") : t("External link", "बाह्य लिंक")}</td><td><Toggle disabled={!scope.canManage} checked={r.published} onChange={(v) => update((db) => { db.recordings.find((x) => x.id === r.id)!.published = v; }, { action: v ? "recording.publish" : "recording.unpublish", entity: r.title.en })} /></td></tr>)}</tbody></table>
      <Modal open={add} onClose={() => setAdd(false)} title={t("Add recorded lecture", "रेकॉर्डेड व्याख्यान जोडा")} footer={<button className="btn-primary" onClick={save}>{t("Save (unpublished)", "जतन (अप्रकाशित)")}</button>}>
        <div className="grid gap-3">
          <Field label={t("Title", "शीर्षक")} required>{(id) => <input id={id} className="input" value={f.title} onChange={(e) => setF({ ...f, title: e.target.value })} />}</Field>
          <Field label={t("Subject", "विषय")} required>{(id) => <select id={id} className="input" value={f.subjectId} onChange={(e) => setF({ ...f, subjectId: e.target.value })}><option value="">—</option>{db.subjects.filter((s) => courseIds.includes(s.courseId)).map((s) => <option key={s.id} value={s.id}>{l(s.name)} ({l(db.courses.find((c) => c.id === s.courseId)?.name)})</option>)}</select>}</Field>
          <Field label={t("Source", "स्रोत")}>{(id) => <select id={id} className="input" value={f.source} onChange={(e) => setF({ ...f, source: e.target.value as "external" | "hosted" })}><option value="external">{t("External link (YouTube unlisted, Vimeo…)", "बाह्य लिंक")}</option><option value="hosted">{t("Academy-hosted secure streaming", "अकॅडमी सुरक्षित स्ट्रीमिंग")}</option></select>}</Field>
          {f.source === "external" ? <Field label="URL">{(id) => <input id={id} className="input" value={f.url} onChange={(e) => setF({ ...f, url: e.target.value })} />}</Field> : <Field label={t("Video file", "व्हिडिओ फाइल")} hint={t("Upload goes to secure storage in production.", "प्रत्यक्षात सुरक्षित स्टोरेजमध्ये अपलोड होईल.")}>{(id) => <input id={id} type="file" accept="video/*" className="input" />}</Field>}
          <Field label={t("Duration (min)", "कालावधी")}>{(id) => <input id={id} type="number" className="input" value={f.durationMin} onChange={(e) => setF({ ...f, durationMin: Number(e.target.value) })} />}</Field>
        </div>
      </Modal>
    </Card>
  );
}

/* ---------------- Study materials ---------------- */
export function MaterialsManager({ scope, canApprove, canPublishDirect }: { scope: Scope; canApprove: boolean; canPublishDirect: boolean }) {
  const { t, l, d } = useI18n();
  const { db, update } = useStore();
  const [tab, setTab] = useState<"all" | "pending">(canApprove ? "pending" : "all");
  const [up, setUp] = useState(false);
  const courseIds = [...new Set(db.batches.filter((b) => scope.batchIds.includes(b.id)).map((b) => b.courseId))];
  const [f, setF] = useState({ titleEn: "", titleMr: "", subjectId: "", access: "both" as MaterialAccess, lang: "both" as Material["lang"], availableFrom: today(), watermark: true, sizeKb: 0, batchIds: [] as string[] });
  const list = db.materials.filter((m) => m.batchIds.some((b) => scope.batchIds.includes(b)) && (tab === "all" || m.status === "pending")).sort((a, b) => b.uploadedAt.localeCompare(a.uploadedAt));
  const needsApproval = db.settings.materialApproval && !canPublishDirect;
  const save = () => {
    const s = db.subjects.find((x) => x.id === f.subjectId);
    if (!f.titleEn || !s || !f.batchIds.length) return toast.error(t("Title, subject, batch and file are required", "शीर्षक, विषय, बॅच व फाइल आवश्यक"));
    update((db) => db.materials.unshift({ id: uid("m"), courseId: s.courseId, subjectId: s.id, batchIds: f.batchIds, title: { en: f.titleEn, mr: f.titleMr || f.titleEn }, kind: "pdf", sizeKb: f.sizeKb || 1024, access: f.access, status: needsApproval ? "pending" : "approved", uploadedBy: scope.actorId, uploadedAt: today(), availableFrom: f.availableFrom, lang: f.lang, downloads: 0, watermark: f.watermark }), { action: "material.upload", entity: f.titleEn, detail: needsApproval ? "Pending approval" : "Published directly" });
    setUp(false);
    toast.success(needsApproval ? t("Uploaded – waiting for admin approval", "अपलोड झाले – प्रशासकीय मंजुरी बाकी") : t("Published to students", "विद्यार्थ्यांसाठी प्रकाशित"));
  };
  const decide = (id: string, status: "approved" | "rejected") => update((db) => { const m = db.materials.find((x) => x.id === id)!; m.status = status; if (status === "approved") m.batchIds.forEach((b) => studentsIn(db, b).forEach((s) => db.inbox.unshift({ id: uid("in"), studentId: s.id, title: { en: "New study material", mr: "नवीन अभ्यास साहित्य" }, body: m.title, at: nowISO(), read: false, kind: "material" }))); }, { action: `material.${status}`, entity: id });
  return (
    <div>
      <PageHeader title={t("Study materials", "अभ्यास साहित्य")} actions={scope.canManage && <button className="btn-primary" onClick={() => { setF({ ...f, titleEn: "", titleMr: "", batchIds: scope.batchIds.slice(0, 1) }); setUp(true); }}><Upload className="size-4" />{t("Upload", "अपलोड")}</button>} />
      {!canApprove && <div className="mb-4"><Notice>{needsApproval ? t("Your uploads go to the admin for approval before students can see them.", "आपले अपलोड विद्यार्थ्यांना दिसण्यापूर्वी प्रशासकीय मंजुरीसाठी जातात.") : t("You are allowed to publish without approval.", "आपल्याला मंजुरीशिवाय प्रकाशित करण्याची परवानगी आहे.")}</Notice></div>}
      <Tabs tabs={[{ id: "all", label: t("All", "सर्व") }, { id: "pending", label: t("Pending approval", "मंजुरी बाकी"), count: db.materials.filter((m) => m.status === "pending" && m.batchIds.some((b) => scope.batchIds.includes(b))).length }]} value={tab} onChange={setTab} />
      <Table head={[t("Material", "साहित्य"), t("Access", "प्रवेश"), t("Available", "उपलब्ध"), t("Downloads", "डाउनलोड"), t("Status", "स्थिती"), ""]} empty={!list.length}>
        {list.map((m) => (
          <tr key={m.id}>
            <td><p className="font-medium">{l(m.title)}</p><p className="text-xs text-slate-500">{l(db.subjects.find((s) => s.id === m.subjectId)?.name)} · {fmtSize(m.sizeKb)} · {db.staff.find((s) => s.id === m.uploadedBy)?.name}</p></td>
            <td className="text-xs">{m.access}{m.watermark && <Badge className="ml-1">WM</Badge>}</td>
            <td className="text-xs">{d(m.availableFrom)}</td>
            <td>{m.downloads}</td>
            <td><StatusBadge s={m.status} /></td>
            <td>{canApprove && m.status === "pending" && <div className="flex gap-1"><button className="btn-ghost btn-sm text-emerald-700" onClick={() => decide(m.id, "approved")}><Check className="size-4" />{t("Approve", "मंजूर")}</button><button className="btn-ghost btn-sm text-red-600" onClick={() => decide(m.id, "rejected")}><X className="size-4" /></button></div>}</td>
          </tr>
        ))}
      </Table>
      <Modal open={up} onClose={() => setUp(false)} title={t("Upload study material", "अभ्यास साहित्य अपलोड")} size="lg" footer={<button className="btn-primary" onClick={save}>{needsApproval ? t("Submit for approval", "मंजुरीसाठी पाठवा") : t("Publish", "प्रकाशित करा")}</button>}>
        <div className="grid gap-3 sm:grid-cols-2">
          <Field label={t("Title (English)", "शीर्षक (इंग्रजी)")} required>{(id) => <input id={id} className="input" value={f.titleEn} onChange={(e) => setF({ ...f, titleEn: e.target.value })} />}</Field>
          <Field label={t("Title (Marathi)", "शीर्षक (मराठी)")}>{(id) => <input id={id} className="input" value={f.titleMr} onChange={(e) => setF({ ...f, titleMr: e.target.value })} />}</Field>
          <Field label={t("Subject", "विषय")} required>{(id) => <select id={id} className="input" value={f.subjectId} onChange={(e) => setF({ ...f, subjectId: e.target.value })}><option value="">—</option>{db.subjects.filter((s) => courseIds.includes(s.courseId)).map((s) => <option key={s.id} value={s.id}>{l(s.name)} – {l(db.courses.find((c) => c.id === s.courseId)?.name)}</option>)}</select>}</Field>
          <Field label={t("File (PDF / image)", "फाइल (PDF / प्रतिमा)")} required>{(id) => <input id={id} type="file" accept="application/pdf,image/*" className="input" onChange={(e) => setF({ ...f, sizeKb: Math.round((e.target.files?.[0]?.size ?? 0) / 1024) })} />}</Field>
          <Field label={t("Access", "प्रवेश")}>{(id) => <select id={id} className="input" value={f.access} onChange={(e) => setF({ ...f, access: e.target.value as MaterialAccess })}><option value="view">{t("View only", "फक्त पाहणे")}</option><option value="download">{t("Download only", "फक्त डाउनलोड")}</option><option value="both">{t("View & download", "पाहणे व डाउनलोड")}</option><option value="restricted">{t("Restricted", "मर्यादित")}</option></select>}</Field>
          <Field label={t("Content language", "मजकूर भाषा")}>{(id) => <select id={id} className="input" value={f.lang} onChange={(e) => setF({ ...f, lang: e.target.value as Material["lang"] })}><option value="both">मराठी + English</option><option value="mr">मराठी</option><option value="en">English</option></select>}</Field>
          <Field label={t("Available from", "उपलब्ध दिनांक")}>{(id) => <input id={id} type="date" className="input" value={f.availableFrom} onChange={(e) => setF({ ...f, availableFrom: e.target.value })} />}</Field>
          <div className="flex items-end pb-2"><Toggle checked={f.watermark} onChange={(v) => setF({ ...f, watermark: v })} label={t("Watermark with student ID", "विद्यार्थी ओळखीचा वॉटरमार्क")} /></div>
          <div className="sm:col-span-2"><p className="label">{t("Batches", "बॅचेस")}</p><div className="flex flex-wrap gap-2">{db.batches.filter((b) => scope.batchIds.includes(b.id)).map((b) => <label key={b.id} className="flex items-center gap-1.5 rounded-md border px-2 py-1 text-sm"><input type="checkbox" checked={f.batchIds.includes(b.id)} onChange={(e) => setF({ ...f, batchIds: e.target.checked ? [...f.batchIds, b.id] : f.batchIds.filter((x) => x !== b.id) })} />{b.name}</label>)}</div></div>
        </div>
      </Modal>
    </div>
  );
}

/* ---------------- Assignments ---------------- */
export function AssignmentsManager({ scope }: { scope: Scope }) {
  const { t, l, d, dt } = useI18n();
  const { db, update } = useStore();
  const [create, setCreate] = useState(false);
  const [open, setOpen] = useState<string | null>(null);
  const [grade, setGrade] = useState<{ id: string; marks: number; feedback: string } | null>(null);
  const [f, setF] = useState({ batchId: scope.batchIds[0] ?? "", subjectId: "", title: "", instructions: "", due: today(), maxMarks: 20, passMarks: 8, allowLate: true, allowResubmit: true });
  const list = db.assignments.filter((a) => scope.batchIds.includes(a.batchId));
  const a = db.assignments.find((x) => x.id === open);
  const save = () => {
    if (!f.title || !f.batchId) return toast.error(t("Title and batch required", "शीर्षक व बॅच आवश्यक"));
    update((db) => { db.assignments.unshift({ id: uid("as"), ...f, attachments: [], issued: today(), createdBy: scope.actorId }); studentsIn(db, f.batchId).forEach((s) => db.inbox.unshift({ id: uid("in"), studentId: s.id, title: { en: "New assignment", mr: "नवीन गृहपाठ" }, body: { en: `${f.title} – due ${f.due}`, mr: `${f.title} – अंतिम ${f.due}` }, at: nowISO(), read: false, kind: "test" })); }, { action: "assignment.create", entity: f.title });
    setCreate(false);
  };
  const saveGrade = (status: "graded" | "correction") => {
    if (!grade) return;
    update((db) => { const s = db.submissions.find((x) => x.id === grade.id)!; Object.assign(s, { status, marks: status === "graded" ? grade.marks : undefined, feedback: grade.feedback }); db.inbox.unshift({ id: uid("in"), studentId: s.studentId, title: { en: status === "graded" ? "Assignment graded" : "Correction requested", mr: status === "graded" ? "गृहपाठ तपासला" : "दुरुस्ती आवश्यक" }, body: { en: grade.feedback || "-", mr: grade.feedback || "-" }, at: nowISO(), read: false, kind: "result" }); });
    setGrade(null);
  };
  const subjects = db.subjects.filter((s) => s.courseId === db.batches.find((b) => b.id === f.batchId)?.courseId);
  return (
    <div>
      <PageHeader title={t("Assignments", "गृहपाठ")} actions={scope.canManage && <button className="btn-primary" onClick={() => setCreate(true)}><Plus className="size-4" />{t("New assignment", "नवीन गृहपाठ")}</button>} />
      <Table head={[t("Assignment", "गृहपाठ"), t("Batch", "बॅच"), t("Due", "अंतिम"), t("Submitted", "जमा"), t("Pending review", "तपासणी बाकी"), t("Late", "उशिरा"), ""]} empty={!list.length}>
        {list.map((x) => {
          const subs = db.submissions.filter((s) => s.assignmentId === x.id);
          const total = studentsIn(db, x.batchId).length;
          return <tr key={x.id}><td className="font-medium">{x.title}<p className="text-xs text-slate-500">{l(db.subjects.find((s) => s.id === x.subjectId)?.name)}</p></td><td className="text-xs">{db.batches.find((b) => b.id === x.batchId)?.name}</td><td className="text-xs">{d(x.due)}</td><td>{subs.length}/{total}</td><td>{subs.filter((s) => s.status === "submitted").length}</td><td>{subs.filter((s) => s.late).length}</td><td><button className="btn-ghost btn-sm" onClick={() => setOpen(x.id)}>{t("Review", "तपासा")}</button></td></tr>;
        })}
      </Table>
      {a && (
        <Modal open onClose={() => setOpen(null)} title={a.title} size="lg">
          <table className="table-base"><thead><tr><th>{t("Student", "विद्यार्थी")}</th><th>{t("Files", "फायली")}</th><th>{t("Status", "स्थिती")}</th><th>{t("Marks", "गुण")}</th><th /></tr></thead>
            <tbody>{studentsIn(db, a.batchId).map((s) => { const sub = db.submissions.find((x) => x.assignmentId === a.id && x.studentId === s.id); return <tr key={s.id}><td>{s.name}</td><td className="text-xs">{sub ? <>{sub.files.map((f) => <span key={f.name} className="mr-1 inline-flex items-center gap-1"><FileText className="size-3" />{f.name}</span>)}<br />{dt(sub.submittedAt)}{sub.late && <Badge tone="orange" className="ml-1">late</Badge>}</> : "—"}</td><td>{sub ? <StatusBadge s={sub.status} /> : <Badge>{t("Not submitted", "जमा नाही")}</Badge>}</td><td>{sub?.marks ?? "—"}/{a.maxMarks}</td><td>{sub && scope.canManage && <button className="btn-outline btn-sm" onClick={() => setGrade({ id: sub.id, marks: sub.marks ?? 0, feedback: sub.feedback ?? "" })}>{t("Grade", "गुण द्या")}</button>}</td></tr>; })}</tbody></table>
        </Modal>
      )}
      <Modal open={!!grade} onClose={() => setGrade(null)} title={t("Grade submission", "गुण द्या")} footer={<><button className="btn-outline" onClick={() => saveGrade("correction")}>{t("Request correction", "दुरुस्ती मागवा")}</button><button className="btn-primary" onClick={() => saveGrade("graded")}>{t("Save grade", "गुण जतन")}</button></>}>
        {grade && <div className="grid gap-3"><Field label={`${t("Marks", "गुण")} (/${a?.maxMarks})`}>{(id) => <input id={id} type="number" className="input" value={grade.marks} max={a?.maxMarks} onChange={(e) => setGrade({ ...grade, marks: Number(e.target.value) })} />}</Field><Field label={t("Feedback", "अभिप्राय")}>{(id) => <textarea id={id} rows={3} className="input" value={grade.feedback} onChange={(e) => setGrade({ ...grade, feedback: e.target.value })} />}</Field></div>}
      </Modal>
      <Modal open={create} onClose={() => setCreate(false)} title={t("New assignment", "नवीन गृहपाठ")} size="lg" footer={<button className="btn-primary" onClick={save}>{t("Create & notify", "तयार करा व कळवा")}</button>}>
        <div className="grid gap-3 sm:grid-cols-2">
          <Field label={t("Title", "शीर्षक")} required className="sm:col-span-2">{(id) => <input id={id} className="input" value={f.title} onChange={(e) => setF({ ...f, title: e.target.value })} />}</Field>
          <Field label={t("Batch", "बॅच")} required>{(id) => <select id={id} className="input" value={f.batchId} onChange={(e) => setF({ ...f, batchId: e.target.value })}>{db.batches.filter((b) => scope.batchIds.includes(b.id)).map((b) => <option key={b.id} value={b.id}>{b.name}</option>)}</select>}</Field>
          <Field label={t("Subject", "विषय")}>{(id) => <select id={id} className="input" value={f.subjectId} onChange={(e) => setF({ ...f, subjectId: e.target.value })}><option value="">—</option>{subjects.map((s) => <option key={s.id} value={s.id}>{l(s.name)}</option>)}</select>}</Field>
          <Field label={t("Instructions", "सूचना")} className="sm:col-span-2">{(id) => <textarea id={id} rows={3} className="input" value={f.instructions} onChange={(e) => setF({ ...f, instructions: e.target.value })} />}</Field>
          <Field label={t("Deadline", "अंतिम दिनांक")}>{(id) => <input id={id} type="date" className="input" value={f.due} onChange={(e) => setF({ ...f, due: e.target.value })} />}</Field>
          <Field label={t("Attachments", "जोडण्या")}>{(id) => <input id={id} type="file" multiple className="input" />}</Field>
          <Field label={t("Max marks", "कमाल गुण")}>{(id) => <input id={id} type="number" className="input" value={f.maxMarks} onChange={(e) => setF({ ...f, maxMarks: Number(e.target.value) })} />}</Field>
          <Field label={t("Passing marks", "उत्तीर्ण गुण")}>{(id) => <input id={id} type="number" className="input" value={f.passMarks} onChange={(e) => setF({ ...f, passMarks: Number(e.target.value) })} />}</Field>
          <Toggle checked={f.allowLate} onChange={(v) => setF({ ...f, allowLate: v })} label={t("Allow late submission", "उशिरा जमा चालेल")} />
          <Toggle checked={f.allowResubmit} onChange={(v) => setF({ ...f, allowResubmit: v })} label={t("Allow resubmission", "पुन्हा जमा चालेल")} />
        </div>
      </Modal>
    </div>
  );
}

/* ---------------- Question bank ---------------- */
const TEMPLATE_HEADERS = ["course_id", "subject_id", "chapter_id", "difficulty", "question_en", "question_mr", "option1_en", "option1_mr", "option2_en", "option2_mr", "option3_en", "option3_mr", "option4_en", "option4_mr", "correct_option", "explanation_en", "explanation_mr", "marks", "negative"];
export function QuestionBank({ canManage }: { canManage: boolean }) {
  const { t, l, lc } = useI18n();
  const { db, update } = useStore();
  const [q, setQ] = useState("");
  const [subject, setSubject] = useState("all");
  const [diff, setDiff] = useState("all");
  const [edit, setEdit] = useState<Question | null>(null);
  const [imp, setImp] = useState<{ rows: { row: number; data?: Question; errors: string[]; dup?: boolean }[] } | null>(null);
  const list = db.questions.filter((x) => (subject === "all" || x.subjectId === subject) && (diff === "all" || x.difficulty === diff) && (x.text.en + x.text.mr).toLowerCase().includes(q.toLowerCase()));
  const blank = (): Question => ({ id: "", courseId: db.courses[0].id, subjectId: db.subjects[0].id, text: { en: "", mr: "" }, options: [0, 1, 2, 3].map(() => ({ en: "", mr: "" })), answer: 0, explanation: { en: "", mr: "" }, difficulty: "medium", marks: 2, negative: 0.5 });
  const save = () => {
    if (!edit || !edit.text.en.trim() || edit.options.some((o) => !o.en.trim())) return toast.error(t("Question and all options (English at least) are required", "प्रश्न व सर्व पर्याय (किमान इंग्रजी) आवश्यक"));
    update((db) => { if (edit.id) Object.assign(db.questions.find((x) => x.id === edit.id)!, edit); else db.questions.push({ ...edit, id: uid("q"), courseId: db.subjects.find((s) => s.id === edit.subjectId)!.courseId }); }, { action: edit.id ? "question.update" : "question.create", entity: edit.text.en.slice(0, 40) });
    setEdit(null);
  };
  const onFile = async (file: File) => {
    const rows = parseCSV(await file.text());
    const [head, ...body] = rows;
    const idx = (k: string) => head.indexOf(k);
    const missing = TEMPLATE_HEADERS.filter((h) => idx(h) < 0);
    if (missing.length) return toast.error(`Missing columns: ${missing.join(", ")}`);
    const existing = new Set(db.questions.map((x) => x.text.en.trim().toLowerCase()));
    const seen = new Set<string>();
    const out = body.map((r, i) => {
      const g = (k: string) => (r[idx(k)] ?? "").trim();
      const errors: string[] = [];
      const subj = db.subjects.find((s) => s.id === g("subject_id"));
      if (!subj) errors.push("unknown subject_id");
      if (!g("question_en") && !g("question_mr")) errors.push("question text missing");
      for (let k = 1; k <= 4; k++) if (!g(`option${k}_en`) && !g(`option${k}_mr`)) errors.push(`option${k} missing`);
      const ans = Number(g("correct_option"));
      if (!(ans >= 1 && ans <= 4)) errors.push("correct_option must be 1–4");
      if (!["easy", "medium", "hard"].includes(g("difficulty"))) errors.push("difficulty must be easy/medium/hard");
      const key = g("question_en").toLowerCase();
      const dup = existing.has(key) || seen.has(key);
      seen.add(key);
      const data: Question | undefined = errors.length ? undefined : {
        id: uid("q"), courseId: subj!.courseId, subjectId: subj!.id, chapterId: g("chapter_id") || undefined, difficulty: g("difficulty") as Question["difficulty"],
        text: { en: g("question_en"), mr: g("question_mr") || g("question_en") }, options: [1, 2, 3, 4].map((k) => ({ en: g(`option${k}_en`), mr: g(`option${k}_mr`) || g(`option${k}_en`) })),
        answer: ans - 1, explanation: { en: g("explanation_en"), mr: g("explanation_mr") }, marks: Number(g("marks")) || 1, negative: Number(g("negative")) || 0,
      };
      return { row: i + 2, data, errors, dup };
    });
    setImp({ rows: out });
  };
  const template = () => downloadFile("question-import-template.csv", [TEMPLATE_HEADERS.join(","), ["c_rajyaseva", "s_hist", "ch_h3", "easy", "Who founded the Satyashodhak Samaj?", "सत्यशोधक समाजाची स्थापना कोणी केली?", "Agarkar", "आगरकर", "Jyotirao Phule", "ज्योतिराव फुले", "Tilak", "टिळक", "Ranade", "रानडे", "2", "Founded in 1873.", "१८७३ मध्ये स्थापना.", "2", "0.5"].join(","), ["c_rajyaseva", "s_bad", "", "medium", "Row with an error", "", "a", "", "b", "", "", "", "d", "", "7", "", "", "2", "0"].join(",")].join("\n"));
  return (
    <div>
      <PageHeader title={t("Question bank", "प्रश्नपेढी")} subtitle={`${db.questions.length} ${t("questions", "प्रश्न")}`} actions={canManage && <><button className="btn-outline" onClick={template}><Download className="size-4" />{t("Template", "नमुना")}</button><label className="btn-outline cursor-pointer"><FileUp className="size-4" />{t("Import CSV/Excel", "CSV/Excel आयात")}<input type="file" accept=".csv" className="sr-only" onChange={(e) => e.target.files?.[0] && onFile(e.target.files[0])} /></label><button className="btn-primary" onClick={() => setEdit(blank())}><Plus className="size-4" />{t("Add question", "प्रश्न जोडा")}</button></>} />
      <div className="mb-4 flex flex-wrap gap-2">
        <SearchBox value={q} onChange={setQ} placeholder={t("Search questions", "प्रश्न शोधा")} />
        <Select label="Subject" value={subject} onChange={setSubject} options={[{ value: "all", label: t("All subjects", "सर्व विषय") }, ...db.subjects.map((s) => ({ value: s.id, label: `${l(s.name)} · ${l(db.courses.find((c) => c.id === s.courseId)?.name)}` }))]} />
        <Select label="Difficulty" value={diff} onChange={setDiff} options={[{ value: "all", label: t("All levels", "सर्व स्तर") }, { value: "easy", label: "Easy" }, { value: "medium", label: "Medium" }, { value: "hard", label: "Hard" }]} />
      </div>
      <Table head={[t("Question", "प्रश्न"), t("Subject", "विषय"), t("Level", "स्तर"), t("Marks", "गुण"), t("Used in", "वापर"), ""]} empty={!list.length}>
        {list.map((x) => <tr key={x.id}><td className="max-w-md"><p className="font-medium">{lc(x.text)}</p><p className="text-xs text-emerald-700">✓ {lc(x.options[x.answer])}</p></td><td className="text-xs">{l(db.subjects.find((s) => s.id === x.subjectId)?.name)}</td><td><StatusBadge s={x.difficulty} /></td><td className="text-xs">+{x.marks}/−{x.negative}</td><td>{db.tests.filter((tt) => tt.questionIds.includes(x.id)).length} {t("tests", "चाचण्या")}</td><td>{canManage && <button className="btn-ghost btn-sm" onClick={() => setEdit(structuredClone(x))} aria-label="Edit"><Pencil className="size-4" /></button>}</td></tr>)}
      </Table>
      <Modal open={!!edit} onClose={() => setEdit(null)} title={edit?.id ? t("Edit question", "प्रश्न संपादित") : t("Add question", "प्रश्न जोडा")} size="xl" footer={<button className="btn-primary" onClick={save}>{t("Save", "जतन")}</button>}>
        {edit && (
          <div className="grid gap-3 sm:grid-cols-2">
            <Field label={t("Subject", "विषय")}>{(id) => <select id={id} className="input" value={edit.subjectId} onChange={(e) => setEdit({ ...edit, subjectId: e.target.value, chapterId: undefined })}>{db.subjects.map((s) => <option key={s.id} value={s.id}>{l(s.name)} – {l(db.courses.find((c) => c.id === s.courseId)?.name)}</option>)}</select>}</Field>
            <Field label={t("Chapter", "प्रकरण")}>{(id) => <select id={id} className="input" value={edit.chapterId ?? ""} onChange={(e) => setEdit({ ...edit, chapterId: e.target.value || undefined })}><option value="">—</option>{db.subjects.find((s) => s.id === edit.subjectId)?.chapters.map((c) => <option key={c.id} value={c.id}>{l(c.name)}</option>)}</select>}</Field>
            <Field label="Question (English)">{(id) => <textarea id={id} rows={2} className="input" value={edit.text.en} onChange={(e) => setEdit({ ...edit, text: { ...edit.text, en: e.target.value } })} />}</Field>
            <Field label="प्रश्न (मराठी)">{(id) => <textarea id={id} rows={2} className="input" value={edit.text.mr} onChange={(e) => setEdit({ ...edit, text: { ...edit.text, mr: e.target.value } })} />}</Field>
            {edit.options.map((o, i) => (
              <div key={i} className="flex gap-2 sm:col-span-2">
                <label className="flex items-center gap-1 text-sm"><input type="radio" name="correct" checked={edit.answer === i} onChange={() => setEdit({ ...edit, answer: i })} />{String.fromCharCode(65 + i)}</label>
                <input className="input" placeholder="English" value={o.en} onChange={(e) => { const opts = [...edit.options]; opts[i] = { ...o, en: e.target.value }; setEdit({ ...edit, options: opts }); }} aria-label={`Option ${i + 1} English`} />
                <input className="input" placeholder="मराठी" value={o.mr} onChange={(e) => { const opts = [...edit.options]; opts[i] = { ...o, mr: e.target.value }; setEdit({ ...edit, options: opts }); }} aria-label={`Option ${i + 1} Marathi`} />
              </div>
            ))}
            <Field label="Explanation (English)">{(id) => <textarea id={id} rows={2} className="input" value={edit.explanation.en} onChange={(e) => setEdit({ ...edit, explanation: { ...edit.explanation, en: e.target.value } })} />}</Field>
            <Field label="स्पष्टीकरण (मराठी)">{(id) => <textarea id={id} rows={2} className="input" value={edit.explanation.mr} onChange={(e) => setEdit({ ...edit, explanation: { ...edit.explanation, mr: e.target.value } })} />}</Field>
            <Field label={t("Difficulty", "स्तर")}>{(id) => <select id={id} className="input" value={edit.difficulty} onChange={(e) => setEdit({ ...edit, difficulty: e.target.value as Question["difficulty"] })}><option value="easy">Easy</option><option value="medium">Medium</option><option value="hard">Hard</option></select>}</Field>
            <div className="grid grid-cols-2 gap-2"><Field label={t("Marks", "गुण")}>{(id) => <input id={id} type="number" className="input" value={edit.marks} onChange={(e) => setEdit({ ...edit, marks: Number(e.target.value) })} />}</Field><Field label={t("Negative", "नकारात्मक")}>{(id) => <input id={id} type="number" step="0.25" className="input" value={edit.negative} onChange={(e) => setEdit({ ...edit, negative: Number(e.target.value) })} />}</Field></div>
            <Field label={t("Reference (optional)", "संदर्भ (ऐच्छिक)")}>{(id) => <input id={id} className="input" value={edit.reference ?? ""} onChange={(e) => setEdit({ ...edit, reference: e.target.value })} />}</Field>
            <Field label={t("Image (optional)", "प्रतिमा (ऐच्छिक)")}>{(id) => <input id={id} type="file" accept="image/*" className="input" />}</Field>
            <p className="text-xs text-slate-500 sm:col-span-2">{t("Machine translation drafts may be used but must be reviewed before publishing.", "यांत्रिक भाषांतराचा मसुदा वापरता येईल, परंतु प्रकाशनापूर्वी तपासणी आवश्यक.")}</p>
          </div>
        )}
      </Modal>
      <Modal open={!!imp} onClose={() => setImp(null)} title={t("Import preview", "आयात पूर्वावलोकन")} size="xl" footer={imp && <button className="btn-primary" disabled={!imp.rows.some((r) => r.data && !r.dup)} onClick={() => { const good = imp.rows.filter((r) => r.data && !r.dup).map((r) => r.data!); update((db) => db.questions.push(...good), { action: "question.import", entity: "CSV", detail: `${good.length} rows imported` }); setImp(null); toast.success(`${good.length} ${t("questions imported", "प्रश्न आयात")}`); }}>{t("Import valid rows", "वैध ओळी आयात करा")} ({imp.rows.filter((r) => r.data && !r.dup).length})</button>}>
        {imp && <table className="table-base"><thead><tr><th>{t("Row", "ओळ")}</th><th>{t("Question", "प्रश्न")}</th><th>{t("Result", "परिणाम")}</th></tr></thead><tbody>{imp.rows.map((r) => <tr key={r.row}><td>{r.row}</td><td>{r.data?.text.en ?? "—"}</td><td>{r.errors.length ? <span className="flex items-center gap-1 text-red-600"><XCircle className="size-4" />{r.errors.join("; ")}</span> : r.dup ? <Badge tone="amber">{t("Duplicate – skipped", "डुप्लिकेट – वगळले")}</Badge> : <span className="flex items-center gap-1 text-emerald-700"><CheckCircle2 className="size-4" />OK</span>}</td></tr>)}</tbody></table>}
      </Modal>
    </div>
  );
}

/* ---------------- Tests ---------------- */
export function TestsManager({ scope }: { scope: Scope }) {
  const { t, l, d, lang } = useI18n();
  const { db, update } = useStore();
  const [edit, setEdit] = useState<Test | null>(null);
  const courseIds = [...new Set(db.batches.filter((b) => scope.batchIds.includes(b.id)).map((b) => b.courseId))];
  const list = db.tests.filter((x) => x.batchIds.some((b) => scope.batchIds.includes(b)));
  const blank = (): Test => ({ id: "", title: { en: "", mr: "" }, type: "chapter", courseId: courseIds[0], batchIds: [], questionIds: [], durationMin: 30, negativeMark: 0.5, passPercent: 40, attemptsAllowed: 1, availableFrom: today(), availableTo: today(), resultMode: "immediate", resultsPublished: false, showExplanations: true, shuffle: true, status: "draft" });
  const save = () => {
    if (!edit || !edit.title.en || !edit.questionIds.length || !edit.batchIds.length) return toast.error(t("Title, batches and at least one question are required", "शीर्षक, बॅच व किमान एक प्रश्न आवश्यक"));
    update((db) => { if (edit.id) Object.assign(db.tests.find((x) => x.id === edit.id)!, edit); else db.tests.push({ ...edit, id: uid("t"), title: { en: edit.title.en, mr: edit.title.mr || edit.title.en } }); }, { action: edit.id ? "test.update" : "test.create", entity: edit.title.en });
    setEdit(null);
  };
  const publishResults = (id: string) => update((db) => { const x = db.tests.find((y) => y.id === id)!; x.resultsPublished = true; db.attempts.filter((a) => a.testId === id).forEach((a) => db.inbox.unshift({ id: uid("in"), studentId: a.studentId, title: { en: `Result published: ${x.title.en}`, mr: `निकाल प्रसिद्ध: ${x.title.mr}` }, body: { en: "Open Results to see your score and rank.", mr: "गुण व क्रमांक पाहण्यासाठी निकाल उघडा." }, at: nowISO(), read: false, kind: "result" })); }, { action: "test.publishResults", entity: id });
  return (
    <div>
      <PageHeader title={t("Examinations", "परीक्षा")} actions={scope.canManage && <button className="btn-primary" onClick={() => setEdit(blank())}><Plus className="size-4" />{t("Create test", "चाचणी तयार करा")}</button>} />
      <Table head={[t("Test", "चाचणी"), t("Type", "प्रकार"), t("Window", "कालावधी"), t("Qs / min", "प्रश्न / मि."), t("Attempts", "प्रयत्न"), t("Status", "स्थिती"), t("Results", "निकाल"), ""]} empty={!list.length}>
        {list.map((x) => {
          const atts = db.attempts.filter((a) => a.testId === x.id && a.submittedAt);
          const flagged = atts.filter((a) => a.tabSwitches > 0).length;
          return (
            <tr key={x.id}>
              <td className="font-medium">{l(x.title)}</td><td className="text-xs">{TEST_TYPE[x.type][lang === "mr" ? 1 : 0]}</td><td className="text-xs">{d(x.availableFrom)} – {d(x.availableTo)}</td><td>{x.questionIds.length} / {x.durationMin}</td>
              <td>{atts.length}{flagged > 0 && <Badge tone="amber" className="ml-1" >{flagged} ⚠</Badge>}</td><td><StatusBadge s={x.status} /></td>
              <td className="text-xs">{x.resultMode === "immediate" ? t("Immediate", "तात्काळ") : x.resultsPublished ? <StatusBadge s="published" /> : scope.canManage ? <button className="btn-outline btn-sm" onClick={() => publishResults(x.id)}>{t("Publish", "प्रसिद्ध करा")}</button> : <StatusBadge s="pending" />}</td>
              <td>{scope.canManage && <button className="btn-ghost btn-sm" onClick={() => setEdit(structuredClone(x))} aria-label="Edit"><Pencil className="size-4" /></button>}</td>
            </tr>
          );
        })}
      </Table>
      <Modal open={!!edit} onClose={() => setEdit(null)} title={edit?.id ? t("Edit test", "चाचणी संपादित") : t("Create test", "चाचणी तयार करा")} size="xl" footer={<button className="btn-primary" onClick={save}>{t("Save", "जतन")}</button>}>
        {edit && (
          <div className="grid gap-3 sm:grid-cols-3">
            <Field label="Title (English)" required className="sm:col-span-2">{(id) => <input id={id} className="input" value={edit.title.en} onChange={(e) => setEdit({ ...edit, title: { ...edit.title, en: e.target.value } })} />}</Field>
            <Field label={t("Type", "प्रकार")}>{(id) => <select id={id} className="input" value={edit.type} onChange={(e) => setEdit({ ...edit, type: e.target.value as TestType })}>{Object.entries(TEST_TYPE).map(([k, v]) => <option key={k} value={k}>{v[lang === "mr" ? 1 : 0]}</option>)}</select>}</Field>
            <Field label="शीर्षक (मराठी)" className="sm:col-span-2">{(id) => <input id={id} className="input" value={edit.title.mr} onChange={(e) => setEdit({ ...edit, title: { ...edit.title, mr: e.target.value } })} />}</Field>
            <Field label={t("Course", "अभ्यासक्रम")}>{(id) => <select id={id} className="input" value={edit.courseId} onChange={(e) => setEdit({ ...edit, courseId: e.target.value, batchIds: [], questionIds: [] })}>{courseIds.map((c) => <option key={c} value={c}>{l(db.courses.find((x) => x.id === c)?.name)}</option>)}</select>}</Field>
            <Field label={t("Duration (min)", "वेळ (मि.)")}>{(id) => <input id={id} type="number" className="input" value={edit.durationMin} onChange={(e) => setEdit({ ...edit, durationMin: Number(e.target.value) })} />}</Field>
            <Field label={t("Negative mark / wrong", "नकारात्मक गुण")}>{(id) => <input id={id} type="number" step="0.25" className="input" value={edit.negativeMark} onChange={(e) => setEdit({ ...edit, negativeMark: Number(e.target.value) })} />}</Field>
            <Field label={t("Pass %", "उत्तीर्ण %")}>{(id) => <input id={id} type="number" className="input" value={edit.passPercent} onChange={(e) => setEdit({ ...edit, passPercent: Number(e.target.value) })} />}</Field>
            <Field label={t("Attempts allowed", "प्रयत्न मर्यादा")}>{(id) => <input id={id} type="number" min={1} className="input" value={edit.attemptsAllowed} onChange={(e) => setEdit({ ...edit, attemptsAllowed: Number(e.target.value) })} />}</Field>
            <Field label={t("Available from", "पासून")}>{(id) => <input id={id} type="date" className="input" value={edit.availableFrom} onChange={(e) => setEdit({ ...edit, availableFrom: e.target.value })} />}</Field>
            <Field label={t("Available to", "पर्यंत")}>{(id) => <input id={id} type="date" className="input" value={edit.availableTo} onChange={(e) => setEdit({ ...edit, availableTo: e.target.value })} />}</Field>
            <Field label={t("Results", "निकाल")}>{(id) => <select id={id} className="input" value={edit.resultMode} onChange={(e) => setEdit({ ...edit, resultMode: e.target.value as Test["resultMode"] })}><option value="immediate">{t("Immediate", "तात्काळ")}</option><option value="manual">{t("Published by admin", "प्रशासक प्रसिद्ध करेल")}</option></select>}</Field>
            <div className="flex flex-col gap-2 sm:col-span-3 sm:flex-row sm:gap-6">
              <Toggle checked={edit.shuffle} onChange={(v) => setEdit({ ...edit, shuffle: v })} label={t("Random question order", "प्रश्नांचा यादृच्छिक क्रम")} />
              <Toggle checked={edit.showExplanations} onChange={(v) => setEdit({ ...edit, showExplanations: v })} label={t("Show answer key & explanations", "उत्तरतालिका व स्पष्टीकरण दाखवा")} />
              <Toggle checked={edit.status === "published"} onChange={(v) => setEdit({ ...edit, status: v ? "published" : "draft" })} label={t("Published", "प्रकाशित")} />
            </div>
            <div className="sm:col-span-3"><p className="label">{t("Batches", "बॅचेस")}</p><div className="flex flex-wrap gap-2">{db.batches.filter((b) => b.courseId === edit.courseId && scope.batchIds.includes(b.id)).map((b) => <label key={b.id} className="flex items-center gap-1.5 rounded-md border px-2 py-1 text-sm"><input type="checkbox" checked={edit.batchIds.includes(b.id)} onChange={(e) => setEdit({ ...edit, batchIds: e.target.checked ? [...edit.batchIds, b.id] : edit.batchIds.filter((x) => x !== b.id) })} />{b.name}</label>)}</div></div>
            <div className="sm:col-span-3">
              <p className="label">{t("Questions from bank", "प्रश्नपेढीतील प्रश्न")} ({edit.questionIds.length} · {edit.questionIds.reduce((s, q) => s + (db.questions.find((x) => x.id === q)?.marks ?? 0), 0)} {t("marks", "गुण")})</p>
              <div className="max-h-60 space-y-1 overflow-y-auto rounded-lg border p-2">{db.questions.filter((q) => q.courseId === edit.courseId).map((q) => <label key={q.id} className="flex items-start gap-2 rounded px-1 py-1 text-sm hover:bg-slate-50"><input type="checkbox" className="mt-1" checked={edit.questionIds.includes(q.id)} onChange={(e) => setEdit({ ...edit, questionIds: e.target.checked ? [...edit.questionIds, q.id] : edit.questionIds.filter((x) => x !== q.id) })} /><span className="flex-1">{l(q.text)}</span><StatusBadge s={q.difficulty} /></label>)}</div>
            </div>
          </div>
        )}
      </Modal>
    </div>
  );
}

/* ---------------- Results & rankings ---------------- */
export function ResultsBoard({ scope }: { scope: Scope }) {
  const { t, l } = useI18n();
  const db = useStore((s) => s.db);
  const tests = db.tests.filter((x) => x.batchIds.some((b) => scope.batchIds.includes(b)) && db.attempts.some((a) => a.testId === x.id && a.submittedAt));
  const [tid, setTid] = useState(tests[0]?.id ?? "");
  const [batch, setBatch] = useState("all");
  const test = db.tests.find((x) => x.id === tid);
  const rows = useMemo(() => {
    if (!test) return [];
    const best = new Map<string, typeof db.attempts[number]>();
    db.attempts.filter((a) => a.testId === test.id && a.submittedAt).forEach((a) => { const b = best.get(a.studentId); if (!b || a.score > b.score) best.set(a.studentId, a); });
    return [...best.values()].map((a) => ({ a, s: db.students.find((x) => x.id === a.studentId)!, e: db.enrolments.find((e) => e.studentId === a.studentId && test.batchIds.includes(e.batchId)) }))
      .filter((r) => r.e && scope.batchIds.includes(r.e.batchId) && (batch === "all" || r.e.batchId === batch)).sort((x, y) => y.a.score - x.a.score);
  }, [db, test, batch, scope.batchIds]);
  if (!test) return <EmptyState title={t("No results yet", "अद्याप निकाल नाहीत")} />;
  const max = test.questionIds.reduce((s, q) => s + (db.questions.find((x) => x.id === q)?.marks ?? 0), 0);
  const avg = rows.length ? rows.reduce((s, r) => s + r.a.score, 0) / rows.length : 0;
  const exp = () => downloadFile(`results-${test.id}.csv`, toCSV(rows.map((r, i) => ({ Rank: i + 1, StudentID: r.s.code, Name: r.s.name, Batch: db.batches.find((b) => b.id === r.e?.batchId)?.name, Score: r.a.score, Max: max, Correct: r.a.correct, Wrong: r.a.incorrect, Skipped: r.a.unanswered, TabSwitches: r.a.tabSwitches }))));
  return (
    <div>
      <PageHeader title={t("Results & rankings", "निकाल व क्रमवारी")} actions={<button className="btn-outline" onClick={exp}><Download className="size-4" />CSV / Excel</button>} />
      <div className="mb-4 flex flex-wrap gap-2">
        <Select label="Test" value={tid} onChange={setTid} options={tests.map((x) => ({ value: x.id, label: l(x.title) }))} />
        <Select label="Batch" value={batch} onChange={setBatch} options={[{ value: "all", label: t("Academy-wide", "संपूर्ण अकॅडमी") }, ...test.batchIds.filter((b) => scope.batchIds.includes(b)).map((b) => ({ value: b, label: db.batches.find((x) => x.id === b)?.name ?? b }))]} />
      </div>
      <div className="mb-4 grid grid-cols-3 gap-3 text-center">
        <div className="card p-3"><p className="text-xs text-slate-500">{t("Candidates", "उमेदवार")}</p><p className="text-xl font-bold">{rows.length}</p></div>
        <div className="card p-3"><p className="text-xs text-slate-500">{t("Average", "सरासरी")}</p><p className="text-xl font-bold">{avg.toFixed(1)} / {max}</p></div>
        <div className="card p-3"><p className="text-xs text-slate-500">{t("Topper", "प्रथम")}</p><p className="text-xl font-bold">{rows[0]?.a.score ?? "—"}</p></div>
      </div>
      <Table head={[t("Rank", "क्रमांक"), t("Student", "विद्यार्थी"), t("Batch", "बॅच"), t("Score", "गुण"), "✓ / ✗ / –", t("Flags", "इशारे")]} empty={!rows.length}>
        {rows.map((r, i) => <tr key={r.a.id}><td className="font-bold">#{i + 1}</td><td>{r.s.name}<p className="text-xs text-slate-500">{r.s.code}</p></td><td className="text-xs">{db.batches.find((b) => b.id === r.e?.batchId)?.name}</td><td className="font-semibold">{r.a.score}/{max}</td><td className="text-xs">{r.a.correct} / {r.a.incorrect} / {r.a.unanswered}</td><td>{r.a.tabSwitches > 0 ? <Badge tone="amber">{r.a.tabSwitches} {t("tab switches", "टॅब बदल")}</Badge> : "—"}</td></tr>)}
      </Table>
      <p className="mt-2 text-xs text-slate-500">{t("Tab-switch tracking is an indicator only and does not guarantee detection of malpractice.", "टॅब-बदल नोंद केवळ सूचक आहे; गैरप्रकार पूर्णपणे ओळखण्याची हमी नाही.")}</p>
    </div>
  );
}

