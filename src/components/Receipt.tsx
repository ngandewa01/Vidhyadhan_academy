"use client";
import { Printer, Mail, Copy } from "lucide-react";
import { useStore } from "@/lib/store";
import { useI18n } from "@/lib/i18n";
import { academy } from "@/config/academy";
import { balanceFor, membershipPaid, PAY_METHOD_LABEL } from "@/lib/actions";
import { fmtDateTime, fmtINR } from "@/lib/utils";
import { WhatsAppIcon } from "./brand";
import { Modal, toast } from "./ui";

/** Detailed payment receipt. Reprints keep the original receipt number and are audit-logged. */
export function ReceiptModal({ paymentId, onClose }: { paymentId: string | null; onClose: () => void }) {
  const { t, l, lang } = useI18n();
  const { db, update } = useStore();
  const p = db.payments.find((x) => x.id === paymentId);
  if (!p) return null;
  const s = db.students.find((x) => x.id === p.studentId)!;
  const e = db.enrolments.find((x) => x.id === p.enrolmentId);
  const m = db.memberships.find((x) => x.id === p.membershipId);
  const plan = m && db.libraryPlans.find((x) => x.id === m.planId);
  const course = e && db.courses.find((x) => x.id === e.courseId);
  const batch = e && db.batches.find((x) => x.id === e.batchId);
  const staff = db.staff.find((x) => x.id === p.recordedBy);
  const original = e ? e.originalFee : m ? m.fee + m.discount : p.amount;
  const discount = e ? e.discount : m ? m.discount : 0;
  const balance = e ? balanceFor(db, e) : m ? Math.max(0, m.fee - membershipPaid(db, m.id)) : 0;
  const gst = db.settings.gst;
  const taxable = gst.enabled ? (gst.inclusive ? p.amount / (1 + gst.rate / 100) : p.amount) : p.amount;
  const shareText = `${academy.name.en} – Receipt ${p.receiptNo}\n${s.name} (${s.code})\nAmount: ${fmtINR(p.amount)}\nBalance: ${fmtINR(balance)}`;

  const print = () => {
    update((d) => { const x = d.payments.find((y) => y.id === p.id); if (x) x.reprints++; }, { action: "receipt.print", entity: p.receiptNo, detail: p.reprints ? `Duplicate copy #${p.reprints + 1}` : "Original printed" });
    setTimeout(() => window.print(), 50);
  };

  return (
    <Modal open onClose={onClose} title={t("Payment receipt", "पेमेंट पावती")} size="lg"
      footer={<>
        <a className="btn-outline" target="_blank" rel="noopener noreferrer" href={`https://wa.me/91${s.mobile}?text=${encodeURIComponent(shareText)}`}><WhatsAppIcon className="size-4" />WhatsApp</a>
        <a className="btn-outline" href={`mailto:${s.email ?? ""}?subject=${encodeURIComponent("Receipt " + p.receiptNo)}&body=${encodeURIComponent(shareText)}`}><Mail className="size-4" />{t("Email", "ईमेल")}</a>
        <button className="btn-outline" onClick={() => { navigator.clipboard?.writeText(shareText); toast.info(t("Copied", "कॉपी केले")); }}><Copy className="size-4" /></button>
        <button className="btn-primary" onClick={print}><Printer className="size-4" />{t("Print / Save PDF", "प्रिंट / PDF")}</button>
      </>}>
      <div id="receipt-print" className="print-area rounded-lg border border-slate-200 p-5 text-sm">
        <div className="flex items-start justify-between gap-4 border-b pb-3">
          <div className="flex items-center gap-3">
            <img src={academy.logoUrl} alt="" className="size-14 rounded-full" />
            <div>
              <p className="text-base font-bold">{academy.name.en} <span className="font-semibold text-slate-500">| {academy.name.mr}</span></p>
              <p className="text-xs text-slate-500">{l(academy.address)} · {academy.phone}</p>
              {gst.enabled && gst.gstin && <p className="text-xs text-slate-500">GSTIN: {gst.gstin}</p>}
            </div>
          </div>
          <div className="text-right">
            <p className="text-xs uppercase tracking-wide text-slate-500">{t("Receipt", "पावती")}{p.reprints > 0 && <span className="ml-1 font-semibold text-saffron-700">({t("Duplicate", "प्रत")})</span>}</p>
            <p className="font-mono font-semibold">{p.receiptNo}</p>
            <p className="text-xs text-slate-500">{fmtDateTime(p.date, lang)}</p>
          </div>
        </div>
        <div className="grid gap-3 py-3 sm:grid-cols-2">
          <div><p className="text-xs text-slate-500">{t("Student", "विद्यार्थी")}</p><p className="font-semibold">{s.name}</p><p className="text-xs">{s.code} · +91 {s.mobile}</p></div>
          <div><p className="text-xs text-slate-500">{e ? t("Course / Batch", "अभ्यासक्रम / बॅच") : t("Library plan", "ग्रंथालय योजना")}</p><p className="font-semibold">{course ? l(course.name) : plan ? l(plan.name) : "—"}</p><p className="text-xs">{batch?.name ?? (m ? `${t("Seat", "आसन")} ${m.seat} · ${m.start} → ${m.end}` : "")}</p></div>
        </div>
        <table className="w-full border-t text-sm">
          <tbody>
            <tr><td className="py-1.5 text-slate-600">{t("Original fee", "मूळ शुल्क")}</td><td className="py-1.5 text-right">{fmtINR(original)}</td></tr>
            <tr><td className="py-1.5 text-slate-600">{t("Discount", "सवलत")}{e?.discountReason ? ` (${e.discountReason})` : ""}</td><td className="py-1.5 text-right">− {fmtINR(discount)}</td></tr>
            <tr><td className="py-1.5 text-slate-600">{t("Net fee", "निव्वळ शुल्क")}</td><td className="py-1.5 text-right">{fmtINR(original - discount)}</td></tr>
            {gst.enabled && <tr><td className="py-1.5 text-slate-600">GST {gst.rate}% ({gst.inclusive ? t("inclusive", "समाविष्ट") : t("exclusive", "अतिरिक्त")})</td><td className="py-1.5 text-right">{fmtINR(gst.inclusive ? p.amount - taxable : (p.amount * gst.rate) / 100)}</td></tr>}
            <tr className="border-t font-semibold"><td className="py-2">{t("Amount paid", "भरलेली रक्कम")}</td><td className="py-2 text-right text-base">{fmtINR(p.amount)}</td></tr>
            <tr><td className="py-1.5 text-slate-600">{t("Outstanding balance", "शिल्लक रक्कम")}</td><td className="py-1.5 text-right font-semibold">{fmtINR(balance)}</td></tr>
          </tbody>
        </table>
        <div className="mt-3 grid gap-2 border-t pt-3 text-xs sm:grid-cols-3">
          <div><span className="text-slate-500">{t("Method", "पद्धत")}: </span>{PAY_METHOD_LABEL[p.method][lang === "mr" ? 1 : 0]}{p.gateway ? ` (${p.gateway})` : ""}</div>
          <div><span className="text-slate-500">{t("Reference", "संदर्भ")}: </span><span className="font-mono">{p.reference}</span></div>
          <div><span className="text-slate-500">{t("Received by", "स्वीकारणारे")}: </span>{staff?.name ?? t("Online", "ऑनलाइन")}</div>
        </div>
        <p className="mt-4 text-center text-[11px] text-slate-400">{t("Computer-generated receipt (prototype – not a valid tax document).", "संगणकीय पावती (प्रोटोटाइप – वैध कर दस्तऐवज नाही).")}</p>
      </div>
    </Modal>
  );
}
