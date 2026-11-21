package `in`.vidyadhan.academy

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import `in`.vidyadhan.academy.data.*
import `in`.vidyadhan.academy.khata.OldBook
import `in`.vidyadhan.academy.students.shortRupees
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The one-time repair: payments lost after the conversion come back, duplicates go, edits stay. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = AcademyApp::class)
class OldBookRepairTest {
    @Test fun lostPaymentsComeBackDuplicatesGoEditsStay() = runBlocking {
        AppSettings.oldBookRepaired = false
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDb::class.java).allowMainThreadQueries().build()
        db.khata().insertAll(listOf(
            KhataEntry(key = "a", name = "A", date = 100, debit = 25000), KhataEntry(key = "a", name = "A", date = 101, credit = 6000), KhataEntry(key = "a", name = "A", date = 102, credit = 19000),
            KhataEntry(key = "b", name = "B", date = 100, debit = 10000), KhataEntry(key = "b", name = "B", date = 101, credit = 4000),
            KhataEntry(key = "c", name = "C", date = 100, debit = 8000), KhataEntry(key = "c", name = "C", date = 101, credit = 8000),
        ))
        val dao = db.students()
        // A: converted, then its payments were lost
        val a = dao.insert(Student(name = "A", mobile = "", totalFee = 25000, joinedOn = 100, khataKey = "a"))
        dao.insertInstallments(listOf(Installment(studentId = a, number = 1, amount = 25000, dueOn = 100)))
        // B: converted twice at once: the payment is there two times
        val b = dao.insert(Student(name = "B", mobile = "", totalFee = 10000, joinedOn = 100, khataKey = "b"))
        dao.insertInstallments(listOf(Installment(studentId = b, number = 1, amount = 10000, dueOn = 100)))
        repeat(2) { dao.insertPayment(StudentPayment(studentId = b, amount = 4000, paidOn = 101, note = "From old book")) }
        // C: the owner edited the book payment (amount and date): it must stay as edited
        val c = dao.insert(Student(name = "C", mobile = "", totalFee = 8000, joinedOn = 100, khataKey = "c"))
        dao.insertInstallments(listOf(Installment(studentId = c, number = 1, amount = 8000, dueOn = 100)))
        dao.insertPayment(StudentPayment(studentId = c, amount = 7500, paidOn = 105, note = "From old book"))

        OldBook.repairOnce(db)
        assertEquals(listOf(6000L, 19000L), dao.payments(a).first().map { it.amount }.sorted())
        assertEquals(listOf(4000L), dao.payments(b).first().map { it.amount })
        assertEquals(listOf(7500L), dao.payments(c).first().map { it.amount })
        assertTrue(AppSettings.oldBookRepaired)
        // runs once only
        dao.deletePayment(dao.payments(a).first().first())
        OldBook.repairOnce(db)
        assertEquals(1, dao.payments(a).first().size)
        AppSettings.oldBookRepaired = false
        db.close()
    }

    @Test fun bigAmountsShortened() {
        assertEquals("₹31.7L", shortRupees(31_73_950))
        assertEquals("₹10L", shortRupees(10_00_000))
        assertEquals("₹1.2Cr", shortRupees(1_20_00_000))
        assertEquals("₹45,000", shortRupees(45_000))
    }
}
