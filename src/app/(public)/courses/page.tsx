import type { Metadata } from "next";
import { CoursesView } from "@/views/public-pages";
export const metadata: Metadata = { title: "Courses", description: "MPSC Rajyaseva, PSI/STI/ASO, Police Bharti, Talathi, Combined Group B and C courses." };
export default function Page() { return <CoursesView />; }
