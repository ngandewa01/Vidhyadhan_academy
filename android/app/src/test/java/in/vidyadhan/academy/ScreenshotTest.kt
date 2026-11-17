package `in`.vidyadhan.academy

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import `in`.vidyadhan.academy.data.*
import `in`.vidyadhan.academy.students.buildPlan
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/** Renders each main screen to a PNG so the design can be reviewed without a phone. Sample data only. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = AcademyApp::class, qualifiers = "w393dp-h852dp-xhdpi")
class ScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val out = File(System.getProperty("shots.dir") ?: "build/shots").apply { mkdirs() }

    private fun shot(name: String) {
        repeat(3) { rule.waitForIdle(); Thread.sleep(400); rule.mainClock.advanceTimeBy(600) }
        rule.waitForIdle()
        val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
        File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun seed() = runBlocking {
        val db = AppDb.get(ApplicationProvider.getApplicationContext())
        val t = LocalDate.now()
        val sd = db.students()
        fun plan(fee: Long, n: Int, first: LocalDate) = buildPlan(fee, n, first)
        sd.create(Student(name = "Aarti Patil", mobile = "9876543210", aadhaar = "123412341234", course = "MPSC", totalFee = 30000, joinedOn = t.minusDays(40).toEpochDay()),
            plan(30000, 3, t.minusDays(35)).mapIndexed { i, p -> if (i == 0) p.copy(paidOn = t.minusDays(34).toEpochDay(), mode = "UPI") else p })
        sd.create(Student(name = "Rohan Deshmukh", mobile = "9822001122", course = "MPSC", totalFee = 25000, joinedOn = t.minusDays(70).toEpochDay()),
            plan(25000, 2, t.minusDays(60)).mapIndexed { i, p -> if (i == 0) p.copy(paidOn = t.minusDays(60).toEpochDay(), mode = "Cash") else p })
        sd.create(Student(name = "Sneha Kulkarni", mobile = "9890011223", course = "MPSC", totalFee = 18000, joinedOn = t.minusDays(20).toEpochDay()),
            plan(18000, 1, t.minusDays(20)).map { it.copy(paidOn = t.toEpochDay(), mode = "Cash") })
        sd.create(Student(name = "Vikas Jadhav", mobile = "9765432109", course = "MPSC", totalFee = 28000, joinedOn = t.minusDays(5).toEpochDay()),
            plan(28000, 4, t.plusDays(5)))
        val ed = db.enquiries()
        ed.insert(Enquiry(name = "Pooja Shinde", mobile = "9123456780", course = "MPSC", details = "Asked about weekend batch and fees in installments", createdOn = t.minusDays(2).toEpochDay(), followUpOn = t.toEpochDay()))
        ed.insert(Enquiry(name = "Amol Pawar", mobile = "9988776655", course = "MPSC", details = "Wants demo lecture", createdOn = t.minusDays(5).toEpochDay(), followUpOn = t.minusDays(1).toEpochDay(), status = "CONTACTED"))
        ed.insert(Enquiry(name = "Kiran More", mobile = "9001122334", course = "MPSC", createdOn = t.toEpochDay(), followUpOn = t.plusDays(2).toEpochDay()))
        val ld = db.library()
        listOf(1 to "Normal", 2 to "With locker", 3 to "Extra leg room").forEach { (id, n) -> ld.updateType(SeatType(id.toLong(), n, if (id == 1) 600 else 800, 500, 100)) }
        val names = listOf("Akash Shinde", "Priya Gaikwad", "Nikhil Bhosale", "Snehal Mane", "Omkar Kale", "Rutuja Salunkhe", "Sagar Thorat", "Komal Jagtap", "Tejas Chavan", "Manasi Joshi", "Prathamesh Ghule", "Shruti Wagh")
        val seats = listOf(1, 2, 3, 5, 6, 8, 9, 11, 12, 14, 17, 20)
        names.forEachIndexed { i, n ->
            val till = if (i == 3 || i == 7) t.minusDays(2) else t.plusDays((5 + i * 2).toLong())
            ld.addMember(LibraryMember(name = n, mobile = "98${(10000000 + i * 1234567).toString().take(8)}", seatNo = seats[i], seatType = if (i % 3 == 0) "With locker" else "Normal",
                monthlyFee = if (i % 3 == 0) 800 else 600, deposit = 500, depositPaid = true, joinedOn = t.minusDays(25).toEpochDay(), validTill = till.toEpochDay()),
                listOf(LibraryPayment(memberId = 0, kind = "DEPOSIT", amount = 500, paidOn = t.minusDays(25).toEpochDay()),
                    LibraryPayment(memberId = 0, kind = "FEE", amount = if (i % 3 == 0) 800 else 600, paidOn = t.minusDays(i.toLong()).toEpochDay())))
        }
        AppSettings.academyPhone = "98765 43210"
    }

    private fun nav(label: String) { rule.onAllNodesWithText(label).onLast().performClick() }

    @Test fun allScreens() {
        shot("01-lock-create")
        repeat(2) { listOf("1", "2", "3", "4").forEach { d -> rule.onNodeWithText(d).performClick() } }
        seed()
        shot("02-home")
        rule.onRoot().performTouchInput { swipeUp() }
        shot("03-home-scrolled")
        nav("Students"); shot("04-students")
        rule.onNodeWithText("Rohan Deshmukh").performClick(); shot("05-student-detail")
        rule.onNodeWithText("Remind").performClick(); shot("05b-reminder-sheet")
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        if (rule.onAllNodesWithContentDescription("Back").fetchSemanticsNodes().isNotEmpty()) rule.onNodeWithContentDescription("Back").performClick()
        rule.onNodeWithText("Add student").performClick(); shot("06-student-form")
        rule.onNodeWithContentDescription("Back").performClick()
        nav("Enquiries"); shot("07-enquiries")
        rule.onNodeWithText("Pooja Shinde").performClick(); shot("08-enquiry-detail")
        rule.onNodeWithContentDescription("Back").performClick()
        nav("Library"); shot("09-library")
        rule.onNodeWithText("Members (12)").performClick(); shot("10-library-members")
        rule.onNodeWithText("Snehal Mane").performClick(); shot("11-member-detail")
        rule.onNodeWithContentDescription("Back").performClick()
        nav("Settings"); shot("12-settings")
        rule.onNodeWithText("Backup & export").performClick(); shot("13-backup")
    }
}
