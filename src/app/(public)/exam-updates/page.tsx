import type { Metadata } from "next";
import { ExamUpdatesView } from "@/views/public-pages";
export const metadata: Metadata = { title: "Official Exam Updates", description: "Curated official updates for MPSC, Police Bharti, UPSC, SSC and banking exams." };
export default function Page() { return <ExamUpdatesView />; }
