"use client";
/**
 * Prototype data layer. All data lives in the browser (localStorage) so the prototype is clickable
 * without a backend. In production these mutations become calls to the modular REST API.
 */
import { create } from "zustand";
import { persist, createJSONStorage } from "zustand/middleware";
import { buildSeed, type DB } from "./seed";
import type { Lang, Role, Session } from "./types";
import { uid, nowISO } from "./utils";

interface State {
  db: DB;
  session: Session;
  lang: Lang; // interface language
  hydrated: boolean;
  update: (fn: (db: DB) => void, audit?: { action: string; entity: string; detail?: string }) => void;
  login: (role: Role, userId: string | null) => void;
  logout: () => void;
  setLang: (l: Lang) => void;
  resetDemo: () => void;
}

export const useStore = create<State>()(
  persist(
    (set, get) => ({
      db: buildSeed(),
      session: { role: "visitor", userId: null },
      lang: "mr",
      hydrated: false,
      update: (fn, audit) => {
        const db = structuredClone(get().db);
        fn(db);
        if (audit) {
          db.audit.unshift({ id: uid("au"), at: nowISO(), actor: actorName(get()), action: audit.action, entity: audit.entity, detail: audit.detail ?? "" });
        }
        set({ db });
      },
      login: (role, userId) => set({ session: { role, userId } }),
      logout: () => set({ session: { role: "visitor", userId: null } }),
      setLang: (lang) => {
        if (typeof document !== "undefined") document.documentElement.lang = lang;
        set({ lang });
      },
      resetDemo: () => set({ db: buildSeed() }),
    }),
    {
      name: "vidyadhan-demo-v3",
      storage: createJSONStorage(() => {
        try {
          return localStorage;
        } catch {
          return { getItem: () => null, setItem: () => {}, removeItem: () => {} };
        }
      }),
      partialize: (s) => ({ db: s.db, session: s.session, lang: s.lang }),
      skipHydration: true,
    }
  )
);

function actorName(s: Pick<State, "db" | "session">) {
  const { session, db } = s;
  if (session.role === "student") return db.students.find((x) => x.id === session.userId)?.name ?? "Student";
  return db.staff.find((x) => x.id === session.userId)?.name ?? "System";
}

export const useDB = () => useStore((s) => s.db);
export const useSession = () => useStore((s) => s.session);

export function useCurrentStaff() {
  const { db, session } = useStore();
  return db.staff.find((s) => s.id === session.userId);
}
export function useCurrentStudent() {
  const { db, session } = useStore();
  return db.students.find((s) => s.id === session.userId);
}
export function useActorName() {
  return useStore((s) => actorName(s));
}

export function nextReceiptNo(db: DB) {
  const n = db.receiptSeq++;
  return `${db.settings.receiptPrefix}/2026-27/${n}`;
}
