"use client";
import { useEffect, useState } from "react";
import { ExternalLink, Star, PenLine } from "lucide-react";
import { useI18n } from "@/lib/i18n";
import { academy } from "@/config/academy";
import { cn } from "@/lib/utils";
import type { Achievement } from "@/lib/types";
import { Modal } from "./ui";

type Review = { author: string; authorUrl: string; photo: string; rating: number; text: string; when: string; url: string };
type Data = { configured: boolean; error?: string; name?: string; rating?: number | null; total?: number; mapsUrl?: string; writeReviewUrl?: string; reviews?: Review[] };

export function useGoogleReviews() {
  const { lang } = useI18n();
  const [data, setData] = useState<Data | null>(null);
  useEffect(() => {
    let alive = true;
    fetch(`/api/google-reviews?lang=${lang}`).then((r) => r.json()).then((d) => alive && setData(d)).catch(() => alive && setData({ configured: false, error: "network" }));
    return () => { alive = false; };
  }, [lang]);
  return data;
}

export function Stars({ value, size = 16 }: { value: number; size?: number }) {
  return (
    <span className="inline-flex" aria-label={`${value} / 5`}>
      {[1, 2, 3, 4, 5].map((i) => <Star key={i} style={{ width: size, height: size }} className={i <= Math.round(value) ? "fill-amber-400 text-amber-400" : "text-slate-300"} />)}
    </span>
  );
}

const GoogleWord = () => (
  <span className="font-semibold" aria-label="Google"><span className="text-[#4285F4]">G</span><span className="text-[#EA4335]">o</span><span className="text-[#FBBC05]">o</span><span className="text-[#4285F4]">g</span><span className="text-[#34A853]">l</span><span className="text-[#EA4335]">e</span></span>
);

/** Google reviews section. Shows only genuine reviews from the Places API; never fabricated content. */
export function GoogleReviews({ limit = 5, adminHint = false }: { limit?: number; adminHint?: boolean }) {
  const { t } = useI18n();
  const data = useGoogleReviews();
  const [open, setOpen] = useState<number | null>(null);
  const mapsUrl = data?.mapsUrl || academy.googleMapsUrl;

  if (!data) return <div className="grid gap-4 md:grid-cols-3">{[0, 1, 2].map((i) => <div key={i} className="card h-40 animate-pulse bg-slate-100" />)}</div>;

  if (!data.configured || data.error || !data.reviews?.length) {
    return (
      <div className="card flex flex-col items-start gap-3 p-6 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <p className="text-lg"><GoogleWord /> {t("reviews", "रिव्ह्यूज")}</p>
          <p className="text-sm text-slate-600">{t("Read what our students say about us on Google.", "आमच्याबद्दल विद्यार्थी Google वर काय म्हणतात ते वाचा.")}</p>
          {adminHint && <p className="mt-2 text-xs text-amber-700">{data.error ? `Google API: ${data.error}` : t("Live reviews are not connected yet: set GOOGLE_PLACES_API_KEY and GOOGLE_PLACE_ID in Vercel.", "थेट रिव्ह्यूज अद्याप जोडलेले नाहीत: Vercel मध्ये GOOGLE_PLACES_API_KEY व GOOGLE_PLACE_ID सेट करा.")}</p>}
        </div>
        {mapsUrl && <a href={mapsUrl} target="_blank" rel="noopener noreferrer" className="btn-outline">{t("See reviews on Google", "Google वर रिव्ह्यूज पहा")}<ExternalLink className="size-4" /></a>}
      </div>
    );
  }

  const reviews = data.reviews.slice(0, limit);
  const sel = open !== null ? reviews[open] : null;
  return (
    <div>
      <div className="mb-5 flex flex-wrap items-center justify-between gap-4">
        <div className="flex items-center gap-3">
          <span className="text-4xl font-bold">{data.rating?.toFixed(1)}</span>
          <div><Stars value={data.rating ?? 0} size={20} /><p className="text-sm text-slate-600">{data.total} {t("reviews on", "रिव्ह्यूज")} <GoogleWord /></p></div>
        </div>
        <div className="flex flex-wrap gap-2">
          {mapsUrl && <a href={mapsUrl} target="_blank" rel="noopener noreferrer" className="btn-outline">{t("See all reviews", "सर्व रिव्ह्यूज")}<ExternalLink className="size-4" /></a>}
          {data.writeReviewUrl && <a href={data.writeReviewUrl} target="_blank" rel="noopener noreferrer" className="btn-primary"><PenLine className="size-4" />{t("Write a review", "रिव्ह्यू लिहा")}</a>}
        </div>
      </div>
      <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
        {reviews.map((r, i) => (
          <figure key={i} className="card flex flex-col p-5">
            <div className="flex items-center gap-3">
              {r.photo ? <img src={r.photo} alt="" referrerPolicy="no-referrer" className="size-10 rounded-full" /> : <span className="flex size-10 items-center justify-center rounded-full bg-brand-100 font-semibold text-brand-800">{r.author[0]}</span>}
              <div className="min-w-0">
                {r.authorUrl ? <a href={r.authorUrl} target="_blank" rel="noopener noreferrer" className="block truncate font-semibold hover:underline">{r.author}</a> : <p className="truncate font-semibold">{r.author}</p>}
                <p className="text-xs text-slate-500">{r.when}</p>
              </div>
            </div>
            <div className="mt-2"><Stars value={r.rating} /></div>
            <blockquote className={cn("mt-2 flex-1 whitespace-pre-line text-sm text-slate-700", "line-clamp-5")}>{r.text}</blockquote>
            <figcaption className="mt-3 flex items-center justify-between text-xs text-slate-500">
              {r.text.length > 220 ? <button className="font-medium text-brand-700 hover:underline" onClick={() => setOpen(i)}>{t("Read more", "पुढे वाचा")}</button> : <span />}
              <a href={r.url || mapsUrl} target="_blank" rel="noopener noreferrer" className="inline-flex items-center gap-1 hover:underline">{t("Posted on", "प्रकाशित")} <GoogleWord /></a>
            </figcaption>
          </figure>
        ))}
      </div>
      <Modal open={!!sel} onClose={() => setOpen(null)} title={sel?.author ?? ""}>
        {sel && <><Stars value={sel.rating} /><p className="mt-2 whitespace-pre-line text-sm">{sel.text}</p><p className="mt-3 text-xs text-slate-500">{sel.when} · <GoogleWord /></p></>}
      </Modal>
    </div>
  );
}

/** Card for a successful candidate, with full-size image viewer. */
export function ResultCard({ a, courseName }: { a: Achievement; courseName?: string }) {
  const { t, l } = useI18n();
  const [open, setOpen] = useState(false);
  return (
    <figure className="card group overflow-hidden">
      <button type="button" onClick={() => a.photo && setOpen(true)} className="block w-full" aria-label={l(a.name)}>
        {a.photo ? (
          <img src={a.photo} alt={`${l(a.name)} – ${l(a.result)}`} loading="lazy" className="aspect-square w-full object-cover transition-transform duration-300 group-hover:scale-[1.02]" />
        ) : (
          <div className="flex aspect-square w-full items-center justify-center bg-slate-100 text-sm text-slate-400">{t("Photo", "फोटो")}</div>
        )}
      </button>
      <figcaption className="p-4 text-center">
        <p className="font-bold text-slate-900">{l(a.name)}</p>
        <p className="mt-1 inline-block rounded-full bg-emerald-50 px-3 py-0.5 text-sm font-semibold text-emerald-700">{l(a.result)}</p>
        <p className="mt-1 text-xs text-slate-500">{[l(a.exam), a.year, courseName].filter(Boolean).join(" · ")}</p>
      </figcaption>
      <Modal open={open} onClose={() => setOpen(false)} title={l(a.name)} size="lg">
        <img src={a.photo} alt={`${l(a.name)} – ${l(a.result)}`} className="mx-auto max-h-[75vh] w-auto rounded-lg" />
      </Modal>
    </figure>
  );
}
