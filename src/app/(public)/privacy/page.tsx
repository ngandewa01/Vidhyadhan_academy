import type { Metadata } from "next";
import { LegalView } from "@/views/legal";
import { PRIVACY } from "@/content/legal";

export const metadata: Metadata = { title: "Privacy Policy", description: "How Vidyadhan Academy collects, uses, shares and protects personal information." };
export default function Page() { return <LegalView doc={PRIVACY} />; }
