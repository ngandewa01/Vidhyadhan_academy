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
    @Test fun masksAadhaar() = assertEquals("XXXX XXXX 9012", maskAadhaar("123456789012"))
}
