package `in`.vidyadhan.academy.khata

import androidx.room.withTransaction
import `in`.vidyadhan.academy.data.AppDb
import `in`.vidyadhan.academy.data.Installment
import `in`.vidyadhan.academy.data.KhataEntry
import `in`.vidyadhan.academy.data.Student
import `in`.vidyadhan.academy.data.StudentPayment
import kotlinx.coroutines.flow.first

/**
 * Students confirmed from the old Khatabook become normal students: what the book charged becomes the fee and its
 * installments (unequal, on the dates charged), and every payment in the book becomes a normal payment that can be
 * edited. The book lines are kept for history; payments moved to the student are no longer counted as old-book money.
 */
object OldBook {
    data class Plan(val totalFee: Long, val installments: List<Installment>, val payments: List<StudentPayment>)

    /** Payment mode from the book's note, when it says one. */
    fun modeOf(details: String): String {
        val d = details.lowercase()
        return when {
            listOf("upi", "gpay", "phonepe", "paytm").any { it in d } -> "UPI"
            listOf("bank", "neft", "imps", "cheque", "rtgs").any { it in d } -> "Bank"
            "cash" in d -> "Cash"
            else -> ""
        }
    }

    fun planFor(studentId: Long, lines: List<KhataEntry>): Plan {
        val sorted = lines.sortedWith(compareBy({ it.date }, { it.id }))
        val charges = sorted.filter { it.debit > 0 }
        val pays = sorted.filter { it.credit > 0 }.map {
            StudentPayment(studentId = studentId, amount = it.credit, paidOn = it.date, mode = modeOf(it.details), note = "From old book")
        }
        val inst = when {
            charges.isNotEmpty() -> charges.mapIndexed { i, c ->
                Installment(studentId = studentId, number = i + 1, amount = c.debit, dueOn = c.date,
                    label = if (charges.size == 1) "Fee (old book)" else "Fee ${i + 1} (old book)")
            }
            // the book has payments but no charge written: the fee is what was paid, so nothing shows as advance
            pays.isNotEmpty() -> listOf(Installment(studentId = studentId, number = 1, amount = pays.sumOf { it.amount }, dueOn = pays.first().paidOn,
                label = "Paid (old book, no charge written)"))
            else -> emptyList()
        }
        return Plan(inst.sumOf { it.amount }, inst, pays)
    }

    /** Converts one student, once: only while it is linked to the book and has no fee, installments or payments yet. */
    suspend fun convert(db: AppDb, s: Student) {
        if (s.khataKey.isBlank() || s.totalFee != 0L) return
        val dao = db.students()
        if (dao.installments(s.id).first().isNotEmpty() || dao.payments(s.id).first().isNotEmpty()) return
        val plan = planFor(s.id, db.khata().forKey(s.khataKey).filter { it.kind != "PAYMENT_MOVED" })
        if (plan.installments.isEmpty()) return
        db.withTransaction {
            dao.update(s.copy(totalFee = plan.totalFee))
            dao.insertInstallments(plan.installments)
            plan.payments.forEach { dao.insertPayment(it) }
            db.khata().movePayments(s.khataKey)
        }
    }

    /** Converts every linked student not converted yet (run when the app opens). */
    suspend fun convertAll(db: AppDb) {
        db.students().allStudents().filter { it.khataKey.isNotBlank() && it.totalFee == 0L }.forEach { convert(db, it) }
    }
}
