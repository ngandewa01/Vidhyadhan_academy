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
)

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
    val paidOn: Long? = null,
    val mode: String = "", // Cash / UPI / Bank
)

data class StudentWithFees(
    val id: Long,
    val name: String,
    val mobile: String,
    val aadhaar: String,
    val course: String,
    val totalFee: Long,
    val paid: Long,
    val nextDue: Long?, // earliest unpaid due date
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
)

@Entity
data class SeatType(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val fee: Long = 0,
    val deposit: Long = 0,
    val regFee: Long = 0,
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
)
