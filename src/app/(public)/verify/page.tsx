import type { Metadata } from "next";
import { Suspense } from "react";
import { VerifyView } from "@/views/verify";
export const metadata: Metadata = { title: "Verify Certificate / ID Card", description: "Verify a Vidyadhan Academy certificate or student ID." };
export default function Page() { return <Suspense><VerifyView /></Suspense>; }
