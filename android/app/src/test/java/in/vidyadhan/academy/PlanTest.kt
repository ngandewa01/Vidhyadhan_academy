package `in`.vidyadhan.academy

import `in`.vidyadhan.academy.students.buildPlan
import `in`.vidyadhan.academy.students.maskAadhaar
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class PlanTest {
    @Test fun splitsFeeAndKeepsTotal() {
        val p = buildPlan(10000, 3, LocalDate.of(2026, 1, 31))
        assertEquals(10000L, p.sumOf { it.amount })
        assertEquals(listOf(3334L, 3333L, 3333L), p.map { it.amount })
        assertEquals(LocalDate.of(2026, 2, 28).toEpochDay(), p[1].dueOn)
    }
    @Test fun partialPaymentsFillInstallmentsInOrder() {
        val plan = listOf(10000L, 15000L, 5000L).mapIndexed { i, a -> `in`.vidyadhan.academy.data.Installment(studentId = 1, number = i + 1, amount = a, dueOn = 100L + i) }
        val st = `in`.vidyadhan.academy.students.allocate(plan, 13000)
        assertEquals(true, st[0].fullyPaid); assertEquals(3000L, st[1].covered); assertEquals(12000L, st[1].remaining); assertEquals(0L, st[2].covered)
    }
    @Test fun seatRanges() {
        assertEquals(setOf(1, 2, 3, 7, 10, 11), `in`.vidyadhan.academy.library.parseSeats("1-3, 7, 10–11"))
    }
    @Test fun masksAadhaar() = assertEquals("XXXX XXXX 9012", maskAadhaar("123456789012"))
}
