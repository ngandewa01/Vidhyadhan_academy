import type { Metadata } from "next";
import { EnquiryView } from "@/views/public-pages";
export const metadata: Metadata = { title: "Enquiry", description: "Send an admission enquiry to Vidyadhan Academy." };
export default async function Page({ searchParams }: { searchParams: Promise<{ course?: string }> }) {
  const { course } = await searchParams;
  return <EnquiryView course={course} />;
}
