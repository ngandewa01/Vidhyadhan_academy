package `in`.vidyadhan.academy

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import `in`.vidyadhan.academy.data.AppDb
import `in`.vidyadhan.academy.data.Ids
import `in`.vidyadhan.academy.data.KhataEntry
import `in`.vidyadhan.academy.data.Student
import `in`.vidyadhan.academy.khata.OldBook
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = AcademyApp::class)
class OldBookRaceTest {
    @Test fun twoRunsAtOnceWithIdBlockChange() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDb::class.java).build()
        val k = db.khata()
        val lines = mutableListOf<KhataEntry>()
        for (i in 1..60) {
            val key = "p$i"
            lines += KhataEntry(key = key, name = "P $i", date = 100L + i, debit = 30000)
            for (j in 1..(1 + i % 4)) lines += KhataEntry(key = key, name = "P $i", date = 200L + i + j, credit = 5000)
        }
        k.insertAll(lines)
        for (i in 1..60) db.students().insert(Student(name = "P $i", mobile = "", totalFee = 0, joinedOn = 100, khataKey = "p$i"))
        val a = async(Dispatchers.IO) { runCatching { OldBook.convertAll(db) } }
        val b = async(Dispatchers.IO) { delay(5); Ids.device = 1; runCatching { OldBook.convertAll(db) } }
        println("run A: " + a.await().exceptionOrNull()); println("run B: " + b.await().exceptionOrNull())
        val pays = db.students().allPayments().first().groupBy { it.studentId }
        val wrong = db.students().allStudents().filter { s ->
            val i = s.name.removePrefix("P ").toInt()
            (pays[s.id]?.sumOf { it.amount } ?: 0L) != 5000L * (1 + i % 4) || s.totalFee != 30000L
        }.map { "${it.name}: fee ${it.totalFee} paid ${pays[it.id]?.sumOf { p -> p.amount } ?: 0}" }
        println("wrong: ${wrong.size} " + wrong.take(8))
        Ids.device = 0
        db.close()
        assertTrue(wrong.toString(), wrong.isEmpty())
    }
}
