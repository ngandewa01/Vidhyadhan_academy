import type { Metadata } from "next";
import { BatchesView } from "@/views/public-pages";
export const metadata: Metadata = { title: "Batches & Schedules", description: "Upcoming offline, online and hybrid batches with timings." };
export default function Page() { return <BatchesView />; }
