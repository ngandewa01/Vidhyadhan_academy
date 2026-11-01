import type { Metadata } from "next";
import { TestimonialsView } from "@/views/public-pages";
export const metadata: Metadata = { title: "Testimonials", description: "Student testimonials." };
export default function Page() { return <TestimonialsView />; }
