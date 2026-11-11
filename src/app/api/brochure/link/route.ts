import { TOKEN_TTL_SEC, isConfigured, rateLimited, signToken } from "@/lib/brochure-server";

export const dynamic = "force-dynamic";

const noStore = { "Cache-Control": "no-store" };

export async function POST(req: Request) {
  let body: { name?: unknown; mobile?: unknown; consent?: unknown };
  try {
    body = await req.json();
  } catch {
    return Response.json({ error: "bad_request" }, { status: 400, headers: noStore });
  }
  const name = String(body.name ?? "").trim();
  const mobile = String(body.mobile ?? "").replace(/\D/g, "").slice(-10);
  if (name.length < 2 || name.length > 80 || !/^[6-9]\d{9}$/.test(mobile) || body.consent !== true) {
    return Response.json({ error: "invalid" }, { status: 400, headers: noStore });
  }
  const client = (req.headers.get("x-forwarded-for") || "unknown").split(",")[0].trim();
  if (rateLimited(client)) return Response.json({ error: "rate_limited" }, { status: 429, headers: noStore });
  if (!isConfigured()) return Response.json({ configured: false }, { headers: noStore });
  return Response.json({ configured: true, url: `/api/brochure/file?t=${signToken()}`, expiresInSec: TOKEN_TTL_SEC }, { headers: noStore });
}
