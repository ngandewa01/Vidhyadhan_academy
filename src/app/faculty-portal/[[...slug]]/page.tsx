import type { Metadata } from "next";
import { FacultyRouter } from "@/views/faculty";
export const metadata: Metadata = { title: "Faculty portal", robots: { index: false } };
export default async function Page({ params }: { params: Promise<{ slug?: string[] }> }) {
  const { slug = [] } = await params;
  return <FacultyRouter slug={slug} />;
}
