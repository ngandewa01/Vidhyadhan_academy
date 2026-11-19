package `in`.vidyadhan.academy.ai

import android.content.Context
import androidx.work.*
import `in`.vidyadhan.academy.AppSettings
import `in`.vidyadhan.academy.students.today
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

@Serializable data class Cite(val title: String, val domain: String, val uri: String)
@Serializable data class KeyDate(val label: String, val value: String, val cite: Int)

/** One MPSC news item or one current-affairs point. Every line shown was backed by a cited page. */
@Serializable
data class NewsItem(
    val id: String, val kind: String, // NEWS or CA
    val day: Long, val type: String = "", val exam: String = "", val title: String = "", val forWho: String = "",
    val en: String, val mr: String = "", val dates: List<KeyDate> = emptyList(), val cites: List<Cite>,
    val official: Boolean = false, val read: Boolean = false, val posted: Boolean = false, val at: Long = System.currentTimeMillis(),
)

/**
 * MPSC news and the daily current-affairs digest, made by Gemini with Google Search and checked by the app:
 * a line is kept only when Google's grounding data shows a page that backs it, and only pages from
 * government sites and trusted newspapers count. Anything not about MPSC exams is dropped.
 */
object News {
    const val NEWS = "NEWS"; const val CA = "CA"
    val TYPES = listOf("Advertisement", "Result", "Answer key", "Timetable", "Admit card", "Notice")
    private val OFFICIAL = listOf("mpsc.gov.in", "mpsconline.gov.in")
    private val TRUSTED = OFFICIAL + listOf("maharashtra.gov.in", "gov.in", "nic.in", "rbi.org.in", "pib.gov.in", "loksatta.com", "lokmat.com",
        "maharashtratimes.com", "esakal.com", "thehindu.com", "indianexpress.com", "timesofindia.indiatimes.com", "hindustantimes.com", "livemint.com", "unfccc.int", "un.org")
    private val OTHER_EXAMS = Regex("""\b(UPSC|SSC|IBPS|RRB|NEET|JEE|CUET|GATE)\b""", RegexOption.IGNORE_CASE)

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val lock = Mutex()
    private val _items = MutableStateFlow<List<NewsItem>>(emptyList())
    val items: StateFlow<List<NewsItem>> = _items
    private var file: File? = null

    fun load(ctx: Context) {
        val f = File(ctx.filesDir, "news.json").also { file = it }
        _items.value = runCatching { json.decodeFromString<List<NewsItem>>(f.readText()) }.getOrDefault(emptyList())
    }
    private fun save(list: List<NewsItem>) {
        val keep = list.sortedByDescending { it.at }.take(300)
        _items.value = keep
        file?.let { f -> runCatching { f.writeText(json.encodeToString(keep)) } }
    }
    fun update(item: NewsItem) = save(_items.value.map { if (it.id == item.id) item else it })
    fun markAllRead(kind: String) = save(_items.value.map { if (it.kind == kind) it.copy(read = true) else it })

    private fun trusted(d: String) = TRUSTED.any { d == it || d.endsWith(".$it") }
    private fun official(d: String) = OFFICIAL.any { d == it || d.endsWith(".$it") }
    private fun keyOf(vararg s: String) = s.joinToString(" ").lowercase().filter { it.isLetterOrDigit() }.take(90)

    // ───────────── prompts ─────────────

    private val fmt = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH)

    private fun newsPrompt(known: List<String>) = """
Today is ${LocalDate.now().format(fmt)}. Use Google Search to find announcements made by MPSC (Maharashtra Public Service Commission) in the last 7 days.
Only these kinds count: new exam advertisements, results, answer keys, exam timetables or date changes, admit cards / hall tickets, and important notices about MPSC exams (syllabus or pattern changes, exam postponed).
Search mpsc.gov.in first. Newspaper reports are fine only when they report an MPSC announcement.
Leave out: other commissions and exams (UPSC, SSC, banking, police bharti not conducted by MPSC), tenders, MPSC staff matters, opinions, coaching-class ads, rumours.
Write only facts found in the search results. Never guess a date, fee, number of posts or cut-off. If a detail is not in the results, leave that line out.
${if (known.isNotEmpty()) "Already known, do not repeat:\n" + known.joinToString("\n") { "- $it" } else ""}
Answer in exactly this format, one block per announcement, nothing else:
###
TYPE: one of ${TYPES.joinToString(", ")}
EXAM: exam name, e.g. Rajyaseva Prelims 2026
TITLE: short English title
EN: two or three short English sentences with the main facts
MR: the same in simple Marathi
DATE: label | value   (one line per important date, fee, posts or cut-off; e.g. Last date to apply | 12 December 2025)
FOR: who it matters to, e.g. All MPSC students, PSI aspirants
If there is nothing new, answer only: NONE
""".trim()

    private fun digestPrompt() = """
Today is ${LocalDate.now().format(fmt)}. Use Google Search to pick 3 to 6 current-affairs facts from the last 2 days that MPSC exam students should know.
Only topics in the MPSC syllabus: Maharashtra state decisions and schemes, Indian polity and governance, economy (RBI, budget, reports and indices), government schemes, environment and ecology, science and technology, important appointments, awards of national importance, international relations that affect India.
Leave out: sports scores, films and celebrities, crime, party politics and statements, opinion pieces.
Prefer government sources (gov.in, pib.gov.in, rbi.org.in, maharashtra.gov.in) and major newspapers. Write only facts found in the results.
Answer in exactly this format, one block per fact, nothing else:
###
TOPIC: MPSC paper and topic in Marathi, e.g. GS-3 · अर्थव्यवस्था
EN: one clear English sentence
MR: the same in simple Marathi
""".trim()

    // ───────────── checking what came back ─────────────

    /** One "KEY: value" line of the answer and where it sits in the text. */
    private class Line(val key: String, val value: String, val start: Int, val end: Int)

    /** Splits the answer into blocks of lines, keeping each line's place in the text. */
    private fun blocks(text: String): List<List<Line>> {
        val out = mutableListOf<MutableList<Line>>()
        var pos = 0
        for (raw in text.split('\n')) {
            val start = pos; pos += raw.length + 1
            val l = raw.trim().removePrefix("*").removePrefix("-").trim()
            if (l.startsWith("###")) { out += mutableListOf<Line>(); continue }
            val i = l.indexOf(':')
            if (i in 2..12 && out.isNotEmpty()) out.last() += Line(l.substring(0, i).trim().uppercase().replace("*", ""), l.substring(i + 1).trim().trim('*').trim(), start, start + raw.length)
        }
        return out.filter { it.isNotEmpty() }
    }

    /** Where each supported piece of text sits in the answer. */
    private fun placed(g: Grounded): List<Triple<Int, Int, List<Int>>> {
        val out = mutableListOf<Triple<Int, Int, List<Int>>>()
        var from = 0
        for (s in g.supports) {
            val t = s.text.trim(); if (t.length < 4) continue
            var i = g.text.indexOf(t, from); if (i < 0) i = g.text.indexOf(t)
            if (i < 0) continue
            out += Triple(i, i + t.length, s.sources); from = i
        }
        return out
    }

    /** The sources (indices) backing a line, or empty if nothing backs it. */
    private fun backing(line: Line, p: List<Triple<Int, Int, List<Int>>>): Set<Int> =
        p.filter { (a, b, _) -> a < line.end && b > line.start }.flatMap { it.third }.toSet()

    internal fun check(g: Grounded, kind: String, day: Long): List<NewsItem> {
        if (g.text.trim().equals("NONE", true)) return emptyList()
        val p = placed(g)
        return blocks(g.text).mapNotNull { b ->
            fun v(k: String) = b.firstOrNull { it.key == k }?.value.orEmpty()
            val enLine = b.firstOrNull { it.key == "EN" } ?: return@mapNotNull null
            val enSrc = backing(enLine, p)
            if (enSrc.isEmpty()) return@mapNotNull null // the main sentence must be backed by a page
            val dateLines = b.filter { it.key == "DATE" && it.value.contains('|') }.mapNotNull { l -> backing(l, p).takeIf { it.isNotEmpty() }?.let { l to it } }
            val used = (enSrc + dateLines.flatMap { it.second }).filter { it in g.sources.indices && trusted(g.sources[it].domain) }.distinct()
            if (used.isEmpty()) return@mapNotNull null // no trusted page backs it
            val cites = used.map { g.sources[it].let { s -> Cite(s.title, s.domain, s.uri) } }
            val num = used.withIndex().associate { (i, s) -> s to i + 1 }
            val dates = dateLines.mapNotNull { (l, src) ->
                val c = src.firstNotNullOfOrNull { num[it] } ?: return@mapNotNull null
                val (label, value) = l.value.split('|', limit = 2).map { it.trim() }
                KeyDate(label, value, c)
            }
            if (kind == NEWS) {
                val type = TYPES.firstOrNull { it.equals(v("TYPE"), true) } ?: "Notice"
                val exam = v("EXAM"); val title = v("TITLE").ifBlank { exam }
                val text = "$exam $title ${enLine.value}"
                if (exam.isBlank() || (OTHER_EXAMS.containsMatchIn(text) && !text.contains("MPSC", true))) return@mapNotNull null // not about MPSC
                NewsItem(keyOf(type, exam, title), NEWS, day, type, exam, title, v("FOR"), enLine.value, v("MR"), dates, cites, used.any { official(g.sources[it].domain) })
            } else NewsItem(keyOf(enLine.value) + day, CA, day, title = v("TOPIC"), en = enLine.value, mr = v("MR"), cites = cites,
                official = used.any { official(g.sources[it].domain) })
        }
    }

    // ───────────── running ─────────────

    /** Looks for new MPSC news (and makes today's digest if it is not made yet). Returns how many new items. */
    suspend fun refresh(ctx: Context, force: Boolean = false): Int = lock.withLock {
        if (file == null) load(ctx)
        if (AppSettings.geminiKey.isBlank()) return 0
        val t = today()
        var added = 0
        val now = System.currentTimeMillis()
        if (AppSettings.newsOn && (force || now - AppSettings.newsLast > 10 * 3_600_000L)) {
            val have = _items.value.filter { it.kind == NEWS }
            val fresh = check(Gemini.ask(newsPrompt(have.take(25).map { "${it.exam}: ${it.title}" })), NEWS, t)
                .filter { n -> have.none { it.id == n.id } }.distinctBy { it.id }
            AppSettings.newsLast = now
            if (fresh.isNotEmpty()) {
                save(fresh + _items.value); added += fresh.size
                if (AppSettings.newsNotify) `in`.vidyadhan.academy.notify.Reminders.notify(ctx,
                    fresh.joinToString("\n") { "• ${it.type}: ${it.title}" }, id = 7, title = "MPSC News · ${fresh.size} new")
            }
        }
        if (AppSettings.digestOn && (force || AppSettings.digestDay != t)) {
            val pts = check(Gemini.ask(digestPrompt()), CA, t).distinctBy { it.id }
            AppSettings.digestDay = t
            if (pts.isNotEmpty()) { save(pts + _items.value.filter { !(it.kind == CA && it.day == t) }); added += pts.size }
        }
        added
    }

    // ───────────── sharing ─────────────

    fun post(n: NewsItem, lang: String): String = buildString {
        val mr = lang != "en" && n.mr.isNotBlank()
        if (n.kind == NEWS) append("🎓 ${n.title}\n\n") else append("📰 चालू घडामोडी · ${n.title}\n\n")
        if (lang == "both") { append(n.mr).append("\n\n").append(n.en) } else append(if (mr) n.mr else n.en)
        if (n.dates.isNotEmpty()) append("\n\n").append(n.dates.joinToString("\n") { "• ${it.label}: ${it.value} [${it.cite}]" })
        append("\n\n📎 ${if (lang == "en") "Sources" else "स्रोत / Sources"}:\n")
        append(n.cites.withIndex().joinToString("\n") { (i, c) -> "[${i + 1}] ${c.domain.ifBlank { c.title }}" })
        if (n.kind == NEWS) append("\n🔗 mpsc.gov.in")
        append("\n— ${AppSettings.academyName}")
    }

    /** The daily digest as one post. */
    fun digestPost(points: List<NewsItem>, lang: String): String = buildString {
        append("📰 आजच्या चालू घडामोडी · ${LocalDate.now().format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH))}\n\n")
        val all = points.flatMap { it.cites }.distinctBy { it.uri }
        points.forEachIndexed { i, p ->
            val nums = p.cites.map { c -> all.indexOfFirst { it.uri == c.uri } + 1 }.joinToString(",")
            append("${i + 1}. ").append(if (lang == "en" || p.mr.isBlank()) p.en else p.mr).append(" [$nums]")
            if (p.title.isNotBlank()) append(" (${p.title})")
            append("\n")
        }
        append("\n📎 स्रोत / Sources:\n").append(all.withIndex().joinToString("\n") { (i, c) -> "[${i + 1}] ${c.domain.ifBlank { c.title }}" })
        append("\n— ${AppSettings.academyName}")
    }

    fun schedule(ctx: Context) {
        val req = PeriodicWorkRequestBuilder<NewsWorker>(12, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES).build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("mpsc-news", ExistingPeriodicWorkPolicy.KEEP, req)
    }
}

/** Twice a day (when online): new MPSC news and today's current affairs. */
class NewsWorker(ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {
    override suspend fun doWork(): Result = try {
        News.refresh(applicationContext); Result.success()
    } catch (e: Gemini.Problem) { Result.success() } // a bad key or the free limit: try at the next run
    catch (e: Exception) { if (runAttemptCount < 2) Result.retry() else Result.success() }
}
