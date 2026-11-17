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
    )
}

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
