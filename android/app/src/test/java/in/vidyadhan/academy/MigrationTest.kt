package `in`.vidyadhan.academy

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import `in`.vidyadhan.academy.data.AppDb
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The owner's phone has version-2 data; upgrading must keep every record and turn old "paid" marks into payments. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = AcademyApp::class)
class MigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDb::class.java)

    @Test fun v2ToV3KeepsDataAndCopiesPayments() {
        helper.createDatabase("mig.db", 2).apply {
            execSQL("INSERT INTO Student(id,name,mobile,aadhaar,course,totalFee,joinedOn,notes) VALUES(1,'Test One','9876543210','', 'MPSC', 30000, 20000, '')")
            execSQL("INSERT INTO Installment(studentId,number,amount,dueOn,paidOn,mode) VALUES(1,1,10000,20000,20001,'UPI')")
            execSQL("INSERT INTO Installment(studentId,number,amount,dueOn,paidOn,mode) VALUES(1,2,10000,20030,NULL,'')")
            execSQL("INSERT INTO SeatType(name,fee,deposit,regFee) VALUES('Normal',600,500,100)")
            execSQL("INSERT INTO SeatType(name,fee,deposit,regFee) VALUES('With locker',800,500,100)")
            execSQL("INSERT INTO Enquiry(name,mobile,course,details,createdOn,followUpOn,status) VALUES('Asha','9000000000','MPSC','',20000,20002,'NEW')")
            close()
        }
        val db = helper.runMigrationsAndValidate("mig.db", 3, true)
        db.query("SELECT amount, paidOn, mode FROM StudentPayment").use { c ->
            assertEquals(1, c.count); c.moveToFirst()
            assertEquals(10000L, c.getLong(0)); assertEquals(20001L, c.getLong(1)); assertEquals("UPI", c.getString(2))
        }
        db.query("SELECT icon FROM SeatType ORDER BY id").use { c -> c.moveToFirst(); assertEquals("CHAIR", c.getString(0)); c.moveToNext(); assertEquals("LOCKER", c.getString(0)) }
        db.query("SELECT COUNT(*) FROM Enquiry").use { c -> c.moveToFirst(); assertEquals(1, c.getInt(0)) }
    }
}
