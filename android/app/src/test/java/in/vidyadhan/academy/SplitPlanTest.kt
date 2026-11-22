package `in`.vidyadhan.academy

import `in`.vidyadhan.academy.data.Installment
import `in`.vidyadhan.academy.students.leftToSplit
import `in`.vidyadhan.academy.students.splitPlan
import org.junit.Assert.*
import org.junit.Test

/** Splitting a balance: paid part stays, the rest goes on equal or unequal installments. */
class SplitPlanTest {
    private val plan = listOf(Installment(id = 5, studentId = 8, number = 1, amount = 80000, dueOn = 100, label = "Fee (old book)"))

    @Test fun anandBhangarEqual() {
        assertEquals(45000L, leftToSplit(80000, plan, 35000))
        val out = splitPlan(8, 80000, plan, 35000, listOf(15000L to 200L, 15000L to 231L, 15000L to 262L))!!
        assertEquals(listOf(35000L, 15000L, 15000L, 15000L), out.map { it.amount })
        assertEquals(listOf(1, 2, 3, 4), out.map { it.number })
        assertEquals(80000L, out.sumOf { it.amount })
        assertEquals("Fee (old book)", out[0].label)
    }

    @Test fun unequalAmounts() {
        val out = splitPlan(8, 80000, plan, 35000, listOf(25000L to 231L, 20000L to 200L))!!
        assertEquals(listOf(35000L, 20000L, 25000L), out.map { it.amount }) // sorted by date
    }

    @Test fun mustAddUpExactly() {
        assertNull(splitPlan(8, 80000, plan, 35000, listOf(20000L to 200L, 20000L to 231L)))
        assertNull(splitPlan(8, 80000, plan, 35000, listOf(45000L to 200L, 0L to 231L)))
    }

    @Test fun fullyPaidInstallmentsStay() {
        val p = listOf(Installment(studentId = 1, number = 1, amount = 10000, dueOn = 1), Installment(studentId = 1, number = 2, amount = 20000, dueOn = 50))
        val out = splitPlan(1, 30000, p, 10000, listOf(10000L to 60L, 10000L to 90L))!!
        assertEquals(listOf(10000L, 10000L, 10000L), out.map { it.amount })
    }
}
