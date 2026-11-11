"use client";
import { useMemo, useState } from "react";
import { QRCodeSVG } from "qrcode.react";
import { Armchair, Check, Download, ExternalLink, Eye, LogIn, LogOut, Pencil, Plus, Printer, RefreshCw, RotateCcw, Send, Undo2, X, Archive } from "lucide-react";
import { useStore } from "@/lib/store";
import { useI18n } from "@/lib/i18n";
import { attendanceStats, balanceFor, createMembership, membershipFee, createStudent, membershipPaid, paidFor, PAY_METHOD_LABEL, recordPayment } from "@/lib/actions";
import { buildSeed, DEFAULT_HOME_PHOTOS, DEFAULT_HOME_SECTIONS } from "@/lib/seed";
import { addMonths, cn, daysBetween, downloadFile, fmtINR, isValidMobile, nowISO, toCSV, today, uid } from "@/lib/utils";
import { Badge, BarChart, Card, EmptyState, Field, Modal, Notice, PageHeader, SearchBox, Select, StatCard, StatusBadge, Table, Tabs, Toggle, toast, statusLabel } from "@/components/ui";
import { ReceiptModal } from "@/components/Receipt";
import { GoogleReviews } from "@/components/GoogleReviews";
import { IdCardFace, TICKET_CATS } from "./student";
import { DATE_LABEL } from "./public-pages";
import { academy } from "@/config/academy";
import type { Achievement, Announcement, Testimonial, Channel, ExamUpdate, HomePhoto, HomeSection, HomeSectionKey, ImportantDateKind, L, LibraryPlan, PayMethod, PromoCode, Settings, Ticket, UpdateSource } from "@/lib/types";

function useA() {
  const db = useStore((s) => s.db);
  const session = useStore((s) => s.session);
  const update = useStore((s) => s.update);
  const me = db.staff.find((x) => x.id === session.userId)!;
  return { db, me, update, isOwner: me?.role === "owner" };
}

/* ---------------- Fees & payments ---------------- */
export function Fees({ canManage }: { canManage: boolean }) {
  const { t, l, d, dt, lang } = useI18n();
  const { db, me, update } = useA();
  const [tab, setTab] = useState<"due" | "tx" | "offers">("due");
  const [q, setQ] = useState("");
  const [pay, setPay] = useState<{ enrolmentId?: string; membershipId?: string; studentId: string; amount: number; method: PayMethod; reference: string; note: string } | null>(null);
  const [receipt, setReceipt] = useState<string | null>(null);
  const [refund, setRefund] = useState<{ id: string; reason: string } | null>(null);
  const [promo, setPromo] = useState<PromoCode | null>(null);
  const collected = db.payments.filter((p) => p.status === "success").reduce((a, p) => a + p.amount, 0);
  const refunded = db.payments.filter((p) => p.status === "refunded").reduce((a, p) => a + p.amount, 0);
  const due = db.enrolments.map((e) => ({ e, s: db.students.find((x) => x.id === e.studentId)!, bal: balanceFor(db, e), paid: paidFor(db, e.id) })).filter((r) => r.bal > 0 && r.e.admissionStatus !== "cancelled" && r.s.name.toLowerCase().includes(q.toLowerCase()));
  const overdueOf = (e: typeof db.enrolments[number]) => { const paid = paidFor(db, e.id); let cum = 0, od = 0; e.installments.forEach((i) => { cum += i.amount; if (i.due < today() && paid < cum) od += Math.min(i.amount, cum - paid); }); return od; };
  const tx = db.payments.filter((p) => (p.receiptNo + (db.students.find((s) => s.id === p.studentId)?.name ?? "") + p.reference).toLowerCase().includes(q.toLowerCase())).sort((a, b) => b.date.localeCompare(a.date));
  const savePay = () => {
    if (!pay || pay.amount <= 0) return toast.error(t("Enter a valid amount", "वैध रक्कम लिहा"));
    if (pay.method !== "cash" && !pay.reference.trim()) return toast.error(t("Transaction reference is required", "व्यवहार संदर्भ आवश्यक"));
    let pid = "";
    update((db) => { pid = recordPayment(db, { ...pay, by: me.id }).id; if (pay.membershipId) { const m = db.memberships.find((x) => x.id === pay.membershipId); if (m && m.status === "suspended") m.status = "active"; } }, { action: "payment.record", entity: db.students.find((s) => s.id === pay.studentId)?.code ?? "", detail: `₹${pay.amount} ${pay.method} ${pay.reference}` });
    setPay(null); setReceipt(pid);
    toast.success(t("Payment recorded and receipt generated", "पेमेंट नोंदवले व पावती तयार"));
  };
  const doRefund = () => {
    if (!refund || refund.reason.trim().length < 3) return toast.error(t("Reason required", "कारण आवश्यक"));
    update((db) => {
      const p = db.payments.find((x) => x.id === refund.id)!;
      db.payments.push({ ...p, id: uid("pay"), receiptNo: `${p.receiptNo}-R`, status: "refunded", date: nowISO(), recordedBy: me.id, note: refund.reason, reprints: 0 });
      if (p.enrolmentId) { const e = db.enrolments.find((x) => x.id === p.enrolmentId)!; const paid = paidFor(db, e.id); e.paymentStatus = paid <= 0 ? "refunded" : paid >= e.finalFee ? "paid" : "partial"; e.history.push({ at: nowISO(), text: `Refund ₹${p.amount}: ${refund.reason}`, by: me.name }); }
    }, { action: "payment.refund", entity: db.payments.find((x) => x.id === refund.id)?.receiptNo ?? "", detail: refund.reason });
    setRefund(null);
  };
  const savePromo = () => {
    if (!promo?.code) return;
    update((db) => { if (promo.id) Object.assign(db.promos.find((x) => x.id === promo.id)!, promo); else db.promos.push({ ...promo, id: uid("pr"), code: promo.code.toUpperCase() }); }, { action: "promo.save", entity: promo.code });
    setPromo(null);
  };
  return (
    <div>
      <PageHeader title={t("Fees & payments", "शुल्क व पेमेंट")} actions={<button className="btn-outline" onClick={() => downloadFile("payments.csv", toCSV(tx.map((p) => ({ Receipt: p.receiptNo, Date: p.date, Student: db.students.find((s) => s.id === p.studentId)?.name, Amount: p.amount, Method: p.method, Reference: p.reference, Status: p.status }))))}><Download className="size-4" />CSV</button>} />
      <div className="mb-5 grid grid-cols-2 gap-3 lg:grid-cols-4">
        <StatCard label={t("Collected", "जमा")} value={fmtINR(collected)} tone="green" />
        <StatCard label={t("Outstanding", "थकबाकी")} value={fmtINR(db.enrolments.reduce((a, e) => a + balanceFor(db, e), 0))} tone="amber" />
        <StatCard label={t("Overdue", "मुदत उलटलेले")} value={fmtINR(db.enrolments.reduce((a, e) => a + overdueOf(e), 0))} tone="red" />
        <StatCard label={t("Refunds", "परतावा")} value={fmtINR(refunded)} tone="violet" />
      </div>
      <Tabs tabs={[{ id: "due", label: t("Outstanding", "थकबाकी"), count: due.length }, { id: "tx", label: t("Transactions", "व्यवहार") }, { id: "offers", label: t("Discounts & offers", "सवलती व ऑफर्स") }]} value={tab} onChange={setTab} />
      {tab !== "offers" && <div className="mb-3"><SearchBox value={q} onChange={setQ} placeholder={t("Search", "शोधा")} /></div>}
      {tab === "due" && (
        <Table head={[t("Student", "विद्यार्थी"), t("Course", "अभ्यासक्रम"), t("Final fee", "अंतिम शुल्क"), t("Paid", "भरले"), t("Balance", "शिल्लक"), t("Overdue", "मुदत उलटलेले"), ""]} empty={!due.length}>
          {due.map(({ e, s, bal, paid }) => <tr key={e.id}><td><p className="font-medium">{s.name}</p><p className="text-xs text-slate-500">{s.code}</p></td><td className="text-xs">{l(db.courses.find((c) => c.id === e.courseId)?.name)}<br />{db.batches.find((b) => b.id === e.batchId)?.name}</td><td>{fmtINR(e.finalFee)}{e.discount > 0 && <p className="text-xs text-emerald-700" title={e.discountReason}>−{fmtINR(e.discount)}</p>}</td><td>{fmtINR(paid)}</td><td className="font-semibold">{fmtINR(bal)}</td><td className="text-red-600">{overdueOf(e) ? fmtINR(overdueOf(e)) : "—"}</td><td><div className="flex gap-1">{canManage && <button className="btn-primary btn-sm" onClick={() => setPay({ enrolmentId: e.id, studentId: s.id, amount: bal, method: "cash", reference: "", note: "" })}>{t("Record payment", "पेमेंट नोंदवा")}</button>}<button className="btn-ghost btn-sm" title="Reminder" onClick={() => { update((db) => db.inbox.unshift({ id: uid("in"), studentId: s.id, title: { en: "Fee reminder", mr: "शुल्क स्मरणपत्र" }, body: { en: `Outstanding balance ${fmtINR(bal)}.`, mr: `थकबाकी ${fmtINR(bal)}.` }, at: nowISO(), read: false, kind: "payment" })); toast.success(t("Reminder sent", "स्मरणपत्र पाठवले")); }}><Send className="size-4" /></button></div></td></tr>)}
        </Table>
      )}
      {tab === "tx" && (
        <Table head={[t("Receipt", "पावती"), t("Date", "दिनांक"), t("Student", "विद्यार्थी"), t("Method", "पद्धत"), t("Reference", "संदर्भ"), t("Amount", "रक्कम"), t("Status", "स्थिती"), ""]} empty={!tx.length}>
          {tx.map((p) => <tr key={p.id}><td className="font-mono text-xs">{p.receiptNo}</td><td className="text-xs">{dt(p.date)}</td><td>{db.students.find((s) => s.id === p.studentId)?.name}</td><td className="text-xs">{PAY_METHOD_LABEL[p.method][lang === "mr" ? 1 : 0]}</td><td className="font-mono text-xs">{p.reference}</td><td className={cn("font-semibold", p.status === "refunded" && "text-violet-700")}>{p.status === "refunded" ? "−" : ""}{fmtINR(p.amount)}</td><td><StatusBadge s={p.status} /></td><td><div className="flex gap-1">{p.status === "success" && <button className="btn-ghost btn-sm" onClick={() => setReceipt(p.id)} aria-label="Receipt"><Printer className="size-4" /></button>}{p.status === "success" && canManage && !db.payments.some((x) => x.receiptNo === `${p.receiptNo}-R`) && <button className="btn-ghost btn-sm text-violet-700" title={t("Refund", "परतावा")} onClick={() => setRefund({ id: p.id, reason: "" })}><Undo2 className="size-4" /></button>}</div></td></tr>)}
        </Table>
      )}
      {tab === "offers" && (
        <>
          {canManage && <div className="mb-3 flex justify-end"><button className="btn-primary" onClick={() => setPromo({ id: "", code: "", kind: "promo", type: "percent", value: 10, validFrom: today(), validTo: today(), courseIds: [], limit: 50, used: 0, minPayment: 0, needsApproval: false, active: true })}><Plus className="size-4" />{t("New offer", "नवीन ऑफर")}</button></div>}
          <Table head={[t("Code", "कोड"), t("Type", "प्रकार"), t("Value", "मूल्य"), t("Valid", "वैधता"), t("Courses", "अभ्यासक्रम"), t("Used / limit", "वापर / मर्यादा"), t("Approval", "मंजुरी"), t("Active", "सक्रिय"), ""]}>
            {db.promos.map((p) => <tr key={p.id}><td className="font-mono font-semibold">{p.code}</td><td className="text-xs">{p.kind}</td><td>{p.type === "flat" ? fmtINR(p.value) : `${p.value}%`}</td><td className="text-xs">{d(p.validFrom)} – {d(p.validTo)}</td><td className="text-xs">{p.courseIds.length ? p.courseIds.map((c) => l(db.courses.find((x) => x.id === c)?.name)).join(", ") : t("All", "सर्व")}</td><td>{p.used}/{p.limit}</td><td>{p.needsApproval ? <Badge tone="amber">{t("Required", "आवश्यक")}</Badge> : "—"}</td><td><Toggle disabled={!canManage} checked={p.active} onChange={(v) => update((db) => { db.promos.find((x) => x.id === p.id)!.active = v; }, { action: "promo.toggle", entity: p.code })} /></td><td>{canManage && <button className="btn-ghost btn-sm" onClick={() => setPromo(structuredClone(p))}><Pencil className="size-4" /></button>}</td></tr>)}
          </Table>
        </>
      )}
      <Modal open={!!pay} onClose={() => setPay(null)} title={t("Record offline payment", "ऑफलाइन पेमेंट नोंदवा")} footer={<button className="btn-primary" onClick={savePay}>{t("Save & generate receipt", "जतन करा व पावती तयार करा")}</button>}>
        {pay && (
          <div className="grid gap-3 sm:grid-cols-2">
            <p className="text-sm sm:col-span-2">{db.students.find((s) => s.id === pay.studentId)?.name}</p>
            <Field label={t("Amount (₹)", "रक्कम (₹)")} required hint={t("Partial payments allowed", "अंशतः रक्कम चालेल")}>{(id) => <input id={id} type="number" className="input" value={pay.amount} onChange={(e) => setPay({ ...pay, amount: Number(e.target.value) })} />}</Field>
            <Field label={t("Method", "पद्धत")}>{(id) => <select id={id} className="input" value={pay.method} onChange={(e) => setPay({ ...pay, method: e.target.value as PayMethod })}>{(Object.keys(PAY_METHOD_LABEL) as PayMethod[]).map((m) => <option key={m} value={m}>{PAY_METHOD_LABEL[m][lang === "mr" ? 1 : 0]}</option>)}</select>}</Field>
            <Field label={t("Transaction reference", "व्यवहार संदर्भ")} required={pay.method !== "cash"} className="sm:col-span-2">{(id) => <input id={id} className="input" value={pay.reference} placeholder={pay.method === "cash" ? "CASH" : "UTR / Txn ID"} onChange={(e) => setPay({ ...pay, reference: e.target.value })} />}</Field>
            <Field label={t("Note", "टीप")} className="sm:col-span-2">{(id) => <input id={id} className="input" value={pay.note} onChange={(e) => setPay({ ...pay, note: e.target.value })} />}</Field>
          </div>
        )}
      </Modal>
      <Modal open={!!refund} onClose={() => setRefund(null)} title={t("Record refund", "परतावा नोंदवा")} footer={<button className="btn-danger" onClick={doRefund}>{t("Record refund", "परतावा नोंदवा")}</button>}>
        <Notice tone="warn">{t("The original payment is preserved; a refund entry is added and audit-logged.", "मूळ पेमेंट जतन राहते; परतावा नोंद जोडली जाते व ऑडिट होते.")}</Notice>
        <Field label={t("Reason", "कारण")} required className="mt-3">{(id) => <textarea id={id} className="input" rows={2} value={refund?.reason ?? ""} onChange={(e) => refund && setRefund({ ...refund, reason: e.target.value })} />}</Field>
      </Modal>
      <Modal open={!!promo} onClose={() => setPromo(null)} title={t("Offer / discount rule", "ऑफर / सवलत नियम")} size="lg" footer={<button className="btn-primary" onClick={savePromo}>{t("Save", "जतन")}</button>}>
        {promo && (
          <div className="grid gap-3 sm:grid-cols-3">
            <Field label={t("Code", "कोड")} required>{(id) => <input id={id} className="input font-mono uppercase" value={promo.code} onChange={(e) => setPromo({ ...promo, code: e.target.value.toUpperCase() })} />}</Field>
            <Field label={t("Kind", "प्रकार")}>{(id) => <select id={id} className="input" value={promo.kind} onChange={(e) => setPromo({ ...promo, kind: e.target.value as PromoCode["kind"] })}>{["earlybird", "promo", "referral", "seasonal", "scholarship"].map((k) => <option key={k}>{k}</option>)}</select>}</Field>
            <div className="grid grid-cols-2 gap-2"><Field label={t("Type", "प्रकार")}>{(id) => <select id={id} className="input" value={promo.type} onChange={(e) => setPromo({ ...promo, type: e.target.value as PromoCode["type"] })}><option value="percent">%</option><option value="flat">₹</option></select>}</Field><Field label={t("Value", "मूल्य")}>{(id) => <input id={id} type="number" className="input" value={promo.value} onChange={(e) => setPromo({ ...promo, value: Number(e.target.value) })} />}</Field></div>
            <Field label={t("Valid from", "पासून")}>{(id) => <input id={id} type="date" className="input" value={promo.validFrom} onChange={(e) => setPromo({ ...promo, validFrom: e.target.value })} />}</Field>
            <Field label={t("Valid to", "पर्यंत")}>{(id) => <input id={id} type="date" className="input" value={promo.validTo} onChange={(e) => setPromo({ ...promo, validTo: e.target.value })} />}</Field>
            <Field label={t("Usage limit", "वापर मर्यादा")}>{(id) => <input id={id} type="number" className="input" value={promo.limit} onChange={(e) => setPromo({ ...promo, limit: Number(e.target.value) })} />}</Field>
            <Field label={t("Minimum fee (₹)", "किमान शुल्क (₹)")}>{(id) => <input id={id} type="number" className="input" value={promo.minPayment} onChange={(e) => setPromo({ ...promo, minPayment: Number(e.target.value) })} />}</Field>
            <div className="sm:col-span-2"><p className="label">{t("Eligible courses (none = all)", "पात्र अभ्यासक्रम (काहीही नाही = सर्व)")}</p><div className="flex flex-wrap gap-2">{db.courses.map((c) => <label key={c.id} className="flex items-center gap-1 text-xs"><input type="checkbox" checked={promo.courseIds.includes(c.id)} onChange={(e) => setPromo({ ...promo, courseIds: e.target.checked ? [...promo.courseIds, c.id] : promo.courseIds.filter((x) => x !== c.id) })} />{l(c.name)}</label>)}</div></div>
            <Toggle checked={promo.needsApproval} onChange={(v) => setPromo({ ...promo, needsApproval: v })} label={t("Requires approval", "मंजुरी आवश्यक")} />
          </div>
        )}
      </Modal>
      <ReceiptModal paymentId={receipt} onClose={() => setReceipt(null)} />
    </div>
  );
}

export function Receipts() {
  const { t, dt } = useI18n();
  const { db } = useA();
  const [q, setQ] = useState("");
  const [r, setR] = useState<string | null>(null);
  const list = db.payments.filter((p) => p.status === "success" && (p.receiptNo + (db.students.find((s) => s.id === p.studentId)?.name ?? "")).toLowerCase().includes(q.toLowerCase())).sort((a, b) => b.date.localeCompare(a.date));
  return (
    <div>
      <PageHeader title={t("Receipts", "पावत्या")} subtitle={t("Reprints keep the original receipt number and are logged as duplicates.", "पुनर्मुद्रणात मूळ पावती क्रमांक कायम राहतो व 'प्रत' म्हणून नोंद होते.")} actions={<SearchBox value={q} onChange={setQ} placeholder={t("Receipt no. / student", "पावती क्र. / विद्यार्थी")} />} />
      <Table head={[t("Receipt", "पावती"), t("Date", "दिनांक"), t("Student", "विद्यार्थी"), t("Amount", "रक्कम"), t("Prints", "प्रिंट"), ""]} empty={!list.length}>
        {list.map((p) => <tr key={p.id}><td className="font-mono text-xs">{p.receiptNo}</td><td className="text-xs">{dt(p.date)}</td><td>{db.students.find((s) => s.id === p.studentId)?.name}</td><td className="font-semibold">{fmtINR(p.amount)}</td><td>{p.reprints ? <Badge tone="orange">{p.reprints}×</Badge> : "—"}</td><td><button className="btn-outline btn-sm" onClick={() => setR(p.id)}><Eye className="size-4" />{t("View / print", "पहा / प्रिंट")}</button></td></tr>)}
      </Table>
      <ReceiptModal paymentId={r} onClose={() => setR(null)} />
    </div>
  );
}

/* ---------------- Library ---------------- */
const SEATS = 80;
export function LibraryAdmin({ canManage }: { canManage: boolean }) {
  const { t, l, d, lang } = useI18n();
  const { db, me, update } = useA();
  const [tab, setTab] = useState<"members" | "desk" | "seats" | "plans">("members");
  const [filter, setFilter] = useState("all");
  const [q, setQ] = useState("");
  const [join, setJoin] = useState<{ mode: "existing" | "new"; studentId: string; name: string; mobile: string; planId: string; start: string; discount: number; payNow: boolean } | null>(null);
  const [receipt, setReceipt] = useState<string | null>(null);
  const [plan, setPlan] = useState<LibraryPlan | null>(null);
  const active = db.memberships.filter((m) => m.status === "active");
  const expiring = active.filter((m) => daysBetween(today(), m.end) <= 7);
  const collection = db.payments.filter((p) => p.membershipId && p.status === "success").reduce((a, p) => a + p.amount, 0);
  const outstanding = db.memberships.reduce((a, m) => a + Math.max(0, m.fee - membershipPaid(db, m.id)), 0);
  const list = db.memberships.filter((m) => (filter === "all" || m.status === filter || (filter === "expiring" && m.status === "active" && daysBetween(today(), m.end) <= 7)) && (db.students.find((s) => s.id === m.studentId)?.name ?? "").toLowerCase().includes(q.toLowerCase())).sort((a, b) => a.end.localeCompare(b.end));
  const seatOf = new Map(active.map((m) => [m.seat, m]));
  const save = () => {
    if (!join) return;
    if (join.mode === "new" && (join.name.length < 2 || !isValidMobile(join.mobile))) return toast.error(t("Name and valid mobile required", "नाव व वैध मोबाईल आवश्यक"));
    if (join.mode === "existing" && !join.studentId) return toast.error(t("Select student", "विद्यार्थी निवडा"));
    if (active.length >= SEATS) return toast.error(t("Abhyasika Kendra is at full capacity", "अभ्यासिका केंद्र पूर्ण भरले आहे"));
    let pid: string | null = null;
    update((db) => {
      const sid = join.mode === "new" ? createStudent(db, { name: join.name, mobile: join.mobile }).id : join.studentId;
      const m = createMembership(db, { studentId: sid, planId: join.planId, start: join.start, discount: join.discount });
      if (join.payNow) pid = recordPayment(db, { studentId: sid, membershipId: m.id, amount: m.fee, method: "cash", reference: "CASH", by: me.id }).id;
    }, { action: "library.membership", entity: join.name || join.studentId, detail: join.planId });
    setJoin(null);
    if (pid) setReceipt(pid);
    toast.success(t("Membership created", "सदस्यत्व तयार"));
  };
  const setStatus = (id: string, status: string) => update((db) => { db.memberships.find((m) => m.id === id)!.status = status as never; }, { action: "library.status", entity: id, detail: status });
  const renew = (id: string) => update((db) => { const m = db.memberships.find((x) => x.id === id)!; const p = db.libraryPlans.find((x) => x.id === m.planId)!; const start = m.end >= today() ? m.end : today(); const n = createMembership(db, { studentId: m.studentId, planId: p.id, start, discount: 0, renewal: true }); n.seat = m.seat; if (m.end < today()) m.status = "expired"; }, { action: "library.renew", entity: id });
  const checkin = (mid: string) => update((db) => { const open = db.checkins.find((c) => c.membershipId === mid && c.date === today() && !c.outTime); const time = new Date().toTimeString().slice(0, 5); if (open) open.outTime = time; else db.checkins.push({ id: uid("ci"), membershipId: mid, date: today(), inTime: time }); });
  return (
    <div>
      <PageHeader title={t("Abhyasika Kendra management", "अभ्यासिका केंद्र व्यवस्थापन")} subtitle={t("Separate business module: Abhyasika Kendra only and course + Abhyasika Kendra bundle memberships.", "स्वतंत्र व्यवसाय विभाग: फक्त अभ्यासिका केंद्र व अभ्यासक्रम + अभ्यासिका केंद्र संच सदस्यत्व.")} actions={canManage && <button className="btn-primary" onClick={() => setJoin({ mode: "new", studentId: "", name: "", mobile: "", planId: db.libraryPlans[0].id, start: today(), discount: 0, payNow: true })}><Plus className="size-4" />{t("New membership", "नवीन सदस्यत्व")}</button>} />
      <div className="mb-5 grid grid-cols-2 gap-3 lg:grid-cols-5">
        <StatCard label={t("Active members", "सक्रिय सदस्य")} value={active.length} tone="green" />
        <StatCard label={t("Occupancy", "आसन वापर")} value={`${active.length}/${SEATS}`} hint={`${SEATS - active.length} ${t("seats free", "आसने रिकामी")}`} />
        <StatCard label={t("Expiring ≤ 7 days", "७ दिवसांत संपणारे")} value={expiring.length} tone="amber" />
        <StatCard label={t("Collection", "संकलन")} value={fmtINR(collection)} tone="violet" />
        <StatCard label={t("Outstanding", "थकबाकी")} value={fmtINR(outstanding)} tone="red" />
      </div>
      <Tabs tabs={[{ id: "members", label: t("Memberships", "सदस्यत्व") }, { id: "desk", label: t("Check-in desk", "प्रवेश नोंद") }, { id: "seats", label: t("Seat map", "आसन नकाशा") }, { id: "plans", label: t("Plans & pricing", "योजना व दर") }]} value={tab} onChange={setTab} />
      {tab === "members" && (
        <>
          <div className="mb-3 flex flex-wrap gap-2"><SearchBox value={q} onChange={setQ} placeholder={t("Search member", "सदस्य शोधा")} />{["all", "active", "expiring", "expired", "suspended", "cancelled"].map((s) => <button key={s} onClick={() => setFilter(s)} className={filter === s ? "btn-primary btn-sm" : "btn-outline btn-sm"}>{s === "expiring" ? t("Expiring", "संपणारे") : s === "all" ? t("All", "सर्व") : statusLabel(s, lang)}</button>)}</div>
          <Table head={[t("Member", "सदस्य"), t("Plan", "योजना"), t("Period", "कालावधी"), t("Seat", "आसन"), t("Paid / fee", "भरले / शुल्क"), t("Status", "स्थिती"), ""]} empty={!list.length}>
            {list.map((m) => { const s = db.students.find((x) => x.id === m.studentId)!; const p = db.libraryPlans.find((x) => x.id === m.planId)!; const left = daysBetween(today(), m.end); const paid = membershipPaid(db, m.id); return (
              <tr key={m.id}><td><p className="font-medium">{s.name}</p><p className="text-xs text-slate-500">{s.code} · {db.enrolments.some((e) => e.studentId === s.id) ? t("Coaching student", "कोचिंग विद्यार्थी") : t("Abhyasika Kendra only", "फक्त अभ्यासिका केंद्र")}</p></td><td className="text-xs">{l(p.name)}{p.kind === "bundle" && <Badge tone="orange" className="ml-1">bundle</Badge>}</td><td className="text-xs">{d(m.start)} – {d(m.end)}{m.status === "active" && <p className={left <= 7 ? "font-semibold text-amber-600" : "text-slate-500"}>{left} {t("days left", "दिवस बाकी")}</p>}</td><td>{m.seat}</td><td className="text-xs">{fmtINR(paid)} / {fmtINR(m.fee)}</td><td><StatusBadge s={m.status} /></td>
                <td>{canManage && <div className="flex flex-wrap gap-1">{paid < m.fee && <button className="btn-primary btn-sm" onClick={() => { let pid = ""; update((db) => { pid = recordPayment(db, { studentId: s.id, membershipId: m.id, amount: m.fee - paid, method: "cash", reference: "CASH", by: me.id }).id; const x = db.memberships.find((y) => y.id === m.id)!; if (x.status === "suspended") x.status = "active"; }, { action: "library.payment", entity: s.code }); setReceipt(pid); }}>{t("Collect", "जमा करा")}</button>}<button className="btn-ghost btn-sm" title={t("Renew", "नूतनीकरण")} onClick={() => renew(m.id)}><RefreshCw className="size-4" /></button>{m.status === "active" && <button className="btn-ghost btn-sm text-amber-700" onClick={() => setStatus(m.id, "suspended")}>{t("Suspend", "निलंबित")}</button>}{m.status === "suspended" && <button className="btn-ghost btn-sm" onClick={() => setStatus(m.id, "active")}>{t("Activate", "सक्रिय")}</button>}{m.status !== "cancelled" && <button className="btn-ghost btn-sm text-red-600" onClick={() => confirm(t("Cancel membership?", "सदस्यत्व रद्द करायचे?")) && setStatus(m.id, "cancelled")}><X className="size-4" /></button>}{m.status === "active" && left <= 7 && <button className="btn-ghost btn-sm" title="Reminder" onClick={() => { update((db) => db.inbox.unshift({ id: uid("in"), studentId: s.id, title: { en: "Abhyasika Kendra renewal reminder", mr: "अभ्यासिका केंद्र नूतनीकरण स्मरणपत्र" }, body: { en: `Your membership expires on ${m.end}.`, mr: `आपले सदस्यत्व ${m.end} रोजी संपते.` }, at: nowISO(), read: false, kind: "library" })); toast.success(t("Reminder sent", "स्मरणपत्र पाठवले")); }}><Send className="size-4" /></button>}</div>}</td></tr>); })}
          </Table>
        </>
      )}
      {tab === "desk" && (
        <Card title={t("Today's check-in / check-out", "आजची प्रवेश / निर्गमन नोंद")}>
          <div className="mb-3"><SearchBox value={q} onChange={setQ} placeholder={t("Search member", "सदस्य शोधा")} /></div>
          <ul className="divide-y divide-slate-100">{active.filter((m) => (db.students.find((s) => s.id === m.studentId)?.name ?? "").toLowerCase().includes(q.toLowerCase())).map((m) => { const c = db.checkins.find((x) => x.membershipId === m.id && x.date === today()); const s = db.students.find((x) => x.id === m.studentId)!; return <li key={m.id} className="flex items-center justify-between gap-2 py-2 text-sm"><span><b>{s.name}</b> · {t("Seat", "आसन")} {m.seat}</span><span className="flex items-center gap-2 text-xs text-slate-500">{c ? `${c.inTime}${c.outTime ? ` – ${c.outTime}` : ""}` : "—"}{canManage && (!c || !c.outTime) && <button className="btn-outline btn-sm" onClick={() => checkin(m.id)}>{c ? <><LogOut className="size-4" />{t("Check out", "बाहेर")}</> : <><LogIn className="size-4" />{t("Check in", "आत")}</>}</button>}</span></li>; })}</ul>
          <p className="mt-3 text-xs text-slate-500">{t("Check-in can also be done by QR at the entrance (configurable to the academy's process).", "प्रवेशद्वारावर QR द्वारेही नोंद करता येते (अकॅडमीच्या पद्धतीनुसार कॉन्फिगर).")}</p>
        </Card>
      )}
      {tab === "seats" && (
        <Card title={t("Seat allocation", "आसन वाटप")}>
          <div className="grid grid-cols-8 gap-1.5 sm:grid-cols-10 lg:grid-cols-16">{Array.from({ length: SEATS }, (_, i) => { const seat = `S-${String(i + 1).padStart(2, "0")}`; const m = seatOf.get(seat); const s = m && db.students.find((x) => x.id === m.studentId); return <div key={seat} title={s ? `${s.name} · ${m?.end}` : t("Free", "रिकामे")} className={cn("flex aspect-square flex-col items-center justify-center rounded-md text-[10px] font-medium", m ? (daysBetween(today(), m.end) <= 7 ? "bg-amber-400 text-white" : "bg-brand-600 text-white") : "bg-slate-100 text-slate-500")}><Armchair className="size-3.5" />{i + 1}</div>; })}</div>
          <div className="mt-3 flex gap-4 text-xs text-slate-500"><span className="flex items-center gap-1"><span className="size-3 rounded bg-brand-600" />{t("Occupied", "वापरात")}</span><span className="flex items-center gap-1"><span className="size-3 rounded bg-amber-400" />{t("Expiring soon", "लवकरच संपणारे")}</span><span className="flex items-center gap-1"><span className="size-3 rounded bg-slate-100 ring-1 ring-slate-200" />{t("Free", "रिकामे")}</span></div>
        </Card>
      )}
      {tab === "plans" && (
        <>
          <Notice tone="warn">{t("Prices shown are demo values. Set the academy's actual Abhyasika Kendra fees here.", "दाखवलेले दर डेमो आहेत. अकॅडमीचे प्रत्यक्ष अभ्यासिका केंद्र शुल्क येथे ठरवा.")}</Notice>
          <div className="mt-3"><Table head={[t("Plan", "योजना"), t("Type", "प्रकार"), t("Months", "महिने"), t("Fee", "शुल्क"), t("Registration", "नोंदणी शुल्क"), t("Active", "सक्रिय"), ""]}>{db.libraryPlans.map((p) => <tr key={p.id}><td className="font-medium">{l(p.name)}</td><td>{p.kind === "bundle" ? `Bundle · ${l(db.courses.find((c) => c.id === p.bundleCourseId)?.name)}` : t("Abhyasika Kendra only", "फक्त अभ्यासिका केंद्र")}</td><td>{p.months}</td><td>{fmtINR(p.fee)}</td><td>{fmtINR(p.registrationFee)}</td><td><Toggle disabled={!canManage} checked={p.active} onChange={(v) => update((db) => { db.libraryPlans.find((x) => x.id === p.id)!.active = v; })} /></td><td>{canManage && <button className="btn-ghost btn-sm" onClick={() => setPlan(structuredClone(p))}><Pencil className="size-4" /></button>}</td></tr>)}</Table></div>
          {canManage && <button className="btn-outline mt-3" onClick={() => setPlan({ id: "", name: { en: "", mr: "" }, months: 1, fee: 0, registrationFee: 0, locker: false, spaciousSeat: false, kind: "library", active: true })}><Plus className="size-4" />{t("Add plan", "योजना जोडा")}</button>}
        </>
      )}
      <Modal open={!!plan} onClose={() => setPlan(null)} title={t("Abhyasika Kendra plan", "अभ्यासिका केंद्र योजना")} footer={<button className="btn-primary" onClick={() => { if (!plan?.name.en) return; update((db) => { if (plan.id) Object.assign(db.libraryPlans.find((x) => x.id === plan.id)!, plan); else db.libraryPlans.push({ ...plan, id: uid("lp") }); }, { action: "library.plan", entity: plan.name.en, detail: `₹${plan.fee}` }); setPlan(null); }}>{t("Save", "जतन")}</button>}>
        {plan && <div className="grid gap-3 sm:grid-cols-2"><Field label="Name (English)">{(id) => <input id={id} className="input" value={plan.name.en} onChange={(e) => setPlan({ ...plan, name: { ...plan.name, en: e.target.value } })} />}</Field><Field label="नाव (मराठी)">{(id) => <input id={id} className="input" value={plan.name.mr} onChange={(e) => setPlan({ ...plan, name: { ...plan.name, mr: e.target.value } })} />}</Field><Field label={t("Months", "महिने")}>{(id) => <select id={id} className="input" value={plan.months} onChange={(e) => setPlan({ ...plan, months: Number(e.target.value) })}>{[1, 3, 6, 12].map((m) => <option key={m}>{m}</option>)}</select>}</Field><Field label={t("Fee (₹)", "शुल्क (₹)")}>{(id) => <input id={id} type="number" className="input" value={plan.fee} onChange={(e) => setPlan({ ...plan, fee: Number(e.target.value) })} />}</Field><Field label={t("Type", "प्रकार")}>{(id) => <select id={id} className="input" value={plan.kind} onChange={(e) => setPlan({ ...plan, kind: e.target.value as LibraryPlan["kind"] })}><option value="library">{t("Abhyasika Kendra only", "फक्त अभ्यासिका केंद्र")}</option><option value="bundle">{t("Course + Abhyasika Kendra bundle", "अभ्यासक्रम + अभ्यासिका केंद्र संच")}</option></select>}</Field><Field label={t("Registration fee (₹, one-time)", "नोंदणी शुल्क (₹, एकदा)")}>{(id) => <input id={id} type="number" className="input" value={plan.registrationFee} onChange={(e) => setPlan({ ...plan, registrationFee: Number(e.target.value) })} />}</Field><div className="flex flex-wrap items-end gap-4 sm:col-span-2"><Toggle checked={plan.locker} onChange={(v) => setPlan({ ...plan, locker: v })} label={t("Locker included", "लॉकर समाविष्ट")} /><Toggle checked={plan.spaciousSeat} onChange={(v) => setPlan({ ...plan, spaciousSeat: v })} label={t("Spacious seat", "प्रशस्त आसन")} /></div>{plan.kind === "bundle" && <Field label={t("Bundled course", "संच अभ्यासक्रम")}>{(id) => <select id={id} className="input" value={plan.bundleCourseId ?? ""} onChange={(e) => setPlan({ ...plan, bundleCourseId: e.target.value })}>{db.courses.map((c) => <option key={c.id} value={c.id}>{l(c.name)}</option>)}</select>}</Field>}</div>}
      </Modal>
      <Modal open={!!join} onClose={() => setJoin(null)} title={t("New Abhyasika Kendra membership", "नवीन अभ्यासिका केंद्र सदस्यत्व")} size="lg" footer={<button className="btn-primary" onClick={save}>{t("Create", "तयार करा")}</button>}>
        {join && (
          <div className="grid gap-3 sm:grid-cols-2">
            <div className="flex gap-2 sm:col-span-2">{(["new", "existing"] as const).map((m) => <button key={m} onClick={() => setJoin({ ...join, mode: m })} className={join.mode === m ? "btn-primary btn-sm" : "btn-outline btn-sm"}>{m === "new" ? t("New Abhyasika Kendra only member", "नवीन फक्त-अभ्यासिका केंद्र सदस्य") : t("Existing student", "विद्यमान विद्यार्थी")}</button>)}</div>
            {join.mode === "new" ? <><Field label={t("Name", "नाव")} required>{(id) => <input id={id} className="input" value={join.name} onChange={(e) => setJoin({ ...join, name: e.target.value })} />}</Field><Field label={t("Mobile", "मोबाईल")} required>{(id) => <input id={id} className="input" maxLength={10} value={join.mobile} onChange={(e) => setJoin({ ...join, mobile: e.target.value.replace(/\D/g, "") })} />}</Field></>
              : <Field label={t("Student", "विद्यार्थी")} className="sm:col-span-2">{(id) => <select id={id} className="input" value={join.studentId} onChange={(e) => setJoin({ ...join, studentId: e.target.value })}><option value="">—</option>{db.students.map((s) => <option key={s.id} value={s.id}>{s.name} ({s.code})</option>)}</select>}</Field>}
            <Field label={t("Plan", "योजना")}>{(id) => <select id={id} className="input" value={join.planId} onChange={(e) => setJoin({ ...join, planId: e.target.value })}>{db.libraryPlans.filter((p) => p.active).map((p) => <option key={p.id} value={p.id}>{l(p.name)} – {fmtINR(p.fee)}</option>)}</select>}</Field>
            <Field label={t("Start date", "सुरुवात")}>{(id) => <input id={id} type="date" className="input" value={join.start} onChange={(e) => setJoin({ ...join, start: e.target.value })} />}</Field>
            <Field label={t("Discount (₹)", "सवलत (₹)")}>{(id) => <input id={id} type="number" className="input" value={join.discount} onChange={(e) => setJoin({ ...join, discount: Number(e.target.value) })} />}</Field>
            <div className="text-sm"><p className="label">{t("Summary", "सारांश")}</p>{t("Expires", "मुदत")}: <b>{addMonths(join.start, db.libraryPlans.find((p) => p.id === join.planId)?.months ?? 1)}</b><br />{t("Registration", "नोंदणी")}: <b>{fmtINR(db.libraryPlans.find((p) => p.id === join.planId)?.registrationFee ?? 0)}</b><br />{t("Total fee", "एकूण शुल्क")}: <b>{fmtINR(membershipFee(db.libraryPlans.find((p) => p.id === join.planId)!, join.discount))}</b></div>
            <Toggle checked={join.payNow} onChange={(v) => setJoin({ ...join, payNow: v })} label={t("Collect full payment now (cash)", "आता पूर्ण रक्कम जमा (रोख)")} />
          </div>
        )}
      </Modal>
      <ReceiptModal paymentId={receipt} onClose={() => setReceipt(null)} />
    </div>
  );
}

/* ---------------- Official exam updates & sources ---------------- */
export function ExamUpdatesAdmin({ canManage }: { canManage: boolean }) {
  const { t, l, d, lang } = useI18n();
  const { db, update } = useA();
  const [tab, setTab] = useState<"pending" | "published" | "archive" | "sources">("pending");
  const [edit, setEdit] = useState<ExamUpdate | null>(null);
  const [src, setSrc] = useState<UpdateSource | null>(null);
  const list = db.updates.filter((u) => (tab === "pending" ? u.status === "pending" : tab === "published" ? u.status === "published" : ["rejected", "archived"].includes(u.status))).sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  const followers = (aid: string, urgent: boolean) => db.students.filter((s) => s.status === "active" && (urgent || s.follows.includes(aid)));
  const publish = (u: ExamUpdate) => {
    const target = followers(u.authorityId, u.urgent);
    update((db) => { const x = db.updates.find((y) => y.id === u.id); if (x) Object.assign(x, u, { status: "published" }); else db.updates.unshift({ ...u, status: "published" }); target.forEach((s) => db.inbox.unshift({ id: uid("in"), studentId: s.id, title: u.title, body: u.summary, at: nowISO(), read: false, kind: "update" })); }, { action: "examUpdate.publish", entity: u.title.en, detail: `Notified ${target.length} students` });
    setEdit(null);
    toast.success(t(`Published. Notified ${target.length} followers.`, `प्रकाशित. ${target.length} फॉलोअर्सना कळवले.`));
  };
  const setStatus = (id: string, status: ExamUpdate["status"]) => update((db) => { db.updates.find((x) => x.id === id)!.status = status; }, { action: `examUpdate.${status}`, entity: id });
  const blank = (): ExamUpdate => ({ id: uid("eu"), authorityId: db.authorities[0].id, title: { en: "", mr: "" }, summary: { en: "", mr: "" }, sourceUrl: "", status: "pending", createdAt: today(), urgent: false, dates: [] });
  return (
    <div>
      <PageHeader title={t("Official exam updates", "अधिकृत परीक्षा अपडेट्स")} actions={canManage && <button className="btn-primary" onClick={() => setEdit(blank())}><Plus className="size-4" />{t("Add update / urgent notice", "अपडेट / तातडीची सूचना")}</button>} />
      <Notice>{t("Automatic fetching runs only where an integration is configured. Fetched items always wait here for review. Never republish content in violation of source terms – link to the official source.", "स्वयंचलित संकलन फक्त कॉन्फिगर केलेल्या जोडणीसाठी चालते. प्राप्त नोंदी नेहमी पुनरावलोकनासाठी थांबतात. स्रोताच्या अटींचे उल्लंघन करून पुनर्प्रकाशन करू नका – अधिकृत स्रोताची लिंक द्या.")}</Notice>
      <div className="mt-4"><Tabs tabs={[{ id: "pending", label: t("Pending review", "पुनरावलोकन बाकी"), count: db.updates.filter((u) => u.status === "pending").length }, { id: "published", label: t("Published", "प्रकाशित") }, { id: "archive", label: t("Rejected / archived", "नाकारलेले / संग्रहित") }, { id: "sources", label: t("Sources", "स्रोत"), count: db.sources.length }]} value={tab} onChange={setTab} /></div>
      {tab !== "sources" ? (
        <div className="space-y-3">
          {list.map((u) => (
            <div key={u.id} className="card p-4">
              <div className="flex flex-wrap items-start justify-between gap-2">
                <div className="min-w-0 flex-1"><div className="flex flex-wrap gap-1.5 text-xs"><Badge tone="blue">{l(db.authorities.find((a) => a.id === u.authorityId)?.name)}</Badge>{u.urgent && <Badge tone="red">{t("Urgent – all students", "तातडीचे – सर्व विद्यार्थी")}</Badge>}{u.sourceId && <Badge>{db.sources.find((s) => s.id === u.sourceId)?.name}</Badge>}<span className="text-slate-500">{d(u.createdAt)}</span></div><p className="mt-1 font-semibold">{l(u.title)}</p><p className="text-sm text-slate-600">{l(u.summary)}</p>{u.dates.length > 0 && <p className="mt-1 text-xs text-slate-500">{u.dates.map((x) => `${DATE_LABEL[x.kind][lang === "mr" ? 1 : 0]}: ${d(x.date)}`).join(" · ")}</p>}</div>
                {canManage && <div className="flex flex-wrap gap-1">
                  {u.status === "pending" && <><button className="btn-ghost btn-sm" onClick={() => setEdit(structuredClone(u))}><Pencil className="size-4" />{t("Review & edit", "तपासा")}</button><button className="btn-primary btn-sm" onClick={() => publish(u)}><Check className="size-4" />{t("Approve", "मंजूर")}</button><button className="btn-ghost btn-sm text-red-600" onClick={() => setStatus(u.id, "rejected")}><X className="size-4" /></button></>}
                  {u.status === "published" && <><button className="btn-ghost btn-sm" onClick={() => setEdit(structuredClone(u))}><Pencil className="size-4" /></button><button className="btn-ghost btn-sm" onClick={() => setStatus(u.id, "archived")}><Archive className="size-4" />{t("Archive", "संग्रहित")}</button></>}
                  {["rejected", "archived"].includes(u.status) && <button className="btn-ghost btn-sm" onClick={() => setStatus(u.id, "pending")}><RotateCcw className="size-4" />{t("Restore", "पुनर्स्थापित")}</button>}
                </div>}
              </div>
              {u.status === "pending" && <p className="mt-2 text-xs text-slate-500">{t("Will notify", "कळवले जाईल")}: {followers(u.authorityId, u.urgent).length} {t("students following this authority", "हे फॉलो करणारे विद्यार्थी")}</p>}
            </div>
          ))}
          {!list.length && <EmptyState title={t("Nothing here", "येथे काही नाही")} />}
        </div>
      ) : (
        <>
          <Table head={[t("Source", "स्रोत"), t("Kind", "प्रकार"), t("Authority", "संस्था"), t("Integration", "जोडणी"), t("Active", "सक्रिय"), ""]}>
            {db.sources.map((s) => <tr key={s.id}><td><p className="font-medium">{s.name}</p>{s.url && <a href={s.url} target="_blank" rel="noopener noreferrer" className="inline-flex items-center gap-1 text-xs text-brand-700">{s.url}<ExternalLink className="size-3" /></a>}</td><td className="text-xs uppercase">{s.kind}</td><td className="text-xs">{l(db.authorities.find((a) => a.id === s.authorityId)?.name)}</td><td>{s.integration === "configured" ? <Badge tone="green">{t("Configured", "जोडलेले")}</Badge> : s.integration === "manual" ? <Badge tone="blue">{t("Manual curation", "हस्तनिवड")}</Badge> : <Badge tone="amber">{t("Not configured", "जोडलेले नाही")}</Badge>}</td><td><Toggle disabled={!canManage} checked={s.active} onChange={(v) => update((db) => { db.sources.find((x) => x.id === s.id)!.active = v; })} /></td><td>{canManage && <button className="btn-ghost btn-sm" onClick={() => setSrc(structuredClone(s))}><Pencil className="size-4" /></button>}</td></tr>)}
          </Table>
          <p className="mt-2 text-xs text-slate-500">{t("X/Twitter (@mpsc_office) requires paid API access and credentials; until configured, admins curate updates manually with a link to the official post.", "X/Twitter (@mpsc_office) साठी सशुल्क API व क्रेडेन्शियल्स आवश्यक; तोपर्यंत प्रशासक अधिकृत पोस्टच्या लिंकसह अपडेट्स स्वतः निवडतात.")}</p>
          {canManage && <button className="btn-outline mt-3" onClick={() => setSrc({ id: "", name: "", kind: "rss", url: "", authorityId: db.authorities[0].id, active: true, integration: "not-configured" })}><Plus className="size-4" />{t("Add source", "स्रोत जोडा")}</button>}
          {canManage && <button className="btn-outline ml-2 mt-3" onClick={() => { const a = prompt("Authority / exam name"); if (a) update((db) => db.authorities.push({ id: uid("a"), name: { en: a, mr: a }, group: "Other", url: "" }), { action: "authority.add", entity: a }); }}><Plus className="size-4" />{t("Add authority", "संस्था जोडा")}</button>}
        </>
      )}
      <Modal open={!!edit} onClose={() => setEdit(null)} title={t("Exam update", "परीक्षा अपडेट")} size="xl" footer={edit && <><button className="btn-outline" onClick={() => { update((db) => { const x = db.updates.find((y) => y.id === edit.id); if (x) Object.assign(x, edit); else db.updates.unshift(edit); }, { action: "examUpdate.save", entity: edit.title.en }); setEdit(null); }}>{t("Save draft", "मसुदा जतन")}</button><button className="btn-primary" onClick={() => publish(edit)}>{t("Publish & notify", "प्रकाशित करा व कळवा")}</button></>}>
        {edit && (
          <div className="grid gap-3 sm:grid-cols-2">
            <Field label={t("Authority / exam", "संस्था / परीक्षा")}>{(id) => <select id={id} className="input" value={edit.authorityId} onChange={(e) => setEdit({ ...edit, authorityId: e.target.value })}>{db.authorities.map((a) => <option key={a.id} value={a.id}>{l(a.name)}</option>)}</select>}</Field>
            <Field label={t("Official source URL", "अधिकृत स्रोत URL")}>{(id) => <input id={id} className="input" value={edit.sourceUrl} onChange={(e) => setEdit({ ...edit, sourceUrl: e.target.value })} />}</Field>
            <Field label="Title (English)">{(id) => <input id={id} className="input" value={edit.title.en} onChange={(e) => setEdit({ ...edit, title: { ...edit.title, en: e.target.value } })} />}</Field>
            <Field label="शीर्षक (मराठी)">{(id) => <input id={id} className="input" value={edit.title.mr} onChange={(e) => setEdit({ ...edit, title: { ...edit.title, mr: e.target.value } })} />}</Field>
            <Field label="Summary (English)">{(id) => <textarea id={id} rows={3} className="input" value={edit.summary.en} onChange={(e) => setEdit({ ...edit, summary: { ...edit.summary, en: e.target.value } })} />}</Field>
            <Field label="सारांश (मराठी)">{(id) => <textarea id={id} rows={3} className="input" value={edit.summary.mr} onChange={(e) => setEdit({ ...edit, summary: { ...edit.summary, mr: e.target.value } })} />}</Field>
            <div className="sm:col-span-2">
              <p className="label">{t("Important dates", "महत्त्वाच्या तारखा")}</p>
              {edit.dates.map((x, i) => <div key={i} className="mb-2 flex gap-2"><select className="input" value={x.kind} onChange={(e) => { const ds = [...edit.dates]; ds[i] = { ...x, kind: e.target.value as ImportantDateKind }; setEdit({ ...edit, dates: ds }); }} aria-label="Date type">{Object.entries(DATE_LABEL).map(([k, v]) => <option key={k} value={k}>{v[lang === "mr" ? 1 : 0]}</option>)}</select><input type="date" className="input" value={x.date} onChange={(e) => { const ds = [...edit.dates]; ds[i] = { ...x, date: e.target.value }; setEdit({ ...edit, dates: ds }); }} aria-label="Date" /><button className="btn-ghost" onClick={() => setEdit({ ...edit, dates: edit.dates.filter((_, k) => k !== i) })} aria-label="Remove"><X className="size-4" /></button></div>)}
              <button className="btn-outline btn-sm" onClick={() => setEdit({ ...edit, dates: [...edit.dates, { kind: "appEnd", date: today() }] })}><Plus className="size-4" />{t("Add date", "तारीख जोडा")}</button>
              <p className="mt-1 text-xs text-slate-500">{t(`Students get reminders ${db.settings.reminderDays.join(", ")} day(s) before each date.`, `प्रत्येक तारखेपूर्वी ${db.settings.reminderDays.join(", ")} दिवस स्मरणपत्र.`)}</p>
            </div>
            <Toggle checked={edit.urgent} onChange={(v) => setEdit({ ...edit, urgent: v })} label={t("Urgent academy-wide notice (notify everyone)", "तातडीची अकॅडमी-व्यापी सूचना (सर्वांना कळवा)")} />
          </div>
        )}
      </Modal>
      <Modal open={!!src} onClose={() => setSrc(null)} title={t("Update source", "अपडेट स्रोत")} footer={<button className="btn-primary" onClick={() => { if (!src?.name) return; update((db) => { if (src.id) Object.assign(db.sources.find((x) => x.id === src.id)!, src); else db.sources.push({ ...src, id: uid("src") }); }, { action: "source.save", entity: src.name }); setSrc(null); }}>{t("Save", "जतन")}</button>}>
        {src && <div className="grid gap-3"><Field label={t("Name", "नाव")}>{(id) => <input id={id} className="input" value={src.name} onChange={(e) => setSrc({ ...src, name: e.target.value })} />}</Field><Field label={t("Kind", "प्रकार")}>{(id) => <select id={id} className="input" value={src.kind} onChange={(e) => setSrc({ ...src, kind: e.target.value as UpdateSource["kind"] })}><option value="website">Official website</option><option value="x">X / Twitter</option><option value="rss">RSS feed</option><option value="other">Other</option></select>}</Field><Field label="URL">{(id) => <input id={id} className="input" value={src.url} onChange={(e) => setSrc({ ...src, url: e.target.value })} />}</Field><Field label={t("Authority", "संस्था")}>{(id) => <select id={id} className="input" value={src.authorityId} onChange={(e) => setSrc({ ...src, authorityId: e.target.value })}>{db.authorities.map((a) => <option key={a.id} value={a.id}>{l(a.name)}</option>)}</select>}</Field><Notice>{t("Credentials for API sources are stored in secure server configuration, never in this form.", "API स्रोतांचे क्रेडेन्शियल्स सुरक्षित सर्व्हर कॉन्फिगरेशनमध्ये ठेवले जातात, या फॉर्ममध्ये नाही.")}</Notice></div>}
      </Modal>
    </div>
  );
}

/* ---------------- Notifications ---------------- */
const CH: Channel[] = ["inapp", "whatsapp", "sms", "email", "telegram"];
const CH_LABEL: Record<Channel, string> = { inapp: "In-app", whatsapp: "WhatsApp", sms: "SMS", email: "Email", telegram: "Telegram" };
const TEMPLATES: { key: string; en: string; mr: string }[] = [
  { key: "fee_reminder", en: "Dear {name}, your instalment of {amount} for {course} is due on {date}. – Vidyadhan Academy", mr: "प्रिय {name}, {course} साठी {amount} चा हप्ता {date} रोजी देय आहे. – विद्याधन अकॅडमी" },
  { key: "class_reminder", en: "Reminder: {class} starts at {time} today. Join: {link}", mr: "स्मरणपत्र: {class} आज {time} वाजता सुरू होईल. लिंक: {link}" },
  { key: "absent_alert", en: "{name} was marked absent on {date}.", mr: "{name} {date} रोजी अनुपस्थित होते." },
  { key: "result", en: "Result for {test} is published. Score: {score}.", mr: "{test} चा निकाल प्रसिद्ध. गुण: {score}." },
  { key: "library_expiry", en: "Your Abhyasika Kendra membership expires on {date}. Renew to keep seat {seat}.", mr: "आपले अभ्यासिका केंद्र सदस्यत्व {date} रोजी संपेल. आसन {seat} टिकवण्यासाठी नूतनीकरण करा." },
];
export function NotificationsAdmin({ canManage }: { canManage: boolean }) {
  const { t, l, dt } = useI18n();
  const { db, me, update } = useA();
  const [tab, setTab] = useState<"compose" | "history" | "templates" | "channels">("compose");
  const [f, setF] = useState({ title: "", body: "", channels: ["inapp"] as Channel[], type: "all" as "all" | "course" | "batch" | "student", ids: [] as string[], schedule: "" });
  const recipients = useMemo(() => {
    if (f.type === "all") return db.students.filter((s) => s.status === "active");
    if (f.type === "student") return db.students.filter((s) => f.ids.includes(s.id));
    const sids = db.enrolments.filter((e) => e.enrolmentStatus === "active" && (f.type === "course" ? f.ids.includes(e.courseId) : f.ids.includes(e.batchId))).map((e) => e.studentId);
    return db.students.filter((s) => sids.includes(s.id));
  }, [db, f.type, f.ids]);
  const send = () => {
    if (!f.title || !f.body) return toast.error(t("Title and message required", "शीर्षक व संदेश आवश्यक"));
    if (!f.channels.length) return toast.error(t("Choose at least one channel", "किमान एक माध्यम निवडा"));
    const live = f.channels.filter((c) => db.settings.channels[c]);
    const consented = recipients.filter((s) => f.channels.some((c) => c === "inapp" || (c === "whatsapp" && s.consent.whatsapp) || (c === "sms" && s.consent.sms) || (c === "email" && s.consent.email && s.email) || (c === "telegram" && s.telegramLinked)));
    update((db) => {
      db.broadcasts.unshift({ id: uid("bc"), title: f.title, body: f.body, channels: f.channels, audience: { type: f.type, ids: f.ids }, scheduledAt: f.schedule || undefined, createdAt: nowISO(), createdBy: me.id, status: f.schedule ? "scheduled" : "sent", delivered: f.schedule ? 0 : consented.length, failed: f.schedule ? 0 : recipients.length - consented.length });
      if (!f.schedule && f.channels.includes("inapp")) recipients.forEach((s) => db.inbox.unshift({ id: uid("in"), studentId: s.id, title: { en: f.title, mr: f.title }, body: { en: f.body, mr: f.body }, at: nowISO(), read: false, kind: "general" }));
    }, { action: f.schedule ? "notify.schedule" : "notify.send", entity: f.title, detail: `${recipients.length} recipients via ${f.channels.join(",")}` });
    toast.success(f.schedule ? t("Scheduled", "नियोजित") : t(`Sent to ${recipients.length} students${live.length < f.channels.length ? " (disabled channels skipped – simulated)" : ""}`, `${recipients.length} विद्यार्थ्यांना पाठवले`));
    setF({ ...f, title: "", body: "", schedule: "" });
    setTab("history");
  };
  return (
    <div>
      <PageHeader title={t("Notifications & communication", "सूचना व संवाद")} />
      <Tabs tabs={[{ id: "compose", label: t("Compose", "संदेश लिहा") }, { id: "history", label: t("History & delivery", "इतिहास व वितरण"), count: db.broadcasts.length }, { id: "templates", label: t("Templates", "साचे") }, { id: "channels", label: t("Channels & events", "माध्यमे व घटना") }]} value={tab} onChange={setTab} />
      {tab === "compose" && (
        <div className="grid gap-5 lg:grid-cols-[1fr_300px]">
          <Card>
            <div className="grid gap-3">
              <Field label={t("Title", "शीर्षक")} required>{(id) => <input id={id} className="input" value={f.title} onChange={(e) => setF({ ...f, title: e.target.value })} />}</Field>
              <Field label={t("Message", "संदेश")} required hint={`${f.body.length} ${t("characters", "अक्षरे")} · Marathi Unicode supported`}>{(id) => <textarea id={id} rows={4} className="input" value={f.body} onChange={(e) => setF({ ...f, body: e.target.value })} />}</Field>
              <div><p className="label">{t("Channels", "माध्यमे")}</p><div className="flex flex-wrap gap-2">{CH.map((c) => <label key={c} className={cn("flex items-center gap-1.5 rounded-md border px-2 py-1 text-sm", !db.settings.channels[c] && "opacity-60")}><input type="checkbox" checked={f.channels.includes(c)} onChange={(e) => setF({ ...f, channels: e.target.checked ? [...f.channels, c] : f.channels.filter((x) => x !== c) })} />{CH_LABEL[c]}{!db.settings.channels[c] && <span className="text-[10px] text-amber-700">({t("not configured", "जोडलेले नाही")})</span>}</label>)}</div></div>
              <div className="grid gap-3 sm:grid-cols-2">
                <Field label={t("Audience", "प्रेक्षक")}>{(id) => <select id={id} className="input" value={f.type} onChange={(e) => setF({ ...f, type: e.target.value as typeof f.type, ids: [] })}><option value="all">{t("All active students", "सर्व सक्रिय विद्यार्थी")}</option><option value="course">{t("Selected courses", "निवडक अभ्यासक्रम")}</option><option value="batch">{t("Selected batches", "निवडक बॅचेस")}</option><option value="student">{t("Individual students", "वैयक्तिक विद्यार्थी")}</option></select>}</Field>
                <Field label={t("Schedule (optional)", "वेळ ठरवा (ऐच्छिक)")}>{(id) => <input id={id} type="datetime-local" className="input" value={f.schedule} onChange={(e) => setF({ ...f, schedule: e.target.value })} />}</Field>
              </div>
              {f.type !== "all" && <div className="flex max-h-40 flex-wrap gap-2 overflow-y-auto rounded-lg border p-2">{(f.type === "course" ? db.courses.map((c) => ({ id: c.id, n: l(c.name) })) : f.type === "batch" ? db.batches.map((b) => ({ id: b.id, n: b.name })) : db.students.map((s) => ({ id: s.id, n: s.name }))).map((o) => <label key={o.id} className="flex items-center gap-1 text-xs"><input type="checkbox" checked={f.ids.includes(o.id)} onChange={(e) => setF({ ...f, ids: e.target.checked ? [...f.ids, o.id] : f.ids.filter((x) => x !== o.id) })} />{o.n}</label>)}</div>}
              {canManage && <button className="btn-primary justify-self-start" onClick={send}><Send className="size-4" />{f.schedule ? t("Schedule", "नियोजित करा") : t("Send now", "आता पाठवा")}</button>}
            </div>
          </Card>
          <Card title={t("Preview", "पूर्वावलोकन")}>
            <p className="text-sm">{t("Recipients", "प्राप्तकर्ते")}: <b>{recipients.length}</b></p>
            <div className="mt-3 rounded-xl bg-[#e7ffdb] p-3 text-sm shadow-inner"><p className="font-semibold">{f.title || "…"}</p><p className="whitespace-pre-wrap">{f.body || "…"}</p><p className="mt-1 text-right text-[10px] text-slate-500">{academy.name.en}</p></div>
            <p className="mt-3 text-xs text-slate-500">{t("WhatsApp requires an approved Business Platform provider, pre-approved templates and user opt-in. Telegram requires the student to start the bot. Messages are sent only to students who consented.", "व्हॉट्सअ‍ॅपसाठी मंजूर बिझनेस प्लॅटफॉर्म प्रदाता, पूर्व-मंजूर साचे व संमती आवश्यक. टेलिग्रामसाठी विद्यार्थ्याने बॉट सुरू करणे आवश्यक. फक्त संमती दिलेल्यांनाच संदेश.")}</p>
          </Card>
        </div>
      )}
      {tab === "history" && (
        <Table head={[t("Message", "संदेश"), t("Audience", "प्रेक्षक"), t("Channels", "माध्यमे"), t("When", "केव्हा"), t("Status", "स्थिती"), t("Delivered / failed", "वितरित / अयशस्वी")]} empty={!db.broadcasts.length}>
          {db.broadcasts.map((b) => <tr key={b.id}><td><p className="font-medium">{b.title}</p><p className="text-xs text-slate-500">{b.body.slice(0, 60)}</p></td><td className="text-xs">{b.audience.type}{b.audience.ids.length ? ` (${b.audience.ids.length})` : ""}</td><td className="text-xs">{b.channels.map((c) => CH_LABEL[c]).join(", ")}</td><td className="text-xs">{dt(b.scheduledAt ?? b.createdAt)}</td><td><StatusBadge s={b.status} /></td><td className="text-xs"><span className="text-emerald-700">{b.delivered}</span> / <span className="text-red-600">{b.failed}</span></td></tr>)}
        </Table>
      )}
      {tab === "templates" && (
        <div className="space-y-3">{TEMPLATES.map((x) => <Card key={x.key} title={<code>{x.key}</code>}><div className="grid gap-3 sm:grid-cols-2"><div><p className="label">English</p><p className="rounded-md bg-slate-50 p-2 text-sm">{x.en}</p></div><div><p className="label">मराठी</p><p className="rounded-md bg-slate-50 p-2 text-sm">{x.mr}</p></div></div></Card>)}<p className="text-xs text-slate-500">{t("WhatsApp templates must be approved by Meta via your provider before use.", "व्हॉट्सअ‍ॅप साचे वापरण्यापूर्वी प्रदात्यामार्फत Meta कडून मंजूर असणे आवश्यक.")}</p></div>
      )}
      {tab === "channels" && (
        <div className="space-y-5">
          <Card title={t("Channels", "माध्यमे")}><div className="flex flex-wrap gap-4">{CH.map((c) => <Toggle key={c} disabled={!canManage} checked={db.settings.channels[c]} onChange={(v) => update((db) => { db.settings.channels[c] = v; }, { action: "channel.toggle", entity: c, detail: String(v) })} label={CH_LABEL[c]} />)}</div><p className="mt-2 text-xs text-slate-500">{t("Enabling a channel in production requires provider credentials in server configuration. Providers are replaceable.", "प्रत्यक्ष वापरात माध्यम सक्षम करण्यासाठी सर्व्हरवर प्रदात्याचे क्रेडेन्शियल्स आवश्यक. प्रदाते बदलता येतात.")}</p></Card>
          <Card title={t("Event → channel mapping", "घटना → माध्यम")} bodyClass="p-0">
            <table className="table-base"><thead><tr><th>{t("Event", "घटना")}</th>{CH.map((c) => <th key={c}>{CH_LABEL[c]}</th>)}</tr></thead>
              <tbody>{Object.entries(db.settings.eventChannels).map(([ev, chs]) => <tr key={ev}><td className="font-medium">{ev}</td>{CH.map((c) => <td key={c}><input type="checkbox" disabled={!canManage} checked={chs.includes(c)} onChange={(e) => update((db) => { db.settings.eventChannels[ev] = e.target.checked ? [...chs, c] : chs.filter((x) => x !== c); })} aria-label={`${ev} ${c}`} /></td>)}</tr>)}</tbody></table>
          </Card>
        </div>
      )}
    </div>
  );
}

/* ---------------- Support tickets ---------------- */
export function Tickets({ canManage }: { canManage: boolean }) {
  const { t, dt, lang } = useI18n();
  const { db, me, update } = useA();
  const [status, setStatus] = useState("all");
  const [open, setOpen] = useState<string | null>(null);
  const [reply, setReply] = useState("");
  const list = db.tickets.filter((x) => status === "all" || x.status === status).sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  const tk = db.tickets.find((x) => x.id === open);
  const patch = (p: Partial<Ticket>, log: string) => tk && update((db) => { Object.assign(db.tickets.find((x) => x.id === tk.id)!, p); db.inbox.unshift({ id: uid("in"), studentId: tk.studentId, title: { en: `Ticket ${tk.code} updated`, mr: `तक्रार ${tk.code} अद्ययावत` }, body: { en: log, mr: log }, at: nowISO(), read: false, kind: "general" }); }, { action: "ticket.update", entity: tk.code, detail: log });
  const counts = Object.fromEntries(["new", "open", "inProgress", "resolved", "closed"].map((s) => [s, db.tickets.filter((x) => x.status === s).length]));
  return (
    <div>
      <PageHeader title={t("Support tickets", "मदत तक्रारी")} />
      <div className="mb-4 flex flex-wrap gap-2"><button onClick={() => setStatus("all")} className={status === "all" ? "btn-primary btn-sm" : "btn-outline btn-sm"}>{t("All", "सर्व")} ({db.tickets.length})</button>{Object.entries(counts).map(([s, n]) => <button key={s} onClick={() => setStatus(s)} className={status === s ? "btn-primary btn-sm" : "btn-outline btn-sm"}>{statusLabel(s, lang)} ({n})</button>)}</div>
      <Table head={[t("Ticket", "तक्रार"), t("Student", "विद्यार्थी"), t("Category", "प्रकार"), t("Priority", "प्राधान्य"), t("Assigned", "नियुक्त"), t("Status", "स्थिती")]} empty={!list.length}>
        {list.map((x) => <tr key={x.id} className="cursor-pointer hover:bg-slate-50" onClick={() => setOpen(x.id)}><td><p className="font-medium">{x.subject}</p><p className="text-xs text-slate-500">{x.code} · {dt(x.createdAt)}</p></td><td>{db.students.find((s) => s.id === x.studentId)?.name}</td><td className="text-xs">{TICKET_CATS[x.category][lang === "mr" ? 1 : 0]}</td><td><StatusBadge s={x.priority} /></td><td className="text-xs">{db.staff.find((s) => s.id === x.assignedTo)?.name ?? "—"}</td><td><StatusBadge s={x.status} /></td></tr>)}
      </Table>
      {tk && (
        <Modal open onClose={() => setOpen(null)} title={`${tk.code} · ${tk.subject}`} size="lg" footer={canManage ? <><button className="btn-outline" onClick={() => { patch({ status: "resolved" }, "Marked resolved"); }}>{t("Mark resolved", "सोडवले")}</button><button className="btn-primary" onClick={() => { if (!reply.trim()) return; update((db) => { const x = db.tickets.find((y) => y.id === tk.id)!; x.messages.push({ by: me.id, text: reply, at: nowISO() }); if (x.status === "new") x.status = "open"; }); patch({}, "New reply from support"); setReply(""); }}><Send className="size-4" />{t("Reply", "उत्तर द्या")}</button></> : undefined}>
          {canManage && <div className="mb-3 grid gap-2 sm:grid-cols-3"><select className="input" value={tk.status} onChange={(e) => patch({ status: e.target.value as Ticket["status"] }, `Status → ${e.target.value}`)} aria-label="Status">{["new", "open", "inProgress", "resolved", "closed"].map((s) => <option key={s} value={s}>{statusLabel(s, lang)}</option>)}</select><select className="input" value={tk.priority} onChange={(e) => patch({ priority: e.target.value as Ticket["priority"] }, `Priority → ${e.target.value}`)} aria-label="Priority">{["low", "medium", "high", "urgent"].map((s) => <option key={s} value={s}>{statusLabel(s, lang)}</option>)}</select><select className="input" value={tk.assignedTo ?? ""} onChange={(e) => patch({ assignedTo: e.target.value }, `Assigned to ${db.staff.find((s) => s.id === e.target.value)?.name}`)} aria-label="Assign"><option value="">{t("Unassigned", "नियुक्त नाही")}</option>{db.staff.filter((s) => s.role !== "faculty").map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}</select></div>}
          <div className="space-y-2">{tk.messages.map((m, i) => { const staff = db.staff.find((s) => s.id === m.by); return <div key={i} className={cn("max-w-[85%] rounded-lg p-3 text-sm", staff ? "ml-auto bg-brand-50" : "bg-slate-100")}><p>{m.text}</p><p className="mt-1 text-[11px] text-slate-500">{staff?.name ?? db.students.find((s) => s.id === m.by)?.name} · {dt(m.at)}</p></div>; })}</div>
          {canManage && <textarea className="input mt-3" rows={2} value={reply} onChange={(e) => setReply(e.target.value)} placeholder={t("Write a reply…", "उत्तर लिहा…")} aria-label="Reply" />}
        </Modal>
      )}
    </div>
  );
}

/* ---------------- Achievements & testimonials ---------------- */
/** Resize an uploaded image in the browser (keeps the prototype's local storage small). */
function resizeImage(file: File, max = 900): Promise<string> {
  return new Promise((resolve, reject) => {
    const img = new Image();
    img.onload = () => {
      const k = Math.min(1, max / Math.max(img.width, img.height));
      const c = document.createElement("canvas");
      c.width = Math.round(img.width * k); c.height = Math.round(img.height * k);
      c.getContext("2d")!.drawImage(img, 0, 0, c.width, c.height);
      resolve(c.toDataURL("image/jpeg", 0.82));
      URL.revokeObjectURL(img.src);
    };
    img.onerror = reject;
    img.src = URL.createObjectURL(file);
  });
}
export function Achievements({ canManage }: { canManage: boolean }) {
  const { t, l } = useI18n();
  const { db, update } = useA();
  const [edit, setEdit] = useState<Achievement | null>(null);
  const [tm, setTm] = useState<Testimonial | null>(null);
  const blank = (): Achievement => ({ id: "", name: { en: "", mr: "" }, exam: { en: "", mr: "" }, result: { en: "", mr: "" }, year: new Date().getFullYear(), consent: false, published: false, demo: false });
  const save = () => {
    if (!edit || (!edit.name.en && !edit.name.mr)) return toast.error(t("Student name is required", "विद्यार्थ्याचे नाव आवश्यक"));
    if (edit.published && !edit.consent) return toast.error(t("Student consent is required before publishing", "प्रकाशनापूर्वी विद्यार्थ्याची संमती आवश्यक"));
    const fill = (x: L) => ({ en: x.en || x.mr, mr: x.mr || x.en });
    const rec = { ...edit, name: fill(edit.name), exam: fill(edit.exam), result: fill(edit.result) };
    update((db) => { if (rec.id) Object.assign(db.achievements.find((x) => x.id === rec.id)!, rec); else db.achievements.unshift({ ...rec, id: uid("ac") }); }, { action: "achievement.save", entity: rec.name.en });
    setEdit(null);
  };
  const onPhoto = async (f?: File) => { if (f && edit) setEdit({ ...edit, photo: await resizeImage(f) }); };
  return (
    <div className="space-y-6">
      <PageHeader title={t("Results & achievements", "निकाल व यश")} actions={canManage && <button className="btn-primary" onClick={() => setEdit(blank())}><Plus className="size-4" />{t("Add successful student", "यशस्वी विद्यार्थी जोडा")}</button>} />
      <Notice tone="warn">{t("Publish names and photos only with the student's consent. Unpublished records stay saved internally.", "विद्यार्थ्याच्या संमतीनेच नाव व फोटो प्रकाशित करा. अप्रकाशित नोंदी अंतर्गत जतन राहतात.")}</Notice>
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {db.achievements.map((a) => (
          <div key={a.id} className="card overflow-hidden">
            {a.photo ? <img src={a.photo} alt="" className="aspect-[4/3] w-full object-cover" /> : <div className="flex aspect-[4/3] items-center justify-center bg-slate-100 text-sm text-slate-400">{t("No photo", "फोटो नाही")}</div>}
            <div className="space-y-2 p-4">
              <p className="font-semibold">{l(a.name)} {a.featured && <Badge tone="orange">{t("Featured", "ठळक")}</Badge>}</p>
              <p className="text-sm text-emerald-700">{l(a.result)}</p>
              <p className="text-xs text-slate-500">{[l(a.exam), a.year].filter(Boolean).join(" · ")}</p>
              <div className="flex flex-wrap items-center gap-3 text-xs">
                {a.consent ? <Badge tone="green">{t("Consent ✓", "संमती ✓")}</Badge> : <Badge tone="red">{t("No consent", "संमती नाही")}</Badge>}
                <Toggle disabled={!canManage || !a.consent} checked={a.published} onChange={(v) => update((db) => { db.achievements.find((x) => x.id === a.id)!.published = v; }, { action: v ? "achievement.publish" : "achievement.unpublish", entity: a.name.en })} label={t("On website", "संकेतस्थळावर")} />
                {canManage && <button className="btn-ghost btn-sm ml-auto" onClick={() => setEdit(structuredClone(a))} aria-label="Edit"><Pencil className="size-4" /></button>}
              </div>
            </div>
          </div>
        ))}
      </div>

      <Card title={t("Google reviews", "Google रिव्ह्यूज")}>
        <GoogleReviews limit={3} adminHint />
        <details className="mt-4 text-sm text-slate-600">
          <summary className="cursor-pointer font-medium text-slate-800">{t("How to connect Google reviews", "Google रिव्ह्यूज कसे जोडावे")}</summary>
          <ol className="mt-2 list-decimal space-y-1 pl-5">
            <li>{t("Google Cloud Console → create a project → enable billing → enable \"Places API (New)\".", "Google Cloud Console → प्रोजेक्ट तयार करा → बिलिंग सुरू करा → \"Places API (New)\" सक्षम करा.")}</li>
            <li>{t("Create an API key and restrict it to Places API (New).", "API की तयार करा व ती फक्त Places API (New) पुरती मर्यादित करा.")}</li>
            <li>{t("Find the academy's Place ID with Google's Place ID Finder (developers.google.com/maps/documentation/places/web-service/place-id).", "Google Place ID Finder वापरून अकॅडमीचा Place ID शोधा.")}</li>
            <li>{t("Vercel → Project → Settings → Environment Variables: add GOOGLE_PLACES_API_KEY and GOOGLE_PLACE_ID, then redeploy.", "Vercel → Project → Settings → Environment Variables: GOOGLE_PLACES_API_KEY व GOOGLE_PLACE_ID जोडा, मग पुन्हा deploy करा.")}</li>
          </ol>
          <p className="mt-2 text-xs">{t("Google provides up to 5 reviews through this API and requires showing author attribution. Reviews are refreshed every 6 hours.", "या API द्वारे Google जास्तीत जास्त ५ रिव्ह्यूज देते आणि लेखकाचे नाव दाखवणे आवश्यक आहे. रिव्ह्यूज दर ६ तासांनी अद्ययावत होतात.")}</p>
        </details>
      </Card>

      <Card title={t("Testimonials shared with the academy", "अकॅडमीकडे आलेले अभिप्राय")} actions={canManage && <button className="btn-outline btn-sm" onClick={() => setTm({ id: "", name: "", course: "", text: { en: "", mr: "" }, consent: false, published: false, demo: false })}><Plus className="size-4" />{t("Add", "जोडा")}</button>}>
        {db.testimonials.length ? <ul className="space-y-2 text-sm">{db.testimonials.map((x) => <li key={x.id} className="flex items-center justify-between gap-2"><span>{l(x.text)} — <b>{x.name}</b></span><Toggle disabled={!canManage || !x.consent} checked={x.published} onChange={(v) => update((db) => { db.testimonials.find((y) => y.id === x.id)!.published = v; })} /></li>)}</ul> : <p className="text-sm text-slate-500">{t("None yet. Google reviews are shown on the website automatically once connected.", "अद्याप नाहीत. जोडल्यानंतर Google रिव्ह्यूज संकेतस्थळावर आपोआप दिसतील.")}</p>}
      </Card>

      <Modal open={!!edit} onClose={() => setEdit(null)} title={t("Successful student", "यशस्वी विद्यार्थी")} size="lg" footer={<button className="btn-primary" onClick={save}>{t("Save", "जतन")}</button>}>
        {edit && (
          <div className="grid gap-3 sm:grid-cols-2">
            <Field label="Name (English)">{(id) => <input id={id} className="input" value={edit.name.en} onChange={(e) => setEdit({ ...edit, name: { ...edit.name, en: e.target.value } })} />}</Field>
            <Field label="नाव (मराठी)">{(id) => <input id={id} className="input" value={edit.name.mr} onChange={(e) => setEdit({ ...edit, name: { ...edit.name, mr: e.target.value } })} />}</Field>
            <Field label="Selection / rank (English)" hint="e.g. Selected – Mumbai Police">{(id) => <input id={id} className="input" value={edit.result.en} onChange={(e) => setEdit({ ...edit, result: { ...edit.result, en: e.target.value } })} />}</Field>
            <Field label="निवड / क्रमांक (मराठी)" hint="उदा. मुंबई पोलीस पदी निवड">{(id) => <input id={id} className="input" value={edit.result.mr} onChange={(e) => setEdit({ ...edit, result: { ...edit.result, mr: e.target.value } })} />}</Field>
            <Field label="Exam / department (English)">{(id) => <input id={id} className="input" value={edit.exam.en} onChange={(e) => setEdit({ ...edit, exam: { ...edit.exam, en: e.target.value } })} />}</Field>
            <Field label="परीक्षा / विभाग (मराठी)">{(id) => <input id={id} className="input" value={edit.exam.mr} onChange={(e) => setEdit({ ...edit, exam: { ...edit.exam, mr: e.target.value } })} />}</Field>
            <Field label={t("Year (optional)", "वर्ष (ऐच्छिक)")}>{(id) => <input id={id} type="number" className="input" value={edit.year ?? ""} onChange={(e) => setEdit({ ...edit, year: e.target.value ? Number(e.target.value) : undefined })} />}</Field>
            <Field label={t("Course (optional)", "अभ्यासक्रम (ऐच्छिक)")}>{(id) => <select id={id} className="input" value={edit.courseId ?? ""} onChange={(e) => setEdit({ ...edit, courseId: e.target.value || undefined })}><option value="">—</option>{db.courses.map((c) => <option key={c.id} value={c.id}>{l(c.name)}</option>)}</select>}</Field>
            <div className="sm:col-span-2">
              <Field label={t("Photo or congratulations poster", "फोटो किंवा अभिनंदन पोस्टर")} hint={t("Resized automatically. In this prototype the photo is saved in this browser only; permanent website images go in /public/results (or cloud storage once the backend exists).", "आपोआप लहान केले जाते. या प्रोटोटाइपमध्ये फोटो फक्त या ब्राउझरमध्ये जतन होतो; कायमस्वरूपी प्रतिमा /public/results (किंवा बॅकएंड आल्यावर क्लाउड स्टोरेज) मध्ये ठेवा.")}>{(id) => <input id={id} type="file" accept="image/*" className="input" onChange={(e) => onPhoto(e.target.files?.[0])} />}</Field>
              {edit.photo && <img src={edit.photo} alt="" className="mt-2 h-40 rounded-lg border object-cover" />}
            </div>
            <Toggle checked={edit.consent} onChange={(v) => setEdit({ ...edit, consent: v })} label={t("Student consent recorded", "विद्यार्थ्याची संमती नोंदवली")} />
            <Toggle checked={edit.published} onChange={(v) => setEdit({ ...edit, published: v })} label={t("Show on website", "संकेतस्थळावर दाखवा")} />
            <Toggle checked={!!edit.featured} onChange={(v) => setEdit({ ...edit, featured: v })} label={t("Feature on home page", "मुख्यपृष्ठावर ठळक")} />
          </div>
        )}
      </Modal>
      <Modal open={!!tm} onClose={() => setTm(null)} title={t("Testimonial", "अभिप्राय")} footer={<button className="btn-primary" onClick={() => { if (!tm?.name) return; if (tm.published && !tm.consent) return toast.error(t("Consent required", "संमती आवश्यक")); update((db) => db.testimonials.unshift({ ...tm, id: uid("tm"), text: { en: tm.text.en || tm.text.mr, mr: tm.text.mr || tm.text.en } }), { action: "testimonial.add", entity: tm.name }); setTm(null); }}>{t("Save", "जतन")}</button>}>
        {tm && <div className="grid gap-3"><Field label={t("Student name", "नाव")}>{(id) => <input id={id} className="input" value={tm.name} onChange={(e) => setTm({ ...tm, name: e.target.value })} />}</Field><Field label={t("Course / result", "अभ्यासक्रम / निकाल")}>{(id) => <input id={id} className="input" value={tm.course} onChange={(e) => setTm({ ...tm, course: e.target.value })} />}</Field><Field label="मराठी">{(id) => <textarea id={id} rows={3} className="input" value={tm.text.mr} onChange={(e) => setTm({ ...tm, text: { ...tm.text, mr: e.target.value } })} />}</Field><Field label="English">{(id) => <textarea id={id} rows={3} className="input" value={tm.text.en} onChange={(e) => setTm({ ...tm, text: { ...tm.text, en: e.target.value } })} />}</Field><Toggle checked={tm.consent} onChange={(v) => setTm({ ...tm, consent: v })} label={t("Consent recorded", "संमती नोंदवली")} /><Toggle checked={tm.published} onChange={(v) => setTm({ ...tm, published: v })} label={t("Show on website", "संकेतस्थळावर दाखवा")} /></div>}
      </Modal>
    </div>
  );
}

/* ---------------- Referrals ---------------- */
export function Referrals({ canManage }: { canManage: boolean }) {
  const { t, d } = useI18n();
  const { db, update } = useA();
  const r = db.settings.referral;
  const set = (p: Partial<typeof r>) => update((db) => { Object.assign(db.settings.referral, p); }, { action: "referral.settings", entity: "referral", detail: JSON.stringify(p) });
  return (
    <div className="space-y-5">
      <PageHeader title={t("Referral programme", "रेफरल कार्यक्रम")} actions={<Toggle disabled={!canManage} checked={r.enabled} onChange={(v) => set({ enabled: v })} label={r.enabled ? t("Enabled", "सक्रिय") : t("Disabled", "बंद")} />} />
      {!r.enabled && <Notice>{t("The programme is designed but disabled. Students won't see referral codes until it is enabled.", "कार्यक्रम तयार आहे पण बंद आहे. सक्रिय होईपर्यंत विद्यार्थ्यांना रेफरल कोड दिसणार नाहीत.")}</Notice>}
      <div className="grid gap-3 sm:grid-cols-4">
        <StatCard label={t("Referrals", "रेफरल")} value={db.referrals.length} />
        <StatCard label={t("Converted", "रूपांतरित")} value={db.referrals.filter((x) => ["admitted", "paid"].includes(x.status)).length} tone="green" />
        <StatCard label={t("Rewards pending", "बाकी बक्षिसे")} value={fmtINR(db.referrals.filter((x) => x.rewardStatus === "pending").reduce((a, x) => a + x.reward, 0))} tone="amber" />
        <StatCard label={t("Rewards credited", "दिलेली बक्षिसे")} value={fmtINR(db.referrals.filter((x) => x.rewardStatus === "credited").reduce((a, x) => a + x.reward, 0))} tone="violet" />
      </div>
      <Card title={t("Rules", "नियम")}>
        <div className="grid gap-3 sm:grid-cols-3">
          <Field label={t("Reward type", "बक्षीस प्रकार")}>{(id) => <select id={id} disabled={!canManage} className="input" value={r.rewardType} onChange={(e) => set({ rewardType: e.target.value as "flat" | "percent" })}><option value="flat">{t("Fixed amount", "निश्चित रक्कम")}</option><option value="percent">{t("Percentage", "टक्केवारी")}</option></select>}</Field>
          <Field label={t("Referrer reward", "संदर्भदात्याचे बक्षीस")}>{(id) => <input id={id} disabled={!canManage} type="number" className="input" value={r.referrerReward} onChange={(e) => set({ referrerReward: Number(e.target.value) })} />}</Field>
          <Field label={t("Referred student discount", "नवीन विद्यार्थ्याची सवलत")}>{(id) => <input id={id} disabled={!canManage} type="number" className="input" value={r.refereeDiscount} onChange={(e) => set({ refereeDiscount: Number(e.target.value) })} />}</Field>
          <Field label={t("Reward trigger", "बक्षीस केव्हा")}>{(id) => <select id={id} disabled={!canManage} className="input" value={r.trigger} onChange={(e) => set({ trigger: e.target.value as "admission" | "fullPayment" })}><option value="admission">{t("After admission", "प्रवेशानंतर")}</option><option value="fullPayment">{t("After full payment", "पूर्ण पेमेंटनंतर")}</option></select>}</Field>
          <Field label={t("Campaign valid to", "मोहीम पर्यंत")}>{(id) => <input id={id} disabled={!canManage} type="date" className="input" value={r.validTo} onChange={(e) => set({ validTo: e.target.value })} />}</Field>
          <Field label={t("Max referrals per student", "प्रति विद्यार्थी कमाल")}>{(id) => <input id={id} disabled={!canManage} type="number" className="input" value={r.limitPerStudent} onChange={(e) => set({ limitPerStudent: Number(e.target.value) })} />}</Field>
        </div>
        <p className="mt-3 text-xs text-slate-500">{t("Safeguards: self-referrals, duplicate mobile numbers and duplicate rewards are blocked automatically.", "सुरक्षा: स्वतःचा रेफरल, डुप्लिकेट मोबाईल व दुहेरी बक्षिसे आपोआप रोखली जातात.")}</p>
      </Card>
      <Table head={[t("Referrer", "संदर्भदाता"), t("Referred", "संदर्भित"), t("Date", "दिनांक"), t("Status", "स्थिती"), t("Reward", "बक्षीस"), ""]} empty={!db.referrals.length}>
        {db.referrals.map((x) => <tr key={x.id}><td>{db.students.find((s) => s.id === x.referrerId)?.name}</td><td>{x.name}<p className="text-xs text-slate-500">{x.mobile}</p></td><td className="text-xs">{d(x.createdAt)}</td><td><Badge>{x.status}</Badge></td><td>{fmtINR(x.reward)} <Badge>{x.rewardStatus}</Badge></td><td>{canManage && x.rewardStatus === "pending" && <button className="btn-outline btn-sm" onClick={() => update((db) => { db.referrals.find((y) => y.id === x.id)!.rewardStatus = "credited"; }, { action: "referral.credit", entity: x.id })}>{t("Credit", "जमा करा")}</button>}</td></tr>)}
      </Table>
    </div>
  );
}

/* ---------------- ID cards & certificates ---------------- */
export function Certificates({ canManage }: { canManage: boolean }) {
  const { t, l, d } = useI18n();
  const { db, me, update } = useA();
  const [tab, setTab] = useState<"certs" | "id">("certs");
  const [gen, setGen] = useState<{ kind: "completion" | "participation" | "achievement"; title: string; batchId: string; studentIds: string[] } | null>(null);
  const [view, setView] = useState<string | null>(null);
  const [idFor, setIdFor] = useState(db.students[0]?.id ?? "");
  const cert = db.certificates.find((c) => c.id === view);
  const issue = () => {
    if (!gen || !gen.title || !gen.studentIds.length) return toast.error(t("Title and students required", "शीर्षक व विद्यार्थी आवश्यक"));
    update((db) => gen.studentIds.forEach((sid) => db.certificates.push({ id: uid("ce"), number: `VA-CERT-${new Date().getFullYear()}-${String(db.certificates.length + 1).padStart(4, "0")}`, studentId: sid, kind: gen.kind, title: gen.title, issuedAt: today(), reissues: 0 })), { action: "certificate.issue", entity: gen.title, detail: `${gen.studentIds.length} certificate(s)` });
    setGen(null);
    toast.success(t("Certificates generated", "प्रमाणपत्रे तयार"));
  };
  const batchStudents = (bid: string) => db.enrolments.filter((e) => e.batchId === bid).map((e) => e.studentId);
  return (
    <div>
      <PageHeader title={t("ID cards & certificates", "ओळखपत्रे व प्रमाणपत्रे")} actions={canManage && tab === "certs" && <button className="btn-primary" onClick={() => setGen({ kind: "completion", title: "", batchId: db.batches[0].id, studentIds: [] })}><Plus className="size-4" />{t("Generate (single / bulk)", "तयार करा (एक / अनेक)")}</button>} />
      <Tabs tabs={[{ id: "certs", label: t("Certificates", "प्रमाणपत्रे"), count: db.certificates.length }, { id: "id", label: t("ID cards", "ओळखपत्रे") }]} value={tab} onChange={setTab} />
      {tab === "certs" && (
        <Table head={[t("Number", "क्रमांक"), t("Student", "विद्यार्थी"), t("Type", "प्रकार"), t("Title", "शीर्षक"), t("Issued", "दिनांक"), t("Reissues", "पुनर्निर्गमन"), ""]} empty={!db.certificates.length}>
          {db.certificates.map((c) => <tr key={c.id}><td className="font-mono text-xs">{c.number}</td><td>{db.students.find((s) => s.id === c.studentId)?.name}</td><td className="text-xs">{c.kind}</td><td>{c.title}</td><td className="text-xs">{d(c.issuedAt)}</td><td>{c.reissues}</td><td><div className="flex gap-1"><button className="btn-ghost btn-sm" onClick={() => setView(c.id)}><Eye className="size-4" /></button>{canManage && <button className="btn-ghost btn-sm" title={t("Reissue", "पुनर्निर्गमन")} onClick={() => update((db) => { db.certificates.find((x) => x.id === c.id)!.reissues++; }, { action: "certificate.reissue", entity: c.number, detail: `by ${me.name}` })}><RotateCcw className="size-4" /></button>}</div></td></tr>)}
        </Table>
      )}
      {tab === "id" && (
        <div className="grid gap-5 lg:grid-cols-[280px_1fr]">
          <Card title={t("Select student", "विद्यार्थी निवडा")}><select className="input" value={idFor} onChange={(e) => setIdFor(e.target.value)} aria-label="Student">{db.students.map((s) => <option key={s.id} value={s.id}>{s.name} ({s.code})</option>)}</select><button className="btn-primary mt-3 w-full" onClick={() => window.print()}><Printer className="size-4" />{t("Print / PDF", "प्रिंट / PDF")}</button><p className="mt-2 text-xs text-slate-500">{t("Bulk printing by batch uses the same template.", "बॅचनिहाय एकत्र छपाईसाठी हाच साचा वापरला जातो.")}</p></Card>
          <div><IdCardFace studentId={idFor} /></div>
        </div>
      )}
      <Modal open={!!gen} onClose={() => setGen(null)} title={t("Generate certificates", "प्रमाणपत्रे तयार करा")} size="lg" footer={<button className="btn-primary" onClick={issue}>{t("Generate", "तयार करा")} ({gen?.studentIds.length ?? 0})</button>}>
        {gen && <div className="grid gap-3 sm:grid-cols-2"><Field label={t("Type", "प्रकार")}>{(id) => <select id={id} className="input" value={gen.kind} onChange={(e) => setGen({ ...gen, kind: e.target.value as typeof gen.kind })}><option value="completion">{t("Course completion", "अभ्यासक्रम पूर्ती")}</option><option value="participation">{t("Test participation", "चाचणी सहभाग")}</option><option value="achievement">{t("Achievement", "यश")}</option></select>}</Field><Field label={t("Title", "शीर्षक")}>{(id) => <input id={id} className="input" value={gen.title} onChange={(e) => setGen({ ...gen, title: e.target.value })} />}</Field><Field label={t("Batch", "बॅच")}>{(id) => <select id={id} className="input" value={gen.batchId} onChange={(e) => setGen({ ...gen, batchId: e.target.value, studentIds: [] })}>{db.batches.map((b) => <option key={b.id} value={b.id}>{b.name}</option>)}</select>}</Field><div className="flex items-end"><button className="btn-outline btn-sm" onClick={() => setGen({ ...gen, studentIds: batchStudents(gen.batchId) })}>{t("Select whole batch", "संपूर्ण बॅच निवडा")}</button></div><div className="flex max-h-48 flex-wrap gap-2 overflow-y-auto rounded-lg border p-2 sm:col-span-2">{batchStudents(gen.batchId).map((sid) => <label key={sid} className="flex items-center gap-1 text-xs"><input type="checkbox" checked={gen.studentIds.includes(sid)} onChange={(e) => setGen({ ...gen, studentIds: e.target.checked ? [...gen.studentIds, sid] : gen.studentIds.filter((x) => x !== sid) })} />{db.students.find((s) => s.id === sid)?.name}</label>)}</div></div>}
      </Modal>
      {cert && (
        <Modal open onClose={() => setView(null)} title={cert.number} size="xl" footer={<button className="btn-primary" onClick={() => window.print()}><Printer className="size-4" />{t("Print / PDF", "प्रिंट / PDF")}</button>}>
          <div className="print-area relative mx-auto aspect-[1.414] w-full max-w-3xl border-8 border-double border-brand-700 bg-gradient-to-br from-white to-brand-50 p-8 text-center">
            <img src={academy.logoUrl} alt="" className="mx-auto mb-2 size-20 rounded-full" /><p className="text-sm font-semibold tracking-widest text-brand-700">{academy.name.en.toUpperCase()} · {academy.name.mr}</p>
            <p className="mt-4 text-3xl font-bold text-brand-950">{cert.kind === "completion" ? "Certificate of Completion" : cert.kind === "participation" ? "Certificate of Participation" : "Certificate of Achievement"}</p>
            <p className="text-lg text-brand-800">{cert.kind === "completion" ? "अभ्यासक्रम पूर्ती प्रमाणपत्र" : cert.kind === "participation" ? "सहभाग प्रमाणपत्र" : "गौरव प्रमाणपत्र"}</p>
            <p className="mt-6 text-sm text-slate-600">This is to certify that / प्रमाणित करण्यात येते की</p>
            <p className="mt-2 text-2xl font-bold">{db.students.find((s) => s.id === cert.studentId)?.name}</p>
            <p className="mt-2 text-slate-700">{cert.title}</p>
            <div className="mt-8 flex items-end justify-between text-xs">
              <div className="text-left"><p>{t("Date", "दिनांक")}: {d(cert.issuedAt)}</p><p className="font-mono">{cert.number}</p></div>
              <QRCodeSVG value={`${typeof window !== "undefined" ? window.location.origin : ""}/verify?code=${cert.number}`} size={64} />
              <div className="text-right"><div className="mb-1 h-8 w-32 border-b border-slate-400" /><p>{t("Authorised signature", "अधिकृत स्वाक्षरी")}</p></div>
            </div>
          </div>
        </Modal>
      )}
    </div>
  );
}

/* ---------------- Reports ---------------- */
export function Reports() {
  const { t, l, d } = useI18n();
  const { db } = useA();
  const finance = db.staff.find((s) => s.id === useStore.getState().session.userId);
  const canFin = finance?.role === "owner" || finance?.permissions.fees !== "none";
  const [tab, setTab] = useState("admissions");
  const [from, setFrom] = useState(`${new Date().getFullYear() - 1}-04-01`);
  const [to, setTo] = useState(today());
  const [course, setCourse] = useState("all");
  const [batch, setBatch] = useState("all");
  const [branch, setBranch] = useState("all");
  const inRange = (x: string) => x.slice(0, 10) >= from && x.slice(0, 10) <= to;
  const enr = db.enrolments.filter((e) => inRange(e.createdAt) && (course === "all" || e.courseId === course) && (batch === "all" || e.batchId === batch) && (branch === "all" || e.branchId === branch));
  const pays = db.payments.filter((p) => inRange(p.date) && (course === "all" || db.enrolments.find((e) => e.id === p.enrolmentId)?.courseId === course));
  const months = Array.from({ length: 6 }, (_, i) => { const dd = new Date(); dd.setMonth(dd.getMonth() - 5 + i); return dd.toISOString().slice(0, 7); });
  const ly = (m: string) => `${Number(m.slice(0, 4)) - 1}${m.slice(4)}`;
  let rows: Record<string, unknown>[] = [];
  let chart: { label: string; value: number; secondary?: number }[] = [];
  let fmt = (n: number) => String(n);
  if (tab === "admissions") { rows = enr.map((e) => ({ Date: e.createdAt, Student: db.students.find((s) => s.id === e.studentId)?.name, Course: l(db.courses.find((c) => c.id === e.courseId)?.name), Batch: db.batches.find((b) => b.id === e.batchId)?.name, Admission: e.admissionStatus, Payment: e.paymentStatus, Enrolment: e.enrolmentStatus })); chart = months.map((m) => ({ label: d(m + "-01", { month: "short" }), value: db.enrolments.filter((e) => e.createdAt.slice(0, 7) === m).length, secondary: db.enrolments.filter((e) => e.createdAt.slice(0, 7) === ly(m)).length })); }
  if (tab === "fees") { rows = pays.map((p) => ({ Receipt: p.receiptNo, Date: p.date.slice(0, 10), Student: db.students.find((s) => s.id === p.studentId)?.name, Amount: p.amount, Method: p.method, Status: p.status })); chart = months.map((m) => ({ label: d(m + "-01", { month: "short" }), value: db.payments.filter((p) => p.status === "success" && p.date.slice(0, 7) === m).reduce((a, p) => a + p.amount, 0), secondary: db.payments.filter((p) => p.status === "success" && p.date.slice(0, 7) === ly(m)).reduce((a, p) => a + p.amount, 0) })); fmt = fmtINR; }
  if (tab === "attendance") { const st = db.students.filter((s) => db.enrolments.some((e) => e.studentId === s.id && (course === "all" || e.courseId === course) && (batch === "all" || e.batchId === batch))); rows = st.map((s) => { const a = attendanceStats(db, s.id, batch === "all" ? undefined : batch); return { Student: s.name, ID: s.code, Present: a.present, Late: a.late, Absent: a.absent, Percent: a.percent.toFixed(1) }; }); chart = db.batches.filter((b) => batch === "all" || b.id === batch).map((b) => { const r = db.attendance.filter((a) => a.batchId === b.id && inRange(a.date)); return { label: b.name.split(" ")[0], value: r.length ? Math.round((r.filter((x) => x.status !== "A").length / r.length) * 100) : 0 }; }); fmt = (n) => `${n}%`; }
  if (tab === "exams") { rows = db.attempts.filter((a) => a.submittedAt && inRange(a.submittedAt)).map((a) => ({ Test: l(db.tests.find((x) => x.id === a.testId)?.title), Student: db.students.find((s) => s.id === a.studentId)?.name, Score: a.score, Correct: a.correct, Wrong: a.incorrect, Skipped: a.unanswered })); chart = db.tests.map((x) => { const at = db.attempts.filter((a) => a.testId === x.id && a.submittedAt); return { label: l(x.title).slice(0, 10), value: at.length ? Math.round(at.reduce((s, a) => s + a.score, 0) / at.length) : 0 }; }); }
  if (tab === "enquiries") { const eq = db.enquiries.filter((e) => inRange(e.createdAt) && (course === "all" || e.courseId === course)); rows = eq.map((e) => ({ Date: e.createdAt.slice(0, 10), Name: e.name, Course: l(db.courses.find((c) => c.id === e.courseId)?.name), Source: e.source, Status: e.status })); chart = ["new", "contacted", "followup", "converted", "notInterested"].map((s) => ({ label: s, value: eq.filter((e) => e.status === s).length })); }
  if (tab === "faculty") { rows = db.staff.filter((s) => s.role === "faculty").map((f) => ({ Faculty: f.name, Batches: f.batchIds.length, LiveClasses: db.live.filter((c) => c.facultyId === f.id).length, Materials: db.materials.filter((m) => m.uploadedBy === f.id).length, Assignments: db.assignments.filter((a) => a.createdBy === f.id).length, PendingGrading: db.submissions.filter((s) => s.status === "submitted" && db.assignments.find((a) => a.id === s.assignmentId)?.createdBy === f.id).length })); chart = rows.map((r) => ({ label: String(r.Faculty).split(" ")[2] ?? String(r.Faculty), value: Number(r.LiveClasses) + Number(r.Materials) + Number(r.Assignments) })); }
  if (tab === "courses") { rows = db.batches.filter((b) => course === "all" || b.courseId === course).map((b) => { const n = db.enrolments.filter((e) => e.batchId === b.id && e.enrolmentStatus === "active").length; return { Batch: b.name, Course: l(db.courses.find((c) => c.id === b.courseId)?.name), Mode: b.mode, Enrolled: n, Capacity: b.capacity, Occupancy: `${Math.round((n / b.capacity) * 100)}%` }; }); chart = rows.map((r) => ({ label: String(r.Batch).split(" ")[0], value: parseInt(String(r.Occupancy)) })); fmt = (n) => `${n}%`; }
  if (tab === "library") { rows = db.memberships.map((m) => ({ Member: db.students.find((s) => s.id === m.studentId)?.name, Plan: l(db.libraryPlans.find((p) => p.id === m.planId)?.name), Start: m.start, End: m.end, Seat: m.seat, Status: m.status, Paid: membershipPaid(db, m.id) })); chart = ["active", "expired", "suspended", "cancelled"].map((s) => ({ label: s, value: db.memberships.filter((m) => m.status === s).length })); }
  if (tab === "students") { rows = db.students.map((s) => ({ ID: s.code, Name: s.name, Status: s.status, Joined: s.createdAt, Courses: db.enrolments.filter((e) => e.studentId === s.id).length })); chart = [{ label: "Active", value: db.students.filter((s) => s.status === "active").length }, { label: "Inactive", value: db.students.filter((s) => s.status === "inactive").length }]; }
  const tabs = [["admissions", "Admissions", "प्रवेश"], ["students", "Students", "विद्यार्थी"], ...(canFin ? [["fees", "Fees / payments", "शुल्क"]] : []), ["attendance", "Attendance", "उपस्थिती"], ["exams", "Exams / results", "परीक्षा"], ["enquiries", "Enquiries", "चौकशी"], ["faculty", "Faculty", "प्राध्यापक"], ["courses", "Courses / batches", "अभ्यासक्रम"], ["library", "Abhyasika Kendra", "अभ्यासिका केंद्र"]];
  return (
    <div>
      <PageHeader title={t("Reports & analytics", "अहवाल व विश्लेषण")} actions={<><button className="btn-outline" onClick={() => downloadFile(`report-${tab}.csv`, toCSV(rows))}><Download className="size-4" />CSV</button><button className="btn-outline" onClick={() => downloadFile(`report-${tab}.xls`, toCSV(rows).replace(/,/g, "\t"), "application/vnd.ms-excel")}>Excel</button><button className="btn-outline" onClick={() => window.print()}>PDF</button></>} />
      <Card className="mb-4" bodyClass="flex flex-wrap items-end gap-3">
        <Field label={t("From", "पासून")}>{(id) => <input id={id} type="date" className="input" value={from} onChange={(e) => setFrom(e.target.value)} />}</Field>
        <Field label={t("To", "पर्यंत")}>{(id) => <input id={id} type="date" className="input" value={to} onChange={(e) => setTo(e.target.value)} />}</Field>
        <Field label={t("Course", "अभ्यासक्रम")}>{(id) => <select id={id} className="input" value={course} onChange={(e) => setCourse(e.target.value)}><option value="all">{t("All", "सर्व")}</option>{db.courses.map((c) => <option key={c.id} value={c.id}>{l(c.name)}</option>)}</select>}</Field>
        <Field label={t("Batch", "बॅच")}>{(id) => <select id={id} className="input" value={batch} onChange={(e) => setBatch(e.target.value)}><option value="all">{t("All", "सर्व")}</option>{db.batches.filter((b) => course === "all" || b.courseId === course).map((b) => <option key={b.id} value={b.id}>{b.name}</option>)}</select>}</Field>
        <Field label={t("Branch", "शाखा")}>{(id) => <select id={id} className="input" value={branch} onChange={(e) => setBranch(e.target.value)}><option value="all">{t("All", "सर्व")}</option>{db.branches.map((b) => <option key={b.id} value={b.id}>{l(b.name)}</option>)}</select>}</Field>
      </Card>
      <Tabs tabs={tabs.map(([id, en, mr]) => ({ id, label: t(en, mr) }))} value={tab} onChange={setTab} />
      <Card className="mb-4" title={["admissions", "fees"].includes(tab) ? <span className="flex items-center gap-3">{t("Monthly comparison", "मासिक तुलना")}<span className="flex items-center gap-1 text-xs font-normal"><span className="size-2.5 rounded bg-brand-600" />{t("This year", "हे वर्ष")}<span className="ml-2 size-2.5 rounded bg-saffron-400" />{t("Last year", "मागील वर्ष")}</span></span> : t("Summary", "सारांश")}><BarChart data={chart} format={fmt} /></Card>
      <div className="card overflow-x-auto">
        <table className="table-base"><thead><tr>{Object.keys(rows[0] ?? { "—": "" }).map((k) => <th key={k}>{k}</th>)}</tr></thead><tbody>{rows.slice(0, 100).map((r, i) => <tr key={i}>{Object.values(r).map((v, j) => <td key={j} className="text-sm">{typeof v === "number" && tab === "fees" && j === 3 ? fmtINR(v) : String(v ?? "")}</td>)}</tr>)}</tbody></table>
        {!rows.length && <p className="p-6 text-center text-sm text-slate-500">{t("No data for the selected filters", "निवडलेल्या फिल्टरसाठी माहिती नाही")}</p>}
      </div>
      <p className="mt-2 text-xs text-slate-500">{t("Exports are UTF-8 with BOM so Marathi text opens correctly in Excel. Historical records are preserved.", "निर्यात UTF-8 (BOM) मध्ये असल्याने मराठी मजकूर Excel मध्ये योग्य दिसतो. ऐतिहासिक नोंदी जतन राहतात.")}</p>
    </div>
  );
}

/* ---------------- Website CMS ---------------- */
function LInput({ label, value, onChange, area }: { label: string; value: L; onChange: (v: L) => void; area?: boolean }) {
  return (
    <div className="grid gap-2 sm:grid-cols-2">
      <Field label={`${label} (English)`}>{(id) => area ? <textarea id={id} rows={3} className="input" value={value.en} onChange={(e) => onChange({ ...value, en: e.target.value })} /> : <input id={id} className="input" value={value.en} onChange={(e) => onChange({ ...value, en: e.target.value })} />}</Field>
      <Field label={`${label} (मराठी)`}>{(id) => area ? <textarea id={id} rows={3} className="input" value={value.mr} onChange={(e) => onChange({ ...value, mr: e.target.value })} /> : <input id={id} className="input" value={value.mr} onChange={(e) => onChange({ ...value, mr: e.target.value })} />}</Field>
    </div>
  );
}
const SECTION_LABEL: Record<HomeSectionKey, L> = {
  photos: { en: "Extra photo strip (optional, off by default)", mr: "अतिरिक्त फोटो पट्टी (ऐच्छिक, डीफॉल्ट बंद)" },
  courses: { en: "Courses we offer", mr: "आमचे अभ्यासक्रम" },
  why: { en: "Why choose us", mr: "आम्हालाच का" },
  batches: { en: "Batches & announcements", mr: "बॅचेस व सूचना" },
  results: { en: "Results", mr: "निकाल" },
  reviews: { en: "Google reviews", mr: "गुगल पुनरावलोकने" },
  library: { en: "Faculty & Abhyasika Kendra", mr: "प्राध्यापक व अभ्यासिका केंद्र" },
  cta: { en: "Call-to-action banner", mr: "संपर्क बॅनर" },
};

function moveItem<T>(arr: T[], i: number, dir: -1 | 1): T[] {
  const j = i + dir;
  if (j < 0 || j >= arr.length) return arr;
  const next = [...arr];
  [next[i], next[j]] = [next[j], next[i]];
  return next;
}

function HomeLayout({ cms, setCms, canManage }: { cms: Settings["cms"]; setCms: (c: Settings["cms"]) => void; canManage: boolean }) {
  const { t, l } = useI18n();
  const sections = cms.homeSections ?? DEFAULT_HOME_SECTIONS;
  const photos = cms.homePhotos ?? DEFAULT_HOME_PHOTOS;
  const setSections = (next: HomeSection[]) => setCms({ ...cms, homeSections: next });
  const setPhotos = (next: HomePhoto[]) => setCms({ ...cms, homePhotos: next });
  const addPhoto = async (file: File) => {
    const src = await resizeImage(file);
    setPhotos([...photos, { id: uid("ph"), src, caption: { en: "", mr: "" }, enabled: true }]);
  };
  const updatePhoto = (i: number, patch: Partial<HomePhoto>) => setPhotos(photos.map((p, k) => (k === i ? { ...p, ...patch } : p)));
  return (
    <div className="grid gap-4 lg:grid-cols-2">
      <Card title={t("Section order", "विभागांचा क्रम")}>
        <p className="mb-3 text-xs text-slate-500">{t("The hero and enquiry form stay at the top. Use the arrows to reorder and the switch to show or hide. Save to publish.", "वरचा भाग व चौकशी फॉर्म नेहमी वर राहतील. बाणांनी क्रम बदला, स्विचने दाखवा/लपवा. प्रसिद्ध करण्यासाठी जतन करा.")}</p>
        <ul className="space-y-2">
          {sections.map((s, i) => (
            <li key={s.key} className="flex items-center justify-between gap-2 rounded-lg border border-slate-200 p-2 text-sm">
              <span className={s.enabled ? "" : "text-slate-400 line-through"}>{i + 1}. {l(SECTION_LABEL[s.key])}</span>
              <span className="flex items-center gap-1">
                <Toggle disabled={!canManage} checked={s.enabled} onChange={(v) => setSections(sections.map((x, k) => (k === i ? { ...x, enabled: v } : x)))} />
                <button className="btn-ghost btn-sm" disabled={!canManage || i === 0} onClick={() => setSections(moveItem(sections, i, -1))} aria-label={t("Move up", "वर हलवा")}>↑</button>
                <button className="btn-ghost btn-sm" disabled={!canManage || i === sections.length - 1} onClick={() => setSections(moveItem(sections, i, 1))} aria-label={t("Move down", "खाली हलवा")}>↓</button>
              </span>
            </li>
          ))}
        </ul>
      </Card>
      <Card title={t("Photos (below enquiry form)", "फोटो (चौकशी फॉर्मखाली)")}>
        {canManage && <input type="file" accept="image/*" className="input mb-3" aria-label={t("Upload photo", "फोटो अपलोड")} onChange={(e) => { const f = e.target.files?.[0]; if (f) addPhoto(f); e.target.value = ""; }} />}
        <ul className="space-y-3">
          {photos.map((p, i) => (
            <li key={p.id} className="flex gap-3 rounded-lg border border-slate-200 p-2">
              <img src={p.src} alt="" className="h-20 w-28 shrink-0 rounded object-cover" />
              <div className="min-w-0 flex-1 space-y-2">
                <input className="input" disabled={!canManage} placeholder="Caption (English)" value={p.caption.en} onChange={(e) => updatePhoto(i, { caption: { ...p.caption, en: e.target.value } })} />
                <input className="input" disabled={!canManage} placeholder="कॅप्शन (मराठी)" value={p.caption.mr} onChange={(e) => updatePhoto(i, { caption: { ...p.caption, mr: e.target.value } })} />
                <div className="flex flex-wrap items-center gap-2">
                  <Toggle disabled={!canManage} checked={p.enabled} onChange={(v) => updatePhoto(i, { enabled: v })} label={t("Show", "दाखवा")} />
                  <button className="btn-ghost btn-sm" disabled={!canManage || i === 0} onClick={() => setPhotos(moveItem(photos, i, -1))} aria-label={t("Move up", "वर हलवा")}>↑</button>
                  <button className="btn-ghost btn-sm" disabled={!canManage || i === photos.length - 1} onClick={() => setPhotos(moveItem(photos, i, 1))} aria-label={t("Move down", "खाली हलवा")}>↓</button>
                  <button className="btn-ghost btn-sm text-red-600" disabled={!canManage} onClick={() => setPhotos(photos.filter((_, k) => k !== i))} aria-label={t("Delete photo", "फोटो हटवा")}>{t("Delete", "हटवा")}</button>
                </div>
              </div>
            </li>
          ))}
        </ul>
        {!photos.length && <p className="text-sm text-slate-400">{t("No photos yet", "अद्याप फोटो नाहीत")}</p>}
      </Card>
    </div>
  );
}

export function Cms({ canManage }: { canManage: boolean }) {
  const { t, l, d } = useI18n();
  const { db, update } = useA();
  const [tab, setTab] = useState("home");
  const [cms, setCms] = useState(db.settings.cms);
  const [seo, setSeo] = useState(db.settings.seo);
  const [ann, setAnn] = useState<Announcement | null>(null);
  const save = () => { update((db) => { db.settings.cms = cms; db.settings.seo = seo; }, { action: "cms.update", entity: tab }); toast.success(t("Website updated", "संकेतस्थळ अद्ययावत")); };
  return (
    <div>
      <PageHeader title={t("Website CMS", "वेबसाइट CMS")} subtitle={t("Edit public website content without a developer.", "डेव्हलपरशिवाय सार्वजनिक संकेतस्थळ बदला.")} actions={<><a href="/" target="_blank" className="btn-outline"><ExternalLink className="size-4" />{t("View site", "साइट पहा")}</a>{canManage && <button className="btn-primary" onClick={save}>{t("Save & publish", "जतन व प्रकाशित")}</button>}</>} />
      <Tabs tabs={[{ id: "home", label: t("Home & about", "मुख्यपृष्ठ व माहिती") }, { id: "layout", label: t("Homepage layout", "मुख्यपृष्ठ मांडणी") }, { id: "ann", label: t("Announcements", "सूचना") }, { id: "faq", label: "FAQ" }, { id: "faculty", label: t("Faculty profiles", "प्राध्यापक") }, { id: "seo", label: "SEO" }]} value={tab} onChange={setTab} />
      {tab === "home" && (
        <div className="space-y-4">
          <Card title={t("Hero", "मुख्य भाग")}><div className="space-y-3"><LInput label="Headline" value={cms.heroTitle} onChange={(v) => setCms({ ...cms, heroTitle: v })} /><LInput label="Subtitle" value={cms.heroSubtitle} onChange={(v) => setCms({ ...cms, heroSubtitle: v })} area /></div></Card>
          <Card title={t("Why choose us", "आम्हालाच का")}><div className="space-y-2">{cms.whyUs.map((w, i) => <LInput key={i} label={`#${i + 1}`} value={w} onChange={(v) => { const a = [...cms.whyUs]; a[i] = v; setCms({ ...cms, whyUs: a }); }} />)}</div></Card>
          <Card title={t("About & Abhyasika Kendra", "माहिती व अभ्यासिका केंद्र")}><div className="space-y-3"><LInput label="About" value={cms.aboutBody} onChange={(v) => setCms({ ...cms, aboutBody: v })} area /><LInput label="Abhyasika Kendra blurb" value={cms.libraryBlurb} onChange={(v) => setCms({ ...cms, libraryBlurb: v })} area /></div></Card>
          <Card title={t("Gallery", "छायाचित्रे")}><input type="file" multiple accept="image/*" className="input" aria-label="Gallery upload" /><p className="mt-1 text-xs text-slate-500">{t("Images are compressed automatically for low-bandwidth users (production).", "कमी बँडविड्थसाठी प्रतिमा आपोआप संकुचित होतील (प्रत्यक्षात).")}</p></Card>
        </div>
      )}
      {tab === "layout" && <HomeLayout cms={cms} setCms={setCms} canManage={canManage} />}
      {tab === "ann" && (
        <>
          {canManage && <button className="btn-outline mb-3" onClick={() => setAnn({ id: "", title: { en: "", mr: "" }, body: { en: "", mr: "" }, date: today(), pinned: false, published: true })}><Plus className="size-4" />{t("New announcement", "नवीन सूचना")}</button>}
          <Table head={[t("Title", "शीर्षक"), t("Date", "दिनांक"), t("Pinned", "महत्त्वाचे"), t("Published", "प्रकाशित"), ""]}>{db.announcements.map((a) => <tr key={a.id}><td>{l(a.title)}</td><td className="text-xs">{d(a.date)}</td><td><Toggle disabled={!canManage} checked={a.pinned} onChange={(v) => update((db) => { db.announcements.find((x) => x.id === a.id)!.pinned = v; })} /></td><td><Toggle disabled={!canManage} checked={a.published} onChange={(v) => update((db) => { db.announcements.find((x) => x.id === a.id)!.published = v; })} /></td><td>{canManage && <button className="btn-ghost btn-sm" onClick={() => setAnn(structuredClone(a))}><Pencil className="size-4" /></button>}</td></tr>)}</Table>
        </>
      )}
      {tab === "faq" && <div className="space-y-3">{db.faqs.map((f) => <Card key={f.id}><LInput label="Q" value={f.q} onChange={(v) => update((db) => { db.faqs.find((x) => x.id === f.id)!.q = v; })} /><div className="mt-2"><LInput label="A" value={f.a} area onChange={(v) => update((db) => { db.faqs.find((x) => x.id === f.id)!.a = v; })} /></div></Card>)}{canManage && <button className="btn-outline" onClick={() => update((db) => db.faqs.push({ id: uid("fq"), q: { en: "New question", mr: "नवीन प्रश्न" }, a: { en: "", mr: "" } }))}><Plus className="size-4" />FAQ</button>}</div>}
      {tab === "faculty" && <div className="space-y-3">{db.facultyProfiles.map((f) => <Card key={f.id} actions={<Toggle disabled={!canManage} checked={f.published} onChange={(v) => update((db) => { db.facultyProfiles.find((x) => x.id === f.id)!.published = v; })} label={t("Published", "प्रकाशित")} />}><Field label={t("Display name", "नाव")}>{(id) => <input id={id} className="input" value={f.name} onChange={(e) => update((db) => { db.facultyProfiles.find((x) => x.id === f.id)!.name = e.target.value; })} />}</Field><div className="mt-2"><LInput label="Subject" value={f.subject} onChange={(v) => update((db) => { db.facultyProfiles.find((x) => x.id === f.id)!.subject = v; })} /></div><div className="mt-2"><LInput label="Bio" area value={f.bio} onChange={(v) => update((db) => { db.facultyProfiles.find((x) => x.id === f.id)!.bio = v; })} /></div></Card>)}</div>}
      {tab === "seo" && (
        <div className="space-y-3">
          {Object.entries(seo).map(([page, v]) => (
            <Card key={page} title={`/${page === "home" ? "" : page}`}>
              <div className="grid gap-3 sm:grid-cols-2"><Field label="Meta title" hint={`${v.title.length}/60`}>{(id) => <input id={id} className="input" value={v.title} onChange={(e) => setSeo({ ...seo, [page]: { ...v, title: e.target.value } })} />}</Field><Field label="Meta description" hint={`${v.description.length}/160`}>{(id) => <input id={id} className="input" value={v.description} onChange={(e) => setSeo({ ...seo, [page]: { ...v, description: e.target.value } })} />}</Field></div>
              <div className="mt-3 rounded-lg border bg-white p-3"><p className="truncate text-lg text-[#1a0dab]">{v.title}</p><p className="text-xs text-emerald-700">vidyadhanacademy.in/{page === "home" ? "" : page}</p><p className="line-clamp-2 text-sm text-slate-600">{v.description}</p></div>
            </Card>
          ))}
          <p className="text-xs text-slate-500">{t("Course pages have their own SEO fields under Courses.", "अभ्यासक्रम पानांचे SEO 'अभ्यासक्रम' विभागात आहे.")}</p>
        </div>
      )}
      <Modal open={!!ann} onClose={() => setAnn(null)} title={t("Announcement", "सूचना")} size="lg" footer={<button className="btn-primary" onClick={() => { if (!ann?.title.en) return; update((db) => { if (ann.id) Object.assign(db.announcements.find((x) => x.id === ann.id)!, ann); else db.announcements.unshift({ ...ann, id: uid("an") }); }, { action: "announcement.save", entity: ann.title.en }); setAnn(null); }}>{t("Save", "जतन")}</button>}>
        {ann && <div className="space-y-3"><LInput label="Title" value={ann.title} onChange={(v) => setAnn({ ...ann, title: v })} /><LInput label="Body" value={ann.body} area onChange={(v) => setAnn({ ...ann, body: v })} /><Toggle checked={ann.pinned} onChange={(v) => setAnn({ ...ann, pinned: v })} label={t("Pin to top", "वर ठेवा")} /><p className="text-xs text-slate-500">{t("Tip: a machine translation draft may be used, but review the Marathi text before publishing.", "टीप: यांत्रिक भाषांतराचा मसुदा वापरता येतो, पण प्रकाशनापूर्वी मराठी मजकूर तपासा.")}</p></div>}
      </Modal>
    </div>
  );
}

/* ---------------- Settings ---------------- */
export function SettingsAdmin({ canManage }: { canManage: boolean }) {
  const { t } = useI18n();
  const { db, update, isOwner } = useA();
  const s = db.settings;
  const set = (fn: (st: typeof s) => void, what: string) => update((db) => fn(db.settings), { action: "settings.update", entity: what });
  return (
    <div className="space-y-5">
      <PageHeader title={t("Settings", "सेटिंग्ज")} />
      <Card title={t("Payment gateways", "पेमेंट गेटवे")}>
        <Table head={[t("Gateway", "गेटवे"), t("Credentials", "क्रेडेन्शियल्स"), t("Enabled", "सक्षम"), t("Default", "मुख्य")]}>
          {s.gateways.map((g) => <tr key={g.id}><td className="font-medium">{g.name}</td><td>{g.configured ? <Badge tone="green">{t("Configured", "जोडलेले")}</Badge> : <Badge tone="amber">{t("Not configured (simulated)", "जोडलेले नाही (सिम्युलेटेड)")}</Badge>}</td><td><Toggle disabled={!canManage} checked={g.enabled} onChange={(v) => set((st) => { st.gateways.find((x) => x.id === g.id)!.enabled = v; }, `gateway ${g.id}`)} /></td><td><input type="radio" name="gw-default" disabled={!canManage || !g.enabled} checked={g.isDefault} onChange={() => set((st) => st.gateways.forEach((x) => (x.isDefault = x.id === g.id)), "default gateway")} aria-label={`Default ${g.name}`} /></td></tr>)}
        </Table>
        <p className="mt-2 text-xs text-slate-500">{t("API keys live in secure server environment variables – never in the database or this UI.", "API की सुरक्षित सर्व्हर एन्व्हायर्नमेंटमध्ये ठेवल्या जातात – डेटाबेस किंवा या UI मध्ये नाही.")}</p>
      </Card>
      <Card title="GST">
        <Notice tone="warn">{t("GST is optional and disabled by default. Tax settings require validation by a qualified professional before production use.", "GST ऐच्छिक असून पूर्वनिर्धारितपणे बंद आहे. प्रत्यक्ष वापरापूर्वी कर सेटिंग्ज पात्र तज्ज्ञाकडून तपासून घ्या.")}</Notice>
        <div className="mt-3 grid gap-3 sm:grid-cols-4">
          <div className="flex items-end pb-2"><Toggle disabled={!canManage} checked={s.gst.enabled} onChange={(v) => set((st) => { st.gst.enabled = v; }, "gst.enabled")} label={t("Apply GST", "GST लागू")} /></div>
          <Field label="GSTIN">{(id) => <input id={id} disabled={!canManage} className="input" value={s.gst.gstin} onChange={(e) => set((st) => { st.gst.gstin = e.target.value.toUpperCase(); }, "gstin")} />}</Field>
          <Field label={t("Rate %", "दर %")}>{(id) => <input id={id} disabled={!canManage} type="number" className="input" value={s.gst.rate} onChange={(e) => set((st) => { st.gst.rate = Number(e.target.value); }, "gst.rate")} />}</Field>
          <Field label={t("Pricing", "किंमत")}>{(id) => <select id={id} disabled={!canManage} className="input" value={s.gst.inclusive ? "in" : "ex"} onChange={(e) => set((st) => { st.gst.inclusive = e.target.value === "in"; }, "gst.inclusive")}><option value="in">{t("Tax inclusive", "कर समाविष्ट")}</option><option value="ex">{t("Tax exclusive", "कर अतिरिक्त")}</option></select>}</Field>
        </div>
      </Card>
      <Card title={t("Academic rules", "शैक्षणिक नियम")}>
        <div className="grid gap-3 sm:grid-cols-3">
          <Field label={t("Minimum attendance %", "किमान उपस्थिती %")}>{(id) => <input id={id} disabled={!canManage} type="number" className="input" value={s.attendance.minimumPercent} onChange={(e) => set((st) => { st.attendance.minimumPercent = Number(e.target.value); }, "attendance.min")} />}</Field>
          <Field label={t("Low-attendance warning %", "कमी उपस्थिती इशारा %")}>{(id) => <input id={id} disabled={!canManage} type="number" className="input" value={s.attendance.warningPercent} onChange={(e) => set((st) => { st.attendance.warningPercent = Number(e.target.value); }, "attendance.warn")} />}</Field>
          <Field label={t("QR validity (seconds)", "QR वैधता (सेकंद)")}>{(id) => <input id={id} disabled={!canManage} type="number" className="input" value={s.attendance.qrValiditySec} onChange={(e) => set((st) => { st.attendance.qrValiditySec = Math.max(10, Number(e.target.value)); }, "attendance.qr")} />}</Field>
          <Field label={t("Exam-date reminders (days before)", "परीक्षा स्मरणपत्रे (दिवस आधी)")}>{(id) => <input id={id} disabled={!canManage} className="input" value={s.reminderDays.join(", ")} onChange={(e) => set((st) => { st.reminderDays = e.target.value.split(",").map((x) => Number(x.trim())).filter((x) => x > 0); }, "reminders")} />}</Field>
          <Field label={t("Receipt prefix", "पावती उपसर्ग")}>{(id) => <input id={id} disabled={!canManage} className="input" value={s.receiptPrefix} onChange={(e) => set((st) => { st.receiptPrefix = e.target.value.toUpperCase(); }, "receiptPrefix")} />}</Field>
        </div>
        <div className="mt-4 flex flex-col gap-2">
          <Toggle disabled={!canManage} checked={s.transferNeedsApproval} onChange={(v) => set((st) => { st.transferNeedsApproval = v; }, "transfer approval")} label={t("Batch transfers require owner approval", "बॅच बदलासाठी मालक मंजुरी आवश्यक")} />
          <Toggle disabled={!canManage} checked={s.materialApproval} onChange={(v) => set((st) => { st.materialApproval = v; }, "material approval")} label={t("Study material requires admin approval (default)", "अभ्यास साहित्यासाठी प्रशासकीय मंजुरी (पूर्वनिर्धारित)")} />
        </div>
      </Card>
      <Card title={t("Security, privacy & backups (production plan)", "सुरक्षा, गोपनीयता व बॅकअप (प्रत्यक्ष योजना)")}>
        <ul className="grid gap-1.5 text-sm text-slate-600 sm:grid-cols-2">
          {[t("Encryption in transit (TLS) and at rest", "प्रवासात (TLS) व संचयनात एन्क्रिप्शन"), t("Role-based access + audit logs", "भूमिका-आधारित प्रवेश + ऑडिट नोंदी"), t("OTP/login rate limiting, session & device management", "OTP/लॉगिन मर्यादा, सत्र व उपकरण व्यवस्थापन"), t("Consent & opt-out for communications", "संवादासाठी संमती व नकार"), t("Student data export & deactivation", "विद्यार्थी माहिती निर्यात व खाते बंद"), t("DPDP Act review recommended", "DPDP कायद्याचे कायदेशीर पुनरावलोकन शिफारसीय"), t("Daily encrypted off-site backups; frequent points for payments", "दैनिक एन्क्रिप्टेड ऑफ-साइट बॅकअप; पेमेंटसाठी वारंवार"), t("Retention: 7d frequent · 30d daily · 12w weekly · 12m monthly", "जतन: ७ दिवस · ३० दिवस · १२ आठवडे · १२ महिने")].map((x) => <li key={x} className="flex gap-2"><Check className="mt-0.5 size-4 shrink-0 text-emerald-600" />{x}</li>)}
        </ul>
      </Card>
      {isOwner && (
        <Card title={t("Prototype data", "प्रोटोटाइप माहिती")}>
          <p className="text-sm text-slate-600">{t("All data in this prototype is stored in this browser only.", "या प्रोटोटाइपमधील सर्व माहिती फक्त या ब्राउझरमध्ये साठवली आहे.")}</p>
          <button className="btn-danger mt-3" onClick={() => { if (confirm(t("Reset all demo data to the original sample?", "सर्व डेमो माहिती मूळ स्थितीत आणायची?"))) { useStore.setState({ db: buildSeed() }); toast.success(t("Demo data reset", "डेमो माहिती पुनर्स्थापित")); } }}><RotateCcw className="size-4" />{t("Reset demo data", "डेमो माहिती रीसेट")}</button>
        </Card>
      )}
    </div>
  );
}

