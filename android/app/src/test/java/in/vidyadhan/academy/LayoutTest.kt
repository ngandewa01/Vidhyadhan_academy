package `in`.vidyadhan.academy

import `in`.vidyadhan.academy.data.LibraryMember
import `in`.vidyadhan.academy.data.LibraryRoom
import `in`.vidyadhan.academy.data.MemberPlan
import `in`.vidyadhan.academy.library.*
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class LayoutTest {
    @Test fun parsesRowsGapsAndDoors() {
        val rows = parseLayout("16 17 _ 18\n\n30 D  29 x 0")
        assertEquals(2, rows.size)
        assertEquals(listOf(Cell.Seat(16), Cell.Seat(17), Cell.Gap, Cell.Seat(18)), rows[0])
        assertEquals(listOf(Cell.Seat(30), Cell.Door, Cell.Seat(29)), rows[1]) // junk and 0 are dropped
        assertEquals("16 17 _ 18\n30 D 29", formatLayout(rows))
    }

    @Test fun quickFillAndSeatLists() {
        assertEquals("1 2 3\n4 5", quickLayout(1, 5, 3))
        assertEquals("5 6\n7", quickLayout(7, 5, 2))
        val rooms = listOf(LibraryRoom(1, "Lab 1", 0, "1 2 3"), LibraryRoom(2, "Lab 2", 1, "10 _ 11\nD 3"))
        assertEquals(listOf(1, 2, 3, 10, 11), allSeats(rooms, 40))
        assertEquals((1..4).toList(), allSeats(emptyList(), 4))
        assertEquals("Lab 2", roomOf(11, rooms)?.name)
        assertEquals(setOf(3), duplicateSeats(rooms))
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
