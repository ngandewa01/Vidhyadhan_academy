import { isConfigured, loadBrochure, verifyToken } from "@/lib/brochure-server";

export const dynamic = "force-dynamic";

const text = (msg: string, status: number) => new Response(msg, { status, headers: { "Content-Type": "text/plain; charset=utf-8", "Cache-Control": "no-store" } });

export async function GET(req: Request) {
  const token = new URL(req.url).searchParams.get("t") ?? "";
  if (!isConfigured()) return text("Brochure is not available yet.", 404);
  if (!verifyToken(token)) return text("This download link is invalid or has expired. Please request the brochure again.", 403);
  const file = await loadBrochure();
  if (!file) return text("Brochure is not available right now.", 502);
  return new Response(file.body, {
    headers: {
      "Content-Type": file.type,
      "Content-Disposition": `attachment; filename="${file.filename}"`,
      "Cache-Control": "no-store",
      "X-Robots-Tag": "noindex, nofollow",
    },
  });
}
