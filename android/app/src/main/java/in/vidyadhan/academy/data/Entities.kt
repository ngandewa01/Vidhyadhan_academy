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
