import type { Metadata } from "next";
import { SyllabusView } from "@/views/public-pages";
export const metadata: Metadata = { title: "Syllabus", description: "Subject, chapter and topic-wise syllabus for each course." };
export default function Page() { return <SyllabusView />; }
