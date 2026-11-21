package `in`.vidyadhan.academy.ai

import `in`.vidyadhan.academy.AppSettings
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
 * Claude with its web search tool, called from the phone with the owner's key.
 * Raw HTTP on the app's own OkHttp and kotlinx.serialization stack: one endpoint, and the citation
 * fields are read exactly as the Messages API returns them.
 */
object Claude {
    /** The cheapest current model. Web search uses the basic tool version, which this model supports. */
    const val MODEL = "claude-haiku-5-5"
    private const val URL = "https://api.anthropic.com/v1/messages"
    private const val VERSION = "2023-06-01"
    /** Answers are searched only on these sites (government and trusted newspapers). */
    val DOMAINS = listOf("mpsc.gov.in", "mpsconline.gov.in", "maharashtra.gov.in", "gov.in", "nic.in", "pib.gov.in", "rbi.org.in",
        "loksatta.com", "lokmat.com", "maharashtratimes.com", "esakal.com", "thehindu.com", "indianexpress.com",
        "timesofindia.indiatimes.com", "hindustantimes.com", "livemint.com", "unfccc.int", "un.org")
    /** Limits per request, to keep the cost small. */
    const val MAX_SEARCHES = 3
    const val MAX_TOKENS = 2000

    class Problem(msg: String) : Exception(msg)

    private val http = OkHttpClient.Builder().callTimeout(120, TimeUnit.SECONDS).readTimeout(120, TimeUnit.SECONDS).build()
    private val json = Json { ignoreUnknownKeys = true }

    /** Asks Claude to search the trusted sites and answer. Each text piece keeps the pages it cites. */
    suspend fun ask(prompt: String, key: String = AppSettings.claudeKey, searches: Int = MAX_SEARCHES): Grounded = withContext(Dispatchers.IO) {
        if (key.isBlank()) throw Problem("Add your Claude key in Settings → MPSC News")
        val messages = mutableListOf(buildJsonObject { put("role", "user"); put("content", prompt) })
        var last: JsonObject? = null
        repeat(3) {
            val body = buildJsonObject {
                put("model", MODEL)
                put("max_tokens", MAX_TOKENS)
                putJsonArray("messages") { messages.forEach { add(it) } }
                putJsonArray("tools") {
                    addJsonObject {
                        put("type", "web_search_20250305")
                        put("name", "web_search")
                        put("max_uses", searches)
                        putJsonArray("allowed_domains") { DOMAINS.forEach { add(it) } }
                    }
                }
            }
            val o = send(key, body)
            last = o
            if (o["stop_reason"]?.jsonPrimitive?.contentOrNull != "pause_turn") return@withContext parse(o)
            // a long search paused the turn: send what it had and let it continue
            messages += buildJsonObject { put("role", "assistant"); put("content", o["content"] ?: JsonArray(emptyList())) }
        }
        parse(last ?: throw Problem("Claude did not answer"))
    }

    /** Checks the key and that web search works. Returns how many pages were found. */
    suspend fun test(key: String): Int =
        ask("Search mpsc.gov.in for the latest MPSC announcement and reply in one short sentence.", key, searches = 1).sources.size

    private fun send(key: String, body: JsonObject): JsonObject {
        val r = http.newCall(Request.Builder().url(URL).header("x-api-key", key).header("anthropic-version", VERSION)
            .post(body.toString().toRequestBody("application/json".toMediaType())).build()).execute()
        val text = r.use { it.body.string() }
        if (!r.isSuccessful) throw Problem(errorOf(r.code, text))
        return json.parseToJsonElement(text).jsonObject
    }

    private fun errorOf(code: Int, body: String): String {
        val m = runCatching { json.parseToJsonElement(body).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content }.getOrNull()
        return when {
            code == 401 || code == 403 -> "That Claude key is not valid, or it may not use web search"
            code == 429 -> "Claude limit reached for now. ${m.orEmpty()}".trim()
            m?.contains("credit", true) == true -> "No Claude credit left. Add credit or raise the limit in console.anthropic.com"
            code >= 500 || code == 529 -> "Claude is busy, try again later"
            else -> m ?: "Claude error $code"
        }
    }

    private fun hostOf(url: String) = runCatching { URI(url).host.orEmpty() }.getOrDefault("").lowercase().removePrefix("www.")

    /** Reads a Messages API answer: search results become sources, cited text becomes supported text. */
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
        for (b in o["content"]?.jsonArray.orEmpty()) {
            val bo = b.jsonObject
            when (bo["type"]?.jsonPrimitive?.contentOrNull) {
                "web_search_tool_result" -> (bo["content"] as? JsonArray).orEmpty().forEach { r ->
                    val ro = r.jsonObject
                    if (ro["type"]?.jsonPrimitive?.contentOrNull == "web_search_result") {
                        sourceOf(ro["url"]?.jsonPrimitive?.contentOrNull.orEmpty(), ro["title"]?.jsonPrimitive?.contentOrNull.orEmpty())
                    }
                }
                "text" -> {
                    val t = bo["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    full.append(t)
                    val cited = bo["citations"]?.jsonArray.orEmpty().mapNotNull { c ->
                        val co = c.jsonObject
                        val url = co["url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                        sourceOf(url, co["title"]?.jsonPrimitive?.contentOrNull.orEmpty())
                    }.distinct()
                    if (cited.isNotEmpty()) supports += Support(t, cited)
                }
            }
        }
        return Grounded(full.toString(), sources, supports)
    }
}
