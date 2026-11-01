import type { Metadata } from "next";
import { AboutView } from "@/views/public-pages";
export const metadata: Metadata = { title: "About the Academy", description: "About Vidyadhan Academy, a competitive exam coaching institute in Maharashtra." };
export default function Page() { return <AboutView />; }
