package `in`.vidyadhan.academy.ai

import `in`.vidyadhan.academy.AppSettings

/** An error from any AI, with a message the owner can read. */
open class AiProblem(msg: String) : Exception(msg)

/** The AIs the owner can choose. [connected] is false until the AI's search is confirmed working. */
enum class AiProvider(val label: String, val note: String, val connected: Boolean, val keyPrefix: String, val keyLink: String) {
    GEMINI("Gemini", "Free key · Google Search", true, "AIza", "https://aistudio.google.com/app/apikey"),
    CLAUDE("Claude", "Needs API billing (not your subscription) · web search on trusted sites", true, "sk-ant-", "https://console.anthropic.com/settings/keys"),
    GPT("GPT (OpenAI)", "Needs API billing (not ChatGPT) · web search on trusted sites", true, "sk-", "https://platform.openai.com/api-keys"),
    GROQ("Groq", "Not connected yet · free-plan search not confirmed", false, "gsk_", "https://console.groq.com/keys"),
}

/** Chooses the main AI and the backup, and asks them. */
object AiRouter {
    private const val TEST = "Search mpsc.gov.in for the latest MPSC announcement and reply in one short sentence."

    fun keyOf(p: AiProvider): String = if (p == AiProvider.GEMINI) AppSettings.geminiKey else AppSettings.keyOf(p.name)
    fun saveKey(p: AiProvider, key: String) { if (p == AiProvider.GEMINI) AppSettings.geminiKey = key.trim() else AppSettings.setKeyOf(p.name, key) }

    fun primary(): AiProvider = runCatching { AiProvider.valueOf(AppSettings.aiPrimary) }.getOrDefault(AiProvider.GEMINI)
    /** The backup, if one is chosen, is different from the main AI, and is connected. */
    fun backup(): AiProvider? = runCatching { AiProvider.valueOf(AppSettings.aiBackup) }.getOrNull()?.takeIf { it != primary() && it.connected }

    /** True when the main AI is connected and has a key. */
    fun ready(): Boolean = primary().let { it.connected && keyOf(it).isNotBlank() }

    /** Asks the main AI; if it fails (for example its daily limit) and a backup has a key, asks the backup. */
    suspend fun ask(prompt: String): Grounded {
        val first = primary()
        if (!ready()) throw AiProblem("Add a ${first.label} key in Settings → MPSC News → API keys")
        return try {
            askWith(first, prompt)
        } catch (e: AiProblem) {
            val b = backup()?.takeIf { keyOf(it).isNotBlank() } ?: throw e
            askWith(b, prompt)
        }
    }

    suspend fun askWith(p: AiProvider, prompt: String, key: String = keyOf(p)): Grounded = when (p) {
        AiProvider.GEMINI -> Gemini.ask(prompt, key)
        AiProvider.CLAUDE -> Claude.ask(prompt, key)
        AiProvider.GPT -> Gpt.ask(prompt, key)
        AiProvider.GROQ -> throw AiProblem("Groq is not connected yet")
    }

    /** Checks a key with a real search. Returns how many pages were found. */
    suspend fun test(p: AiProvider, key: String): Int = askWith(p, TEST, key).sources.size
}
