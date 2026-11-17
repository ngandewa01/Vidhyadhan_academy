package `in`.vidyadhan.academy.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AirlineSeatLegroomExtra
import androidx.compose.material.icons.rounded.EventSeat
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import `in`.vidyadhan.academy.data.SeatType

/** Turns "1-10, 15, 20-22" into the set of seat numbers. */
fun parseSeats(text: String): Set<Int> = text.split(",", ";").flatMap { part ->
    val p = part.trim()
    val range = Regex("^(\\d+)\\s*[-–to]+\\s*(\\d+)$").find(p)
    when {
        range != null -> { val (a, b) = range.destructured; val x = a.toInt(); val y = b.toInt(); (minOf(x, y)..maxOf(x, y)).toList() }
        p.toIntOrNull() != null -> listOf(p.toInt())
        else -> emptyList()
    }
}.toSet()

/** Which type a seat number belongs to: the first type that lists it, otherwise the type with no seats listed (usually "Normal"). */
fun typeForSeat(n: Int, types: List<SeatType>): SeatType? =
    types.firstOrNull { n in parseSeats(it.seats) } ?: types.firstOrNull { it.seats.isBlank() } ?: types.firstOrNull()

/** Seat icon: plain chair, chair with leg room, or chair with a small lock badge. */
@Composable
fun SeatIcon(icon: String, tint: Color, size: Dp = 20.dp, badgeBg: Color = Color.White) {
    Box(Modifier.size(size + 4.dp)) {
        Icon(if (icon == "LEGROOM") Icons.Rounded.AirlineSeatLegroomExtra else Icons.Rounded.EventSeat, null, tint = tint, modifier = Modifier.size(size).align(Alignment.Center))
        if (icon == "LOCKER") Box(
            Modifier.align(Alignment.TopEnd).size(size * 0.55f).clip(CircleShape).background(badgeBg).border(1.dp, tint, CircleShape), Alignment.Center,
        ) { Icon(Icons.Rounded.Lock, null, tint = tint, modifier = Modifier.size(size * 0.36f)) }
    }
}
