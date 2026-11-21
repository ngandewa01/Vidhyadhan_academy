package `in`.vidyadhan.academy

import `in`.vidyadhan.academy.ai.Grounded
import `in`.vidyadhan.academy.ai.News
import `in`.vidyadhan.academy.ai.Source
import `in`.vidyadhan.academy.ai.Support
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NewsTest {
    private val text = """
###
TYPE: Advertisement
EXAM: Rajyaseva Prelims 2026
TITLE: Rajyaseva 2026 advertisement
EN: MPSC announced the State Services Prelims 2026 for 250 posts.
MR: एमपीएससीने राज्यसेवा पूर्व परीक्षा २०२६ ची जाहिरात दिली.
DATE: Last date to apply | 12 December 2025
DATE: Exam date | 1 March 2026
FOR: All MPSC students
###
TYPE: Result
EXAM: SSC CGL 2025
TITLE: SSC CGL result
EN: SSC declared the CGL 2025 result.
###
TYPE: Result
EXAM: PSI Mains 2025
TITLE: PSI result
EN: The PSI Mains result is out with 1488 qualified.
""".trimIndent()

    private val g = Grounded(text,
        listOf(Source("mpsc.gov.in", "mpsc.gov.in", "u1"), Source("ssc.gov.in", "ssc.gov.in", "u2"), Source("randomblog.com", "randomblog.com", "u3")),
        listOf(
            Support("MPSC announced the State Services Prelims 2026 for 250 posts.", listOf(0)),
            Support("Last date to apply | 12 December 2025", listOf(0)),
            // "Exam date" has no support -> dropped
            Support("SSC declared the CGL 2025 result.", listOf(1)), // not MPSC -> dropped
            Support("The PSI Mains result is out with 1488 qualified.", listOf(2)), // untrusted source only -> dropped
        ))

    @Test fun keepsOnlyBackedMpscLinesFromTrustedPages() {
        val items = News.check(g, News.NEWS, 100)
        assertEquals(1, items.size)
        val n = items[0]
        assertEquals("Advertisement", n.type)
        assertEquals(listOf("12 December 2025"), n.dates.map { it.value }) // the unbacked exam date is gone
        assertEquals(listOf("mpsc.gov.in"), n.cites.map { it.domain })
        assertTrue(n.official)
        assertTrue(News.post(n, "mr").contains("[1] mpsc.gov.in"))
    }

    @Test fun nothingNewMeansNothing() {
        assertEquals(0, News.check(Grounded("NONE", emptyList(), emptyList()), News.NEWS, 1).size)
    }
}
