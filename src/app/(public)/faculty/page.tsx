import type { Metadata } from "next";
import { FacultyView } from "@/views/public-pages";
export const metadata: Metadata = { title: "Faculty", description: "Meet the faculty of Vidyadhan Academy." };
export default function Page() { return <FacultyView />; }
