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

/** Renders the main screens to PNG files so the design can be reviewed without a phone. Sample data only. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = AcademyApp::class, qualifiers = "w393dp-h852dp-xhdpi")
class ScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val out = File(System.getProperty("shots.dir") ?: "build/shots").apply { mkdirs() }

    private fun settle() { repeat(3) { rule.waitForIdle(); Thread.sleep(400); rule.mainClock.advanceTimeBy(600) }; rule.waitForIdle() }
    private fun shot(name: String) {
        settle()
        val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
        File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun back() { rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }; settle() }
    private fun backButton() { if (rule.onAllNodesWithContentDescription("Back").fetchSemanticsNodes().isNotEmpty()) rule.onNodeWithContentDescription("Back").performClick(); settle() }
    private fun nav(label: String) { rule.onAllNodesWithText(label).onLast().performClick(); settle() }

    private fun seed() = runBlocking {
        val db = AppDb.get(ApplicationProvider.getApplicationContext())
        val t = LocalDate.now()
        val sd = db.students()
        fun day(d: Long) = t.plusDays(d).toEpochDay()
        val a = sd.create(Student(name = "Aarti Patil", mobile = "9876543210", aadhaar = "123412341234", course = "MPSC", totalFee = 30000, joinedOn = day(-40), batch = "Morning", dob = t.minusYears(22).toEpochDay()),
            buildPlan(30000, 3, t.minusDays(35)))
        sd.insertPayment(StudentPayment(studentId = a, amount = 10000, paidOn = day(-34), mode = "UPI"))
        val r = sd.create(Student(name = "Rohan Deshmukh", mobile = "9822001122", course = "MPSC", totalFee = 25000, joinedOn = day(-70), batch = "Evening"),
            listOf(Installment(studentId = 0, number = 1, amount = 10000, dueOn = day(-60)), Installment(studentId = 0, number = 2, amount = 8000, dueOn = day(-10)),
                Installment(studentId = 0, number = 3, amount = 7000, dueOn = day(20))))
        sd.insertPayment(StudentPayment(studentId = r, amount = 10000, paidOn = day(-60), mode = "Cash"))
        sd.insertPayment(StudentPayment(studentId = r, amount = 3000, paidOn = day(-8), mode = "UPI", note = "Rest next week"))
        val s3 = sd.create(Student(name = "Sneha Kulkarni", mobile = "9890011223", course = "MPSC", totalFee = 18000, joinedOn = day(-20), batch = "Weekend"), buildPlan(18000, 1, t.minusDays(20)))
        sd.insertPayment(StudentPayment(studentId = s3, amount = 18000, paidOn = day(0), mode = "Cash"))
        sd.create(Student(name = "Vikas Jadhav", mobile = "9765432109", course = "MPSC", totalFee = 28000, joinedOn = day(-5), batch = "Morning"), buildPlan(28000, 4, t.plusDays(3)))
        val ed = db.enquiries()
        ed.insert(Enquiry(name = "Pooja Shinde", mobile = "9123456780", course = "MPSC", details = "Asked about weekend batch and fees in installments", createdOn = day(-2), followUpOn = day(0), source = "Instagram"))
        ed.insert(Enquiry(name = "Amol Pawar", mobile = "9988776655", course = "MPSC", details = "Wants demo lecture", createdOn = day(-5), followUpOn = day(-1), status = "CONTACTED", source = "Friend / referral"))
        ed.insert(Enquiry(name = "Kiran More", mobile = "9001122334", course = "MPSC", createdOn = day(0), followUpOn = day(2), source = "Walk-in"))
        ed.insert(Enquiry(name = "Neha Kadam", mobile = "9001122335", course = "MPSC", createdOn = day(-6), followUpOn = day(-3), status = "JOINED", source = "Instagram"))
        val ld = db.library()
        ld.updateType(SeatType(1, "Normal", 600, 500, 100, "", "CHAIR"))
        ld.updateType(SeatType(2, "Extra leg room", 700, 500, 100, "9-16", "LEGROOM"))
        AppSettings.lockerFee = 200; AppSettings.lockerDeposit = 100
        val names = listOf("Akash Shinde", "Priya Gaikwad", "Nikhil Bhosale", "Snehal Mane", "Omkar Kale", "Rutuja Salunkhe", "Sagar Thorat", "Komal Jagtap", "Tejas Chavan", "Manasi Joshi", "Prathamesh Ghule", "Shruti Wagh")
        val seats = listOf(1, 2, 3, 5, 6, 8, 9, 11, 12, 14, 17, 20)
        names.forEachIndexed { i, n ->
            val till = if (i == 3 || i == 7) t.minusDays(2) else t.plusDays((4 + i * 2).toLong())
            val type = if (seats[i] in 9..16) "Extra leg room" else "Normal"
            val locker = i % 3 == 0
            val fee = (if (type == "Extra leg room") 700L else 600L) + if (locker) 200 else 0
            ld.addMember(LibraryMember(name = n, mobile = "98${(10000000 + i * 1234567).toString().take(8)}", seatNo = seats[i], seatType = type,
                monthlyFee = fee, deposit = 500, depositPaid = true, joinedOn = day(-25), validTill = till.toEpochDay(), dob = if (i == 1) t.minusYears(21).toEpochDay() else null,
                lockerNo = if (locker) "${i + 1}" else "", lockerFee = if (locker) 200 else 0),
                listOf(LibraryPayment(memberId = 0, kind = "DEPOSIT", amount = 500, paidOn = day(-25), mode = "Cash"),
                    LibraryPayment(memberId = 0, kind = "FEE", amount = fee, paidOn = day(-i.toLong()), mode = "UPI")))
        }
        ld.addMember(LibraryMember(name = "Old Member", mobile = "9811111111", seatNo = 7, seatType = "Normal", monthlyFee = 800, deposit = 500, depositPaid = true,
            depositReturnedOn = day(-30), joinedOn = day(-200), validTill = day(-30), active = false, leftOn = day(-30)), emptyList())
        val xd = db.expenses()
        xd.insert(Expense(category = "Rent", amount = 12000, spentOn = day(-3)))
        xd.insert(Expense(category = "Electricity", amount = 2400, spentOn = day(-2)))
        xd.insert(Expense(category = "Salary", amount = 8000, spentOn = day(-1), note = "Library attendant"))
        xd.insert(Expense(category = "Rent", amount = 12000, spentOn = t.minusMonths(1).toEpochDay()))
        AppSettings.academyPhone = "98765 43210"
    }

    @Test fun allScreens() {
        shot("00-welcome")
        rule.onNodeWithText("Use on this phone only").performClick(); settle()
        repeat(2) { listOf("1", "2", "3", "4").forEach { d -> rule.onNodeWithText(d).performClick() } }
        seed()
        runBlocking {
            val ld = AppDb.get(ApplicationProvider.getApplicationContext()).library()
            ld.insertPlan(MemberPlan(name = "3 months", months = 3, price = 1700))
            ld.insertPlan(MemberPlan(name = "6 months with locker", months = 6, price = 4200, withLocker = true))
            ld.allMembers().first { it.name == "Priya Gaikwad" }.let { ld.updateMember(it.copy(balance = 300, planName = "3 months")) }
        }
        shot("02-home")
        // the receipt, drawn as a picture (same drawing as the PDF)
        run {
            val bmp = android.graphics.Bitmap.createBitmap(840, 1190, android.graphics.Bitmap.Config.ARGB_8888)
            val cv = android.graphics.Canvas(bmp); cv.drawColor(android.graphics.Color.WHITE); cv.scale(2f, 2f)
            `in`.vidyadhan.academy.ui.drawReceipt(ApplicationProvider.getApplicationContext(), cv, `in`.vidyadhan.academy.ui.ReceiptData("VA-S00042", LocalDate.now().toEpochDay(),
                "FEE RECEIPT", "Rohan Deshmukh", listOf("Mobile" to "+91 98220 01122"),
                8800, listOf("Total fee" to "₹25,000", "Paid so far" to "₹21,000", "Balance" to "₹4,000"),
                forText = "MPSC course fee · Evening batch", forSub = "Installment 2 of 3 · UPI · 4512 8890 1123",
                nextAmount = 4000, nextDue = LocalDate.now().plusDays(17).toEpochDay()))
            File(out, "18-receipt.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            // the admission form, as a picture
            val fb = android.graphics.Bitmap.createBitmap(1190, 1684, android.graphics.Bitmap.Config.ARGB_8888)
            val fc = android.graphics.Canvas(fb); fc.drawColor(android.graphics.Color.WHITE); fc.scale(2f, 2f)
            `in`.vidyadhan.academy.ui.drawAdmissionForm(ApplicationProvider.getApplicationContext(), fc, `in`.vidyadhan.academy.ui.AdmissionDoc(
                "ADMISSION FORM", "VA-A0002", LocalDate.now().toEpochDay(), "Rohan Deshmukh", "9822001122", "", listOf("MPSC", "Evening", "Joined 12 Sep 2026"),
                null, "", "Sunil Deshmukh", "", "Flat 4, Sai Residency, Malwadi, Hadapsar, Pune 411028", "FEES · TOTAL ₹25,000",
                listOf(Triple("Installment 1 · 12 Sep 2026", "₹10,000", "PAID"), Triple("Installment 2 · 11 Nov 2026", "₹8,000", "PAID"), Triple("Installment 3 · 10 Dec 2026", "₹7,000", "₹4,000 DUE"))))
            File(out, "18b-admission.png").outputStream().use { fb.compress(Bitmap.CompressFormat.PNG, 100, it) }
            // the monthly report, both pages
            val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
            val rep = runBlocking { `in`.vidyadhan.academy.money.loadReport(ctx, java.time.YearMonth.now()) }
            listOf<(android.graphics.Canvas) -> Unit>({ `in`.vidyadhan.academy.money.drawReportPage1(ctx, it, rep) }, { `in`.vidyadhan.academy.money.drawReportPage2(ctx, it, rep) })
                .forEachIndexed { i, draw ->
                    val b = android.graphics.Bitmap.createBitmap(1190, 1684, android.graphics.Bitmap.Config.ARGB_8888)
                    val cv2 = android.graphics.Canvas(b); cv2.drawColor(android.graphics.Color.WHITE); cv2.scale(2f, 2f); draw(cv2)
                    File(out, "19-report-${i + 1}.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
                }
        }
        nav("Students")
        rule.waitUntil(30_000) { rule.onAllNodesWithText("Rohan Deshmukh").fetchSemanticsNodes().isNotEmpty() } // the list loads from the database
        shot("04-students")
        rule.onNodeWithText("Rohan Deshmukh").performClick(); shot("05-student-detail")
        rule.onNodeWithText("Split", substring = true).performScrollTo().performClick(); shot("05c-split")
        rule.onNodeWithText("Unequal").performClick(); settle(); shot("05d-split-unequal"); back()
        if (rule.onAllNodesWithText("Record a payment").fetchSemanticsNodes().isEmpty()) { rule.onNodeWithText("Rohan Deshmukh").performClick(); settle() }
        rule.onNodeWithText("Record a payment").performScrollTo().performClick(); shot("05b-payment-sheet"); back()
        backButton()
        rule.onNodeWithText("Admit").performClick(); shot("06-student-form")
        rule.onNodeWithText("Course fee").performTextInput("25000"); settle()
        rule.onNodeWithText("Equal plan").performScrollTo().performClick(); shot("06b-student-plan")
        backButton()
        nav("Settings"); shot("07-more")
        nav("Enquiries"); shot("07b-enquiries")

        // Seats: no room yet → create one → edit → view
        nav("Abhyasika"); rule.onNodeWithText("Room plan", substring = true).performClick(); settle(); shot("08-seats-start")
        rule.onNodeWithText("+ Create room").performClick(); shot("08b-room-edit")
        repeat(4) { rule.onNodeWithContentDescription("Top wall more").performScrollTo().performClick() }
        repeat(3) { rule.onNodeWithContentDescription("Bottom wall more").performScrollTo().performClick() }
        rule.onNodeWithContentDescription("Back-to-back double rows more").performScrollTo().performClick()
        rule.onNodeWithText("Apply layout", substring = true).performScrollTo().performClick(); settle()
        rule.onNodeWithContentDescription("Seat 1").performScrollTo(); shot("08b2-all-walls")
        rule.onNodeWithText("Live map").performClick(); shot("08c-room-plan")
        rule.onNodeWithContentDescription("Seat 7").performScrollTo().performClick(); shot("08d-seat-sheet"); back()

        nav("Abhyasika")
        if (rule.onAllNodesWithText("Members").fetchSemanticsNodes().isNotEmpty()) { rule.onNodeWithText("Members").performScrollTo().performClick(); settle() }
        shot("09-members")
        rule.onNode(hasSetTextAction()).performTextReplacement("Snehal"); settle()
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Snehal Mane"))
        rule.onNodeWithText("Snehal Mane").performClick(); shot("10-member-detail")
        rule.onNodeWithContentDescription("Edit").performClick(); shot("10b-member-edit")
        rule.onNodeWithText("Seat and locker").performScrollTo(); shot("10c-member-locker")
        backButton()
        backButton()
        rule.onNodeWithText("Plans").performClick(); shot("11-plans")
        backButton()
        rule.onNode(hasSetTextAction()).performTextReplacement("Akash"); settle()
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Akash Shinde"))
        rule.onNodeWithText("Akash Shinde").performClick(); settle()
        rule.onNodeWithText("Renew", substring = true).performScrollTo().performClick(); shot("12-renew-sheet"); back()
        backButton()

        nav("Settings"); rule.onNodeWithText("Reports").performScrollTo().performClick(); shot("13-money")
        rule.onNodeWithText("Expenses (3)").performClick(); shot("13b-expenses")
        nav("Settings"); rule.onNodeWithText("Broadcast").performScrollTo().performClick(); shot("14-broadcast")
        backButton()
        nav("Dashboard"); shot("15-dashboard")
        rule.onNodeWithContentDescription("Search").performClick(); settle()
        rule.onNodeWithText("Name, mobile, seat or locker no.").performTextInput("sh"); shot("16-search")
        backButton()
        nav("Settings"); rule.onNodeWithText("Telegram bot").performScrollTo().performClick(); shot("17-settings")
        back()
        // Telegram people: everyone listed, connected or not
        AppSettings.botToken = "test"; AppSettings.botName = "TestBot"; AppSettings.setTelegramChat("k_s1", 11L)
        nav("Dashboard"); nav("Settings"); rule.onNodeWithText("Telegram bot").performScrollTo().performClick(); settle()
        rule.onNodeWithText("Telegram people", substring = true).performScrollTo().performClick(); settle(); shot("17b-tg-people")
        rule.onAllNodesWithText("Not connected", substring = true)[0].performClick(); settle(); shot("17c-tg-not-connected")
        rule.onAllNodesWithText("Connect")[0].performClick(); settle(); shot("17d-tg-connect")
        back(); back(); back()
        AppSettings.botToken = ""; AppSettings.botName = ""; AppSettings.clearTelegramChat("k_s1")
        // account card and devices
        val now = System.currentTimeMillis()
        `in`.vidyadhan.academy.cloud.Cloud.previewSignedIn("vidyadhan.academy@gmail.com")
        `in`.vidyadhan.academy.cloud.Devices.preview(listOf(
            `in`.vidyadhan.academy.cloud.Devices.Device(AppSettings.installId, "Samsung Galaxy M34", "0.7.76", now, false),
            `in`.vidyadhan.academy.cloud.Devices.Device("b", "Redmi Note 12", "0.7.76", now - 10 * 60_000, false),
            `in`.vidyadhan.academy.cloud.Devices.Device("c", "Vivo Y21", "0.7.60", now - 12L * 86_400_000, false)), AppSettings.installId)
        nav("Dashboard"); nav("Settings"); settle(); shot("17e-account-card")
        rule.onNodeWithText("Devices", substring = true).performClick(); settle(); shot("17f-devices")
        back()
        `in`.vidyadhan.academy.cloud.Devices.preview(emptyList(), "")
        `in`.vidyadhan.academy.cloud.Cloud.previewSignedOut()

        // the same main screens with large text, as on the owner's phone (catches cut-off and overflowing text)
        org.robolectric.RuntimeEnvironment.setFontScale(1.3f)
        rule.activityRule.scenario.recreate(); settle()
        nav("Dashboard"); shot("L1-dashboard")
        nav("Students"); shot("L2-students")
        rule.onNodeWithText("Rohan Deshmukh").performClick(); shot("L3-student-sheet")
        rule.onNodeWithText("Split", substring = true).performScrollTo().performClick(); rule.onNodeWithText("Unequal").performClick(); settle(); shot("L3b-split-unequal"); back(); back()
        nav("Enquiries"); shot("L4-enquiries")
        rule.onNodeWithText("+ New Admission").performClick(); settle()
        rule.onNodeWithText("Course fee").performTextInput("45000"); settle()
        rule.onNodeWithText("Unequal plan").performScrollTo(); shot("L5-admission-plan")
        nav("Abhyasika"); shot("L6-room")
        nav("Settings"); shot("L7-bot")
        `in`.vidyadhan.academy.cloud.Cloud.previewSignedIn("vidyadhan.academy@gmail.com")
        `in`.vidyadhan.academy.cloud.Devices.preview(listOf(
            `in`.vidyadhan.academy.cloud.Devices.Device(AppSettings.installId, "Samsung Galaxy M34", "0.7.76", System.currentTimeMillis(), false),
            `in`.vidyadhan.academy.cloud.Devices.Device("b", "Redmi Note 12", "0.7.76", System.currentTimeMillis() - 600_000, false)), AppSettings.installId)
        settle(); shot("L8-account-card")
        rule.onNodeWithText("Devices", substring = true).performClick(); settle(); shot("L9-devices")
    }
}
