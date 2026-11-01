import type { Metadata } from "next";
import { ResourcesView } from "@/views/public-pages";
export const metadata: Metadata = { title: "Important Exam Links", description: "Official websites of examination authorities." };
export default function Page() { return <ResourcesView />; }
