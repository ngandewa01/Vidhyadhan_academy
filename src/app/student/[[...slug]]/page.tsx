import type { Metadata } from "next";
import { StudentRouter } from "@/views/student";
export const metadata: Metadata = { title: "Student portal", robots: { index: false } };
export default async function Page({ params }: { params: Promise<{ slug?: string[] }> }) {
  const { slug = [] } = await params;
  return <StudentRouter slug={slug} />;
}
