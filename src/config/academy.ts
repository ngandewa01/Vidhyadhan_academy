/**
 * ACADEMY CONFIGURATION – single source of truth for real-world details.
 *
 * Every value marked PLACEHOLDER must be replaced with genuine academy information
 * before going live. Do not publish invented addresses, numbers or affiliations.
 */
export const academy = {
  name: { en: "Vidyadhan Academy", mr: "विद्याधन अकॅडमी" },
  tagline: {
    en: "Focused coaching for Maharashtra competitive examinations",
    mr: "महाराष्ट्रातील स्पर्धा परीक्षांसाठी लक्ष्यकेंद्रित मार्गदर्शन",
  },
  // PLACEHOLDER – replace with the real address
  address: {
    en: "[Academy address – to be provided], Maharashtra",
    mr: "[अकॅडमीचा पत्ता – लवकरच], महाराष्ट्र",
  },
  // PLACEHOLDER – replace with real numbers (E.164 without + for wa.me)
  phone: "+91 XXXXX XXXXX",
  whatsapp: "910000000000",
  email: "info@example.com",
  hours: { en: "Mon–Sat, 8:00 AM – 8:00 PM", mr: "सोम–शनि, सकाळी ८ – रात्री ८" },
  mapEmbedUrl: "", // PLACEHOLDER – Google Maps embed URL
  social: {
    youtube: "",
    instagram: "",
    telegram: "",
    facebook: "",
  },
  gstin: "", // optional; GST is disabled by default
  logoUrl: "", // PLACEHOLDER – empty shows the text monogram
  branchCode: "MAIN",
} as const;

export const isPlaceholder = (v: string) => !v || v.includes("XXXXX") || v.includes("[") || v.includes("example.com") || v === "910000000000";
