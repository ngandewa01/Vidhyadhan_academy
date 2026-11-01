import type { Metadata, Viewport } from "next";
import "@fontsource/mukta/400.css";
import "@fontsource/mukta/500.css";
import "@fontsource/mukta/600.css";
import "@fontsource/mukta/700.css";
import "./globals.css";
import { Hydrator } from "@/components/Hydrator";
import { Toaster } from "@/components/ui";

export const metadata: Metadata = {
  title: { default: "Vidyadhan Academy | विद्याधन अकॅडमी – Competitive Exam Coaching", template: "%s | Vidyadhan Academy" },
  description: "Competitive exam coaching in Maharashtra – MPSC Rajyaseva, PSI/STI/ASO, Police Bharti, Talathi, Combined Group B & C. Marathi and English medium.",
  applicationName: "Vidyadhan Academy",
  openGraph: { type: "website", siteName: "Vidyadhan Academy", locale: "mr_IN" },
  robots: { index: true, follow: true },
};
export const viewport: Viewport = { themeColor: "#2a3594", width: "device-width", initialScale: 1 };

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="mr">
      <body className="min-h-screen font-sans">
        <a href="#main" className="sr-only focus:not-sr-only focus:fixed focus:left-2 focus:top-2 focus:z-[200] focus:rounded focus:bg-white focus:px-3 focus:py-2">Skip to content</a>
        <Hydrator />
        {children}
        <Toaster />
      </body>
    </html>
  );
}
