/**
 * Google reviews via the official Google Places API (New) – no scraping.
 *
 * Configure in Vercel → Project → Settings → Environment Variables:
 *   GOOGLE_PLACES_API_KEY   – API key with "Places API (New)" enabled (restrict it to this API)
 *   GOOGLE_PLACE_ID         – the academy's Place ID (Google Business Profile)
 *
 * Notes: Google returns at most 5 reviews (chosen by Google). Showing all reviews requires the
 * Google Business Profile API with owner OAuth. Attribution (author name/photo/link + Google) must be shown.
 */
import { NextResponse } from "next/server";

export const dynamic = "force-dynamic";
const CACHE_SECONDS = 21600; // Google response cached for 6 hours

type PlaceReview = {
  rating?: number;
  text?: { text?: string; languageCode?: string };
  originalText?: { text?: string; languageCode?: string };
  relativePublishTimeDescription?: string;
  publishTime?: string;
  googleMapsUri?: string;
  authorAttribution?: { displayName?: string; uri?: string; photoUri?: string };
};

export async function GET(req: Request) {
  const key = process.env.GOOGLE_PLACES_API_KEY;
  const placeId = process.env.GOOGLE_PLACE_ID;
  if (!key || !placeId) return NextResponse.json({ configured: false });

  const lang = new URL(req.url).searchParams.get("lang") === "en" ? "en" : "mr";
  try {
    const res = await fetch(`https://places.googleapis.com/v1/places/${encodeURIComponent(placeId)}?languageCode=${lang}`, {
      headers: { "X-Goog-Api-Key": key, "X-Goog-FieldMask": "displayName,rating,userRatingCount,googleMapsUri,reviews" },
      next: { revalidate: CACHE_SECONDS },
    });
    if (!res.ok) return NextResponse.json({ configured: true, error: `Google API error ${res.status}` }, { status: 502 });
    const p = await res.json();
    return NextResponse.json({
      configured: true,
      name: p.displayName?.text ?? "",
      rating: p.rating ?? null,
      total: p.userRatingCount ?? 0,
      mapsUrl: p.googleMapsUri ?? "",
      writeReviewUrl: `https://search.google.com/local/writereview?placeid=${encodeURIComponent(placeId)}`,
      reviews: ((p.reviews ?? []) as PlaceReview[]).map((r) => ({
        author: r.authorAttribution?.displayName ?? "Google user",
        authorUrl: r.authorAttribution?.uri ?? "",
        photo: r.authorAttribution?.photoUri ?? "",
        rating: r.rating ?? 0,
        text: r.text?.text ?? r.originalText?.text ?? "",
        when: r.relativePublishTimeDescription ?? "",
        url: r.googleMapsUri ?? "",
      })),
    });
  } catch {
    return NextResponse.json({ configured: true, error: "Could not reach Google" }, { status: 502 });
  }
}
