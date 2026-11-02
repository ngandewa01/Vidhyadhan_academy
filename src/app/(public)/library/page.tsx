import type { Metadata } from "next";
import { LibraryView } from "@/views/public-pages";
export const metadata: Metadata = { title: "Abhyasika Kendra Membership", description: "Abhyasika Kendra with seat allocation – Abhyasika Kendra only or course bundle plans." };
export default function Page() { return <LibraryView />; }
