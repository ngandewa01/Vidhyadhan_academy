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

    @Test fun v3ToV4TurnsLockerSeatsIntoLockerAddOn() {
        helper.createDatabase("mig4.db", 3).apply {
            execSQL("INSERT INTO SeatType(name,fee,deposit,regFee,seats,icon) VALUES('Normal',600,500,100,'','CHAIR')")
            execSQL("INSERT INTO SeatType(name,fee,deposit,regFee,seats,icon) VALUES('With locker',800,500,100,'','LOCKER')")
            execSQL("INSERT INTO SeatType(name,fee,deposit,regFee,seats,icon) VALUES('Extra leg room',700,500,100,'','LEGROOM')")
            execSQL("INSERT INTO LibraryMember(name,mobile,aadhaar,seatNo,seatType,monthlyFee,deposit,depositPaid,joinedOn,validTill,active,photo,aadhaarPhoto) VALUES('A','9000000001','',3,'With locker',800,500,1,20000,20030,1,'','')")
            execSQL("INSERT INTO LibraryMember(name,mobile,aadhaar,seatNo,seatType,monthlyFee,deposit,depositPaid,joinedOn,validTill,active,photo,aadhaarPhoto) VALUES('B','9000000002','',9,'Extra leg room',700,500,1,20000,20030,1,'','')")
            close()
        }
        val db = helper.runMigrationsAndValidate("mig4.db", 4, true)
        db.query("SELECT seatType, lockerNo, monthlyFee FROM LibraryMember ORDER BY name").use { c ->
            c.moveToFirst(); assertEquals("Normal", c.getString(0)); assertEquals("-", c.getString(1)); assertEquals(800L, c.getLong(2))
            c.moveToNext(); assertEquals("Extra leg room", c.getString(0)); assertEquals("", c.getString(1))
        }
        db.query("SELECT COUNT(*) FROM SeatType").use { c -> c.moveToFirst(); assertEquals(2, c.getInt(0)) }
    }

    @Test fun v4ToV5AddsFormerStudentFields() {
        helper.createDatabase("mig5.db", 4).apply {
            execSQL("INSERT INTO Student(id,name,mobile,aadhaar,course,totalFee,joinedOn,notes,batch,photo,aadhaarPhoto) VALUES(1,'X','9876543210','','MPSC',100,1,'','','','')")
            close()
        }
        val db = helper.runMigrationsAndValidate("mig5.db", 5, true)
        db.query("SELECT leftOn, leftReason FROM Student").use { c -> c.moveToFirst(); assertEquals(true, c.isNull(0)); assertEquals("", c.getString(1)) }
    }

    @Test fun v5ToV6AddsRoomsPlansAndBalance() {
        helper.createDatabase("mig6.db", 5).apply {
            execSQL("INSERT INTO LibraryMember(name,mobile,aadhaar,seatNo,seatType,monthlyFee,deposit,depositPaid,joinedOn,validTill,active,photo,aadhaarPhoto,lockerNo,lockerFee) VALUES('A','9000000001','',3,'Normal',800,500,1,20000,20030,1,'','','',0)")
            close()
        }
        val db = helper.runMigrationsAndValidate("mig6.db", 6, true)
        db.query("SELECT balance, planName, seatNo FROM LibraryMember").use { c ->
            c.moveToFirst(); assertEquals(0L, c.getLong(0)); assertEquals("", c.getString(1)); assertEquals(3, c.getInt(2))
        }
        db.execSQL("INSERT INTO LibraryRoom(name, layout) VALUES('Lab 1', '1 2 3')")
        db.execSQL("INSERT INTO MemberPlan(name, months, price) VALUES('3 months', 3, 2400)")
        db.query("SELECT sortOrder FROM LibraryRoom").use { c -> c.moveToFirst(); assertEquals(0, c.getInt(0)) }
        db.query("SELECT withLocker, active FROM MemberPlan").use { c -> c.moveToFirst(); assertEquals(0, c.getInt(0)); assertEquals(1, c.getInt(1)) }
    }
}
