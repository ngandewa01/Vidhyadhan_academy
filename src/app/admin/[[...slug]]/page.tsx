import type { Metadata } from "next";
import { AdminRouter } from "@/views/admin";
export const metadata: Metadata = { title: "Administration", robots: { index: false } };
export default async function Page({ params }: { params: Promise<{ slug?: string[] }> }) {
  const { slug = [] } = await params;
  return <AdminRouter slug={slug} />;
}
