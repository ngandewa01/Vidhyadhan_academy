package `in`.vidyadhan.academy.ai

import `in`.vidyadhan.academy.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** One web page Google Search used for the answer. */
data class Source(val title: String, val domain: String, val uri: String)

/** A piece of the answer text and the sources (indices into [Grounded.sources]) that back it. */
data class Support(val text: String, val sources: List<Int>)

/** Gemini's answer with what backs it: the text, the pages found by Google Search, and which sentence came from which page. */
data class Grounded(val text: String, val sources: List<Source>, val supports: List<Support>)

/**
 * Google Gemini (free plan) with Google Search grounding, called directly from the phone.
 * The key is the owner's own key from aistudio.google.com; the model is picked from the key's model list.
 */
object Gemini {
    private const val BASE = "https://generativelanguage.googleapis.com/v1beta"
    private val http = OkHttpClient.Builder().callTimeout(120, TimeUnit.SECONDS).readTimeout(120, TimeUnit.SECONDS).build()
    private val json = Json { ignoreUnknownKeys = true }

    class Problem(msg: String) : AiProblem(msg)

    private fun get(path: String, key: String): JsonObject {
        val r = http.newCall(Request.Builder().url("$BASE/$path").header("x-goog-api-key", key).build()).execute()
        r.use { val b = it.body.string(); if (!it.isSuccessful) throw Problem(errorOf(it.code, b)); return json.parseToJsonElement(b).jsonObject }
    }

    private fun messageOf(body: String): String? =
        runCatching { json.parseToJsonElement(body).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content }.getOrNull()

    private fun errorOf(code: Int, body: String): String {
        val m = messageOf(body)
        return when (code) {
            400, 403 -> if (m?.contains("API key", true) == true) "The Gemini key is not valid" else (m ?: "Gemini refused the request ($code)")
            429 -> "Gemini free limit reached. Google says: " + (m ?: "too many requests").take(300)
            else -> m ?: "Gemini error $code"
        }
    }

    /** All Gemini Flash models this key can use: Flash-Lite first (largest free allowance), newest first. */
    suspend fun models(key: String): List<String> = withContext(Dispatchers.IO) {
        fun ver(n: String) = Regex("""gemini-(\d+(?:\.\d+)?)""").find(n)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
        val all = get("models?pageSize=200", key)["models"]?.jsonArray.orEmpty().map { it.jsonObject }
            .filter { m -> m["supportedGenerationMethods"]?.jsonArray?.any { it.jsonPrimitive.content == "generateContent" } == true }
            .map { it["name"]!!.jsonPrimitive.content.removePrefix("models/") }
            .filter { n -> n.startsWith("gemini") && "flash" in n && listOf("image", "tts", "audio", "live", "thinking", "exp", "preview").none { it in n } }
        // Flash-Lite first: it has the largest free allowance; then the full Flash models, newest first
        val preferred = listOf("gemini-3.5-flash-lite").filter { it in all }
        val lite = all.filter { "lite" in it }.sortedWith(compareByDescending<String> { ver(it) }.thenBy { it.endsWith("-latest") }.thenBy { it.length })
        val full = all.filter { "lite" !in it }.sortedWith(compareByDescending<String> { ver(it) }.thenBy { it.endsWith("-latest") }.thenBy { it.length })
        (preferred + lite + full).distinct().ifEmpty { throw Problem("This key has no Gemini Flash model") }
    }

    /** Seconds Google asks us to wait (RetryInfo "retryDelay": "37s"), if any. */
    private fun retryAfter(body: String): Long? = Regex(""""retryDelay"\s*:\s*"(\d+)(?:\.\d+)?s"""").find(body)?.groupValues?.get(1)?.toLongOrNull()

    /** The model has no free allowance at all (or for the day), so waiting will not help. */
    private fun zeroQuota(body: String) = "limit: 0" in body || "PerDay" in body || "per day" in body.lowercase()

    @Volatile private var cached: Pair<String, List<String>>? = null
    /** The model list, fetched once per key while the app runs. */
    private suspend fun modelsCached(key: String): List<String> = cached?.takeIf { it.first == key }?.second ?: models(key).also { cached = key to it }

    private fun lazyModels(key: String): List<String> = kotlinx.coroutines.runBlocking { modelsCached(key) }

    /** The first model to try. */
    suspend fun pickModel(key: String): String = models(key).first()

    /** Checks the key works; returns the model it will use first. */
    suspend fun test(key: String): String = pickModel(key)

    /**
     * Asks Gemini with Google Search turned on. If a model has no free quota (or no Search grounding) it moves on
     * to the next Flash model and remembers the one that worked.
     */
    suspend fun ask(prompt: String, key: String = AppSettings.geminiKey): Grounded = withContext(Dispatchers.IO) {
        if (key.isBlank()) throw Problem("Add your Gemini key in Settings → MPSC News")
        fun body(think: Boolean) = buildJsonObject {
            putJsonArray("contents") { addJsonObject { put("role", "user"); putJsonArray("parts") { addJsonObject { put("text", prompt) } } } }
            putJsonArray("tools") { addJsonObject { putJsonObject("google_search") {} } }
            putJsonObject("generationConfig") {
                put("temperature", 0.2)
                if (!think) putJsonObject("thinkingConfig") { put("thinkingBudget", 0) } // summaries need no long thinking: much faster
            }
        }.toString()
        // the saved model first; the list is only fetched when that one does not work
        val saved = AppSettings.geminiModel
        val order = if (saved.isNotBlank()) sequence { yield(saved); yieldAll(lazyModels(key).filter { it != saved }) } else lazyModels(key).asSequence()
        var last = "Gemini did not answer"
        for (m in order.take(8)) {
            var code: Int; var b: String; var tries = 0; var think = false
            while (true) {
                val r = http.newCall(Request.Builder().url("$BASE/models/$m:generateContent").header("x-goog-api-key", key)
                    .post(body(think).toRequestBody("application/json".toMediaType())).build()).execute()
                code = r.code; b = r.use { it.body.string() }
                if (r.isSuccessful) { AppSettings.geminiModel = m; return@withContext parse(json.parseToJsonElement(b).jsonObject) }
                // a model that does not accept "no thinking": ask again the normal way
                if (code == 400 && !think && "thinking" in b.lowercase()) { think = true; continue }
                // a per-minute limit: wait as long as Google asks (at most 20 seconds), once
                val wait = retryAfter(b)
                if (code == 429 && !zeroQuota(b) && tries < 1) { tries++; kotlinx.coroutines.delay((wait ?: 10L).coerceIn(3, 20) * 1000); continue }
                break
            }
            last = "$m: " + errorOf(code, b)
            val msg = messageOf(b).orEmpty()
            // each model has its own free allowance, so a limit on one is a reason to try the next
            val tryNext = code == 404 || code == 429 || (code == 400 && ("not supported" in msg || "Search" in msg))
            if (!tryNext) break
        }
        throw Problem(last)
    }

    internal fun parse(o: JsonObject): Grounded {
        val c = o["candidates"]?.jsonArray?.firstOrNull()?.jsonObject ?: throw Problem("Gemini gave no answer")
        val text = c["content"]?.jsonObject?.get("parts")?.jsonArray.orEmpty()
            .mapNotNull { p -> p.jsonObject["text"]?.jsonPrimitive?.contentOrNull }.joinToString("")
        val g = c["groundingMetadata"]?.jsonObject
        val sources = g?.get("groundingChunks")?.jsonArray.orEmpty().map { ch ->
            val w = ch.jsonObject["web"]?.jsonObject
            val title = w?.get("title")?.jsonPrimitive?.contentOrNull.orEmpty()
            Source(title, (w?.get("domain")?.jsonPrimitive?.contentOrNull ?: title).lowercase().removePrefix("www."), w?.get("uri")?.jsonPrimitive?.contentOrNull.orEmpty())
        }
        val supports = g?.get("groundingSupports")?.jsonArray.orEmpty().mapNotNull { s ->
            val so = s.jsonObject
            val t = so["segment"]?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            Support(t, so["groundingChunkIndices"]?.jsonArray.orEmpty().map { it.jsonPrimitive.int })
        }
        return Grounded(text, sources, supports)
    }
}
