import type { Metadata } from "next";
import { ContactView } from "@/views/public-pages";
export const metadata: Metadata = { title: "Contact Us", description: "Contact Vidyadhan Academy for admissions." };
export default function Page() { return <ContactView />; }
