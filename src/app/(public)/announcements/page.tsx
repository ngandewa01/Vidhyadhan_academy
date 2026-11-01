import type { Metadata } from "next";
import { AnnouncementsView } from "@/views/public-pages";
export const metadata: Metadata = { title: "Announcements", description: "Latest academy announcements." };
export default function Page() { return <AnnouncementsView />; }
