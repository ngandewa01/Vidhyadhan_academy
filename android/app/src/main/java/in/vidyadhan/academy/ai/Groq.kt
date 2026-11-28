package `in`.vidyadhan.academy.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Groq's compound model through its OpenAI-compatible chat API, with the owner's key. Compound has built-in web search
 * with citations, so Groq does the search and the summary (the main AI). Its answers keep the pages it used as sources.
 */
object Groq {
    private const val URL = "https://api.groq.com/openai/v1/chat/completions"
    private const val MODEL = "groq/compound"

    private val http = OkHttpClient.Builder().callTimeout(90, TimeUnit.SECONDS).readTimeout(90, TimeUnit.SECONDS).build()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun ask(prompt: String, key: String): Grounded = withContext(Dispatchers.IO) {
        if (key.isBlank()) throw AiProblem("Add your Groq key in Settings → MPSC News → API keys")
        val body = buildJsonObject {
            put("model", MODEL)
            put("temperature", 0.2)
            putJsonArray("messages") {
                addJsonObject { put("role", "system"); put("content", "Answer only from facts you are sure of. You cannot browse; say so if a fact needs a web page.") }
                addJsonObject { put("role", "user"); put("content", prompt) }
            }
        }
        val r = http.newCall(Request.Builder().url(URL).header("Authorization", "Bearer $key")
            .post(body.toString().toRequestBody("application/json".toMediaType())).build()).execute()
        val text = r.use { it.body.string() }
        if (!r.isSuccessful) {
            val msg = runCatching { json.parseToJsonElement(text).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content }.getOrNull()
            throw AiProblem("Groq: " + (msg ?: "error ${r.code}"))
        }
        val answer = json.parseToJsonElement(text).jsonObject["choices"]?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.content
            ?: throw AiProblem("Groq gave no answer")
        // the pages compound searched, when Groq returns them (search results per executed tool)
        val sources = json.parseToJsonElement(text).jsonObject["choices"]?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("message")?.jsonObject?.get("executed_tools")?.jsonArray.orEmpty()
            .flatMap { t -> t.jsonObject["search_results"]?.jsonObject?.get("results")?.jsonArray.orEmpty() }
            .mapNotNull { r ->
                val o = r.jsonObject
                val url = o["url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val title = o["title"]?.jsonPrimitive?.contentOrNull.orEmpty()
                Source(title, runCatching { java.net.URI(url).host.orEmpty().removePrefix("www.") }.getOrDefault(title).lowercase(), url)
            }
            .distinctBy { it.uri }
        // one support per answer: every sentence is backed by the pages the search used
        Grounded(answer, sources, if (sources.isEmpty()) emptyList() else listOf(Support(answer, sources.indices.toList())))
    }
}
