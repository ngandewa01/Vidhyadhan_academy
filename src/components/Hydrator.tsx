"use client";
import { useEffect } from "react";
import { useStore } from "@/lib/store";

/** Rehydrates the browser-stored demo DB after first paint (keeps SSR markup stable). */
export function Hydrator() {
  useEffect(() => {
    Promise.resolve(useStore.persist.rehydrate()).finally(() => {
      useStore.setState({ hydrated: true });
      document.documentElement.lang = useStore.getState().lang;
    });
  }, []);
  return null;
}
