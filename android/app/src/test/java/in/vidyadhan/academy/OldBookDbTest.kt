package `in`.vidyadhan.academy

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import `in`.vidyadhan.academy.data.AppDb
import `in`.vidyadhan.academy.data.KhataEntry
import `in`.vidyadhan.academy.data.Student
import `in`.vidyadhan.academy.khata.OldBook
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Converting old-book students on a real database: every payment must arrive, for every student. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = AcademyApp::class)
class OldBookDbTest {
    @Test fun everyPaymentOfEveryStudentArrives() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDb::class.java).allowMainThreadQueries().build()
        val k = db.khata()
        k.insertAll(listOf(
            KhataEntry(key = "kale vinayak", name = "Vinayak Kale", date = 100, debit = 25000),
            KhataEntry(key = "kale vinayak", name = "Vinayak Kale", date = 101, credit = 6000),
            KhataEntry(key = "kale vinayak", name = "Vinayak Kale", date = 130, credit = 9000),
            KhataEntry(key = "kale vinayak", name = "Vinayak Kale", date = 160, credit = 10000),
            KhataEntry(key = "aarti vighne", name = "Aarti Vighne", date = 200, debit = 35000),
            KhataEntry(key = "aarti vighne", name = "Aarti Vighne", date = 210, credit = 35000),
        ))
        val s = db.students()
        s.insert(Student(name = "Vinayak Kale", mobile = "", totalFee = 0, joinedOn = 100, khataKey = "kale vinayak"))
        s.insert(Student(name = "Aarti Vighne", mobile = "", totalFee = 0, joinedOn = 200, khataKey = "aarti vighne"))
        OldBook.convertAll(db)
        val all = s.allStudents().associateBy { it.name }
        val vk = all.getValue("Vinayak Kale"); val av = all.getValue("Aarti Vighne")
        assertEquals(25000L, vk.totalFee)
        assertEquals(listOf(6000L, 9000L, 10000L), s.payments(vk.id).first().map { it.amount }.sorted())
        assertEquals(listOf(35000L), s.payments(av.id).first().map { it.amount })
        db.close()
    }
}
