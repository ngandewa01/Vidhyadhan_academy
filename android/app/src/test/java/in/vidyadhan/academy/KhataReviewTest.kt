package `in`.vidyadhan.academy

import `in`.vidyadhan.academy.data.KhataEntry
import `in`.vidyadhan.academy.khata.Khata
import org.junit.Assert.*
import org.junit.Test

/** The review before Students: who is pre-picked as a course student, and who is still waiting. */
class KhataReviewTest {
    private fun line(key: String, debit: Long = 0, credit: Long = 0, review: String = "") =
        KhataEntry(key = key, name = key, date = 19000, debit = debit, credit = credit, review = review)

    @Test fun overTwentyThousandPaidPlusDueIsPrePickedAsCourse() {
        val p = Khata.people(listOf(line("a", debit = 25000), line("a", credit = 10000))).single()
        assertEquals(Khata.COURSE, Khata.suggested(p))
    }

    @Test fun twentyThousandOrLessIsNotPrePicked() {
        val p = Khata.people(listOf(line("b", debit = 20000))).single()
        assertNull(Khata.suggested(p))
    }

    @Test fun paymentOnlyPersonCountsPaidPlusDue() {
        val p = Khata.people(listOf(line("c", credit = 35000))).single()
        assertEquals(35000L, p.paidPlusDue)
        assertEquals(Khata.COURSE, Khata.suggested(p))
    }

    @Test fun onlyUnreviewedPeopleArePending() {
        val people = Khata.people(listOf(line("d", debit = 5000), line("e", debit = 5000, review = "DONE"), line("f", debit = 5000, review = "SKIP")))
        assertEquals(listOf("d"), people.filter { it.review.isEmpty() }.map { it.key })
    }
}
