import type { Metadata } from "next";
import { Suspense } from "react";
import { AdmissionView } from "@/views/auth";
export const metadata: Metadata = { title: "Online Admission", description: "Apply online for courses and Abhyasika Kendra membership." };
export default function Page() { return <Suspense><AdmissionView /></Suspense>; }
