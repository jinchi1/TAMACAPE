import {validateRequest, generationRequest} from "./catalog.mjs";
import {parseLyrics, musicPayload} from "./song.mjs";
export {FamilyBudget} from "./budget.mjs";

const json = (status, body) => new Response(JSON.stringify(body), {
  status, headers:{"Content-Type":"application/json; charset=utf-8","Cache-Control":"no-store"}
});
async function readLimited(stream, limit, binary = false) {
  if (!stream) throw Error("invalid_request");
  const reader = stream.getReader();
  const chunks = [];
  let size = 0;
  try {
    while (true) {
      const {done, value} = await reader.read();
      if (done) break;
      size += value.byteLength;
      if (size > limit) { await reader.cancel(); throw Error("too_large"); }
      chunks.push(value);
    }
  } finally { reader.releaseLock(); }
  const bytes = new Uint8Array(size);
  let offset = 0;
  for (const chunk of chunks) { bytes.set(chunk, offset); offset += chunk.byteLength; }
  return binary ? bytes : new TextDecoder().decode(bytes);
}
async function authenticated(header, secret) {
  if (!header || header.length > 256) return false;
  const digest = async value => new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value)));
  const a = await digest(header);
  const b = await digest("Bearer " + secret);
  let diff = 0;
  for (let i=0; i<a.length; i++) diff |= a[i] ^ b[i];
  return diff === 0;
}
async function previewSignature(body, lyrics, secret) {
  const encoder = new TextEncoder();
  const key = await crypto.subtle.importKey("raw", encoder.encode(secret),
    {name:"HMAC",hash:"SHA-256"}, false, ["sign"]);
  const context = JSON.stringify([body.stage,body.companion,body.age,body.style,body.topic,lyrics]);
  return Array.from(new Uint8Array(await crypto.subtle.sign("HMAC",key,encoder.encode(context))),
    byte=>byte.toString(16).padStart(2,"0")).join("");
}
export async function handle(request, env, fetcher = fetch) {
  if (new URL(request.url).pathname !== "/v1/family") return json(404,{error:"not_found"});
  if (request.method !== "POST") return json(405,{error:"method_not_allowed"});
  const expires = Date.parse(env.ACCESS_EXPIRES_AT || "");
  if (!env.OPENAI_API_KEY || !env.OPENAI_MODEL || !env.AI_LIMITER || !env.FAMILY_BUDGET ||
      !env.APP_ACCESS_TOKEN || env.APP_ACCESS_TOKEN.length < 32 || !Number.isFinite(expires))
    return json(503,{error:"not_configured"});
  if (Date.now() >= expires) return json(401,{error:"access_expired"});
  if (!await authenticated(request.headers.get("Authorization"), env.APP_ACCESS_TOKEN))
    return json(401,{error:"unauthorized"});
  if (!request.headers.get("Content-Type")?.startsWith("application/json"))
    return json(415,{error:"invalid_content_type"});
  let body;
  try { body = validateRequest(JSON.parse(await readLimited(request.body,4096))); }
  catch { return json(400,{error:"invalid_request"}); }
  if (body.kind === "song" && (!env.ElevenLabs_API || env.MUSIC_ENABLED !== "true"))
    return json(503,{error:"music_not_configured"});
  let approvedLyrics;
  if (body.kind === "song") {
    try {
      if (typeof body.preview !== "string" || body.preview.length > 1200) throw Error();
      approvedLyrics = parseLyrics(body.preview);
      const expected = await previewSignature(body, approvedLyrics, env.OPENAI_API_KEY);
      if (typeof body.signature !== "string" || !await authenticated("Bearer " + body.signature, expected))
        throw Error();
    } catch { return json(400,{error:"invalid_preview"}); }
  }
  try {
    // Shared demo token: total per-location quota, not a user identity or billing cap.
    if (!(await env.AI_LIMITER.limit({key:"family-demo"})).success)
      return json(429,{error:"rate_limited"});
    const budget = env.FAMILY_BUDGET.get(env.FAMILY_BUDGET.idFromName("family-global"));
    const budgetResponse = await budget.fetch("https://budget/check", {method:"POST"});
    if (!budgetResponse.ok) throw Error("temporarily_unavailable");
    const decision = await budgetResponse.json();
    if (decision.allowed !== true)
      return json(429,{error:decision.reason === "daily_limit" ? "daily_limit" : "rate_limited"});
    if (body.kind === "song") {
      const dedup = await budget.fetch("https://budget/song", {method:"POST",
        body:JSON.stringify([body.stage,body.companion,body.age,body.style,body.topic])});
      if (!dedup.ok) return json(dedup.status === 429 ? 429 : 503,{error:"music_limit"});
    }
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(),body.kind === "song" ? 110000 : 25000);
    const abort = () => controller.abort();
    request.signal.addEventListener("abort",abort,{once:true});
    async function api(path, payload) {
      const response = await fetcher("https://api.openai.com/v1/" + path, {
        method:"POST", signal:controller.signal,
        headers:{"Authorization":"Bearer " + env.OPENAI_API_KEY,"Content-Type":"application/json"},
        body:JSON.stringify(payload)
      });
      if (!response.ok) throw Error({
        400:"provider_request_rejected", 401:"provider_auth_failed",
        404:"provider_model_unavailable", 429:"provider_rate_limit_or_quota"
      }[response.status] || "temporarily_unavailable");
      return JSON.parse(await readLimited(response.body,65536));
    }
    async function permitted(text) {
      const result = await api("moderations",{model:"omni-moderation-latest",input:text});
      return Array.isArray(result.results) && result.results.length > 0 &&
        result.results.every(item => item.flagged === false);
    }
    try {
      if (body.kind === "song") {
        const lyrics = approvedLyrics;
        let audio;
        try {
          audio = await fetcher("https://api.elevenlabs.io/v1/music?output_format=mp3_44100_128",{
          method:"POST", signal:controller.signal, redirect:"manual",
          headers:{"xi-api-key":env.ElevenLabs_API,"Content-Type":"application/json"},
            body:JSON.stringify(musicPayload(lyrics,body.style))
          });
        } catch {
          throw Error(controller.signal.aborted ? "music_interrupted" : "music_connection_failed");
        }
        if (!audio.ok) throw Error({
          400:"music_request_rejected", 401:"music_access_denied", 403:"music_access_denied",
          422:"music_request_rejected", 429:"music_quota_or_rate_limit"
        }[audio.status] || "music_provider_failed");
        if (!audio.headers.get("Content-Type")?.startsWith("audio/mpeg"))
          throw Error("music_unexpected_format");
        let bytes;
        try {
          bytes = await readLimited(audio.body, 1000000, true);
        } catch (error) {
          throw Error(error?.message === "too_large" ? "invalid_audio" : "music_download_failed");
        }
        if (bytes.length < 1024) throw Error("invalid_audio");
        let encoded = "";
        for (let i=0;i<bytes.length;i+=8192) encoded += String.fromCharCode(...bytes.subarray(i,i+8192));
        return json(200,{version:1,kind:"song",source:"ai",text:JSON.stringify(lyrics),audio:btoa(encoded)});
      }
      if (body.answer && !await permitted(body.answer)) return json(422,{error:"try_family_topic"});
      const result = await api("responses",generationRequest(body,env.OPENAI_MODEL));
      if (result.status !== "completed") throw Error("incomplete_response");
      const messages = result.output?.filter(item=>item.type === "message");
      if (messages?.some(item=>item.content?.some(part=>part.type === "refusal")))
        return json(422,{error:"try_family_topic"});
      const text = messages?.flatMap(item=>item.content || [])
        .filter(item=>item.type === "output_text").map(item=>item.text).join("\n").trim();
      if (!text || text.length > (body.kind === "coach" ? 350 : 1200) || /https?:\/\//i.test(text)) throw Error("invalid_response");
      if (!await permitted(text)) return json(422,{error:"try_family_topic"});
      if (body.kind === "lyrics") {
        const lyrics = parseLyrics(text);
        return json(200,{version:1,kind:"lyrics",source:"ai",text:JSON.stringify(lyrics),
          signature:await previewSignature(body, lyrics, env.OPENAI_API_KEY)});
      }
      return json(200,{version:1,kind:body.kind,text,source:"ai"});
    } finally {
      clearTimeout(timer);
      request.signal.removeEventListener("abort",abort);
    }
  } catch (error) {
    // Only fixed diagnostic categories; never forward provider bodies or credentials.
    const safeCodes = ["provider_request_rejected","provider_auth_failed",
      "provider_model_unavailable","provider_rate_limit_or_quota",
      "music_request_rejected","music_access_denied","music_quota_or_rate_limit",
      "music_provider_failed","music_unexpected_format","invalid_audio",
      "music_interrupted","music_connection_failed","music_download_failed"];
    return json(503,{error:safeCodes.includes(error?.message) ? error.message : "temporarily_unavailable"});
  }
}
export default {fetch: (request,env) => handle(request,env)};
