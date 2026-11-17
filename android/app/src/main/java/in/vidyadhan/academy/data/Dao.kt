package `in`.vidyadhan.academy.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface StudentDao {
    @Query("SELECT * FROM Student ORDER BY name COLLATE NOCASE") fun students(): Flow<List<Student>>
    @Query("SELECT * FROM Installment ORDER BY studentId, number") fun allInstallments(): Flow<List<Installment>>
    @Query("SELECT * FROM StudentPayment ORDER BY paidOn DESC, id DESC") fun allPayments(): Flow<List<StudentPayment>>

    @Query("SELECT * FROM Student WHERE id = :id") fun student(id: Long): Flow<Student?>
    @Query("SELECT * FROM Installment WHERE studentId = :id ORDER BY number") fun installments(id: Long): Flow<List<Installment>>
    @Query("SELECT * FROM StudentPayment WHERE studentId = :id ORDER BY paidOn DESC, id DESC") fun payments(id: Long): Flow<List<StudentPayment>>

    @Query("SELECT * FROM Student ORDER BY name COLLATE NOCASE") suspend fun allStudents(): List<Student>
    @Query("SELECT * FROM Installment") suspend fun installmentList(): List<Installment>
    @Query("SELECT * FROM StudentPayment") suspend fun paymentList(): List<StudentPayment>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM StudentPayment WHERE paidOn BETWEEN :from AND :to") fun paidBetween(from: Long, to: Long): Flow<Long>
    @Query("SELECT s.name AS name, p.amount AS amount, p.paidOn AS paidOn, 'STUDENT_FEE' AS kind FROM StudentPayment p JOIN Student s ON s.id = p.studentId ORDER BY p.paidOn DESC, p.id DESC LIMIT 8")
    fun recentPaid(): Flow<List<PaymentRow>>

    @Insert suspend fun insert(s: Student): Long
    @Update suspend fun update(s: Student)
    @Delete suspend fun delete(s: Student)
    @Insert suspend fun insertInstallments(list: List<Installment>)
    @Query("DELETE FROM Installment WHERE studentId = :id") suspend fun clearInstallments(id: Long)
    @Insert suspend fun insertPayment(p: StudentPayment): Long
    @Delete suspend fun deletePayment(p: StudentPayment)

    @Transaction
    suspend fun create(s: Student, plan: List<Installment>, firstPayment: StudentPayment? = null): Long {
        val id = insert(s)
        insertInstallments(plan.map { it.copy(id = 0, studentId = id) })
        firstPayment?.let { insertPayment(it.copy(studentId = id)) }
        return id
    }

    @Transaction
    suspend fun replacePlan(s: Student, plan: List<Installment>) {
        update(s)
        clearInstallments(s.id)
        insertInstallments(plan.map { it.copy(id = 0, studentId = s.id) })
    }
}

@Dao
interface EnquiryDao {
    @Query("SELECT * FROM Enquiry ORDER BY followUpOn, id DESC") fun all(): Flow<List<Enquiry>>
    @Query("SELECT * FROM Enquiry WHERE id = :id") fun get(id: Long): Flow<Enquiry?>
    @Query("SELECT COUNT(*) FROM Enquiry WHERE status IN ('NEW','CONTACTED') AND followUpOn <= :day") suspend fun dueCount(day: Long): Int
    @Insert suspend fun insert(e: Enquiry): Long
    @Update suspend fun update(e: Enquiry)
    @Delete suspend fun delete(e: Enquiry)
}

@Dao
interface LibraryDao {
    @Query("SELECT * FROM SeatType ORDER BY id") fun seatTypes(): Flow<List<SeatType>>
    @Insert suspend fun insertType(t: SeatType)
    @Update suspend fun updateType(t: SeatType)
    @Delete suspend fun deleteType(t: SeatType)

    @Query("SELECT * FROM LibraryMember ORDER BY name COLLATE NOCASE") fun members(): Flow<List<LibraryMember>>
    @Query("SELECT * FROM LibraryMember WHERE id = :id") fun member(id: Long): Flow<LibraryMember?>
    @Query("SELECT * FROM LibraryMember WHERE seatNo = :seat ORDER BY joinedOn DESC") fun seatHistory(seat: Int): Flow<List<LibraryMember>>
    @Query("SELECT * FROM LibraryPayment WHERE memberId = :id ORDER BY paidOn DESC, id DESC") fun payments(id: Long): Flow<List<LibraryPayment>>
    @Query("SELECT * FROM LibraryPayment ORDER BY paidOn DESC") fun allPayments(): Flow<List<LibraryPayment>>
    @Query("SELECT COUNT(*) FROM LibraryMember WHERE active = 1 AND validTill <= :day") suspend fun expiringCount(day: Long): Int
    @Query("SELECT * FROM LibraryMember ORDER BY name COLLATE NOCASE") suspend fun allMembers(): List<LibraryMember>
    @Query("SELECT * FROM LibraryMember WHERE id = :id") suspend fun memberNow(id: Long): LibraryMember?
    @Query("SELECT COALESCE(SUM(amount), 0) FROM LibraryPayment WHERE kind IN ('FEE','REGISTRATION') AND paidOn BETWEEN :from AND :to") fun collectedBetween(from: Long, to: Long): Flow<Long>
    @Query("SELECT m.name AS name, p.amount AS amount, p.paidOn AS paidOn, p.kind AS kind FROM LibraryPayment p JOIN LibraryMember m ON m.id = p.memberId ORDER BY p.paidOn DESC, p.id DESC LIMIT 8")
    fun recentPayments(): Flow<List<PaymentRow>>

    @Insert suspend fun insertMember(m: LibraryMember): Long
    @Update suspend fun updateMember(m: LibraryMember)
    @Delete suspend fun deleteMember(m: LibraryMember)
    @Insert suspend fun insertPayment(p: LibraryPayment): Long

    @Transaction
    suspend fun addMember(m: LibraryMember, pays: List<LibraryPayment>): Long {
        val id = insertMember(m)
        pays.forEach { insertPayment(it.copy(memberId = id)) }
        return id
    }
    @Transaction
    suspend fun pay(m: LibraryMember, p: LibraryPayment): Long { updateMember(m); return insertPayment(p) }
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM Note WHERE kind = :kind AND ownerId = :id ORDER BY createdAt DESC") fun notes(kind: String, id: Long): Flow<List<Note>>
    @Insert suspend fun insert(n: Note)
    @Delete suspend fun delete(n: Note)
}

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM Expense ORDER BY spentOn DESC, id DESC") fun all(): Flow<List<Expense>>
    @Insert suspend fun insert(e: Expense)
    @Update suspend fun update(e: Expense)
    @Delete suspend fun delete(e: Expense)
}

/** Version 3 → 4: lockers become an add-on any seat can have, instead of a seat type. */
class V3toV4 : AutoMigrationSpec {
    override fun onPostMigrate(db: SupportSQLiteDatabase) {
        db.execSQL("UPDATE LibraryMember SET lockerNo = '-', seatType = 'Normal' WHERE seatType LIKE '%locker%'")
        db.execSQL("DELETE FROM SeatType WHERE icon = 'LOCKER' OR name LIKE '%locker%'")
    }
}

/** Version 2 → 3: copies old "paid" marks into real payment records and gives seat types their icons. */
class V2toV3 : AutoMigrationSpec {
    override fun onPostMigrate(db: SupportSQLiteDatabase) {
        db.execSQL("INSERT INTO StudentPayment(studentId, amount, paidOn, mode, note) SELECT studentId, amount, paidOn, mode, '' FROM Installment WHERE paidOn IS NOT NULL")
        db.execSQL("UPDATE SeatType SET icon = 'LOCKER' WHERE name LIKE '%locker%'")
        db.execSQL("UPDATE SeatType SET icon = 'LEGROOM' WHERE name LIKE '%leg%'")
    }
}

@Database(
    entities = [Student::class, Installment::class, StudentPayment::class, Enquiry::class, SeatType::class, LibraryMember::class, LibraryPayment::class, Note::class, Expense::class],
    version = 4, exportSchema = true,
    autoMigrations = [AutoMigration(from = 2, to = 3, spec = V2toV3::class), AutoMigration(from = 3, to = 4, spec = V3toV4::class)],
)
abstract class AppDb : RoomDatabase() {
    abstract fun students(): StudentDao
    abstract fun enquiries(): EnquiryDao
    abstract fun library(): LibraryDao
    abstract fun notes(): NoteDao
    abstract fun expenses(): ExpenseDao

    companion object {
        @Volatile private var inst: AppDb? = null
        fun get(ctx: Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, AppDb::class.java, "academy.db")
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        // Starting seat types; fees and seat numbers are set by the owner in Settings.
                        db.execSQL("INSERT INTO SeatType(name, fee, deposit, regFee, seats, icon) VALUES('Normal', 0, 0, 0, '', 'CHAIR')")
                        db.execSQL("INSERT INTO SeatType(name, fee, deposit, regFee, seats, icon) VALUES('Extra leg room', 0, 0, 0, '', 'LEGROOM')")
                    }
                })
                .fallbackToDestructiveMigrationFrom(true, 1) // only the very first test builds (version 1) are reset
                .build().also { inst = it }
        }
        fun dbFileName() = "academy.db"
        fun closeForRestore() { inst?.close(); inst = null }
    }
}
