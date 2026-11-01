import type { Metadata } from "next";
import { buildSeed } from "@/lib/seed";
import { CourseDetailView } from "@/views/public-pages";

export function generateStaticParams() {
  return buildSeed().courses.map((c) => ({ slug: c.slug }));
}
export async function generateMetadata({ params }: { params: Promise<{ slug: string }> }): Promise<Metadata> {
  const { slug } = await params;
  const c = buildSeed().courses.find((x) => x.slug === slug);
  return c ? { title: { absolute: c.seo.title }, description: c.seo.description } : { title: "Course" };
}
export default async function Page({ params }: { params: Promise<{ slug: string }> }) {
  const { slug } = await params;
  return <CourseDetailView slug={slug} />;
}
