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

    @Test fun csvMobileColumnIsRead() {
        val e = Khata.parseCsv("name,date,debit,credit,details,mobile\nRahul Shinde,2023-05-01,5000,,,\"9876 543 210\"").single()
        assertEquals("9876543210", e.mobile)
    }

    @Test fun reimportKeepsConfirmationTypeAndNumber() {
        val old = listOf(KhataEntry(key = "a", name = "A", date = 1, debit = 5000, type = Khata.LIBRARY, review = "DONE", mobile = "9000000001"),
            KhataEntry(key = "a", name = "A", date = 1, kind = "PAYMENT", credit = 1000))
        val fresh = listOf(KhataEntry(key = "a", name = "A", date = 1, debit = 5000), KhataEntry(key = "b", name = "B", date = 2, debit = 1000))
        val out = Khata.carryOver(old, fresh)
        assertEquals(Khata.LIBRARY, out[0].type); assertEquals("DONE", out[0].review); assertEquals("9000000001", out[0].mobile)
        assertEquals("", out[1].review)
    }

    @Test fun onlyConfirmedPeopleLeaveThePending() {
        val people = Khata.people(listOf(line("d", debit = 5000), line("e", debit = 5000, review = "DONE"), line("f", debit = 5000, review = "SKIP")))
        assertEquals(listOf("d", "f"), people.filter { it.pending }.map { it.key })
    }
}
