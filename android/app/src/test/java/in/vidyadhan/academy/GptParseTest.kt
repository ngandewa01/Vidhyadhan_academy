package `in`.vidyadhan.academy

import `in`.vidyadhan.academy.ai.Gpt
import `in`.vidyadhan.academy.ai.News
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test

/** Reads GPT's Responses answer: cited text becomes a checked news item. */
class GptParseTest {
    private val answer = """
    {"output":[
      {"type":"web_search_call","id":"w1","status":"completed"},
      {"type":"message","content":[{"type":"output_text",
        "text":"###\nTYPE: Result\nEXAM: PSI 2026\nEN: The result is out.",
        "annotations":[{"type":"url_citation","url":"https://mpsc.gov.in/result","title":"MPSC result","start_index":0,"end_index":10}]}]}
    ]}
    """.trimIndent()

    @Test fun citedResultIsKept() {
        val g = Gpt.parse(Json.parseToJsonElement(answer).jsonObject)
        assertEquals(1, g.sources.size)
        assertEquals("mpsc.gov.in", g.sources[0].domain)
        val items = News.check(g, News.NEWS, 0L)
        assertEquals(1, items.size)
        assertEquals("Result", items[0].type)
    }
}
