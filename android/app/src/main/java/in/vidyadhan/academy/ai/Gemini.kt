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

    class Problem(msg: String) : Exception(msg)

    private fun get(path: String, key: String): JsonObject {
        val r = http.newCall(Request.Builder().url("$BASE/$path").header("x-goog-api-key", key).build()).execute()
        r.use { val b = it.body.string(); if (!it.isSuccessful) throw Problem(errorOf(it.code, b)); return json.parseToJsonElement(b).jsonObject }
    }

    private fun errorOf(code: Int, body: String): String {
        val m = runCatching { json.parseToJsonElement(body).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content }.getOrNull()
        return when (code) {
            400, 403 -> if (m?.contains("API key", true) == true) "The Gemini key is not valid" else (m ?: "Gemini refused the request ($code)")
            429 -> "Free Gemini limit reached for now; it will try again later"
            else -> m ?: "Gemini error $code"
        }
    }

    /** The newest "Flash" model this key can use (fast, and on the free plan). */
    suspend fun pickModel(key: String): String = withContext(Dispatchers.IO) {
        val models = get("models?pageSize=200", key)["models"]?.jsonArray.orEmpty().map { it.jsonObject }
            .filter { m -> m["supportedGenerationMethods"]?.jsonArray?.any { it.jsonPrimitive.content == "generateContent" } == true }
            .map { it["name"]!!.jsonPrimitive.content.removePrefix("models/") }
            .filter { n -> n.startsWith("gemini") && "flash" in n && listOf("image", "tts", "audio", "live", "lite", "thinking", "exp").none { it in n } }
        models.firstOrNull { it == "gemini-flash-latest" }
            ?: models.maxByOrNull { n -> Regex("""gemini-(\d+(?:\.\d+)?)""").find(n)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0 }
            ?: throw Problem("This key has no Gemini Flash model")
    }

    /** Checks the key works; returns the model it will use. */
    suspend fun test(key: String): String = pickModel(key)

    /** Asks Gemini with Google Search turned on. */
    suspend fun ask(prompt: String): Grounded = withContext(Dispatchers.IO) {
        val key = AppSettings.geminiKey
        if (key.isBlank()) throw Problem("Add your Gemini key in Settings → MPSC News")
        var model = AppSettings.geminiModel.ifBlank { pickModel(key).also { AppSettings.geminiModel = it } }
        val body = buildJsonObject {
            putJsonArray("contents") { addJsonObject { put("role", "user"); putJsonArray("parts") { addJsonObject { put("text", prompt) } } } }
            putJsonArray("tools") { addJsonObject { putJsonObject("google_search") {} } }
            putJsonObject("generationConfig") { put("temperature", 0.2) }
        }.toString()
        fun call(m: String) = http.newCall(Request.Builder().url("$BASE/models/$m:generateContent").header("x-goog-api-key", key)
            .post(body.toRequestBody("application/json".toMediaType())).build()).execute()
        var r = call(model)
        if (r.code == 404) { r.close(); model = pickModel(key).also { AppSettings.geminiModel = it }; r = call(model) } // the model was retired
        r.use {
            val b = it.body.string()
            if (!it.isSuccessful) throw Problem(errorOf(it.code, b))
            parse(json.parseToJsonElement(b).jsonObject)
        }
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
