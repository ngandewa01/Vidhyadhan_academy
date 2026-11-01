/**
 * ACADEMY CONFIGURATION – single source of truth for real-world details.
 *
 * Values marked PLACEHOLDER still need genuine academy information.
 * Do not publish invented addresses, numbers or affiliations.
 */
export const academy = {
  name: { en: "Vidyadhan Academy", mr: "विद्याधन अकॅडमी" },
  founder: { en: "Nikhil Rudrawar", mr: "निखिल रुद्रवार" },
  focus: "MPSC | PSI | STI | ASO",
  tagline: {
    en: "Not just a class — a family.",
    mr: "क्लास नव्हे, एक कुटुंबच...",
  },
  motto: {
    en: "Your dream… our direction.",
    mr: "स्वप्न तुमचे... दिशा आमची",
  },
  quote: {
    text: "ओले मुळ भेदी खडकाचे अंग, अभ्यासाशी सांग कार्यसिद्धी",
    author: { en: "Sant Tukaram", mr: "संत तुकाराम" },
  },
  // TODO: add building / street / landmark / PIN when available
  address: {
    en: "Hadapsar, Pune, Maharashtra",
    mr: "हडपसर, पुणे, महाराष्ट्र",
  },
  phone: "+91 84840 04737",
  // Assumed to be the same number on WhatsApp – change if different (country code, no +)
  whatsapp: "918484004737",
  email: "info@example.com", // PLACEHOLDER
  hours: { en: "Mon–Sat, 8:00 AM – 8:00 PM", mr: "सोम–शनि, सकाळी ८ – रात्री ८" }, // PLACEHOLDER
  mapEmbedUrl: "https://maps.google.com/maps?q=Vidhyadhan%20Academy%20MPSC%20Hadapsar%20Pune&output=embed",
  /**
   * Google Business Profile link (the "Share" link from Google Maps), used for
   * "See all reviews on Google" / "Write a review" buttons. Reviews themselves are
   * fetched server-side when GOOGLE_PLACES_API_KEY and GOOGLE_PLACE_ID are set.
   */
  googleMapsUrl: "https://share.google/VwVBzYt5DnkekqyiU",
  social: {
    youtube: "",
    instagram: "",
    telegram: "",
    facebook: "",
  },
  gstin: "", // optional; GST is disabled by default
  logoUrl: "/brand/logo.png",
  bannerUrl: "/brand/banner.jpg",
  branchCode: "MAIN",
} as const;

export const isPlaceholder = (v: string) => !v || v.includes("XXXXX") || v.includes("[") || v.includes("example.com") || v === "910000000000";
