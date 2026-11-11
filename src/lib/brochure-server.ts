import { createHmac, randomBytes, timingSafeEqual } from "node:crypto";
import { existsSync } from "node:fs";
import { readFile } from "node:fs/promises";
import path from "node:path";

/** Server-only helpers for the locked brochure download. Never import this from client code. */

export const TOKEN_TTL_SEC = 600;
const LOCAL_FILE = path.join(process.cwd(), "private", "brochure.pdf");

const secret = () => process.env.BROCHURE_LINK_SECRET || "";
const sourceUrl = () => process.env.BROCHURE_FILE_URL || "";

export function isConfigured() {
  return secret().length >= 16 && (!!sourceUrl() || existsSync(LOCAL_FILE));
}

export function signToken(ttlSec = TOKEN_TTL_SEC) {
  const payload = Buffer.from(JSON.stringify({ exp: Date.now() + ttlSec * 1000, n: randomBytes(8).toString("hex") })).toString("base64url");
  const sig = createHmac("sha256", secret()).update(payload).digest("base64url");
  return `${payload}.${sig}`;
}

export function verifyToken(token: string) {
  if (secret().length < 16) return false;
  const [payload, sig] = token.split(".");
  if (!payload || !sig) return false;
  const expected = createHmac("sha256", secret()).update(payload).digest();
  const given = Buffer.from(sig, "base64url");
  if (given.length !== expected.length || !timingSafeEqual(given, expected)) return false;
  try {
    const { exp } = JSON.parse(Buffer.from(payload, "base64url").toString());
    return typeof exp === "number" && exp > Date.now();
  } catch {
    return false;
  }
}

const hits = new Map<string, number[]>();
/** Best-effort limit per server instance: 5 link requests per 10 minutes per client. */
export function rateLimited(key: string, max = 5, windowMs = 10 * 60 * 1000) {
  const now = Date.now();
  const recent = (hits.get(key) ?? []).filter((t) => now - t < windowMs);
  if (recent.length >= max) { hits.set(key, recent); return true; }
  recent.push(now);
  hits.set(key, recent);
  return false;
}

const EXT: Record<string, string> = { "application/pdf": "pdf", "image/jpeg": "jpg", "image/png": "png" };

export async function loadBrochure(): Promise<{ body: BodyInit; type: string; filename: string } | null> {
  const url = sourceUrl();
  if (url) {
    const res = await fetch(url, { cache: "no-store" });
    if (!res.ok || !res.body) return null;
    const type = (res.headers.get("content-type") || "application/pdf").split(";")[0].trim();
    return { body: res.body, type, filename: `Vidyadhan-Academy-Brochure.${EXT[type] ?? "pdf"}` };
  }
  if (existsSync(LOCAL_FILE)) {
    const buf = await readFile(LOCAL_FILE);
    return { body: new Uint8Array(buf), type: "application/pdf", filename: "Vidyadhan-Academy-Brochure.pdf" };
  }
  return null;
}
