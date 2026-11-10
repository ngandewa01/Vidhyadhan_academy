"use client";
import Link from "next/link";
import { useState } from "react";
import { ArrowRight, Clock, Languages, CheckCircle2 } from "lucide-react";
import type { Course } from "@/lib/types";
import { useI18n } from "@/lib/i18n";
import { useStore } from "@/lib/store";
import { cn, fmtINR, isValidEmail, isValidMobile, normMobile, nowISO, uid } from "@/lib/utils";
import { Badge, DemoBadge, Field, toast } from "./ui";

export function PageHero({ title, subtitle, children }: { title: string; subtitle?: string; children?: React.ReactNode }) {
  return (
    <section className="border-b border-slate-200 bg-gradient-to-br from-brand-50 via-white to-saffron-50">
      <div className="container-page py-10 sm:py-14">
        <h1 className="text-3xl font-bold text-brand-950 sm:text-4xl">{title}</h1>
        {subtitle && <p className="mt-3 max-w-2xl text-slate-600">{subtitle}</p>}
        {children}
      </div>
    </section>
  );
}

export function SectionHead({ title, subtitle, action }: { title: string; subtitle?: string; action?: React.ReactNode }) {
  return (
    <div className="mb-6 flex flex-col gap-2 sm:flex-row sm:items-end sm:justify-between">
      <div>
        <h2 className="section-title">{title}</h2>
        {subtitle && <p className="mt-1 text-slate-600">{subtitle}</p>}
      </div>
      {action}
    </div>
  );
}

export function CourseCard({ c }: { c: Course }) {
  const { t, l } = useI18n();
  const batches = useStore((s) => s.db.batches).filter((b) => b.courseId === c.id && b.admissionOpen);
  return (
    <article className="card group flex flex-col overflow-hidden transition-shadow hover:shadow-md">
      <div className={cn("relative flex h-28 items-end bg-gradient-to-br p-4", c.color)} aria-hidden>
        <span className="text-4xl font-bold text-white/25">{l(c.name).slice(0, 2)}</span>
        <span className="absolute right-3 top-3 rounded bg-white/15 px-2 py-0.5 text-[10px] font-medium text-white">{t("Image placeholder", "प्रतिमा जागा")}</span>
      </div>
      <div className="flex flex-1 flex-col p-4">
        <div className="mb-2 flex flex-wrap gap-1.5">
          {c.admissionOpen ? <Badge tone="green">{t("Admissions open", "प्रवेश सुरू")}</Badge> : <Badge>{t("Admissions closed", "प्रवेश बंद")}</Badge>}
          <Badge tone="blue"><Languages className="size-3" />{c.mediums.map((m) => (m === "mr" ? t("Marathi", "मराठी") : t("English", "इंग्रजी"))).join(" / ")}</Badge>
        </div>
        <h3 className="text-lg font-semibold">{l(c.name)}</h3>
        <p className="mt-1 flex-1 text-sm text-slate-600">{l(c.short)}</p>
        <div className="mt-3 flex items-center justify-between text-sm">
          <span className="flex items-center gap-1 text-slate-500"><Clock className="size-4" />{c.durationMonths} {t("months", "महिने")}</span>
          {c.showFee !== false && <span className="font-semibold text-brand-800">{t("from", "पासून")} {fmtINR(Math.min(c.fee, ...batches.map((b) => b.fee)))}<span className="ml-1 text-[10px] font-normal text-saffron-700">{t("(demo)", "(डेमो)")}</span></span>}
        </div>
        <Link href={`/courses/${c.slug}`} className="btn-outline mt-4 w-full">{t("View details", "तपशील पहा")}<ArrowRight className="size-4" /></Link>
      </div>
    </article>
  );
}

export function EnquiryForm({ variant = "quick", defaultCourse, onDone }: { variant?: "quick" | "detailed"; defaultCourse?: string; onDone?: () => void }) {
  const { t, l } = useI18n();
  const { db, update } = useStore();
  const [f, setF] = useState({ name: "", mobile: "", email: "", courseId: defaultCourse ?? "", batchId: "", qualification: "", message: "", consent: true });
  const [err, setErr] = useState<Record<string, string>>({});
  const [done, setDone] = useState<null | { dup: boolean }>(null);
  const set = (k: keyof typeof f, v: string | boolean) => setF((p) => ({ ...p, [k]: v }));

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    const er: Record<string, string> = {};
    if (f.name.trim().length < 2) er.name = t("Please enter your name", "कृपया आपले नाव लिहा");
    if (!isValidMobile(f.mobile)) er.mobile = t("Enter a valid 10-digit mobile number", "वैध १० अंकी मोबाईल क्रमांक लिहा");
    if (!f.courseId) er.courseId = t("Select a course", "अभ्यासक्रम निवडा");
    if (variant === "detailed" && !isValidEmail(f.email)) er.email = t("Enter a valid email", "वैध ईमेल लिहा");
    setErr(er);
    if (Object.keys(er).length) return;
    const mobile = normMobile(f.mobile);
    const dup = db.enquiries.some((x) => normMobile(x.mobile) === mobile) || db.students.some((s) => normMobile(s.mobile) === mobile);
    update((d) => {
      d.enquiries.unshift({ id: uid("eq"), type: variant, name: f.name.trim(), mobile, email: f.email || undefined, courseId: f.courseId, batchId: f.batchId || undefined, qualification: f.qualification || undefined, message: f.message || undefined, status: "new", notes: [], createdAt: nowISO(), source: "website" });
    });
    setDone({ dup });
    toast.success(t("Enquiry submitted. Our team will contact you.", "चौकशी नोंदवली. आमची टीम आपल्याशी संपर्क साधेल."));
    onDone?.();
  };

  if (done)
    return (
      <div className="rounded-xl border border-emerald-200 bg-emerald-50 p-5 text-emerald-900">
        <p className="flex items-center gap-2 font-semibold"><CheckCircle2 className="size-5" />{t("Thank you! Your enquiry has been received.", "धन्यवाद! आपली चौकशी प्राप्त झाली.")}</p>
        <p className="mt-1 text-sm">{t("The academy office will call you on the number provided.", "अकॅडमी कार्यालय दिलेल्या क्रमांकावर संपर्क करेल.")}</p>
        {done.dup && <p className="mt-2 text-xs text-emerald-800">{t("We noticed an earlier enquiry with this number; our team will link them.", "या क्रमांकाने आधी चौकशी झाली आहे; आमची टीम त्या जोडेल.")}</p>}
        <button className="btn-outline btn-sm mt-3" onClick={() => { setDone(null); setF({ ...f, name: "", mobile: "", message: "" }); }}>{t("Submit another", "आणखी एक चौकशी")}</button>
      </div>
    );

  const batches = db.batches.filter((b) => b.courseId === f.courseId);
  return (
    <form onSubmit={submit} noValidate className="grid gap-3 sm:grid-cols-2">
      <Field label={t("Full name", "पूर्ण नाव")} required error={err.name} className={variant === "quick" ? "sm:col-span-2" : ""}>
        {(id) => <input id={id} className="input" value={f.name} onChange={(e) => set("name", e.target.value)} autoComplete="name" />}
      </Field>
      <Field label={t("Mobile number", "मोबाईल क्रमांक")} required error={err.mobile}>
        {(id) => <input id={id} className="input" inputMode="numeric" maxLength={10} placeholder="98XXXXXXXX" value={f.mobile} onChange={(e) => set("mobile", e.target.value.replace(/\D/g, ""))} autoComplete="tel" />}
      </Field>
      {variant === "detailed" && (
        <Field label={t("Email (optional)", "ईमेल (ऐच्छिक)")} error={err.email}>
          {(id) => <input id={id} type="email" className="input" value={f.email} onChange={(e) => set("email", e.target.value)} autoComplete="email" />}
        </Field>
      )}
      <Field label={t("Interested course", "इच्छित अभ्यासक्रम")} required error={err.courseId}>
        {(id) => (
          <select id={id} className="input" value={f.courseId} onChange={(e) => { set("courseId", e.target.value); set("batchId", ""); }}>
            <option value="">{t("Select…", "निवडा…")}</option>
            {db.courses.filter((c) => c.published).map((c) => <option key={c.id} value={c.id}>{l(c.name)}</option>)}
          </select>
        )}
      </Field>
      {variant === "detailed" && (
        <>
          <Field label={t("Preferred batch", "पसंतीची बॅच")}>
            {(id) => (
              <select id={id} className="input" value={f.batchId} onChange={(e) => set("batchId", e.target.value)} disabled={!f.courseId}>
                <option value="">{t("Any / not sure", "कोणतीही / निश्चित नाही")}</option>
                {batches.map((b) => <option key={b.id} value={b.id}>{b.name}</option>)}
              </select>
            )}
          </Field>
          <Field label={t("Education qualification", "शैक्षणिक पात्रता")}>
            {(id) => <input id={id} className="input" placeholder={t("e.g. B.A., B.Sc., 12th", "उदा. बी.ए., बी.एस्सी., १२वी")} value={f.qualification} onChange={(e) => set("qualification", e.target.value)} />}
          </Field>
          <Field label={t("Message", "संदेश")} className="sm:col-span-2">
            {(id) => <textarea id={id} rows={3} className="input" value={f.message} onChange={(e) => set("message", e.target.value)} />}
          </Field>
        </>
      )}
      <label className="flex items-start gap-2 text-xs text-slate-600 sm:col-span-2">
        <input type="checkbox" className="mt-0.5" checked={f.consent} onChange={(e) => set("consent", e.target.checked)} />
        {t("I agree to be contacted by phone/WhatsApp about this enquiry.", "या चौकशीसंदर्भात फोन/व्हॉट्सअ‍ॅपवर संपर्क करण्यास माझी संमती आहे.")}
      </label>
      <button type="submit" className="btn-accent sm:col-span-2">{t("Submit enquiry", "चौकशी पाठवा")}</button>
    </form>
  );
}

export function DemoNote({ children }: { children?: React.ReactNode }) {
  const { t } = useI18n();
  return (
    <p className="mt-3 flex flex-wrap items-center gap-2 text-xs text-slate-500">
      <DemoBadge />
      {children ?? t("Values shown are sample data and not actual academy information.", "दाखवलेली माहिती नमुना असून प्रत्यक्ष अकॅडमीची नाही.")}
    </p>
  );
}
