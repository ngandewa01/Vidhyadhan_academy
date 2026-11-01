import type { Metadata } from "next";
import { GalleryView } from "@/views/public-pages";
export const metadata: Metadata = { title: "Gallery", description: "Photo gallery." };
export default function Page() { return <GalleryView />; }
