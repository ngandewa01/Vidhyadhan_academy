import { isConfigured } from "@/lib/brochure-server";

export const dynamic = "force-dynamic";

export function GET() {
  return Response.json({ configured: isConfigured() }, { headers: { "Cache-Control": "no-store" } });
}
