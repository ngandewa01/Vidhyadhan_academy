"use client";
import Link from "next/link";
import { useState } from "react";
import { Camera, FileText, ShieldCheck, Trash2 } from "lucide-react";
import { useI18n } from "@/lib/i18n";
import { useStore } from "@/lib/store";
import { Card, Field, Notice, toast } from "@/components/ui";
import { DEFAULT_ADMISSION_FORM } from "@/lib/seed";
import { fmtAadhaar, fmtSize, isValidAadhaar, isValidEmail, isValidMobile, isValidPin, normMobile, passportPhoto, today } from "@/lib/utils";
import type { Address, CasteCategory, Student } from "@/lib/types";

const STATES = [
  "Maharashtra", "Andhra Pradesh", "Arunachal Pradesh", "Assam", "Bihar", "Chhattisgarh", "Goa", "Gujarat", "Haryana", "Himachal Pradesh", "Jharkhand", "Karnataka", "Kerala",
  "Madhya Pradesh", "Manipur", "Meghalaya", "Mizoram", "Nagaland", "Odisha", "Punjab", "Rajasthan", "Sikkim", "Tamil Nadu", "Telangana", "Tripura", "Uttar Pradesh", "Uttarakhand",
  "West Bengal", "Andaman and Nicobar Islands", "Chandigarh", "Dadra and Nagar Haveli and Daman and Diu", "Delhi", "Jammu and Kashmir", "Ladakh", "Lakshadweep", "Puducherry",
];
const QUALIFICATIONS: [string, string, string][] = [
  ["10th", "10th (SSC)", "१० वी (एसएससी)"], ["12th", "12th (HSC)", "१२ वी (एचएससी)"], ["Diploma", "Diploma", "डिप्लोमा"],
  ["Graduate", "Graduate", "पदवीधर"], ["Post-graduate", "Post-graduate", "पदव्युत्तर"], ["Other", "Other", "इतर"],
];
const CATEGORIES: [CasteCategory, string, string][] = [
  ["open", "Open", "खुला"], ["obc", "OBC", "ओबीसी"], ["sc", "SC", "अनुसूचित जाती"], ["st", "ST", "अनुसूचित जमाती"],
  ["vjnt", "VJ / NT", "विमुक्त जाती / भटक्या जमाती"], ["sbc", "SBC / SEBC", "विशेष मागास / एसईबीसी"], ["ews", "EWS", "आर्थिक दुर्बल घटक"],
];
const HEARD: [string, string, string][] = [
  ["Google", "Google search", "गुगल शोध"], ["Social", "Instagram / Facebook / YouTube", "इन्स्टाग्राम / फेसबुक / यूट्यूब"], ["Friend", "Friend or relative", "मित्र किंवा नातेवाईक"],
  ["Banner", "Banner / hoarding / pamphlet", "बॅनर / होर्डिंग / पत्रक"], ["Other", "Other", "इतर"],
];
const MAX_FILE = 5 * 1024 * 1024;

interface Form {
  name: string; dob: string; gender: string; fatherName: string; motherName: string; parentMobile: string; alternateMobile: string; email: string; category: string;
  qualification: string; institution: string; passingYear: string; heardAbout: string;
  aadhaar: string; aadhaarDoc: { name: string; size: number } | null; photo: string;
  perm: Address; curr: Address; same: boolean; consent: boolean;
}

const blankAddress = (): Address => ({ line1: "", area: "", taluka: "", city: "", district: "", state: "Maharashtra", pin: "" });
const trimAddress = (a: Address): Address => ({ line1: a.line1.trim(), area: a.area.trim(), taluka: a.taluka.trim(), city: a.city.trim(), district: a.district.trim(), state: a.state, pin: a.pin.trim() });
const ageOf = (iso: string) => {
  const b = new Date(iso), n = new Date();
  let a = n.getFullYear() - b.getFullYear();
  if (n.getMonth() < b.getMonth() || (n.getMonth() === b.getMonth() && n.getDate() < b.getDate())) a--;
  return a;
};

function AddressFields({ value, onChange, disabled, errors, prefix }: { value: Address; onChange: (a: Address) => void; disabled?: boolean; errors: Record<string, string>; prefix: string }) {
  const { t } = useI18n();
  const set = (k: keyof Address, v: string) => onChange({ ...value, [k]: v });
  const err = (k: keyof Address) => (disabled ? undefined : errors[`${prefix}.${k}`]);
  const text = (k: keyof Address, autoComplete?: string, extra?: { inputMode?: "numeric"; maxLength?: number }) => (id: string) => (
    <input id={id} className="input" disabled={disabled} autoComplete={autoComplete} inputMode={extra?.inputMode} maxLength={extra?.maxLength} value={value[k]}
      onChange={(e) => set(k, k === "pin" ? e.target.value.replace(/\D/g, "") : e.target.value)} />
  );
  return (
    <div className="grid gap-3 sm:grid-cols-2">
      <Field label={t("House / flat no. and street", "घर / फ्लॅट क्र. व रस्ता")} required error={err("line1")} className="sm:col-span-2">{text("line1", "address-line1")}</Field>
      <Field label={t("Village / area / locality", "गाव / परिसर")} error={err("area")}>{text("area", "address-line2")}</Field>
      <Field label={t("Taluka", "तालुका")} error={err("taluka")}>{text("taluka")}</Field>
      <Field label={t("City / town", "शहर / गाव")} required error={err("city")}>{text("city", "address-level2")}</Field>
      <Field label={t("District", "जिल्हा")} required error={err("district")}>{text("district")}</Field>
      <Field label={t("State", "राज्य")} required error={err("state")}>
        {(id) => <select id={id} className="input" disabled={disabled} value={value.state} onChange={(e) => set("state", e.target.value)}>{STATES.map((s) => <option key={s}>{s}</option>)}</select>}
      </Field>
      <Field label={t("PIN code", "पिन कोड")} required error={err("pin")}>{text("pin", "postal-code", { inputMode: "numeric", maxLength: 6 })}</Field>
    </div>
  );
}

export function AdmissionDetailsForm({ student, onDone }: { student: Student; onDone: () => void }) {
  const { t, lang } = useI18n();
  const update = useStore((s) => s.update);
  const rules = useStore((s) => s.db.settings.admissionForm) ?? DEFAULT_ADMISSION_FORM;
  const p = student.profile;
  const [f, setF] = useState<Form>({
    name: student.name, dob: student.dob ?? "", gender: student.gender ?? "", fatherName: p?.fatherName ?? "", motherName: p?.motherName ?? "",
    parentMobile: student.parentMobile ?? "", alternateMobile: p?.alternateMobile ?? "", email: student.email ?? "", category: p?.category ?? "",
    qualification: p?.qualification ?? "", institution: p?.institution ?? "", passingYear: p?.passingYear ?? "", heardAbout: p?.heardAbout ?? "",
    aadhaar: p?.aadhaar ? fmtAadhaar(p.aadhaar) : "", aadhaarDoc: p?.aadhaarDoc ?? null, photo: p?.photo ?? "",
    perm: p?.permanentAddress ?? blankAddress(), curr: p?.currentAddress ?? blankAddress(), same: p?.sameAddress ?? false, consent: false,
  });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);
  const set = <K extends keyof Form>(k: K, v: Form[K]) => setF((x) => ({ ...x, [k]: v }));
  const label = (opts: [string, string, string][]) => opts.map(([v, en, mr]) => <option key={v} value={v}>{lang === "mr" ? mr : en}</option>);

  const onPhoto = async (file?: File) => {
    if (!file) return;
    if (!/^image\/(jpeg|png)$/.test(file.type)) return toast.error(t("Please use a JPG or PNG photo", "कृपया JPG किंवा PNG फोटो वापरा"));
    if (file.size > MAX_FILE) return toast.error(t("Photo must be under 5 MB", "फोटो ५ MB पेक्षा लहान हवा"));
    setBusy(true);
    try { set("photo", await passportPhoto(file)); } catch { toast.error(t("Could not read this photo. Try another.", "हा फोटो वाचता आला नाही. दुसरा वापरा.")); }
    setBusy(false);
  };
  const onDoc = (file?: File) => {
    if (!file) return;
    if (!/^(application\/pdf|image\/(jpeg|png))$/.test(file.type)) return toast.error(t("Use a PDF, JPG or PNG file", "PDF, JPG किंवा PNG फाइल वापरा"));
    if (file.size > MAX_FILE) return toast.error(t("File must be under 5 MB", "फाइल ५ MB पेक्षा लहान हवी"));
    set("aadhaarDoc", { name: file.name, size: file.size });
  };

  const validate = () => {
    const e: Record<string, string> = {};
    const req = t("Required", "आवश्यक");
    if (f.name.trim().length < 2) e.name = req;
    if (!f.dob) e.dob = req; else if (ageOf(f.dob) < 14 || ageOf(f.dob) > 60) e.dob = t("Please check the date of birth", "जन्मतारीख तपासा");
    if (!f.gender) e.gender = req;
    if (f.fatherName.trim().length < 2) e.fatherName = req;
    if (!isValidMobile(f.parentMobile)) e.parentMobile = t("Enter a valid 10-digit mobile", "वैध १० अंकी मोबाईल लिहा");
    if (f.alternateMobile && !isValidMobile(f.alternateMobile)) e.alternateMobile = t("Enter a valid 10-digit mobile", "वैध १० अंकी मोबाईल लिहा");
    if (!isValidEmail(f.email)) e.email = t("Enter a valid email", "वैध ईमेल लिहा");
    if (!f.qualification) e.qualification = req;
    if (f.passingYear && !(/^\d{4}$/.test(f.passingYear) && Number(f.passingYear) >= 1980 && Number(f.passingYear) <= new Date().getFullYear() + 1)) e.passingYear = t("Enter a 4-digit year", "४ अंकी वर्ष लिहा");
    const aadhaar = f.aadhaar.replace(/\s/g, "");
    if (aadhaar ? !isValidAadhaar(aadhaar) : rules.requireAadhaar) e.aadhaar = aadhaar ? t("This Aadhaar number is not valid. Please re-check.", "हा आधार क्रमांक वैध नाही. कृपया पुन्हा तपासा.") : req;
    if (!f.photo && rules.requirePhoto) e.photo = t("Please upload a passport-size photo", "कृपया पासपोर्ट आकाराचा फोटो जोडा");
    const addr = (a: Address, prefix: string) => {
      if (!a.line1.trim()) e[`${prefix}.line1`] = req;
      if (!a.city.trim()) e[`${prefix}.city`] = req;
      if (!a.district.trim()) e[`${prefix}.district`] = req;
      if (!isValidPin(a.pin)) e[`${prefix}.pin`] = t("Enter a valid 6-digit PIN", "वैध ६ अंकी पिन लिहा");
    };
    addr(f.perm, "perm");
    if (!f.same) addr(f.curr, "curr");
    if (!f.consent) e.consent = t("Please accept to continue", "पुढे जाण्यासाठी संमती द्या");
    return e;
  };

  const submit = () => {
    const e = validate();
    setErrors(e);
    if (Object.keys(e).length) {
      toast.error(t("Please fix the highlighted fields", "कृपया लाल रंगातील माहिती दुरुस्त करा"));
      setTimeout(() => document.querySelector('#details-form [role="alert"]')?.scrollIntoView({ behavior: "smooth", block: "center" }), 50);
      return;
    }
    const aadhaar = f.aadhaar.replace(/\s/g, "");
    const perm = trimAddress(f.perm);
    const curr = f.same ? perm : trimAddress(f.curr);
    update((d) => {
      const s = d.students.find((x) => x.id === student.id)!;
      s.name = f.name.trim();
      s.dob = f.dob;
      s.gender = f.gender as Student["gender"];
      s.email = f.email.trim() || undefined;
      s.parentMobile = normMobile(f.parentMobile);
      s.address = [perm.line1, perm.area, perm.city, perm.district, perm.pin].filter(Boolean).join(", ");
      s.profile = {
        fatherName: f.fatherName.trim(), motherName: f.motherName.trim() || undefined, alternateMobile: f.alternateMobile ? normMobile(f.alternateMobile) : undefined,
        category: (f.category || undefined) as CasteCategory | undefined, qualification: f.qualification, institution: f.institution.trim() || undefined,
        passingYear: f.passingYear || undefined, heardAbout: f.heardAbout || undefined, aadhaar: aadhaar || undefined, aadhaarDoc: f.aadhaarDoc ?? undefined,
        photo: f.photo || undefined, permanentAddress: perm, currentAddress: curr, sameAddress: f.same, consentAt: new Date().toISOString(), updatedAt: new Date().toISOString(),
      };
    }, { action: "student.profile", entity: student.code, detail: "Admission details submitted" });
    toast.success(t("Details saved", "माहिती जतन झाली"));
    onDone();
  };

  return (
    <div id="details-form" className="space-y-5">
      <Notice>{t("Please fill in your details as they appear on your school records or Aadhaar. Fields marked * are required.", "कृपया आपली माहिती शाळेच्या नोंदी किंवा आधारप्रमाणे भरा. * असलेली माहिती आवश्यक आहे.")}</Notice>

      <Card title={t("1. Personal details", "१. वैयक्तिक माहिती")}>
        <div className="grid gap-3 sm:grid-cols-2">
          <Field label={t("Full name", "पूर्ण नाव")} required error={errors.name}>{(id) => <input id={id} className="input" autoComplete="name" value={f.name} onChange={(e) => set("name", e.target.value)} />}</Field>
          <Field label={t("Mobile number (verified)", "मोबाईल क्रमांक (पडताळलेला)")}>{(id) => <input id={id} className="input bg-slate-50" readOnly value={student.mobile} />}</Field>
          <Field label={t("Date of birth", "जन्मतारीख")} required error={errors.dob}>{(id) => <input id={id} type="date" className="input" max={today()} value={f.dob} onChange={(e) => set("dob", e.target.value)} />}</Field>
          <Field label={t("Gender", "लिंग")} required error={errors.gender}>
            {(id) => <select id={id} className="input" value={f.gender} onChange={(e) => set("gender", e.target.value)}><option value="">{t("Select…", "निवडा…")}</option><option value="male">{t("Male", "पुरुष")}</option><option value="female">{t("Female", "स्त्री")}</option><option value="other">{t("Other", "इतर")}</option></select>}
          </Field>
          <Field label={t("Father's / guardian's name", "वडिलांचे / पालकांचे नाव")} required error={errors.fatherName}>{(id) => <input id={id} className="input" value={f.fatherName} onChange={(e) => set("fatherName", e.target.value)} />}</Field>
          <Field label={t("Mother's name", "आईचे नाव")}>{(id) => <input id={id} className="input" value={f.motherName} onChange={(e) => set("motherName", e.target.value)} />}</Field>
          <Field label={t("Parent / guardian mobile", "पालकांचा मोबाईल")} required error={errors.parentMobile} hint={t("Used for emergencies and fee or attendance alerts.", "आणीबाणी तसेच शुल्क व उपस्थिती सूचनांसाठी.")}>
            {(id) => <input id={id} className="input" inputMode="numeric" maxLength={10} value={f.parentMobile} onChange={(e) => set("parentMobile", e.target.value.replace(/\D/g, ""))} />}
          </Field>
          <Field label={t("Alternate mobile", "पर्यायी मोबाईल")} error={errors.alternateMobile}>{(id) => <input id={id} className="input" inputMode="numeric" maxLength={10} value={f.alternateMobile} onChange={(e) => set("alternateMobile", e.target.value.replace(/\D/g, ""))} />}</Field>
          <Field label="Email" error={errors.email}>{(id) => <input id={id} type="email" className="input" autoComplete="email" value={f.email} onChange={(e) => set("email", e.target.value)} />}</Field>
          <Field label={t("Category (optional)", "प्रवर्ग (ऐच्छिक)")} hint={t("Only to guide you on reservation-based eligibility.", "फक्त आरक्षणानुसार पात्रतेबाबत मार्गदर्शनासाठी.")}>
            {(id) => <select id={id} className="input" value={f.category} onChange={(e) => set("category", e.target.value)}><option value="">{t("Prefer not to say", "सांगू इच्छित नाही")}</option>{label(CATEGORIES)}</select>}
          </Field>
        </div>
      </Card>

      <Card title={t("2. Photo and ID", "२. फोटो व ओळखपत्र")}>
        <div className="grid gap-5 sm:grid-cols-[160px_1fr]">
          <div>
            <p className="label">{t("Passport-size photo", "पासपोर्ट आकाराचा फोटो")}{rules.requirePhoto && <span className="text-red-600"> *</span>}</p>
            <div className={`flex aspect-[4/5] w-32 items-center justify-center overflow-hidden rounded-lg border-2 border-dashed ${errors.photo ? "border-red-400" : "border-slate-300"} bg-slate-50`}>
              {f.photo ? <img src={f.photo} alt={t("Your photo", "आपला फोटो")} className="size-full object-cover" /> : <Camera className="size-8 text-slate-300" aria-hidden />}
            </div>
            {errors.photo && <p className="mt-1 text-xs text-red-600" role="alert">{errors.photo}</p>}
          </div>
          <div className="space-y-3">
            <Field label={t("Upload photo", "फोटो जोडा")} hint={t("35 × 45 mm style: face clearly visible, plain light background, JPG or PNG up to 5 MB. We crop it to passport size.", "३५ × ४५ मिमी पद्धत: चेहरा स्पष्ट, साधी हलकी पार्श्वभूमी, JPG किंवा PNG ५ MB पर्यंत. आम्ही तो पासपोर्ट आकारात कापतो.")}>
              {(id) => <input id={id} type="file" accept="image/jpeg,image/png" className="input" disabled={busy} onChange={(e) => { onPhoto(e.target.files?.[0]); e.target.value = ""; }} />}
            </Field>
            {f.photo && <button type="button" className="btn-ghost btn-sm text-red-600" onClick={() => set("photo", "")}><Trash2 className="size-4" />{t("Remove photo", "फोटो काढा")}</button>}
            <Field label={t("Aadhaar number", "आधार क्रमांक")} required={rules.requireAadhaar} error={errors.aadhaar}
              hint={rules.requireAadhaar ? t("12 digits. Kept confidential and shown to staff only in masked form.", "१२ अंक. गोपनीय ठेवला जातो व कर्मचाऱ्यांना फक्त झाकलेल्या स्वरूपात दिसतो.") : t("Optional. Kept confidential and shown to staff only in masked form (XXXX XXXX 1234).", "ऐच्छिक. गोपनीय ठेवला जातो व कर्मचाऱ्यांना फक्त झाकलेल्या स्वरूपात (XXXX XXXX 1234) दिसतो.")}>
              {(id) => <input id={id} className="input" inputMode="numeric" autoComplete="off" placeholder="XXXX XXXX XXXX" maxLength={14} value={f.aadhaar} onChange={(e) => set("aadhaar", fmtAadhaar(e.target.value))} />}
            </Field>
            <Field label={t("Aadhaar card copy (optional)", "आधार कार्ड प्रत (ऐच्छिक)")} hint={t("PDF, JPG or PNG up to 5 MB. In this prototype only the file name is recorded.", "PDF, JPG किंवा PNG ५ MB पर्यंत. या प्रोटोटाइपमध्ये फक्त फाइलचे नाव नोंदवले जाते.")}>
              {(id) => <input id={id} type="file" accept="application/pdf,image/jpeg,image/png" className="input" onChange={(e) => { onDoc(e.target.files?.[0]); e.target.value = ""; }} />}
            </Field>
            {f.aadhaarDoc && (
              <p className="flex items-center gap-2 text-sm text-slate-600"><FileText className="size-4" aria-hidden />{f.aadhaarDoc.name} · {fmtSize(f.aadhaarDoc.size / 1024)}
                <button type="button" className="btn-ghost btn-sm text-red-600" onClick={() => set("aadhaarDoc", null)}>{t("Remove", "काढा")}</button></p>
            )}
          </div>
        </div>
      </Card>

      <Card title={t("3. Address", "३. पत्ता")}>
        <p className="mb-3 font-semibold">{t("Permanent address", "कायमचा पत्ता")}</p>
        <AddressFields value={f.perm} onChange={(a) => set("perm", a)} errors={errors} prefix="perm" />
        <label className="mt-5 flex cursor-pointer items-center gap-2 rounded-lg bg-brand-50 p-3 text-sm font-medium">
          <input type="checkbox" checked={f.same} onChange={(e) => set("same", e.target.checked)} />
          {t("Current address is the same as permanent address", "सध्याचा पत्ता कायम पत्त्यासारखाच आहे")}
        </label>
        <p className="mb-3 mt-5 font-semibold">{t("Current address", "सध्याचा पत्ता")}</p>
        <AddressFields value={f.same ? f.perm : f.curr} onChange={(a) => set("curr", a)} disabled={f.same} errors={errors} prefix="curr" />
      </Card>

      <Card title={t("4. Education and other details", "४. शिक्षण व इतर माहिती")}>
        <div className="grid gap-3 sm:grid-cols-2">
          <Field label={t("Highest qualification", "सर्वोच्च शैक्षणिक पात्रता")} required error={errors.qualification}>
            {(id) => <select id={id} className="input" value={f.qualification} onChange={(e) => set("qualification", e.target.value)}><option value="">{t("Select…", "निवडा…")}</option>{label(QUALIFICATIONS)}</select>}
          </Field>
          <Field label={t("College / university", "महाविद्यालय / विद्यापीठ")}>{(id) => <input id={id} className="input" value={f.institution} onChange={(e) => set("institution", e.target.value)} />}</Field>
          <Field label={t("Year of passing", "उत्तीर्ण वर्ष")} error={errors.passingYear}>{(id) => <input id={id} className="input" inputMode="numeric" maxLength={4} value={f.passingYear} onChange={(e) => set("passingYear", e.target.value.replace(/\D/g, ""))} />}</Field>
          <Field label={t("How did you hear about us?", "आमच्याबद्दल कसे समजले?")}>
            {(id) => <select id={id} className="input" value={f.heardAbout} onChange={(e) => set("heardAbout", e.target.value)}><option value="">{t("Select…", "निवडा…")}</option>{label(HEARD)}</select>}
          </Field>
        </div>
      </Card>

      <Card>
        <label className="flex cursor-pointer items-start gap-3 text-sm">
          <input type="checkbox" className="mt-1" checked={f.consent} onChange={(e) => set("consent", e.target.checked)} />
          <span>
            {t("I confirm that the information is correct. I agree that the academy may store and use my details, including my Aadhaar number if I have given it, for admission, fees, attendance and communication, and keep them confidential, as described in the ", "मी दिलेली माहिती बरोबर असल्याची खात्री देतो/देते. आधार क्रमांकासह (दिला असल्यास) माझी माहिती अकॅडमीने प्रवेश, शुल्क, उपस्थिती व संपर्कासाठी साठवण्यास व वापरण्यास तसेच गोपनीय ठेवण्यास माझी संमती आहे (पहा: ")}
            <Link href="/privacy" target="_blank" className="font-medium text-brand-700 underline">{t("Privacy Policy", "गोपनीयता धोरण")}</Link>
            {t(" and agree to the ", ") आणि मी ")}
            <Link href="/terms" target="_blank" className="font-medium text-brand-700 underline">{t("Terms and Conditions", "अटी व शर्ती")}</Link>
            {t(". If I am under 18, my parent or guardian has agreed.", " मान्य करतो/करते. मी १८ वर्षांखालील असल्यास माझ्या पालकांनी मान्यता दिली आहे.")}
          </span>
        </label>
        {errors.consent && <p className="mt-2 text-xs text-red-600" role="alert">{errors.consent}</p>}
        <p className="mt-2 flex items-center gap-1 text-xs text-slate-500"><ShieldCheck className="size-4" aria-hidden />{t("Your details are visible only to authorised staff.", "आपली माहिती फक्त अधिकृत कर्मचाऱ्यांनाच दिसते.")}</p>
        <div className="mt-4 flex justify-end"><button className="btn-primary" onClick={submit} disabled={busy}>{t("Save & continue", "जतन करून पुढे")}</button></div>
      </Card>
    </div>
  );
}
