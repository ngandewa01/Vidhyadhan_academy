package `in`.vidyadhan.academy.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * OpenAI (GPT) with its web search tool, through the Responses API, called from the phone with the owner's key.
 */
object Gpt {
    /** Check the exact model name on platform.openai.com before the first real use. */
    const val MODEL = "gpt-5.5"
    private const val URL = "https://api.openai.com/v1/responses"

    private val http = OkHttpClient.Builder().callTimeout(120, TimeUnit.SECONDS).readTimeout(120, TimeUnit.SECONDS).build()
    private val json = Json { ignoreUnknownKeys = true }

    /** Asks GPT to search the trusted sites and answer. Each text piece keeps the pages it cites. */
    suspend fun ask(prompt: String, key: String): Grounded = withContext(Dispatchers.IO) {
        if (key.isBlank()) throw AiProblem("Add your GPT key in Settings → MPSC News → API keys")
        val body = buildJsonObject {
            put("model", MODEL)
            put("input", prompt)
            put("max_output_tokens", 2000)
            putJsonArray("tools") {
                addJsonObject {
                    put("type", "web_search")
                    putJsonObject("filters") { putJsonArray("allowed_domains") { Claude.DOMAINS.forEach { add(it) } } }
                }
            }
        }
        val r = http.newCall(Request.Builder().url(URL).header("Authorization", "Bearer $key")
            .post(body.toString().toRequestBody("application/json".toMediaType())).build()).execute()
        val text = r.use { it.body.string() }
        if (!r.isSuccessful) throw AiProblem(errorOf(r.code, text))
        parse(json.parseToJsonElement(text).jsonObject)
    }

    private fun errorOf(code: Int, body: String): String {
        val m = runCatching { json.parseToJsonElement(body).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content }.getOrNull()
        return when {
            code == 401 || code == 403 -> "That GPT key is not valid, or it may not use web search"
            code == 429 -> "GPT limit reached for now. ${m.orEmpty()}".trim()
            m?.contains("quota", true) == true || m?.contains("billing", true) == true -> "No GPT credit left. Add billing at platform.openai.com"
            code >= 500 -> "GPT is busy, try again later"
            else -> m ?: "GPT error $code"
        }
    }

    private fun hostOf(url: String) = runCatching { URI(url).host.orEmpty() }.getOrDefault("").lowercase().removePrefix("www.")

    /** Reads a Responses API answer: url citations become sources; text that has citations counts as supported. */
    internal fun parse(o: JsonObject): Grounded {
        val sources = mutableListOf<Source>()
        fun sourceOf(url: String, title: String): Int {
            val i = sources.indexOfFirst { it.uri == url }
            if (i >= 0) return i
            sources += Source(title.ifBlank { hostOf(url) }, hostOf(url), url)
            return sources.lastIndex
        }
        val full = StringBuilder()
        val supports = mutableListOf<Support>()
        for (item in o["output"]?.jsonArray.orEmpty()) {
            val io = item.jsonObject
            if (io["type"]?.jsonPrimitive?.contentOrNull != "message") continue
            for (c in io["content"]?.jsonArray.orEmpty()) {
                val co = c.jsonObject
                if (co["type"]?.jsonPrimitive?.contentOrNull != "output_text") continue
                val t = co["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
                full.append(t)
                val cited = co["annotations"]?.jsonArray.orEmpty().mapNotNull { a ->
                    val ao = a.jsonObject
                    if (ao["type"]?.jsonPrimitive?.contentOrNull != "url_citation") return@mapNotNull null
                    val url = ao["url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    sourceOf(url, ao["title"]?.jsonPrimitive?.contentOrNull.orEmpty())
                }.distinct()
                if (cited.isNotEmpty()) supports += Support(t, cited)
            }
        }
        return Grounded(full.toString(), sources, supports)
    }
}
