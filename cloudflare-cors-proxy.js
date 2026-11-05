/**
 * Nuvio Web - Proxy CORS per Cloudflare Workers
 *
 * Installazione:
 *   1. dash.cloudflare.com -> Workers & Pages -> Create -> Create Worker
 *   2. "Edit code", incolla TUTTO questo file al posto del codice di esempio, "Deploy"
 *   3. (Consigliato) Settings -> Variables and Secrets:
 *        ALLOWED_ORIGINS = https://<utente>.github.io            (separati da virgola, opzionale)
 *        PROXY_TOKEN     = una stringa segreta a scelta           (opzionale)
 *
 * Uso:
 *   https://<worker>.workers.dev/?url=<URL codificato>
 *   https://<worker>.workers.dev/?url=<URL codificato>&token=<PROXY_TOKEN>
 *
 * Supporta GET/HEAD/POST/PUT/PATCH/DELETE, preflight OPTIONS, richieste Range
 * (seek nei video) e redirect. Gli URL interni nelle playlist HLS (.m3u8)
 * vengono riscritti per passare anch'essi dal proxy.
 */

const HOP_BY_HOP = [
  "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
  "te", "trailer", "transfer-encoding", "upgrade", "host", "origin", "referer",
  "cf-connecting-ip", "cf-ipcountry", "cf-ray", "cf-visitor", "x-forwarded-for",
  "x-forwarded-proto", "x-real-ip",
];

const EXPOSED_HEADERS = [
  "Content-Length", "Content-Range", "Content-Type", "Accept-Ranges",
  "ETag", "Last-Modified", "X-Proxy-Final-Url",
].join(", ");

export default {
  async fetch(request, env) {
    const origin = request.headers.get("Origin") || "";
    const allowedOrigins = (env.ALLOWED_ORIGINS || "")
      .split(",").map((o) => o.trim()).filter(Boolean);
    const originAllowed = allowedOrigins.length === 0 || allowedOrigins.includes(origin);
    const allowOrigin = allowedOrigins.length === 0 ? "*" : (originAllowed ? origin : "null");

    const cors = {
      "Access-Control-Allow-Origin": allowOrigin,
      "Access-Control-Allow-Methods": "GET, HEAD, POST, PUT, PATCH, DELETE, OPTIONS",
      "Access-Control-Allow-Headers": request.headers.get("Access-Control-Request-Headers") || "*",
      "Access-Control-Expose-Headers": EXPOSED_HEADERS,
      "Access-Control-Max-Age": "86400",
      "Vary": "Origin",
    };

    if (request.method === "OPTIONS") {
      return new Response(null, { status: 204, headers: cors });
    }

    const reqUrl = new URL(request.url);
    const fail = (status, message) =>
      new Response(JSON.stringify({ error: message }), {
        status,
        headers: { ...cors, "Content-Type": "application/json" },
      });

    if (!originAllowed) return fail(403, "Origin non consentita");
    if (env.PROXY_TOKEN && reqUrl.searchParams.get("token") !== env.PROXY_TOKEN) {
      return fail(401, "Token mancante o non valido");
    }

    const target = reqUrl.searchParams.get("url");
    if (!target) return fail(400, "Parametro 'url' mancante");

    let targetUrl;
    try {
      targetUrl = new URL(target);
    } catch {
      return fail(400, "URL non valido");
    }
    if (targetUrl.protocol !== "http:" && targetUrl.protocol !== "https:") {
      return fail(400, "Sono supportati solo http e https");
    }

    // Copia gli header della richiesta, togliendo quelli di trasporto/Cloudflare.
    const headers = new Headers();
    for (const [key, value] of request.headers) {
      if (!HOP_BY_HOP.includes(key.toLowerCase())) headers.set(key, value);
    }
    // Header personalizzati passati come x-proxy-<nome> (es. x-proxy-referer).
    for (const [key, value] of request.headers) {
      const lower = key.toLowerCase();
      if (lower.startsWith("x-proxy-")) {
        headers.delete(key);
        headers.set(lower.substring("x-proxy-".length), value);
      }
    }

    let upstream;
    try {
      upstream = await fetch(targetUrl.toString(), {
        method: request.method,
        headers,
        body: ["GET", "HEAD"].includes(request.method) ? undefined : request.body,
        redirect: "follow",
      });
    } catch (error) {
      return fail(502, "Errore di rete verso l'origine: " + error.message);
    }

    const responseHeaders = new Headers(upstream.headers);
    for (const [key, value] of Object.entries(cors)) responseHeaders.set(key, value);
    responseHeaders.set("X-Proxy-Final-Url", upstream.url);
    responseHeaders.delete("Content-Security-Policy");
    responseHeaders.delete("X-Frame-Options");
    responseHeaders.delete("Set-Cookie");

    // Riscrive le playlist HLS perché anche segmenti e sotto-playlist passino dal proxy.
    const contentType = (upstream.headers.get("Content-Type") || "").toLowerCase();
    const isPlaylist = contentType.includes("mpegurl") || new URL(upstream.url).pathname.endsWith(".m3u8");
    if (isPlaylist && request.method === "GET" && upstream.ok) {
      const text = await upstream.text();
      const base = upstream.url;
      const token = reqUrl.searchParams.get("token");
      const proxify = (u) => {
        const absolute = new URL(u, base).toString();
        let out = `${reqUrl.origin}/?url=${encodeURIComponent(absolute)}`;
        if (token) out += `&token=${encodeURIComponent(token)}`;
        return out;
      };
      const rewritten = text.split("\n").map((line) => {
        const trimmed = line.trim();
        if (!trimmed) return line;
        if (trimmed.startsWith("#")) {
          return line.replace(/URI="([^"]+)"/g, (_, u) => `URI="${proxify(u)}"`);
        }
        return proxify(trimmed);
      }).join("\n");
      responseHeaders.delete("Content-Length");
      responseHeaders.delete("Content-Encoding");
      return new Response(rewritten, { status: upstream.status, headers: responseHeaders });
    }

    return new Response(upstream.body, {
      status: upstream.status,
      statusText: upstream.statusText,
      headers: responseHeaders,
    });
  },
};
