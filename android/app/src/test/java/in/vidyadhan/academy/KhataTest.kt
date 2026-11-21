package `in`.vidyadhan.academy

import `in`.vidyadhan.academy.khata.Khata
import org.junit.Assert.*
import org.junit.Test

/** The old Khatabook import: reading the CSV, grouping people, and the totals shown before importing. */
class KhataTest {
    private val csv = """
        name,date,debit,credit,details
        Stu Rahul Patil Mpsc,2023-11-20,25000,,
        Rahul Patil,2023-11-22,,4000,
        Sanika Gharge,2023-05-11,30000,,
        Sanika Gharge,2023-05-12,,5000,
        "Gharge, Sanika",2023-07-13,,3000,cash
        Sam Todalmal Academy,2023-05-19,,,
    """.trimIndent()

    @Test fun readsEveryLine() {
        val e = Khata.parseCsv(csv)
        assertEquals(6, e.size)
        assertEquals(25000L, e[0].debit)
        assertEquals("cash", e[4].details)
    }

    @Test fun samePersonDifferentSpellingsGroupTogether() {
        assertEquals(Khata.keyOf("Stu Rahul Patil Mpsc"), Khata.keyOf("Rahul Patil"))
        assertEquals(Khata.keyOf("Sanika Gharge"), Khata.keyOf("Gharge, Sanika"))
    }

    @Test fun balancesAndTotals() {
        val e = Khata.parseCsv(csv)
        val s = Khata.summary(e)
        assertEquals(3, s.people)
        assertEquals(2, s.owing) // Rahul Patil owes 21,000; Sanika Gharge owes 22,000
        assertEquals(43000L, s.owed)
        assertEquals(0L, s.advance)
        assertEquals(55000L, s.debit)
        assertEquals(12000L, s.credit)
    }

    @Test fun peopleSortedByBalance() {
        val p = Khata.people(Khata.parseCsv(csv))
        assertEquals("Sanika Gharge", p.first().name)
        assertEquals(22000L, p.first().balance)
        assertTrue(p.first().balance >= p.last().balance)
    }

    @Test fun badLinesAreSkipped() {
        assertEquals(0, Khata.parseCsv("name,date,debit,credit,details\nNo date here,not-a-date,1,,").size)
    }

    @Test fun guessesTypeFromName() {
        assertEquals(Khata.COURSE, Khata.guessType("Stu Rahul Patil Mpsc"))
        assertEquals(Khata.ENQUIRY, Khata.guessType("Enquiry Diksha Netke 11th"))
        assertEquals(Khata.OTHER, Khata.guessType("Divya Netke"))
    }

    @Test fun parsedLinesCarryTheGuess() {
        assertEquals(Khata.COURSE, Khata.parseCsv(csv)[0].type)
        assertEquals(Khata.OTHER, Khata.parseCsv(csv)[5].type)
    }
}
