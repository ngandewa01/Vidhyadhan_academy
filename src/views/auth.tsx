"use client";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { CheckCircle2, GraduationCap, KeyRound, Library, Lock, Shield, Smartphone, UserCog, Users, CreditCard, Wallet, Building2, QrCode } from "lucide-react";
import { useI18n } from "@/lib/i18n";
import { useStore } from "@/lib/store";
import { Badge, Card, Field, Notice, Tabs, toast } from "@/components/ui";
import { ReceiptModal } from "@/components/Receipt";
import { AdmissionDetailsForm } from "@/components/AdmissionDetails";
import { createEnrolment, createMembership, createStudent, evaluatePromo, membershipFee, recordPayment } from "@/lib/actions";
import { cn, fmtINR, isValidMobile, normMobile, today } from "@/lib/utils";
import type { Role } from "@/lib/types";
import { portalHome } from "@/components/PublicShell";

const DEMO_OTP = "123456";

/** Client-side rate limiter (demo). Production: enforce on the server per mobile + IP. */
function useRateLimit(max = 5, windowSec = 60) {
  const [attempts, setAttempts] = useState<number[]>([]);
  const [, force] = useState(0);
  useEffect(() => { const i = setInterval(() => force((x) => x + 1), 1000); return () => clearInterval(i); }, []);
  const recent = attempts.filter((a) => Date.now() - a < windowSec * 1000);
  const locked = recent.length >= max;
  const wait = locked ? Math.ceil((recent[0] + windowSec * 1000 - Date.now()) / 1000) : 0;
  return { locked, wait, hit: () => setAttempts((a) => [...a.filter((x) => Date.now() - x < windowSec * 1000), Date.now()]), left: max - recent.length };
}

function OtpInput({ value, onChange }: { value: string; onChange: (v: string) => void }) {
  return <input className="input text-center font-mono text-lg tracking-[0.5em]" inputMode="numeric" autoComplete="one-time-code" maxLength={6} value={value} onChange={(e) => onChange(e.target.value.replace(/\D/g, ""))} aria-label="OTP" />;
}

export function LoginView() {
  const { t } = useI18n();
  const router = useRouter();
  const sp = useSearchParams();
  const next = sp.get("next");
  const { db, login } = useStore();
  const [tab, setTab] = useState<"password" | "otp" | "register">("password");
  const [mobile, setMobile] = useState("");
  const [password, setPassword] = useState("");
  const [name, setName] = useState("");
  const [otp, setOtp] = useState("");
  const [otpSent, setOtpSent] = useState(false);
  const [resendIn, setResendIn] = useState(0);
  const [err, setErr] = useState("");
  const rl = useRateLimit(5, 60);
  const otpRl = useRateLimit(3, 120);
  const [forgot, setForgot] = useState(false);

  useEffect(() => { if (resendIn <= 0) return; const i = setTimeout(() => setResendIn((x) => x - 1), 1000); return () => clearTimeout(i); }, [resendIn]);

  const go = (role: Role, id: string) => {
    login(role, id);
    toast.success(t("Logged in (demo session)", "लॉगिन झाले (डेमो सत्र)"));
    router.push(next ?? portalHome(role));
  };
  const findUser = (m: string): { role: Role; id: string } | null => {
    const s = db.students.find((x) => normMobile(x.mobile) === normMobile(m));
    if (s) return { role: "student", id: s.id };
    const st = db.staff.find((x) => normMobile(x.mobile) === normMobile(m) && x.active);
    if (st) return { role: st.role === "faculty" ? "faculty" : st.role === "owner" ? "owner" : "staff", id: st.id };
    return null;
  };
  const sendOtp = () => {
    setErr("");
    if (!isValidMobile(mobile)) return setErr(t("Enter a valid 10-digit mobile number", "वैध १० अंकी मोबाईल क्रमांक लिहा"));
    if (tab === "register" && name.trim().length < 2) return setErr(t("Enter your full name", "पूर्ण नाव लिहा"));
    if (tab === "otp" && !findUser(mobile)) return setErr(t("No account found for this number. Please register.", "या क्रमांकावर खाते नाही. कृपया नोंदणी करा."));
    if (tab === "register" && findUser(mobile)) return setErr(t("An account already exists for this number. Please log in.", "या क्रमांकावर आधीच खाते आहे. कृपया लॉगिन करा."));
    if (otpRl.locked) return setErr(t(`Too many OTP requests. Try again in ${otpRl.wait}s.`, `खूप वेळा OTP मागवला. ${otpRl.wait} सेकंदांनी पुन्हा प्रयत्न करा.`));
    otpRl.hit();
    setOtpSent(true);
    setResendIn(30);
    toast.info(t(`Demo OTP sent: ${DEMO_OTP}`, `डेमो OTP पाठवला: ${DEMO_OTP}`));
  };
  const verifyOtp = () => {
    if (rl.locked) return setErr(t(`Too many attempts. Try again in ${rl.wait}s.`, `खूप प्रयत्न झाले. ${rl.wait} सेकंदांनी पुन्हा प्रयत्न करा.`));
    rl.hit();
    if (otp !== DEMO_OTP) return setErr(t(`Incorrect OTP. ${rl.left - 1} attempt(s) left.`, `चुकीचा OTP. ${rl.left - 1} प्रयत्न शिल्लक.`));
    if (tab === "register") {
      let id = "";
      useStore.getState().update((d) => { id = createStudent(d, { name: name.trim(), mobile }).id; }, { action: "student.register", entity: normMobile(mobile), detail: "Self-registration via OTP" });
      return go("student", id);
    }
    if (forgot) { toast.success(t("Verified. Set a new password from your profile (demo).", "पडताळणी झाली. प्रोफाइलमधून नवीन पासवर्ड सेट करा (डेमो).")); }
    const u = findUser(mobile)!;
    go(u.role, u.id);
  };
  const passwordLogin = (e: React.FormEvent) => {
    e.preventDefault();
    setErr("");
    if (rl.locked) return setErr(t(`Account temporarily locked. Try again in ${rl.wait}s.`, `खाते तात्पुरते बंद. ${rl.wait} सेकंदांनी प्रयत्न करा.`));
    rl.hit();
    const u = findUser(mobile);
    if (!u || password.length < 4) return setErr(t(`Invalid mobile or password. ${rl.left - 1} attempt(s) left.`, `चुकीचा मोबाईल किंवा पासवर्ड. ${rl.left - 1} प्रयत्न शिल्लक.`));
    go(u.role, u.id);
  };

  const demo = [
    { role: "student" as Role, id: "st_001", icon: GraduationCap, en: "Student", mr: "विद्यार्थी", who: db.students[0]?.name },
    { role: "student" as Role, id: "st_023", icon: Library, en: "Abhyasika Kendra only member", mr: "फक्त अभ्यासिका केंद्र सदस्य", who: db.students[22]?.name },
    { role: "faculty" as Role, id: "u_fac1", icon: Users, en: "Faculty (restricted)", mr: "प्राध्यापक (मर्यादित)", who: "Faculty – History (Demo)" },
    { role: "staff" as Role, id: "u_office", icon: UserCog, en: "Admin staff", mr: "प्रशासकीय कर्मचारी", who: "Office Staff (Demo)" },
    { role: "owner" as Role, id: "u_owner", icon: Shield, en: "Owner / Super admin", mr: "मालक / सुपर अ‍ॅडमिन", who: "Owner (Demo)" },
  ];

  return (
    <div className="container-page grid gap-8 py-10 lg:grid-cols-2">
      <div className="card mx-auto w-full max-w-md p-6">
        <h1 className="text-2xl font-bold">{forgot ? t("Recover account", "खाते पुनर्प्राप्ती") : t("Login / Register", "लॉगिन / नोंदणी")}</h1>
        <p className="mb-4 text-sm text-slate-500">{t("Use your registered mobile number.", "नोंदणीकृत मोबाईल क्रमांक वापरा.")}</p>
        {!forgot && <Tabs tabs={[{ id: "password", label: t("Password", "पासवर्ड") }, { id: "otp", label: t("OTP login", "OTP लॉगिन") }, { id: "register", label: t("New student", "नवीन विद्यार्थी") }]} value={tab} onChange={(v) => { setTab(v); setOtpSent(false); setErr(""); setOtp(""); }} />}
        {err && <div className="mb-3"><Notice tone="danger">{err}</Notice></div>}
        {tab === "password" && !forgot ? (
          <form onSubmit={passwordLogin} className="space-y-3">
            <Field label={t("Mobile number", "मोबाईल क्रमांक")} required>{(id) => <input id={id} className="input" inputMode="numeric" maxLength={10} value={mobile} onChange={(e) => setMobile(e.target.value.replace(/\D/g, ""))} autoComplete="username" />}</Field>
            <Field label={t("Password", "पासवर्ड")} required hint={t("Demo: any 4+ characters", "डेमो: कोणतेही ४+ अक्षरे")}>{(id) => <input id={id} type="password" className="input" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="current-password" />}</Field>
            <button className="btn-primary w-full" disabled={rl.locked}><Lock className="size-4" />{rl.locked ? `${t("Locked", "लॉक")} (${rl.wait}s)` : t("Login", "लॉगिन")}</button>
            <button type="button" className="w-full text-center text-sm font-medium text-brand-700 hover:underline" onClick={() => { setForgot(true); setTab("otp"); }}>{t("Forgot password?", "पासवर्ड विसरलात?")}</button>
          </form>
        ) : (
          <div className="space-y-3">
            {tab === "register" && <Field label={t("Full name", "पूर्ण नाव")} required>{(id) => <input id={id} className="input" value={name} onChange={(e) => setName(e.target.value)} autoComplete="name" />}</Field>}
            <Field label={t("Mobile number", "मोबाईल क्रमांक")} required>{(id) => <input id={id} className="input" inputMode="numeric" maxLength={10} value={mobile} disabled={otpSent} onChange={(e) => setMobile(e.target.value.replace(/\D/g, ""))} />}</Field>
            {!otpSent ? (
              <button className="btn-primary w-full" onClick={sendOtp}><Smartphone className="size-4" />{t("Send OTP", "OTP पाठवा")}</button>
            ) : (
              <>
                <Field label={t("Enter 6-digit OTP", "६ अंकी OTP लिहा")} hint={t(`Demo OTP: ${DEMO_OTP}`, `डेमो OTP: ${DEMO_OTP}`)}>{() => <OtpInput value={otp} onChange={setOtp} />}</Field>
                <button className="btn-primary w-full" onClick={verifyOtp} disabled={otp.length !== 6 || rl.locked}><KeyRound className="size-4" />{t("Verify & continue", "पडताळा व पुढे जा")}</button>
                <div className="flex justify-between text-sm">
                  <button className="text-slate-500 hover:underline" onClick={() => { setOtpSent(false); setOtp(""); }}>{t("Change number", "क्रमांक बदला")}</button>
                  <button className="font-medium text-brand-700 disabled:text-slate-400" disabled={resendIn > 0} onClick={sendOtp}>{resendIn > 0 ? `${t("Resend in", "पुन्हा पाठवा")} ${resendIn}s` : t("Resend OTP", "OTP पुन्हा पाठवा")}</button>
                </div>
              </>
            )}
            {forgot && <button className="w-full text-sm text-slate-500 hover:underline" onClick={() => { setForgot(false); setTab("password"); setOtpSent(false); }}>← {t("Back to login", "लॉगिनकडे परत")}</button>}
          </div>
        )}
        <p className="mt-5 text-xs text-slate-400">{t("Prototype: no SMS is sent. OTP and login attempts are rate-limited (5 attempts/min, 3 OTPs/2 min).", "प्रोटोटाइप: SMS पाठवला जात नाही. OTP व लॉगिन प्रयत्नांवर मर्यादा (५ प्रयत्न/मिनिट, ३ OTP/२ मिनिटे).")}</p>
      </div>
      <div>
        <h2 className="text-lg font-semibold">{t("Explore the prototype as…", "प्रोटोटाइप पहा म्हणून…")}</h2>
        <p className="mb-4 text-sm text-slate-500">{t("One-click demo accounts for each role.", "प्रत्येक भूमिकेसाठी एका क्लिकमध्ये डेमो खाते.")}</p>
        <div className="grid gap-3">
          {demo.map((d) => (
            <button key={d.id} onClick={() => go(d.role, d.id)} className="card flex items-center gap-4 p-4 text-left transition-shadow hover:shadow-md">
              <span className="rounded-lg bg-brand-50 p-2.5 text-brand-700"><d.icon className="size-5" /></span>
              <span className="flex-1"><span className="block font-semibold">{t(d.en, d.mr)}</span><span className="text-sm text-slate-500">{d.who}</span></span>
              <Badge tone="orange">{t("Demo", "डेमो")}</Badge>
            </button>
          ))}
        </div>
      </div>
    </div>
  );
}

/* ---------------- Admission ---------------- */
type Pick = { kind: "course"; batchId: string } | { kind: "library"; planId: string };

export function AdmissionView() {
  const { t, l, d } = useI18n();
  const sp = useSearchParams();
  const { db, session, update, login } = useStore();
  const hydrated = useStore((s) => s.hydrated);
  const student = session.role === "student" ? db.students.find((s) => s.id === session.userId) : undefined;
  const [step, setStep] = useState(student ? (student.profile ? 2 : 1) : 0);
  const [pick, setPick] = useState<Pick | null>(null);
  const [courseId, setCourseId] = useState("");
  const [promo, setPromo] = useState("");
  const [promoMsg, setPromoMsg] = useState<{ ok: boolean; text: string } | null>(null);
  const [discount, setDiscount] = useState(0);
  const [inst, setInst] = useState(1);
  const [method, setMethod] = useState<"upi" | "gateway" | "offline">("upi");
  const [result, setResult] = useState<{ paymentId?: string; offline?: boolean } | null>(null);
  const [receipt, setReceipt] = useState<string | null>(null);
  const [reg, setReg] = useState({ name: "", mobile: "", otp: "", sent: false });
  const [processing, setProcessing] = useState(false);
  const initd = useRef(false);

  useEffect(() => {
    if (!hydrated || initd.current) return;
    initd.current = true;
    const b = sp.get("batch"), c = sp.get("course"), lib = sp.get("library");
    if (b && db.batches.some((x) => x.id === b)) { setPick({ kind: "course", batchId: b }); setCourseId(db.batches.find((x) => x.id === b)!.courseId); }
    else if (c) setCourseId(c);
    else if (lib && db.libraryPlans.some((x) => x.id === lib)) setPick({ kind: "library", planId: lib });
    if (student) setStep(student.profile ? 2 : 1);
  }, [hydrated, sp, db, student]);

  const batch = pick?.kind === "course" ? db.batches.find((b) => b.id === pick.batchId) : undefined;
  const plan = pick?.kind === "library" ? db.libraryPlans.find((p) => p.id === pick.planId) : undefined;
  const fee = batch?.fee ?? (plan ? membershipFee(plan, 0) : 0);
  const net = Math.max(0, fee - discount);
  const firstDue = inst > 1 ? Math.round(net / inst) : net;

  const applyPromo = () => {
    if (!pick) return;
    const r = evaluatePromo(db, promo, batch?.courseId ?? "", fee);
    if (r.ok) {
      if (r.promo.needsApproval) { setPromoMsg({ ok: false, text: t("This concession needs office approval. Apply at the office.", "या सवलतीसाठी कार्यालयाची मंजुरी आवश्यक. कार्यालयात अर्ज करा.") }); return; }
      setDiscount(r.discount); setPromoMsg({ ok: true, text: t(`Applied! You save ${fmtINR(r.discount)}`, `लागू! आपली बचत ${fmtINR(r.discount)}`) });
    } else {
      setDiscount(0);
      const m: Record<string, [string, string]> = { invalid: ["Invalid code", "अवैध कोड"], expired: ["Offer expired", "ऑफरची मुदत संपली"], limit: ["Usage limit reached", "वापर मर्यादा संपली"], course: ["Not valid for this course", "या अभ्यासक्रमासाठी लागू नाही"], min: ["Minimum payment not met", "किमान रक्कम पूर्ण नाही"] };
      setPromoMsg({ ok: false, text: t(...m[r.reason]) });
    }
  };

  const register = () => {
    if (!reg.sent) {
      if (reg.name.trim().length < 2 || !isValidMobile(reg.mobile)) return toast.error(t("Enter name and a valid mobile number", "नाव व वैध मोबाईल क्रमांक लिहा"));
      setReg({ ...reg, sent: true }); toast.info(t(`Demo OTP: ${DEMO_OTP}`, `डेमो OTP: ${DEMO_OTP}`)); return;
    }
    if (reg.otp !== DEMO_OTP) return toast.error(t("Incorrect OTP", "चुकीचा OTP"));
    let id = "";
    update((d) => { id = createStudent(d, { name: reg.name.trim(), mobile: reg.mobile }).id; }, { action: "student.register", entity: reg.mobile, detail: "Registered during admission" });
    login("student", id);
    setStep(1);
  };

  const pay = (simulateFailure = false) => {
    if (!student || !pick) return;
    setProcessing(true);
    setTimeout(() => {
      let pid: string | undefined;
      update((d) => {
        if (pick.kind === "course") {
          const e = createEnrolment(d, { studentId: student.id, batchId: pick.batchId, discount, discountReason: discount ? `Promo ${promo.toUpperCase()}` : "", installments: inst, by: "Online" });
          if (discount) { const p = d.promos.find((x) => x.code.toUpperCase() === promo.toUpperCase()); if (p) p.used++; }
          if (method !== "offline") pid = recordPayment(d, { studentId: student.id, enrolmentId: e.id, amount: firstDue, method: method === "upi" ? "upi" : "gateway", gateway: method === "gateway" ? d.settings.gateways.find((g) => g.isDefault)?.name : undefined, reference: `DEMO${Date.now().toString().slice(-8)}`, by: "online", status: simulateFailure ? "failed" : "success" }).id;
        } else {
          const m = createMembership(d, { studentId: student.id, planId: pick.planId, start: today(), discount });
          if (method !== "offline") pid = recordPayment(d, { studentId: student.id, membershipId: m.id, amount: net, method: "upi", reference: `DEMO${Date.now().toString().slice(-8)}`, by: "online", status: simulateFailure ? "failed" : "success" }).id;
          else m.status = "suspended";
        }
      }, { action: pick.kind === "course" ? "admission.online" : "library.join", entity: student.code, detail: method === "offline" ? "Pay at office – pending verification" : simulateFailure ? "Payment failed" : "Paid online (simulated)" });
      setProcessing(false);
      if (simulateFailure) { toast.error(t("Payment failed (simulated). Your admission is saved as pending; you can retry from Fees.", "पेमेंट अयशस्वी (सिम्युलेटेड). प्रवेश प्रलंबित म्हणून जतन; 'शुल्क' विभागातून पुन्हा प्रयत्न करा.")); }
      setResult({ paymentId: simulateFailure ? undefined : pid, offline: method === "offline" || simulateFailure });
      setStep(5);
    }, 900);
  };

  const steps = [t("Account", "खाते"), t("Your details", "आपली माहिती"), t("Course & batch", "अभ्यासक्रम व बॅच"), t("Fee review", "शुल्क तपासणी"), t("Payment", "पेमेंट"), t("Confirmation", "पुष्टी")];
  const courses = db.courses.filter((c) => c.published && c.admissionOpen);

  return (
    <div className="container-page max-w-4xl py-10">
      <h1 className="text-2xl font-bold sm:text-3xl">{t("Online admission", "ऑनलाइन प्रवेश")}</h1>
      <ol className="mt-5 mb-6 flex gap-1 overflow-x-auto text-xs sm:text-sm" aria-label="Progress">
        {steps.map((s, i) => (
          <li key={s} className={cn("flex flex-1 items-center gap-2 whitespace-nowrap rounded-lg px-3 py-2", i === step ? "bg-brand-700 text-white" : i < step ? "bg-brand-50 text-brand-800" : "bg-slate-100 text-slate-500")} aria-current={i === step ? "step" : undefined}>
            <span className="flex size-5 items-center justify-center rounded-full bg-white/20 text-[11px] font-bold">{i < step ? "✓" : i + 1}</span>{s}
          </li>
        ))}
      </ol>

      {step === 0 && (
        <Card title={t("Register or log in", "नोंदणी किंवा लॉगिन")}>
          <div className="grid gap-3 sm:grid-cols-2">
            <Field label={t("Full name", "पूर्ण नाव")} required>{(id) => <input id={id} className="input" value={reg.name} disabled={reg.sent} onChange={(e) => setReg({ ...reg, name: e.target.value })} />}</Field>
            <Field label={t("Mobile number", "मोबाईल क्रमांक")} required>{(id) => <input id={id} className="input" inputMode="numeric" maxLength={10} value={reg.mobile} disabled={reg.sent} onChange={(e) => setReg({ ...reg, mobile: e.target.value.replace(/\D/g, "") })} />}</Field>
            {reg.sent && <Field label="OTP" hint={`Demo: ${DEMO_OTP}`}>{() => <OtpInput value={reg.otp} onChange={(v) => setReg({ ...reg, otp: v })} />}</Field>}
          </div>
          <p className="mt-3 text-xs text-slate-500">{t("Only name and mobile are needed to start. On the next step you will add your details, photo and address.", "सुरुवातीला फक्त नाव व मोबाईल पुरेसे आहे. पुढील टप्प्यात आपली माहिती, फोटो व पत्ता भरा.")}</p>
          <div className="mt-4 flex flex-wrap gap-2">
            <button className="btn-primary" onClick={register}>{reg.sent ? t("Verify & continue", "पडताळा व पुढे जा") : t("Send OTP", "OTP पाठवा")}</button>
            <Link href="/login?next=/admission" className="btn-outline">{t("I already have an account", "माझे आधीच खाते आहे")}</Link>
          </div>
        </Card>
      )}

      {step === 1 && student && <AdmissionDetailsForm student={student} onDone={() => setStep(2)} />}

      {step === 2 && (
        <div className="space-y-5">
          <Card title={t("1. Choose a course", "१. अभ्यासक्रम निवडा")}>
            <div className="grid gap-2 sm:grid-cols-2">
              {courses.map((c) => (
                <button key={c.id} onClick={() => { setCourseId(c.id); setPick(null); setDiscount(0); setPromoMsg(null); }} className={cn("rounded-lg border p-3 text-left text-sm", courseId === c.id ? "border-brand-600 bg-brand-50 ring-1 ring-brand-600" : "border-slate-200 hover:bg-slate-50")}>
                  <span className="font-semibold">{l(c.name)}</span><span className="block text-xs text-slate-500">{c.durationMonths} {t("months", "महिने")}</span>
                </button>
              ))}
              <button onClick={() => { setCourseId("library"); setPick(null); setDiscount(0); }} className={cn("rounded-lg border p-3 text-left text-sm", courseId === "library" ? "border-saffron-600 bg-saffron-50 ring-1 ring-saffron-600" : "border-slate-200 hover:bg-slate-50")}>
                <span className="flex items-center gap-1 font-semibold"><Library className="size-4" />{t("Abhyasika Kendra membership only", "फक्त अभ्यासिका केंद्र सदस्यत्व")}</span><span className="block text-xs text-slate-500">{t("No coaching enrolment needed", "कोचिंग प्रवेश आवश्यक नाही")}</span>
              </button>
            </div>
          </Card>
          {courseId && courseId !== "library" && (
            <Card title={t("2. Choose a batch", "२. बॅच निवडा")}>
              <div className="grid gap-2">
                {db.batches.filter((b) => b.courseId === courseId && b.admissionOpen).map((b) => (
                  <label key={b.id} className={cn("flex cursor-pointer items-center justify-between gap-3 rounded-lg border p-3 text-sm", pick?.kind === "course" && pick.batchId === b.id ? "border-brand-600 bg-brand-50" : "border-slate-200")}>
                    <span className="flex items-center gap-3"><input type="radio" name="batch" checked={pick?.kind === "course" && pick.batchId === b.id} onChange={() => setPick({ kind: "course", batchId: b.id })} />
                      <span><span className="font-semibold">{b.name}</span><span className="block text-xs text-slate-500">{d(b.startDate)} · {b.days} · {b.time} · {b.mode}</span></span></span>
                    <span className="font-semibold">{fmtINR(b.fee)}</span>
                  </label>
                ))}
                {!db.batches.some((b) => b.courseId === courseId && b.admissionOpen) && <p className="text-sm text-slate-500">{t("No open batches. Please submit an enquiry.", "सुरू बॅच नाही. कृपया चौकशी करा.")}</p>}
              </div>
            </Card>
          )}
          {courseId === "library" && (
            <Card title={t("2. Choose a Abhyasika Kendra plan", "२. अभ्यासिका केंद्र योजना निवडा")}>
              <div className="grid gap-2 sm:grid-cols-2">
                {db.libraryPlans.filter((p) => p.active).map((p) => (
                  <label key={p.id} className={cn("flex cursor-pointer items-center justify-between rounded-lg border p-3 text-sm", pick?.kind === "library" && pick.planId === p.id ? "border-saffron-600 bg-saffron-50" : "border-slate-200")}>
                    <span className="flex items-center gap-2"><input type="radio" name="plan" checked={pick?.kind === "library" && pick.planId === p.id} onChange={() => setPick({ kind: "library", planId: p.id })} />{l(p.name)}</span>
                    <span className="text-right"><span className="block font-semibold">{fmtINR(p.fee + p.registrationFee)}</span><span className="block text-[11px] text-slate-500">{t("incl. registration", "नोंदणीसह")}</span></span>
                  </label>
                ))}
              </div>
            </Card>
          )}
          <div className="flex justify-between gap-2"><button className="btn-outline" onClick={() => setStep(1)}>{t("Edit my details", "माझी माहिती बदला")}</button><button className="btn-primary" disabled={!pick} onClick={() => setStep(3)}>{t("Continue", "पुढे")}</button></div>
        </div>
      )}

      {step === 3 && pick && (
        <div className="grid gap-5 md:grid-cols-[1fr_320px]">
          <Card title={t("Fee review", "शुल्क तपासणी")}>
            <p className="font-semibold">{batch ? `${l(db.courses.find((c) => c.id === batch.courseId)?.name)} – ${batch.name}` : l(plan?.name)}</p>
            <div className="mt-4 flex gap-2">
              <input className="input" placeholder={t("Promo / offer code (try EARLY3000)", "ऑफर कोड (उदा. EARLY3000)")} value={promo} onChange={(e) => setPromo(e.target.value.toUpperCase())} aria-label="Promo code" />
              <button className="btn-outline" onClick={applyPromo}>{t("Apply", "लागू करा")}</button>
            </div>
            {promoMsg && <p className={cn("mt-2 text-sm", promoMsg.ok ? "text-emerald-700" : "text-red-600")}>{promoMsg.text}</p>}
            {batch && (
              <div className="mt-5">
                <p className="label">{t("Payment schedule", "पेमेंट वेळापत्रक")}</p>
                <div className="flex flex-wrap gap-2">{[1, 2, 3].map((n) => <button key={n} onClick={() => setInst(n)} className={inst === n ? "btn-primary btn-sm" : "btn-outline btn-sm"}>{n === 1 ? t("Full payment", "पूर्ण रक्कम") : `${n} ${t("instalments", "हप्ते")}`}</button>)}</div>
                {inst > 1 && <ul className="mt-3 space-y-1 text-sm text-slate-600">{Array.from({ length: inst }, (_, i) => <li key={i} className="flex justify-between"><span>{t("Instalment", "हप्ता")} {i + 1} · {i === 0 ? t("today", "आज") : `+${i} ${t("month", "महिना")}`}</span><span>{fmtINR(i === inst - 1 ? net - Math.round(net / inst) * (inst - 1) : Math.round(net / inst))}</span></li>)}</ul>}
              </div>
            )}
            <p className="mt-4 text-xs text-slate-500">{t("Scholarships and individually negotiated fees are applied by the office before payment.", "शिष्यवृत्ती व वैयक्तिक शुल्क कार्यालयाकडून पेमेंटपूर्वी लागू केले जाते.")}</p>
          </Card>
          <Card title={t("Summary", "सारांश")}>
            <dl className="space-y-2 text-sm">
              <div className="flex justify-between"><dt>{t("Fee", "शुल्क")}</dt><dd>{fmtINR(fee)}</dd></div>
              <div className="flex justify-between text-emerald-700"><dt>{t("Discount", "सवलत")}</dt><dd>− {fmtINR(discount)}</dd></div>
              <div className="flex justify-between border-t pt-2 font-semibold"><dt>{t("Total", "एकूण")}</dt><dd>{fmtINR(net)}</dd></div>
              <div className="flex justify-between rounded-md bg-brand-50 p-2 font-semibold text-brand-900"><dt>{t("Pay now", "आता भरा")}</dt><dd>{fmtINR(firstDue)}</dd></div>
            </dl>
            <p className="mt-2 text-[11px] text-saffron-700">{t("Demo amounts", "डेमो रकमा")}</p>
            <div className="mt-4 flex gap-2"><button className="btn-outline flex-1" onClick={() => setStep(2)}>{t("Back", "मागे")}</button><button className="btn-primary flex-1" onClick={() => setStep(4)}>{t("Proceed", "पुढे")}</button></div>
          </Card>
        </div>
      )}

      {step === 4 && (
        <Card title={t("Choose payment method", "पेमेंट पद्धत निवडा")}>
          <Notice tone="warn">{t("Prototype: no real payment is processed. Gateways are simulated until provider credentials are configured.", "प्रोटोटाइप: कोणतेही खरे पेमेंट होत नाही. प्रदात्याचे क्रेडेन्शियल्स कॉन्फिगर होईपर्यंत गेटवे सिम्युलेटेड आहेत.")}</Notice>
          <div className="mt-4 grid gap-3 sm:grid-cols-3">
            {[
              { id: "upi" as const, icon: QrCode, en: "UPI / QR", mr: "UPI / QR", desc: t("Scan & pay", "स्कॅन करून भरा") },
              { id: "gateway" as const, icon: CreditCard, en: "Card / Netbanking", mr: "कार्ड / नेटबँकिंग", desc: db.settings.gateways.find((g) => g.isDefault && g.enabled)?.name ?? "Gateway" },
              { id: "offline" as const, icon: Building2, en: "Pay at office", mr: "कार्यालयात भरा", desc: t("Cash / bank transfer – verified by staff", "रोख / बँक – कर्मचारी पडताळणी") },
            ].map((m) => (
              <button key={m.id} onClick={() => setMethod(m.id)} className={cn("rounded-xl border p-4 text-left", method === m.id ? "border-brand-600 bg-brand-50 ring-1 ring-brand-600" : "border-slate-200")}>
                <m.icon className="mb-2 size-6 text-brand-700" /><p className="font-semibold">{t(m.en, m.mr)}</p><p className="text-xs text-slate-500">{m.desc}</p>
              </button>
            ))}
          </div>
          {method === "upi" && <div className="mt-4 flex items-center gap-4 rounded-lg bg-slate-50 p-4"><div className="flex size-28 items-center justify-center rounded-lg border-2 border-dashed border-slate-300 text-xs text-slate-400">QR<br />placeholder</div><p className="text-sm text-slate-600">{t("Scan with any UPI app. Payment is auto-verified when the gateway is integrated.", "कोणत्याही UPI अ‍ॅपने स्कॅन करा. गेटवे जोडल्यानंतर पेमेंट आपोआप पडताळले जाईल.")}</p></div>}
          <div className="mt-5 flex flex-wrap justify-between gap-2">
            <button className="btn-outline" onClick={() => setStep(3)}>{t("Back", "मागे")}</button>
            <div className="flex flex-wrap gap-2">
              {method !== "offline" && <button className="btn-ghost text-red-600" onClick={() => pay(true)} disabled={processing}>{t("Simulate failure", "अयशस्वी सिम्युलेट")}</button>}
              <button className="btn-accent" onClick={() => pay(false)} disabled={processing}><Wallet className="size-4" />{processing ? t("Processing…", "प्रक्रिया सुरू…") : method === "offline" ? t("Submit admission", "प्रवेश सादर करा") : `${t("Pay", "भरा")} ${fmtINR(pick?.kind === "course" ? firstDue : net)}`}</button>
            </div>
          </div>
        </Card>
      )}

      {step === 5 && result && (
        <Card>
          <div className="py-6 text-center">
            <CheckCircle2 className={cn("mx-auto size-14", result.offline ? "text-amber-500" : "text-emerald-600")} />
            <h2 className="mt-3 text-xl font-bold">{result.offline ? t("Admission submitted – payment pending", "प्रवेश सादर – पेमेंट प्रलंबित") : t("Admission confirmed!", "प्रवेश निश्चित!")}</h2>
            <p className="mx-auto mt-2 max-w-md text-sm text-slate-600">{result.offline ? t("Please pay at the office. Your admission will be confirmed after staff verify the payment.", "कृपया कार्यालयात शुल्क भरा. कर्मचारी पडताळणीनंतर प्रवेश निश्चित होईल.") : t("Your course access is active. A receipt has been generated and sent to your notifications.", "आपला प्रवेश सक्रिय आहे. पावती तयार झाली असून सूचनांमध्ये पाठवली आहे.")}</p>
            <div className="mt-5 flex flex-wrap justify-center gap-2">
              {result.paymentId && <button className="btn-outline" onClick={() => setReceipt(result.paymentId!)}>{t("View receipt", "पावती पहा")}</button>}
              <Link href="/student" className="btn-primary">{t("Go to dashboard", "डॅशबोर्डवर जा")}</Link>
            </div>
          </div>
        </Card>
      )}
      <ReceiptModal paymentId={receipt} onClose={() => setReceipt(null)} />
    </div>
  );
}
