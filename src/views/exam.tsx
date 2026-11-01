"use client";
import Link from "next/link";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { AlertTriangle, CheckCircle2, ChevronLeft, ChevronRight, Clock, Flag, Languages, Lock, Save, Trophy, WifiOff, XCircle } from "lucide-react";
import { useStore } from "@/lib/store";
import { useI18n } from "@/lib/i18n";
import { rankFor, scoreAttempt } from "@/lib/actions";
import { cn, nowISO, seeded, today, uid } from "@/lib/utils";
import { Badge, BarChart, Card, EmptyState, Modal, Notice, PageHeader, Progress, StatCard, StatusBadge, Tabs, toast } from "@/components/ui";
import { useOnline } from "@/components/PortalShell";
import { useMe } from "./student";
import type { Attempt, Lang, Test, TestType } from "@/lib/types";

export const TEST_TYPE: Record<TestType, [string, string]> = {
  chapter: ["Chapter test", "प्रकरण चाचणी"], mock: ["Full mock", "पूर्ण सराव परीक्षा"], pyq: ["Previous year paper", "मागील वर्षाची प्रश्नपत्रिका"],
  daily: ["Daily practice", "दैनंदिन सराव"], weekly: ["Weekly test", "साप्ताहिक चाचणी"], grand: ["Grand test", "महा चाचणी"],
};

function resultsVisible(t: Test) {
  return t.resultMode === "immediate" || t.resultsPublished;
}

export function TestsView() {
  const { t, lc, d, lang } = useI18n();
  const { db, me, batchIds } = useMe();
  const [tab, setTab] = useState<"open" | "upcoming" | "done">("open");
  const mine = db.tests.filter((x) => x.status === "published" && x.batchIds.some((b) => batchIds.includes(b)));
  const attempts = (id: string) => db.attempts.filter((a) => a.testId === id && a.studentId === me.id && a.submittedAt);
  const lists = {
    open: mine.filter((x) => x.availableFrom <= today() && x.availableTo >= today()),
    upcoming: mine.filter((x) => x.availableFrom > today()),
    done: mine.filter((x) => attempts(x.id).length > 0 || x.availableTo < today()),
  };
  return (
    <div>
      <PageHeader title={t("Tests", "चाचण्या")} subtitle={t("Chapter tests, mocks, previous-year papers, daily, weekly and grand tests.", "प्रकरण चाचण्या, सराव परीक्षा, मागील प्रश्नपत्रिका, दैनंदिन, साप्ताहिक व महा चाचण्या.")} />
      <Tabs tabs={[{ id: "open", label: t("Available", "उपलब्ध"), count: lists.open.length }, { id: "upcoming", label: t("Upcoming", "आगामी"), count: lists.upcoming.length }, { id: "done", label: t("Completed / closed", "पूर्ण / बंद"), count: lists.done.length }]} value={tab} onChange={setTab} />
      <div className="grid gap-4 md:grid-cols-2">
        {lists[tab].map((x) => {
          const used = attempts(x.id);
          const ongoing = db.attempts.find((a) => a.testId === x.id && a.studentId === me.id && !a.submittedAt);
          const left = x.attemptsAllowed - used.length;
          const best = used.length ? Math.max(...used.map((a) => a.score)) : null;
          return (
            <div key={x.id} className="card p-5">
              <div className="flex items-start justify-between gap-2"><div><Badge tone="violet">{TEST_TYPE[x.type][lang === "mr" ? 1 : 0]}</Badge><h2 className="mt-2 font-semibold">{lc(x.title)}</h2></div>{best !== null && resultsVisible(x) && <Badge tone="green">{t("Best", "सर्वोत्तम")}: {best}</Badge>}</div>
              <p className="mt-2 text-sm text-slate-500">{x.questionIds.length} {t("questions", "प्रश्न")} · {x.durationMin} min · {x.negativeMark ? `−${x.negativeMark} ${t("per wrong", "प्रति चूक")}` : t("No negative marking", "नकारात्मक गुण नाहीत")}</p>
              <p className="text-xs text-slate-500">{d(x.availableFrom)} – {d(x.availableTo)} · {t("Attempts left", "उरलेले प्रयत्न")}: {Math.max(0, left)}</p>
              <div className="mt-4 flex flex-wrap gap-2">
                {tab === "open" && (ongoing ? <Link href={`/student/tests/${x.id}`} className="btn-accent btn-sm">{t("Resume attempt", "प्रयत्न पुढे सुरू करा")}</Link> : left > 0 ? <Link href={`/student/tests/${x.id}`} className="btn-primary btn-sm">{t("Start test", "चाचणी सुरू करा")}</Link> : <Badge>{t("No attempts left", "प्रयत्न संपले")}</Badge>)}
                {tab === "upcoming" && <Badge tone="blue"><Lock className="size-3" />{t("Opens", "सुरू")} {d(x.availableFrom)}</Badge>}
                {used.length > 0 && <Link href={`/student/tests/${x.id}/review`} className="btn-outline btn-sm">{resultsVisible(x) ? t("Result & review", "निकाल व पुनरावलोकन") : t("Result pending", "निकाल प्रलंबित")}</Link>}
              </div>
            </div>
          );
        })}
      </div>
      {!lists[tab].length && <EmptyState title={t("Nothing here", "येथे काही नाही")} />}
    </div>
  );
}

export function TestEngine({ testId, review }: { testId: string; review?: boolean }) {
  const { t, lc, contentLang } = useI18n();
  const { db, me, batchIds } = useMe();
  const { update } = useStore();
  const online = useOnline();
  const test = db.tests.find((x) => x.id === testId);
  const ongoing = db.attempts.find((a) => a.testId === testId && a.studentId === me.id && !a.submittedAt);
  const submitted = db.attempts.filter((a) => a.testId === testId && a.studentId === me.id && a.submittedAt).sort((a, b) => b.submittedAt!.localeCompare(a.submittedAt!));
  const [lang, setLang] = useState<Lang>(contentLang);
  const [idx, setIdx] = useState(0);
  const [confirm, setConfirm] = useState(false);
  const [now, setNow] = useState(Date.now());
  const [savedAt, setSavedAt] = useState<number | null>(null);
  const submitting = useRef(false);
  const [justSubmitted, setJustSubmitted] = useState<string | null>(null);

  const remaining = ongoing && test ? Math.max(0, new Date(ongoing.startedAt).getTime() + test.durationMin * 60000 - now) : 0;

  const submit = useCallback((auto = false) => {
    if (!test || !ongoing || submitting.current) return; // prevents duplicate submissions
    submitting.current = true;
    const s = scoreAttempt(useStore.getState().db, test, ongoing.answers);
    update((db) => {
      const a = db.attempts.find((x) => x.id === ongoing.id);
      if (!a || a.submittedAt) return;
      Object.assign(a, { submittedAt: nowISO(), score: s.score, correct: s.correct, incorrect: s.incorrect, unanswered: s.unanswered });
      if (resultsVisible(test)) db.inbox.unshift({ id: uid("in"), studentId: me.id, title: { en: `Result: ${test.title.en}`, mr: `निकाल: ${test.title.mr}` }, body: { en: `You scored ${s.score}/${s.max}.`, mr: `आपले गुण ${s.score}/${s.max}.` }, at: nowISO(), read: false, kind: "result" });
    });
    setJustSubmitted(ongoing.id);
    setConfirm(false);
    toast.success(auto ? t("Time up – test auto-submitted", "वेळ संपली – चाचणी आपोआप सादर") : t("Test submitted", "चाचणी सादर केली"));
  }, [test, ongoing, update, me.id, t]);

  useEffect(() => { const i = setInterval(() => setNow(Date.now()), 1000); return () => clearInterval(i); }, []);
  useEffect(() => { if (ongoing && test && remaining <= 0) submit(true); }, [remaining, ongoing, test, submit]);
  useEffect(() => {
    if (!ongoing) return;
    const onVis = () => {
      if (document.visibilityState === "hidden") {
        update((db) => { const a = db.attempts.find((x) => x.id === ongoing.id); if (a) a.tabSwitches++; db.tabSwitchLog.push({ attemptId: ongoing.id, at: nowISO() }); });
        toast.error(t("Tab switch detected and recorded.", "टॅब बदल आढळला व नोंदवला."));
      }
    };
    document.addEventListener("visibilitychange", onVis);
    return () => document.removeEventListener("visibilitychange", onVis);
  }, [ongoing, update, t]);

  if (!test) return <EmptyState title={t("Test not found", "चाचणी सापडली नाही")} />;
  if (!test.batchIds.some((b) => batchIds.includes(b))) return <EmptyState title={t("This test is not assigned to your batch", "ही चाचणी आपल्या बॅचसाठी नाही")} />;

  const lastId = justSubmitted ?? submitted[0]?.id;
  if (review || (justSubmitted && !ongoing)) {
    const at = db.attempts.find((a) => a.id === lastId);
    if (!at) return <EmptyState title={t("No attempt yet", "अद्याप प्रयत्न नाही")} />;
    return <AttemptResult test={test} attempt={at} />;
  }

  if (!ongoing) {
    const left = test.attemptsAllowed - submitted.length;
    const open = test.availableFrom <= today() && test.availableTo >= today();
    const start = () => {
      if (!open || left <= 0) return;
      const id = uid("atp");
      const r = seeded(id.split("").reduce((a, c) => a + c.charCodeAt(0), 0));
      const order = test.shuffle ? [...test.questionIds].sort(() => r() - 0.5) : [...test.questionIds];
      update((db) => { db.attempts.push({ id, testId: test.id, studentId: me.id, answers: {}, review: [], order, startedAt: nowISO(), score: 0, correct: 0, incorrect: 0, unanswered: 0, tabSwitches: 0 }); });
      setIdx(0);
    };
    return (
      <div className="mx-auto max-w-2xl">
        <Link href="/student/tests" className="text-sm text-brand-700">← {t("Tests", "चाचण्या")}</Link>
        <Card className="mt-3" title={lc(test.title)}>
          <dl className="grid grid-cols-2 gap-3 text-sm sm:grid-cols-4">
            {[[t("Questions", "प्रश्न"), test.questionIds.length], [t("Duration", "वेळ"), `${test.durationMin} min`], [t("Negative", "नकारात्मक"), test.negativeMark || "—"], [t("Attempts left", "उरलेले प्रयत्न"), Math.max(0, left)]].map(([k, v]) => <div key={String(k)}><dt className="text-slate-500">{k}</dt><dd className="font-semibold">{v}</dd></div>)}
          </dl>
          <ul className="mt-4 list-disc space-y-1 pl-5 text-sm text-slate-600">
            <li>{t("The timer starts when you click Start and continues even if you close the page.", "'सुरू करा' दाबल्यावर वेळ सुरू होते व पान बंद केले तरी चालू राहते.")}</li>
            <li>{t("Answers are saved automatically. If your connection drops, reopen the test to resume.", "उत्तरे आपोआप जतन होतात. कनेक्शन तुटल्यास चाचणी पुन्हा उघडून सुरू ठेवा.")}</li>
            <li>{t("The test is submitted automatically when time runs out.", "वेळ संपल्यावर चाचणी आपोआप सादर होते.")}</li>
            <li>{t("You can switch question language anytime; your answers stay the same.", "प्रश्नांची भाषा कधीही बदलू शकता; उत्तरे तशीच राहतात.")}</li>
            <li>{t("Leaving the test tab is recorded.", "चाचणी टॅब सोडल्यास नोंद होते.")}</li>
          </ul>
          <div className="mt-5 flex flex-wrap items-center justify-between gap-3">
            <div className="flex items-center gap-2 text-sm"><Languages className="size-4 text-slate-500" />{(["mr", "en"] as Lang[]).map((x) => <button key={x} onClick={() => setLang(x)} className={lang === x ? "btn-primary btn-sm" : "btn-outline btn-sm"}>{x === "mr" ? "मराठी" : "English"}</button>)}</div>
            <button className="btn-accent" disabled={!open || left <= 0} onClick={start}>{!open ? t("Not open", "उपलब्ध नाही") : left <= 0 ? t("No attempts left", "प्रयत्न संपले") : t("Start test", "चाचणी सुरू करा")}</button>
          </div>
        </Card>
      </div>
    );
  }

  // ---- In progress ----
  const order = ongoing.order;
  const qid = order[Math.min(idx, order.length - 1)];
  const q = db.questions.find((x) => x.id === qid)!;
  const answered = order.filter((id) => ongoing.answers[id] !== undefined && ongoing.answers[id] !== null).length;
  const mm = Math.floor(remaining / 60000), ss = Math.floor((remaining % 60000) / 1000);
  const answer = (opt: number | null) => {
    update((db) => { const a = db.attempts.find((x) => x.id === ongoing.id); if (a && !a.submittedAt) a.answers[qid] = opt; });
    setSavedAt(Date.now());
  };
  const toggleReview = () => update((db) => { const a = db.attempts.find((x) => x.id === ongoing.id)!; a.review = a.review.includes(qid) ? a.review.filter((x) => x !== qid) : [...a.review, qid]; });
  const state = (id: string) => {
    const ans = ongoing.answers[id] !== undefined && ongoing.answers[id] !== null;
    const rev = ongoing.review.includes(id);
    return rev ? (ans ? "answered-review" : "review") : ans ? "answered" : ongoing.answers[id] === null ? "skipped" : "unvisited";
  };

  return (
    <div className="fixed inset-0 z-40 flex flex-col bg-slate-50">
      <header className="flex items-center gap-3 border-b bg-white px-4 py-2.5">
        <p className="min-w-0 flex-1 truncate font-semibold">{lc(test.title, lang)}</p>
        {!online && <span className="flex items-center gap-1 text-xs text-red-600"><WifiOff className="size-4" />{t("Offline – answers saved locally", "ऑफलाइन – उत्तरे स्थानिक जतन")}</span>}
        <span className="hidden items-center gap-1 text-xs text-emerald-700 sm:flex"><Save className="size-3.5" />{savedAt ? t("Saved", "जतन") : t("Autosave on", "स्वयं-जतन")}</span>
        <button onClick={() => setLang(lang === "mr" ? "en" : "mr")} className="btn-outline btn-sm"><Languages className="size-4" />{lang === "mr" ? "English" : "मराठी"}</button>
        <span className={cn("flex items-center gap-1 rounded-lg px-3 py-1.5 font-mono text-sm font-bold", remaining < 60000 ? "bg-red-600 text-white" : "bg-brand-900 text-white")} role="timer" aria-live="off"><Clock className="size-4" />{String(mm).padStart(2, "0")}:{String(ss).padStart(2, "0")}</span>
      </header>
      <div className="flex min-h-0 flex-1 flex-col lg:flex-row">
        <section className="flex min-h-0 flex-1 flex-col overflow-y-auto p-4 sm:p-6">
          <div className="mx-auto w-full max-w-3xl">
            <div className="mb-3 flex items-center justify-between text-sm text-slate-500"><span>{t("Question", "प्रश्न")} {idx + 1} / {order.length}</span><span>+{q.marks} / −{test.negativeMark}</span></div>
            <h2 className="text-lg font-medium leading-relaxed">{lc(q.text, lang)}</h2>
            <div className="mt-5 space-y-2" role="radiogroup" aria-label="Options">
              {q.options.map((o, i) => (
                <button key={i} role="radio" aria-checked={ongoing.answers[qid] === i} onClick={() => answer(i)} className={cn("flex w-full items-center gap-3 rounded-xl border p-3 text-left transition-colors", ongoing.answers[qid] === i ? "border-brand-600 bg-brand-50 ring-1 ring-brand-600" : "border-slate-200 bg-white hover:bg-slate-50")}>
                  <span className={cn("flex size-7 shrink-0 items-center justify-center rounded-full border text-sm font-semibold", ongoing.answers[qid] === i ? "border-brand-600 bg-brand-600 text-white" : "border-slate-300")}>{String.fromCharCode(65 + i)}</span>
                  {lc(o, lang)}
                </button>
              ))}
            </div>
            {ongoing.tabSwitches > 0 && <p className="mt-4 flex items-center gap-1 text-xs text-amber-700"><AlertTriangle className="size-3.5" />{t(`Tab switches recorded: ${ongoing.tabSwitches}`, `टॅब बदल नोंद: ${ongoing.tabSwitches}`)}</p>}
          </div>
        </section>
        <aside className="border-t bg-white p-4 lg:w-72 lg:border-l lg:border-t-0">
          <p className="mb-2 text-sm font-semibold">{t("Question palette", "प्रश्न सूची")}</p>
          <div className="grid grid-cols-8 gap-1.5 lg:grid-cols-5">
            {order.map((id, i) => {
              const s = state(id);
              return <button key={id} onClick={() => setIdx(i)} aria-label={`Q${i + 1} ${s}`} className={cn("relative flex size-9 items-center justify-center rounded-md text-xs font-semibold", i === idx && "ring-2 ring-brand-500 ring-offset-1", s === "answered" ? "bg-emerald-600 text-white" : s === "review" ? "bg-violet-600 text-white" : s === "answered-review" ? "bg-violet-600 text-white" : s === "skipped" ? "bg-red-100 text-red-700" : "bg-slate-100 text-slate-600")}>{i + 1}{s === "answered-review" && <span className="absolute -right-0.5 -top-0.5 size-2.5 rounded-full border border-white bg-emerald-500" />}</button>;
            })}
          </div>
          <div className="mt-3 grid grid-cols-2 gap-1 text-[11px] text-slate-500">
            <span className="flex items-center gap-1"><span className="size-3 rounded bg-emerald-600" />{t("Answered", "उत्तर दिले")} ({answered})</span>
            <span className="flex items-center gap-1"><span className="size-3 rounded bg-slate-100 ring-1 ring-slate-200" />{t("Not answered", "उत्तर नाही")} ({order.length - answered})</span>
            <span className="flex items-center gap-1"><span className="size-3 rounded bg-violet-600" />{t("Marked for review", "पुनरावलोकनासाठी")} ({ongoing.review.length})</span>
            <span className="flex items-center gap-1"><span className="size-3 rounded bg-red-100" />{t("Cleared", "पुसले")}</span>
          </div>
          <button className="btn-accent mt-4 w-full" onClick={() => setConfirm(true)}>{t("Submit test", "चाचणी सादर करा")}</button>
        </aside>
      </div>
      <footer className="flex items-center gap-2 border-t bg-white px-4 py-3">
        <button className="btn-outline" disabled={idx === 0} onClick={() => setIdx(idx - 1)}><ChevronLeft className="size-4" /><span className="hidden sm:inline">{t("Previous", "मागील")}</span></button>
        <button className="btn-ghost" onClick={() => answer(null)}>{t("Clear", "पुसा")}</button>
        <button className={cn("btn-outline", ongoing.review.includes(qid) && "border-violet-500 text-violet-700")} onClick={toggleReview}><Flag className="size-4" /><span className="hidden sm:inline">{t("Mark for review", "पुनरावलोकनासाठी")}</span></button>
        <button className="btn-primary ml-auto" onClick={() => (idx < order.length - 1 ? setIdx(idx + 1) : setConfirm(true))}>{idx < order.length - 1 ? t("Save & next", "जतन करा व पुढे") : t("Finish", "समाप्त")}<ChevronRight className="size-4" /></button>
      </footer>
      <Modal open={confirm} onClose={() => setConfirm(false)} title={t("Submit test?", "चाचणी सादर करायची?")} footer={<><button className="btn-outline" onClick={() => setConfirm(false)}>{t("Continue test", "चाचणी सुरू ठेवा")}</button><button className="btn-accent" onClick={() => submit(false)}>{t("Submit", "सादर करा")}</button></>}>
        <div className="grid grid-cols-3 gap-3 text-center">
          <div className="rounded-lg bg-emerald-50 p-3"><p className="text-2xl font-bold text-emerald-700">{answered}</p><p className="text-xs">{t("Answered", "उत्तर दिले")}</p></div>
          <div className="rounded-lg bg-slate-100 p-3"><p className="text-2xl font-bold">{order.length - answered}</p><p className="text-xs">{t("Unanswered", "उत्तर नाही")}</p></div>
          <div className="rounded-lg bg-violet-50 p-3"><p className="text-2xl font-bold text-violet-700">{ongoing.review.length}</p><p className="text-xs">{t("For review", "पुनरावलोकन")}</p></div>
        </div>
        <p className="mt-3 text-sm text-slate-600">{t("You cannot change answers after submitting.", "सादर केल्यानंतर उत्तरे बदलता येणार नाहीत.")}</p>
      </Modal>
    </div>
  );
}

function AttemptResult({ test, attempt }: { test: Test; attempt: Attempt }) {
  const { t, lc, l, contentLang } = useI18n();
  const { db, me } = useMe();
  const [lang, setLang] = useState<Lang>(contentLang);
  const enr = db.enrolments.find((e) => e.studentId === me.id && test.batchIds.includes(e.batchId));
  const s = useMemo(() => scoreAttempt(db, test, attempt.answers), [db, test, attempt]);
  if (!resultsVisible(test))
    return (
      <div className="mx-auto max-w-xl">
        <Card><div className="py-6 text-center"><CheckCircle2 className="mx-auto size-12 text-emerald-600" /><h2 className="mt-3 text-xl font-bold">{t("Submitted successfully", "यशस्वीपणे सादर")}</h2><p className="mt-1 text-sm text-slate-600">{t("Results will be published by the academy. You'll be notified.", "निकाल अकॅडमीकडून प्रकाशित होईल. आपल्याला सूचना मिळेल.")}</p><Link href="/student/tests" className="btn-primary mt-4">{t("Back to tests", "चाचण्यांकडे परत")}</Link></div></Card>
      </div>
    );
  const pct = s.max ? (Math.max(0, s.score) / s.max) * 100 : 0;
  const batchRank = rankFor(db, test.id, me.id, enr?.batchId);
  const allRank = rankFor(db, test.id, me.id);
  return (
    <div className="space-y-5">
      <Link href="/student/tests" className="text-sm text-brand-700">← {t("Tests", "चाचण्या")}</Link>
      <PageHeader title={lc(test.title)} subtitle={t("Result", "निकाल")} />
      <div className="grid grid-cols-2 gap-3 lg:grid-cols-5">
        <StatCard label={t("Score", "गुण")} value={`${s.score} / ${s.max}`} tone={pct >= test.passPercent ? "green" : "red"} hint={pct >= test.passPercent ? t("Pass", "उत्तीर्ण") : t("Below passing", "उत्तीर्णतेपेक्षा कमी")} />
        <StatCard label={t("Percentage", "टक्केवारी")} value={`${pct.toFixed(1)}%`} />
        <StatCard label={t("Correct / Wrong / Skipped", "बरोबर / चूक / सोडले")} value={`${s.correct} / ${s.incorrect} / ${s.unanswered}`} />
        <StatCard label={t("Batch rank", "बॅच क्रमांक")} value={batchRank.rank ? `#${batchRank.rank}` : "—"} hint={`${t("of", "पैकी")} ${batchRank.of}`} icon={<Trophy className="size-5" />} tone="amber" />
        <StatCard label={t("Academy rank", "अकॅडमी क्रमांक")} value={allRank.rank ? `#${allRank.rank}` : "—"} hint={`${t("of", "पैकी")} ${allRank.of}`} icon={<Trophy className="size-5" />} tone="violet" />
      </div>
      <Card title={t("Subject-wise performance", "विषयनिहाय कामगिरी")}>
        <div className="space-y-3">{Object.entries(s.bySubject).map(([sid, v]) => <div key={sid}><div className="mb-1 flex justify-between text-sm"><span>{l(db.subjects.find((x) => x.id === sid)?.name)}</span><span>{v.correct}/{v.total}</span></div><Progress value={(v.correct / v.total) * 100} tone={v.correct / v.total >= 0.6 ? "green" : v.correct / v.total >= 0.4 ? "amber" : "red"} /></div>)}</div>
      </Card>
      {test.showExplanations ? (
        <Card title={t("Answer review", "उत्तर पुनरावलोकन")} actions={<div className="flex gap-1">{(["mr", "en"] as Lang[]).map((x) => <button key={x} onClick={() => setLang(x)} className={lang === x ? "btn-primary btn-sm" : "btn-outline btn-sm"}>{x === "mr" ? "मराठी" : "EN"}</button>)}</div>}>
          <ol className="space-y-5">
            {attempt.order.map((qid, i) => {
              const q = db.questions.find((x) => x.id === qid)!;
              const a = attempt.answers[qid];
              const ok = a === q.answer;
              return (
                <li key={qid}>
                  <p className="font-medium"><span className="mr-1">{i + 1}.</span>{lc(q.text, lang)} {a === undefined || a === null ? <Badge>{t("Skipped", "सोडले")}</Badge> : ok ? <CheckCircle2 className="inline size-4 text-emerald-600" /> : <XCircle className="inline size-4 text-red-600" />}</p>
                  <ul className="mt-2 grid gap-1 text-sm sm:grid-cols-2">{q.options.map((o, k) => <li key={k} className={cn("rounded-md border px-2 py-1", k === q.answer ? "border-emerald-300 bg-emerald-50" : k === a ? "border-red-300 bg-red-50" : "border-slate-200")}>{String.fromCharCode(65 + k)}. {lc(o, lang)}</li>)}</ul>
                  <p className="mt-1 text-sm text-slate-600"><b>{t("Explanation", "स्पष्टीकरण")}:</b> {lc(q.explanation, lang)}</p>
                </li>
              );
            })}
          </ol>
        </Card>
      ) : <Notice>{t("Answer key and explanations will be released by the academy.", "उत्तरतालिका व स्पष्टीकरणे अकॅडमीकडून प्रसिद्ध होतील.")}</Notice>}
    </div>
  );
}

export function ResultsView() {
  const { t, lc, d } = useI18n();
  const { db, me } = useMe();
  const att = db.attempts.filter((a) => a.studentId === me.id && a.submittedAt).sort((a, b) => a.submittedAt!.localeCompare(b.submittedAt!));
  const rows = att.map((a) => { const test = db.tests.find((x) => x.id === a.testId)!; const max = test.questionIds.reduce((s, q) => s + (db.questions.find((x) => x.id === q)?.marks ?? 0), 0); return { a, test, max, pct: max ? (Math.max(0, a.score) / max) * 100 : 0, visible: resultsVisible(test) }; });
  const visible = rows.filter((r) => r.visible);
  return (
    <div className="space-y-5">
      <PageHeader title={t("Results & ranking", "निकाल व क्रमांक")} />
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
        <StatCard label={t("Tests taken", "दिलेल्या चाचण्या")} value={rows.length} />
        <StatCard label={t("Average", "सरासरी")} value={visible.length ? `${(visible.reduce((s, r) => s + r.pct, 0) / visible.length).toFixed(1)}%` : "—"} />
        <StatCard label={t("Best", "सर्वोत्तम")} value={visible.length ? `${Math.max(...visible.map((r) => r.pct)).toFixed(1)}%` : "—"} tone="green" />
      </div>
      {visible.length > 1 && <Card title={t("Progress over time", "कालानुरूप प्रगती")}><BarChart data={visible.map((r) => ({ label: d(r.a.submittedAt, { day: "numeric", month: "short" }), value: Math.round(r.pct) }))} format={(n) => `${n}%`} /></Card>}
      <Card title={t("Test history", "चाचणी इतिहास")} bodyClass="p-0">
        <div className="overflow-x-auto">
          <table className="table-base">
            <thead><tr><th>{t("Test", "चाचणी")}</th><th>{t("Date", "दिनांक")}</th><th>{t("Score", "गुण")}</th><th>%</th><th>{t("Batch rank", "बॅच क्रमांक")}</th><th /></tr></thead>
            <tbody>{rows.map(({ a, test, max, pct, visible }) => { const enr = db.enrolments.find((e) => e.studentId === me.id && test.batchIds.includes(e.batchId)); const r = rankFor(db, test.id, me.id, enr?.batchId); return <tr key={a.id}><td className="font-medium">{lc(test.title)}</td><td>{d(a.submittedAt)}</td><td>{visible ? `${a.score}/${max}` : <StatusBadge s="pending" />}</td><td>{visible ? `${pct.toFixed(0)}%` : "—"}</td><td>{visible && r.rank ? `#${r.rank} / ${r.of}` : "—"}</td><td><Link href={`/student/tests/${test.id}/review`} className="btn-ghost btn-sm">{t("Open", "उघडा")}</Link></td></tr>; })}</tbody>
          </table>
          {!rows.length && <p className="p-6 text-center text-sm text-slate-500">{t("No tests taken yet", "अद्याप चाचणी दिली नाही")}</p>}
        </div>
      </Card>
    </div>
  );
}
