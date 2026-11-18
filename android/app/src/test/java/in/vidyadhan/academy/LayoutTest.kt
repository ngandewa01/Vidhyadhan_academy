package `in`.vidyadhan.academy

import `in`.vidyadhan.academy.data.LibraryMember
import `in`.vidyadhan.academy.data.LibraryRoom
import `in`.vidyadhan.academy.data.MemberPlan
import `in`.vidyadhan.academy.library.*
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class LayoutTest {
    @Test fun oldRowLayoutBecomesAPlan() {
        val p = planOf("16 17 _ 18\n30 D 29")
        assertEquals(listOf(16, 17, 18, 30, 29), p.items.filter { it.type == PlanItem.SEAT }.map { it.n })
        assertEquals(1, p.items.count { it.type == PlanItem.DOOR })
        assertEquals(2, p.items.count { it.type == PlanItem.DESK })
        assertEquals(p, planOf(planText(p))) // JSON round trip
        assertEquals(FloorPlan(), planOf("not json {"))
    }

    @Test fun quickPlanAndSeatLists() {
        val q = quickPlan(1, 10, 4)
        assertEquals((1..10).toList(), q.items.filter { it.type == PlanItem.SEAT }.map { it.n })
        assertEquals(3, q.items.count { it.type == PlanItem.DESK })
        val rooms = listOf(LibraryRoom(1, "Room 1", 0, planText(quickPlan(1, 3, 8))), LibraryRoom(2, "Room 2", 1, "10 _ 11\nD 3"))
        assertEquals(listOf(1, 2, 3, 10, 11), allSeats(rooms, 40))
        assertEquals((1..4).toList(), allSeats(emptyList(), 4))
        assertEquals("Room 2", roomOf(11, rooms)?.name)
        assertEquals(setOf(3), duplicateSeats(rooms))
        assertEquals(12, nextSeatNumber(rooms))
    }

    @Test fun statusOrder() {
        val t = 20_000L
        fun m(till: Long, balance: Long = 0) = LibraryMember(name = "A", mobile = "9", seatNo = 1, joinedOn = t - 30, validTill = till, balance = balance)
        assertEquals(SeatStatus.Expired, memberStatus(m(t - 1, 300), t))
        assertEquals(SeatStatus.Partly, memberStatus(m(t + 2, 300), t))
        assertEquals(SeatStatus.Ending, memberStatus(m(t + ENDING_DAYS), t))
        assertEquals(SeatStatus.Ending, memberStatus(m(t), t))
        assertEquals(SeatStatus.Active, memberStatus(m(t + ENDING_DAYS + 1), t))
    }

    @Test fun renewals() {
        val plans = listOf(
            MemberPlan(1, "6 months with locker", 6, 6000, withLocker = true),
            MemberPlan(2, "3 months", 3, 2400),
            MemberPlan(3, "Old offer", 2, 1000, active = false),
        )
        assertEquals(listOf("1 month", "3 months", "6 months with locker"), renewOptions(900, plans, hasLocker = false).map { it.name })
        assertEquals(listOf("1 month", "6 months with locker", "3 months"), renewOptions(900, plans, hasLocker = true).map { it.name })
        val today = LocalDate.of(2026, 11, 18).toEpochDay()
        assertEquals(LocalDate.of(2027, 2, 21).toEpochDay(), renewedTill(LocalDate.of(2026, 11, 21).toEpochDay(), today, 3)) // still running: add on
        assertEquals(LocalDate.of(2027, 5, 18).toEpochDay(), renewedTill(LocalDate.of(2026, 10, 1).toEpochDay(), today, 6)) // lapsed: from today
        assertEquals(400L, balanceAfter(0, 2400, 2000))
        assertEquals(0L, balanceAfter(0, 900, 900))
        assertEquals(100L, balanceAfter(100, 900, 900))
    }
}
