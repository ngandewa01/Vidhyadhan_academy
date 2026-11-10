/** Domain operations on the demo DB. Each mirrors a future backend endpoint. */
import type { DB } from "./seed";
import type * as T from "./types";
import { nextReceiptNo } from "./store";
import { addMonths, normMobile, nowISO, today, uid } from "./utils";

export function paidFor(db: DB, enrolmentId: string) {
  return db.payments.filter((p) => p.enrolmentId === enrolmentId && p.status === "success").reduce((a, p) => a + p.amount, 0)
    - db.payments.filter((p) => p.enrolmentId === enrolmentId && p.status === "refunded").reduce((a, p) => a + p.amount, 0);
}
export function balanceFor(db: DB, e: T.Enrolment) {
  return Math.max(0, e.finalFee - paidFor(db, e.id));
}
export function membershipPaid(db: DB, membershipId: string) {
  return db.payments.filter((p) => p.membershipId === membershipId && p.status === "success").reduce((a, p) => a + p.amount, 0);
}

export function evaluatePromo(db: DB, code: string, courseId: string, fee: number): { ok: true; discount: number; promo: T.PromoCode } | { ok: false; reason: string } {
  const p = db.promos.find((x) => x.code.toUpperCase() === code.trim().toUpperCase());
  if (!p || !p.active) return { ok: false, reason: "invalid" };
  const t = today();
  if (t < p.validFrom || t > p.validTo) return { ok: false, reason: "expired" };
  if (p.used >= p.limit) return { ok: false, reason: "limit" };
  if (p.courseIds.length && !p.courseIds.includes(courseId)) return { ok: false, reason: "course" };
  if (fee < p.minPayment) return { ok: false, reason: "min" };
  const discount = p.type === "flat" ? p.value : Math.round((fee * p.value) / 100);
  return { ok: true, discount: Math.min(discount, fee), promo: p };
}

export function createStudent(db: DB, input: { name: string; mobile: string; email?: string }): T.Student {
  const existing = db.students.find((s) => normMobile(s.mobile) === normMobile(input.mobile));
  if (existing) return existing;
  const n = db.students.length + 1;
  const s: T.Student = {
    id: uid("st"), code: `VA-2026-${String(n).padStart(4, "0")}`, name: input.name, mobile: normMobile(input.mobile), email: input.email,
    branchId: "br_main", createdAt: today(), status: "active", follows: ["a_mpsc"], bookmarks: [], contentLang: "mr", parentAlerts: false,
    consent: { whatsapp: true, sms: true, email: !!input.email, publishResults: false }, referralCode: `VA${Math.random().toString(36).slice(2, 6).toUpperCase()}`,
    telegramLinked: false, dataSaver: false, gender: "",
  };
  db.students.push(s);
  return s;
}

export function createEnrolment(db: DB, a: { studentId: string; batchId: string; discount: number; discountReason: string; installments: number; by: string; approvedBy?: string }): T.Enrolment {
  const b = db.batches.find((x) => x.id === a.batchId)!;
  const finalFee = Math.max(0, b.fee - a.discount);
  const parts = Math.max(1, a.installments);
  const inst = Array.from({ length: parts }, (_, i) => ({
    due: addMonths(today(), i).slice(0, 10),
    amount: i === parts - 1 ? finalFee - Math.round(finalFee / parts) * (parts - 1) : Math.round(finalFee / parts),
  }));
  inst[0].due = today();
  const e: T.Enrolment = {
    id: uid("en"), studentId: a.studentId, courseId: b.courseId, batchId: b.id, branchId: b.branchId, originalFee: b.fee, discount: a.discount,
    discountReason: a.discountReason, discountApprovedBy: a.approvedBy, finalFee, installments: inst, paymentStatus: "pending", admissionStatus: "pending",
    enrolmentStatus: "active", createdAt: today(), history: [{ at: nowISO(), text: `Enrolment created for ${b.name}`, by: a.by }],
  };
  db.enrolments.push(e);
  return e;
}

export function recordPayment(db: DB, a: { studentId: string; enrolmentId?: string; membershipId?: string; amount: number; method: T.PayMethod; reference: string; by: string; gateway?: string; status?: "success" | "failed"; note?: string }): T.Payment {
  const status = a.status ?? "success";
  const p: T.Payment = {
    id: uid("pay"), receiptNo: status === "success" ? nextReceiptNo(db) : "—", studentId: a.studentId, enrolmentId: a.enrolmentId, membershipId: a.membershipId,
    amount: a.amount, method: a.method, gateway: a.gateway, reference: a.reference || (a.method === "cash" ? "CASH" : "—"), date: nowISO(), recordedBy: a.by, status, reprints: 0, note: a.note,
  };
  db.payments.push(p);
  if (a.enrolmentId) {
    const e = db.enrolments.find((x) => x.id === a.enrolmentId);
    if (e) {
      const paid = paidFor(db, e.id);
      if (status === "failed" && paid === 0) e.paymentStatus = "failed";
      else e.paymentStatus = paid <= 0 ? "pending" : paid >= e.finalFee ? "paid" : "partial";
      if (paid > 0 && e.admissionStatus === "pending") {
        e.admissionStatus = "confirmed";
        e.history.push({ at: nowISO(), text: "Admission confirmed after payment verification", by: a.by });
        db.inbox.unshift({ id: uid("in"), studentId: e.studentId, title: { en: "Admission confirmed", mr: "प्रवेश निश्चित" }, body: { en: "Welcome! Your course access is now active.", mr: "स्वागत! आपला अभ्यासक्रम प्रवेश सक्रिय झाला आहे." }, at: nowISO(), read: false, kind: "payment" });
      }
      if (status === "success") e.history.push({ at: nowISO(), text: `Payment ₹${a.amount} recorded (${p.receiptNo})`, by: a.by });
    }
  }
  if (status === "success") {
    db.inbox.unshift({ id: uid("in"), studentId: a.studentId, title: { en: `Payment received – ${p.receiptNo}`, mr: `पेमेंट प्राप्त – ${p.receiptNo}` }, body: { en: `₹${a.amount.toLocaleString("en-IN")} received. Your receipt is available in Fees & Receipts.`, mr: `₹${a.amount.toLocaleString("en-IN")} प्राप्त. पावती 'शुल्क व पावत्या' विभागात उपलब्ध.` }, at: nowISO(), read: false, kind: "payment" });
  }
  return p;
}

export function membershipFee(plan: T.LibraryPlan, discount: number, renewal = false) {
  return Math.max(0, plan.fee + (renewal ? 0 : plan.registrationFee) - discount);
}

export function createMembership(db: DB, a: { studentId: string; planId: string; start: string; discount: number; renewal?: boolean }): T.LibraryMembership {
  const plan = db.libraryPlans.find((p) => p.id === a.planId)!;
  const used = new Set(db.memberships.filter((m) => m.status === "active").map((m) => m.seat));
  let seat = "";
  for (let i = 1; i <= 80; i++) { const s = `S-${String(i).padStart(2, "0")}`; if (!used.has(s)) { seat = s; break; } }
  const m: T.LibraryMembership = { id: uid("lm"), studentId: a.studentId, planId: plan.id, start: a.start, end: addMonths(a.start, plan.months), status: "active", seat, fee: membershipFee(plan, a.discount, a.renewal), discount: a.discount, createdAt: today() };
  db.memberships.push(m);
  return m;
}

export function attendanceStats(db: DB, studentId: string, batchId?: string) {
  const recs = db.attendance.filter((a) => a.studentId === studentId && (!batchId || a.batchId === batchId));
  const p = recs.filter((r) => r.status === "P").length;
  const l = recs.filter((r) => r.status === "L").length;
  const total = recs.length;
  return { total, present: p, late: l, absent: total - p - l, percent: total ? ((p + l) / total) * 100 : 100 };
}

export function scoreAttempt(db: DB, test: T.Test, answers: Record<string, number | null>) {
  let correct = 0, incorrect = 0, unanswered = 0, score = 0, max = 0;
  const bySubject: Record<string, { correct: number; total: number }> = {};
  for (const qid of test.questionIds) {
    const q = db.questions.find((x) => x.id === qid);
    if (!q) continue;
    max += q.marks;
    bySubject[q.subjectId] ??= { correct: 0, total: 0 };
    bySubject[q.subjectId].total++;
    const a = answers[qid];
    if (a === null || a === undefined) unanswered++;
    else if (a === q.answer) { correct++; score += q.marks; bySubject[q.subjectId].correct++; }
    else { incorrect++; score -= test.negativeMark; }
  }
  return { correct, incorrect, unanswered, score: Math.round(score * 100) / 100, max, bySubject };
}

export function rankFor(db: DB, testId: string, studentId: string, batchId?: string) {
  const best = new Map<string, number>();
  db.attempts.filter((a) => a.testId === testId && a.submittedAt).forEach((a) => {
    if (batchId) {
      const e = db.enrolments.find((x) => x.studentId === a.studentId && x.batchId === batchId);
      if (!e) return;
    }
    best.set(a.studentId, Math.max(best.get(a.studentId) ?? -Infinity, a.score));
  });
  const sorted = [...best.entries()].sort((a, b) => b[1] - a[1]);
  const idx = sorted.findIndex(([s]) => s === studentId);
  return { rank: idx >= 0 ? idx + 1 : null, of: sorted.length };
}

export const PAY_METHOD_LABEL: Record<T.PayMethod, [string, string]> = {
  cash: ["Cash", "रोख"], upi: ["UPI / QR", "UPI / QR"], gateway: ["Online gateway", "ऑनलाइन गेटवे"], bank: ["Bank transfer", "बँक ट्रान्सफर"],
};

export function courseDeleteBlocker(db: DB, courseId: string): T.L | null {
  if (db.enrolments.some((e) => e.courseId === courseId)) return { en: "students are enrolled in it", mr: "त्यात विद्यार्थी प्रवेशित आहेत" };
  if (db.enquiries.some((e) => e.courseId === courseId)) return { en: "it has enquiries", mr: "त्यासाठी चौकशी नोंदी आहेत" };
  if (db.libraryPlans.some((p) => p.bundleCourseId === courseId)) return { en: "a library bundle uses it", mr: "अभ्यासिका केंद्र संच त्याचा वापर करतो" };
  const testIds = new Set(db.tests.filter((t) => t.courseId === courseId).map((t) => t.id));
  if (db.attempts.some((a) => testIds.has(a.testId))) return { en: "students have attempted its tests", mr: "विद्यार्थ्यांनी त्याच्या चाचण्या दिल्या आहेत" };
  return null;
}

export function deleteCourse(db: DB, courseId: string) {
  const batchIds = new Set(db.batches.filter((b) => b.courseId === courseId).map((b) => b.id));
  const drop = (x: { courseId?: string; batchId?: string }) => x.courseId === courseId || (x.batchId !== undefined && batchIds.has(x.batchId));
  const rec = db as unknown as Record<string, unknown>;
  for (const key of Object.keys(rec)) {
    const v = rec[key];
    if (Array.isArray(v) && v.length && typeof v[0] === "object" && v[0] !== null && key !== "courses") rec[key] = v.filter((x) => !drop(x));
  }
  db.courses = db.courses.filter((c) => c.id !== courseId);
  for (const u of db.staff) u.batchIds = u.batchIds.filter((id) => !batchIds.has(id));
}
