import type { Metadata } from "next";
import { LibraryView } from "@/views/public-pages";
export const metadata: Metadata = { title: "Library Membership", description: "Self-study library with seat allocation – library-only or course bundle plans." };
export default function Page() { return <LibraryView />; }
