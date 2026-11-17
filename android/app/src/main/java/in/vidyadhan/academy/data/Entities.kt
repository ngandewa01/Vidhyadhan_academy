package `in`.vidyadhan.academy.data

import androidx.room.*

@Entity
data class Student(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val mobile: String,
    val aadhaar: String = "",
    val course: String = "MPSC",
    val totalFee: Long,
    val joinedOn: Long, // epoch day
    val notes: String = "",
    @ColumnInfo(defaultValue = "") val batch: String = "",
    val dob: Long? = null, // epoch day
    @ColumnInfo(defaultValue = "") val photo: String = "",
    @ColumnInfo(defaultValue = "") val aadhaarPhoto: String = "",
)

/** The planned schedule. What has actually been paid lives in [StudentPayment]. */
@Entity(
    foreignKeys = [ForeignKey(Student::class, ["id"], ["studentId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("studentId")],
)
data class Installment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val studentId: Long,
    val number: Int,
    val amount: Long,
    val dueOn: Long, // epoch day
    val paidOn: Long? = null, // no longer used (kept for old data)
    val mode: String = "",
)

/** Money actually received from a student: any amount, on any day. */
@Entity(
    foreignKeys = [ForeignKey(Student::class, ["id"], ["studentId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("studentId")],
)
data class StudentPayment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val studentId: Long,
    val amount: Long,
    val paidOn: Long,
    @ColumnInfo(defaultValue = "") val mode: String = "",
    @ColumnInfo(defaultValue = "") val note: String = "",
)

@Entity
data class Enquiry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val mobile: String,
    val course: String = "",
    val details: String = "",
    val createdOn: Long,
    val followUpOn: Long,
    val status: String = "NEW", // NEW, CONTACTED, JOINED, CLOSED
    @ColumnInfo(defaultValue = "") val source: String = "",
)

@Entity
data class SeatType(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val fee: Long = 0,
    val deposit: Long = 0,
    val regFee: Long = 0,
    @ColumnInfo(defaultValue = "") val seats: String = "", // e.g. "1-10, 15"
    @ColumnInfo(defaultValue = "CHAIR") val icon: String = "CHAIR", // CHAIR, LEGROOM, LOCKER
)

@Entity
data class LibraryMember(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val mobile: String,
    val aadhaar: String = "",
    val seatNo: Int,
    val seatType: String = "",
    val monthlyFee: Long = 0,
    val deposit: Long = 0,
    val depositPaid: Boolean = false,
    val depositReturnedOn: Long? = null,
    val joinedOn: Long,
    val validTill: Long, // epoch day; fee paid up to this day
    val active: Boolean = true,
    val dob: Long? = null,
    @ColumnInfo(defaultValue = "") val photo: String = "",
    @ColumnInfo(defaultValue = "") val aadhaarPhoto: String = "",
    val leftOn: Long? = null,
)

@Entity(
    foreignKeys = [ForeignKey(LibraryMember::class, ["id"], ["memberId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("memberId")],
)
data class LibraryPayment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val memberId: Long,
    val kind: String, // FEE, DEPOSIT, REGISTRATION, DEPOSIT_RETURN
    val amount: Long,
    val paidOn: Long,
    @ColumnInfo(defaultValue = "") val mode: String = "",
)

/** A dated note on a student, library member or enquiry. */
@Entity(indices = [Index("kind", "ownerId")])
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String, // STUDENT, MEMBER, ENQUIRY
    val ownerId: Long,
    val text: String,
    val createdAt: Long, // epoch millis
)

@Entity
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String,
    val amount: Long,
    val spentOn: Long, // epoch day
    val note: String = "",
)

/** One student with their fee position worked out from the plan and the payments. */
data class StudentWithFees(
    val id: Long,
    val name: String,
    val mobile: String,
    val aadhaar: String,
    val course: String,
    val batch: String,
    val photo: String,
    val dob: Long?,
    val totalFee: Long,
    val paid: Long,
    val nextDue: Long?, // due date of the first installment not fully covered
    val nextDueAmount: Long, // what is still owed on that installment
)

/** One received payment, for "Recent payments" (not stored as its own table). */
data class PaymentRow(val name: String, val amount: Long, val paidOn: Long, val kind: String)
