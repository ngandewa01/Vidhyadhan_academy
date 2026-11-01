"use client";
import { useSearchParams } from "next/navigation";
import { useEffect, useState } from "react";
import { BadgeCheck, ShieldX } from "lucide-react";
import { useI18n } from "@/lib/i18n";
import { useStore } from "@/lib/store";
import { PageHero } from "@/components/public";
import { StatusBadge } from "@/components/ui";

export function VerifyView() {
  const { t, l, d } = useI18n();
  const sp = useSearchParams();
  const db = useStore((s) => s.db);
  const [code, setCode] = useState("");
  const [q, setQ] = useState("");
  useEffect(() => { const c = sp.get("code"); if (c) { setCode(c); setQ(c); } }, [sp]);
  const cert = q && db.certificates.find((c) => c.number.toLowerCase() === q.trim().toLowerCase());
  const stu = q && !cert && db.students.find((s) => s.code.toLowerCase() === q.trim().toLowerCase());
  const certStu = cert && db.students.find((s) => s.id === cert.studentId);
  const enr = stu && db.enrolments.filter((e) => e.studentId === stu.id);
  return (
    <>
      <PageHero title={t("Verify certificate / ID card", "प्रमाणपत्र / ओळखपत्र पडताळणी")} subtitle={t("Enter the certificate number or student ID printed on the document, or scan its QR code.", "दस्तऐवजावरील प्रमाणपत्र क्रमांक किंवा विद्यार्थी ओळख क्रमांक लिहा, किंवा QR स्कॅन करा.")} />
      <section className="container-page max-w-xl py-10">
        <form onSubmit={(e) => { e.preventDefault(); setQ(code); }} className="flex gap-2">
          <input className="input" value={code} onChange={(e) => setCode(e.target.value)} placeholder="VA-CERT-2026-0001 / VA-2026-0001" aria-label="Code" />
          <button className="btn-primary">{t("Verify", "पडताळा")}</button>
        </form>
        {q && (
          <div className="card mt-6 p-5">
            {cert && certStu ? (
              <div className="flex gap-3"><BadgeCheck className="size-8 text-emerald-600" /><div><p className="font-semibold text-emerald-700">{t("Valid certificate", "वैध प्रमाणपत्र")}</p><p className="mt-1">{certStu.name} · {cert.title}</p><p className="text-sm text-slate-500">{cert.number} · {t("Issued", "दिनांक")} {d(cert.issuedAt)}{cert.reissues ? ` · ${t("Reissued", "पुनर्निर्गमित")} ×${cert.reissues}` : ""}</p></div></div>
            ) : stu ? (
              <div className="flex gap-3"><BadgeCheck className="size-8 text-emerald-600" /><div><p className="font-semibold text-emerald-700">{t("Registered student", "नोंदणीकृत विद्यार्थी")}</p><p className="mt-1">{stu.name} · {stu.code} <StatusBadge s={stu.status} /></p>
                <ul className="mt-1 text-sm text-slate-500">{(enr || []).map((e) => <li key={e.id}>{l(db.courses.find((c) => c.id === e.courseId)?.name)} – <StatusBadge s={e.enrolmentStatus} /></li>)}</ul></div></div>
            ) : (
              <div className="flex gap-3"><ShieldX className="size-8 text-red-600" /><div><p className="font-semibold text-red-700">{t("No matching record", "जुळणारी नोंद नाही")}</p><p className="text-sm text-slate-500">{t("Check the number and try again, or contact the office.", "क्रमांक तपासून पुन्हा प्रयत्न करा किंवा कार्यालयाशी संपर्क साधा.")}</p></div></div>
            )}
          </div>
        )}
      </section>
    </>
  );
}
