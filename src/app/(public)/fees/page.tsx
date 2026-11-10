import type { Metadata } from "next";
import { FeesView } from "@/views/public-pages";
export const metadata: Metadata = { title: "Fees & Plans", description: "Course fees, instalments, scholarships and Abhyasika Kendra plans." };
export default function Page() { return <FeesView />; }
