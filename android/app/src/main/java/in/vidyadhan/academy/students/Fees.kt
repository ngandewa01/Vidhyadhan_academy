package `in`.vidyadhan.academy.students

import `in`.vidyadhan.academy.data.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** How much of one installment is covered by what the student has paid so far. */
data class InstallmentStatus(val inst: Installment, val covered: Long) {
    val remaining: Long get() = (inst.amount - covered).coerceAtLeast(0)
    val fullyPaid: Boolean get() = covered >= inst.amount
    val partlyPaid: Boolean get() = covered in 1 until inst.amount
}

/** Payments fill installments in order: the first installment first, then the next. */
fun allocate(plan: List<Installment>, totalPaid: Long): List<InstallmentStatus> {
    var left = totalPaid
    return plan.sortedBy { it.number }.map { i ->
        val c = minOf(left, i.amount).coerceAtLeast(0)
        left -= c
        InstallmentStatus(i, c)
    }
}

fun summarise(s: Student, plan: List<Installment>, payments: List<StudentPayment>): StudentWithFees {
    val paid = payments.sumOf { it.amount }
    val next = allocate(plan, paid).firstOrNull { !it.fullyPaid }
    return StudentWithFees(
        s.id, s.name, s.mobile, s.aadhaar, s.course, s.batch, s.photo, s.dob, s.totalFee, paid,
        if (paid >= s.totalFee) null else next?.inst?.dueOn,
        if (paid >= s.totalFee) 0 else next?.remaining ?: (s.totalFee - paid),
        s.leftOn, s.leftReason, s.telegram, s.joinedOn, advanceOf(paid, plan, today()),
        khataKey = s.khataKey,
        dueNow = if (paid >= s.totalFee) 0 else (plan.filter { it.dueOn <= today() }.sumOf { it.amount } - paid).coerceAtLeast(0),
    )
}

/** Money paid ahead of the schedule: more than the installments due up to today [t]. */
fun advanceOf(paid: Long, plan: List<Installment>, t: Long): Long = (paid - plan.filter { it.dueOn <= t }.sumOf { it.amount }).coerceAtLeast(0)

/** Every student with their fee position, kept up to date. */
fun studentsWithFees(dao: StudentDao): Flow<List<StudentWithFees>> =
    combine(dao.students(), dao.allInstallments(), dao.allPayments()) { students, plans, pays ->
        val byPlan = plans.groupBy { it.studentId }
        val byPay = pays.groupBy { it.studentId }
        students.map { summarise(it, byPlan[it.id].orEmpty(), byPay[it.id].orEmpty()) }
    }

suspend fun studentsWithFeesNow(dao: StudentDao): List<StudentWithFees> {
    val byPlan = dao.installmentList().groupBy { it.studentId }
    val byPay = dao.paymentList().groupBy { it.studentId }
    return dao.allStudents().map { summarise(it, byPlan[it.id].orEmpty(), byPay[it.id].orEmpty()) }
}

/** What is still to pay on the plan: the fee minus fully paid installments and the paid part of a part-paid one. */
fun leftToSplit(totalFee: Long, plan: List<Installment>, paid: Long): Long {
    val st = allocate(plan, paid)
    return (totalFee - st.filter { it.fullyPaid }.sumOf { it.inst.amount } - st.filter { it.partlyPaid }.sumOf { it.covered }).coerceAtLeast(0)
}

/**
 * Splits what is still to pay into [rows] (amount to due date). Paid installments stay; a part-paid one keeps only what
 * was paid. Null when the rows do not add up to exactly what is left, or an amount is not above zero.
 */
fun splitPlan(studentId: Long, totalFee: Long, plan: List<Installment>, paid: Long, rows: List<Pair<Long, Long>>): List<Installment>? {
    val st = allocate(plan, paid)
    val rest = leftToSplit(totalFee, plan, paid)
    if (rows.isEmpty() || rows.any { it.first <= 0 } || rows.sumOf { it.first } != rest) return null
    val kept = st.filter { it.fullyPaid }.map { it.inst } + st.filter { it.partlyPaid }.map { it.inst.copy(amount = it.covered) }
    val added = rows.map { (a, d) -> Installment(studentId = studentId, number = 0, amount = a, dueOn = d) }
    return (kept.sortedWith(compareBy({ it.dueOn }, { it.number })) + added.sortedBy { it.dueOn })
        .mapIndexed { i, x -> x.copy(id = 0, number = i + 1) }
}
