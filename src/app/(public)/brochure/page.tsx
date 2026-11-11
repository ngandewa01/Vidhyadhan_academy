import type { Metadata } from "next";
import { BrochureView } from "@/views/brochure";

export const metadata: Metadata = {
  title: "Download Brochure",
  description: "Download the Vidyadhan Academy brochure for MPSC, PSI, STI and ASO coaching in Hadapsar, Pune.",
};
export default function Page() { return <BrochureView />; }
