import type { Metadata, Viewport } from "next";
import "@fontsource/mukta/400.css";
import "@fontsource/mukta/600.css";
import "./globals.css";
import { Hydrator } from "@/components/Hydrator";
import { Toaster } from "@/components/ui";
import { academy } from "@/config/academy";

const jsonLd = {
  "@context": "https://schema.org",
  "@type": "EducationalOrganization",
  name: academy.name.en,
  alternateName: academy.name.mr,
  founder: { "@type": "Person", name: academy.founder.en },
  slogan: academy.tagline.mr,
  telephone: academy.phone.replace(/\s/g, ""),
  logo: academy.logoUrl,
  address: { "@type": "PostalAddress", addressLocality: "Hadapsar, Pune", addressRegion: "Maharashtra", addressCountry: "IN" },
  sameAs: [academy.googleMapsUrl].filter(Boolean),
};

export const metadata: Metadata = {
  title: { default: "Vidyadhan Academy, Hadapsar Pune | विद्याधन अकॅडमी – MPSC PSI STI ASO Coaching", template: "%s | Vidyadhan Academy" },
  description: "Nikhil Rudrawar's Vidyadhan Academy, Hadapsar, Pune – MPSC, PSI, STI, ASO and Police Bharti coaching in Marathi and English medium.",
  applicationName: "Vidyadhan Academy",
  openGraph: { type: "website", siteName: "Vidyadhan Academy", locale: "mr_IN" },
  robots: { index: true, follow: true },
};
export const viewport: Viewport = { themeColor: "#0b5583", width: "device-width", initialScale: 1 };

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="mr">
      <body className="min-h-screen font-sans">
        <a href="#main" className="sr-only focus:not-sr-only focus:fixed focus:left-2 focus:top-2 focus:z-[200] focus:rounded focus:bg-white focus:px-3 focus:py-2">Skip to content</a>
        <script type="application/ld+json" dangerouslySetInnerHTML={{ __html: JSON.stringify(jsonLd) }} />
        <Hydrator />
        {children}
        <Toaster />
      </body>
    </html>
  );
}
