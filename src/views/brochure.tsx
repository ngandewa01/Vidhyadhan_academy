"use client";
import { useState } from "react";
import Link from "next/link";
import { CheckCircle2, Clock, FileDown, Lock, MessageCircle } from "lucide-react";
import { useI18n } from "@/lib/i18n";
import { Card } from "@/components/ui";
import { EnquiryForm, PageHero } from "@/components/public";
import { academy } from "@/config/academy";

type Phase = "form" | "loading" | "ready" | "pending" | "error";

export function BrochureView() {
  const { t } = useI18n();
  const [phase, setPhase] = useState<Phase>("form");
  const [url, setUrl] = useState("");

  const requestLink = async (lead: { name: string; mobile: string }) => {
    setPhase("loading");
    try {
      const res = await fetch("/api/brochure/link", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ name: lead.name, mobile: lead.mobile, consent: true }) });
      const data = await res.json();
      if (res.ok && data.configured && data.url) { setUrl(data.url); setPhase("ready"); }
      else if (res.ok) setPhase("pending");
      else setPhase("error");
    } catch {
      setPhase("error");
    }
  };

  return (
    <>
      <PageHero title={t("Download our brochure", "आमचे ब्रोशर डाउनलोड करा")} subtitle={t("Share a few details and the brochure is yours. Our team may call you to help you choose the right course.", "थोडी माहिती द्या आणि ब्रोशर मिळवा. योग्य अभ्यासक्रम निवडण्यात मदतीसाठी आमची टीम आपल्याला कॉल करू शकते.")} />
      <section className="container-page max-w-xl py-10">
        {(phase === "form" || phase === "loading") && (
          <Card title={<span className="flex items-center gap-2"><Lock className="size-4 text-brand-700" aria-hidden />{t("Unlock the brochure", "ब्रोशर अनलॉक करा")}</span>}>
            {phase === "loading" ? (
              <p className="py-6 text-center text-slate-600" role="status">{t("Preparing your download…", "आपले डाउनलोड तयार करत आहोत…")}</p>
            ) : (
              <EnquiryForm variant="quick" source="brochure" note="Brochure download" hideSuccess onDone={requestLink} />
            )}
          </Card>
        )}

        {phase === "ready" && (
          <div className="rounded-xl border border-emerald-200 bg-emerald-50 p-6 text-emerald-900">
            <p className="flex items-center gap-2 text-lg font-semibold"><CheckCircle2 className="size-6" aria-hidden />{t("Thank you! Your brochure is ready.", "धन्यवाद! आपले ब्रोशर तयार आहे.")}</p>
            <a href={url} className="btn-accent mt-4 w-full justify-center py-3 text-base"><FileDown className="size-5" aria-hidden />{t("Download brochure", "ब्रोशर डाउनलोड करा")}</a>
            <p className="mt-3 flex items-center gap-1.5 text-xs text-emerald-800"><Clock className="size-3.5" aria-hidden />{t("This link works for 10 minutes. If it expires, fill in the form again for a new one.", "ही लिंक १० मिनिटे चालते. मुदत संपल्यास नवीन लिंकसाठी फॉर्म पुन्हा भरा.")}</p>
            <Link href="/courses" className="btn-outline mt-4">{t("Explore courses", "अभ्यासक्रम पहा")}</Link>
          </div>
        )}

        {(phase === "pending" || phase === "error") && (
          <div className="rounded-xl border border-amber-200 bg-amber-50 p-6 text-amber-900">
            <p className="font-semibold">{phase === "pending" ? t("Thank you! We have saved your details.", "धन्यवाद! आपली माहिती नोंदवली आहे.") : t("We could not prepare the download right now.", "आत्ता डाउनलोड तयार करता आले नाही.")}</p>
            <p className="mt-1 text-sm">{phase === "pending" ? t("The brochure is not ready for download yet. Our team will share it with you by call or WhatsApp.", "ब्रोशर अद्याप डाउनलोडसाठी तयार नाही. आमची टीम ते आपल्याला कॉल किंवा व्हॉट्सअ‍ॅपवर पाठवेल.") : t("Your enquiry is saved and our team will contact you. You can also try again in a few minutes.", "आपली चौकशी नोंदवली आहे व आमची टीम संपर्क करेल. काही मिनिटांनी पुन्हा प्रयत्नही करू शकता.")}</p>
            <div className="mt-4 flex flex-wrap gap-2">
              <a href={`https://wa.me/${academy.whatsapp}`} target="_blank" rel="noopener noreferrer" className="btn bg-[#25D366] text-slate-900 hover:bg-[#1ebe5a]"><MessageCircle className="size-4" aria-hidden />WhatsApp</a>
              {phase === "error" && <button className="btn-outline" onClick={() => setPhase("form")}>{t("Try again", "पुन्हा प्रयत्न करा")}</button>}
            </div>
          </div>
        )}
      </section>
    </>
  );
}
