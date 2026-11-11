"use client";
import { Fragment } from "react";
import { useI18n } from "@/lib/i18n";
import { Notice } from "@/components/ui";
import { PageHero } from "@/components/public";
import type { LegalDoc } from "@/content/legal";

function Marked({ text }: { text: string }) {
  return (
    <>
      {text.split(/(\[\[.*?\]\])/g).map((part, i) =>
        part.startsWith("[[") ? <mark key={i} className="rounded bg-amber-100 px-1 text-amber-900">{part.slice(2, -2)}</mark> : <Fragment key={i}>{part}</Fragment>,
      )}
    </>
  );
}

export function LegalView({ doc }: { doc: LegalDoc }) {
  const { t, l } = useI18n();
  return (
    <>
      <PageHero title={l(doc.title)} subtitle={l(doc.intro)} />
      <article className="container-page max-w-3xl py-10">
        <Notice tone="warn">
          {t(
            "Draft for legal review. This text has not yet been reviewed by a lawyer and is not in force. Highlighted items must be filled or confirmed by the Academy before publishing.",
            "कायदेशीर तपासणीसाठी मसुदा. हा मजकूर अद्याप वकिलाने तपासलेला नाही व लागू नाही. प्रसिद्ध करण्यापूर्वी अधोरेखित बाबी अकॅडमीने भराव्या किंवा निश्चित कराव्या.",
          )}
        </Notice>
        <p className="mt-3 text-sm text-slate-500"><Marked text={l(doc.updated)} /></p>

        <nav aria-label={t("Contents", "अनुक्रमणिका")} className="card mt-6 p-4">
          <p className="mb-2 text-sm font-semibold">{t("Contents", "अनुक्रमणिका")}</p>
          <ol className="columns-1 gap-6 text-sm text-brand-800 sm:columns-2">
            {doc.sections.map((s, i) => <li key={s.id} className="py-0.5"><a href={`#${s.id}`} className="hover:underline">{i + 1}. {l(s.title)}</a></li>)}
          </ol>
        </nav>

        {doc.sections.map((s, i) => (
          <section key={s.id} id={s.id} className="mt-8 scroll-mt-24">
            <h2 className="text-xl font-bold">{i + 1}. {l(s.title)}</h2>
            {s.body.map((b, k) =>
              "p" in b ? (
                <p key={k} className="mt-3 leading-relaxed text-slate-700"><Marked text={l(b.p)} /></p>
              ) : (
                <ul key={k} className="mt-3 list-disc space-y-1.5 pl-6 leading-relaxed text-slate-700">
                  {b.ul.map((item, j) => <li key={j}><Marked text={l(item)} /></li>)}
                </ul>
              ),
            )}
          </section>
        ))}
      </article>
    </>
  );
}
