import type { Metadata } from "next";
import { LegalView } from "@/views/legal";
import { TERMS } from "@/content/legal";

export const metadata: Metadata = { title: "Terms and Conditions", description: "Terms for joining Vidyadhan Academy, using the website and portal, and the Abhyasika Kendra." };
export default function Page() { return <LegalView doc={TERMS} />; }
