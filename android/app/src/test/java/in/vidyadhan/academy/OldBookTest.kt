package `in`.vidyadhan.academy

import `in`.vidyadhan.academy.data.KhataEntry
import `in`.vidyadhan.academy.khata.OldBook
import org.junit.Assert.*
import org.junit.Test

/** Old-book students as normal students: the charge becomes the fee, every payment a payment. */
class OldBookTest {
    private fun e(date: Long, debit: Long = 0, credit: Long = 0, details: String = "", kind: String = "LEDGER") =
        KhataEntry(key = "amit jadhav", name = "Amit Jadhav", date = date, debit = debit, credit = credit, details = details, kind = kind)

    @Test fun chargeBecomesFeeAndPaymentsStayUnequal() {
        val p = OldBook.planFor(7, listOf(e(100, debit = 45000), e(101, credit = 10000), e(126, credit = 10000), e(170, credit = 10000, details = "gpay"), e(202, credit = 10000)))
        assertEquals(45000L, p.totalFee)
        assertEquals(1, p.installments.size)
        assertEquals(100L, p.installments[0].dueOn)
        assertEquals(listOf(10000L, 10000L, 10000L, 10000L), p.payments.map { it.amount })
        assertEquals("UPI", p.payments[2].mode)
        assertTrue(p.payments.all { it.studentId == 7L && it.note == "From old book" })
    }

    @Test fun paymentRecordedInAppIsAlsoMoved() {
        val p = OldBook.planFor(1, listOf(e(100, debit = 20000), e(150, credit = 5000, details = "Paid · Cash", kind = "PAYMENT")))
        assertEquals(listOf(5000L), p.payments.map { it.amount })
        assertEquals("Cash", p.payments[0].mode)
    }

    @Test fun paymentsWithoutChargeAreNotAdvance() {
        val p = OldBook.planFor(1, listOf(e(100, credit = 35000)))
        assertEquals(35000L, p.totalFee)
        assertEquals(35000L, p.installments.single().amount)
    }

    @Test fun twoChargesBecomeTwoInstallments() {
        val p = OldBook.planFor(1, listOf(e(100, debit = 30000), e(300, debit = 10000), e(120, credit = 5000)))
        assertEquals(40000L, p.totalFee)
        assertEquals(listOf(30000L, 10000L), p.installments.map { it.amount })
        assertEquals(listOf(1, 2), p.installments.map { it.number })
    }

    @Test fun nothingInTheBookMeansNoPlan() {
        assertTrue(OldBook.planFor(1, emptyList()).installments.isEmpty())
    }
}
