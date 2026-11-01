import type { Metadata } from "next";
import { ResultsView } from "@/views/public-pages";
export const metadata: Metadata = { title: "Results & Achievements", description: "Year-wise results and achievements of students." };
export default function Page() { return <ResultsView />; }
