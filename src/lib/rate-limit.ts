const hits = new Map<string, number[]>();

/** Best-effort limit per server instance: `max` calls per `windowMs` per key. */
export function rateLimited(key: string, max = 5, windowMs = 10 * 60 * 1000) {
  const now = Date.now();
  const recent = (hits.get(key) ?? []).filter((t) => now - t < windowMs);
  if (recent.length >= max) { hits.set(key, recent); return true; }
  recent.push(now);
  hits.set(key, recent);
  return false;
}

export const clientKey = (req: Request) => (req.headers.get("x-forwarded-for") || "unknown").split(",")[0].trim();
