import type { Metadata } from "next";
import { Suspense } from "react";
import { LoginView } from "@/views/auth";
export const metadata: Metadata = { title: "Login / Register", description: "Student, faculty and staff login.", robots: { index: false } };
export default function Page() { return <Suspense><LoginView /></Suspense>; }
