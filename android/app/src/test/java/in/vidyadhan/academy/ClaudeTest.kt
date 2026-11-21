package `in`.vidyadhan.academy

import `in`.vidyadhan.academy.ai.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test

/** Reads Claude's answer format (web search results and cited text) and checks which lines count as backed. */
class ClaudeTest {
    private val answer = """
    {"stop_reason":"end_turn","content":[
      {"type":"server_tool_use","id":"s1","name":"web_search","input":{"query":"MPSC"}},
      {"type":"web_search_tool_result","tool_use_id":"s1","content":[
        {"type":"web_search_result","url":"https://mpsc.gov.in/notice-1","title":"MPSC notice","page_age":"1 day"},
        {"type":"web_search_result","url":"https://www.loksatta.com/exam","title":"Loksatta","page_age":"2 days"}]},
      {"type":"text","text":"###\nTYPE: Advertisement\nEXAM: Rajyaseva 2026\nEN: Applications are open until 12 December 2026.",
       "citations":[{"type":"web_search_result_location","url":"https://mpsc.gov.in/notice-1","title":"MPSC notice","cited_text":"Applications are open","encrypted_index":"x"}]},
      {"type":"text","text":"\nMR: अर्ज सुरू आहेत.\nTITLE: Rajyaseva 2026 applications"}
    ]}
    """.trimIndent()

    @Test fun collectsSearchedPagesAsSources() {
        val g = Claude.parse(Json.parseToJsonElement(answer).jsonObject)
        assertEquals(2, g.sources.size)
        assertEquals("mpsc.gov.in", g.sources[0].domain)
        assertEquals("loksatta.com", g.sources[1].domain)
    }

    @Test fun onlyCitedTextIsSupported() {
        val g = Claude.parse(Json.parseToJsonElement(answer).jsonObject)
        assertEquals(1, g.supports.size)
        assertEquals(listOf(0), g.supports[0].sources)
        assertTrue(g.text.contains("Applications are open until 12 December 2026."))
    }

    @Test fun citedAnnouncementIsKept() {
        val g = Claude.parse(Json.parseToJsonElement(answer).jsonObject)
        val items = News.check(g, News.NEWS, 0L)
        assertEquals(1, items.size)
        assertEquals("Advertisement", items[0].type)
        assertEquals("Rajyaseva 2026", items[0].exam)
        assertEquals("mpsc.gov.in", items[0].cites[0].domain)
    }

    @Test fun uncitedAnnouncementIsDropped() {
        val plain = """{"stop_reason":"end_turn","content":[{"type":"text","text":"###\nTYPE: Result\nEXAM: PSI 2026\nEN: Result is out."}]}"""
        val g = Claude.parse(Json.parseToJsonElement(plain).jsonObject)
        assertTrue(News.check(g, News.NEWS, 0L).isEmpty())
    }

    @Test fun searchErrorInsteadOfListIsIgnored() {
        val err = """{"stop_reason":"end_turn","content":[{"type":"web_search_tool_result","tool_use_id":"s","content":{"type":"web_search_tool_result_error","error_code":"max_uses_exceeded"}}]}"""
        val g = Claude.parse(Json.parseToJsonElement(err).jsonObject)
        assertTrue(g.sources.isEmpty())
    }
}

class TapUnlockTest {
    private var t = 0L
    private fun taps(n: Int, gap: Long = 300): TapUnlock {
        val u = TapUnlock(now = { t })
        repeat(n) { t += gap; u.tap() }
        return u
    }

    @Test fun tenQuickTapsUnlock() {
        var t2 = 0L
        val u = TapUnlock(now = { t2 })
        val results = (1..10).map { t2 += 300; u.tap() }
        assertFalse(results.dropLast(1).any { it })
        assertTrue(results.last())
    }

    @Test fun pauseStartsOver() {
        var now = 0L
        val u = TapUnlock(now = { now })
        repeat(8) { now += 300; u.tap() }
        now += 5000 // a long pause
        val unlocked = (1..9).map { now += 300; u.tap() }.any { it }
        assertFalse(unlocked)
    }

    @Test fun hintOnlyFromSeventh() {
        var now = 0L
        val u = TapUnlock(now = { now })
        repeat(6) { now += 300; u.tap() }
        assertNull(u.hint())
        now += 300; u.tap()
        assertEquals("3 more", u.hint())
    }
}
