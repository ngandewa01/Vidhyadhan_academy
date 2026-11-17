package `in`.vidyadhan.academy.data

import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface StudentDao {
    @Query(
        """SELECT s.id, s.name, s.mobile, s.aadhaar, s.course, s.totalFee,
        COALESCE((SELECT SUM(amount) FROM Installment i WHERE i.studentId = s.id AND i.paidOn IS NOT NULL), 0) AS paid,
        (SELECT MIN(dueOn) FROM Installment i WHERE i.studentId = s.id AND i.paidOn IS NULL) AS nextDue
        FROM Student s ORDER BY s.name COLLATE NOCASE"""
    )
    fun listWithFees(): Flow<List<StudentWithFees>>

    @Query("SELECT * FROM Student WHERE id = :id") fun student(id: Long): Flow<Student?>
    @Query("SELECT * FROM Installment WHERE studentId = :id ORDER BY number") fun installments(id: Long): Flow<List<Installment>>
    @Query("SELECT COUNT(DISTINCT studentId) FROM Installment WHERE paidOn IS NULL AND dueOn <= :day") suspend fun dueCount(day: Long): Int
    @Query("SELECT * FROM Student ORDER BY name COLLATE NOCASE") suspend fun allStudents(): List<Student>

    @Insert suspend fun insert(s: Student): Long
    @Update suspend fun update(s: Student)
    @Delete suspend fun delete(s: Student)
    @Insert suspend fun insertInstallments(list: List<Installment>)
    @Update suspend fun updateInstallment(i: Installment)
    @Query("DELETE FROM Installment WHERE studentId = :id") suspend fun clearInstallments(id: Long)

    @Transaction
    suspend fun create(s: Student, plan: List<Installment>) {
        val id = insert(s)
        insertInstallments(plan.map { it.copy(studentId = id) })
    }

    @Transaction
    suspend fun replacePlan(s: Student, plan: List<Installment>) {
        update(s)
        clearInstallments(s.id)
        insertInstallments(plan.map { it.copy(studentId = s.id) })
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
    @Query("SELECT * FROM LibraryPayment WHERE memberId = :id ORDER BY paidOn DESC, id DESC") fun payments(id: Long): Flow<List<LibraryPayment>>
    @Query("SELECT COUNT(*) FROM LibraryMember WHERE active = 1 AND validTill <= :day") suspend fun expiringCount(day: Long): Int
    @Query("SELECT * FROM LibraryMember ORDER BY name COLLATE NOCASE") suspend fun allMembers(): List<LibraryMember>

    @Insert suspend fun insertMember(m: LibraryMember): Long
    @Update suspend fun updateMember(m: LibraryMember)
    @Delete suspend fun deleteMember(m: LibraryMember)
    @Insert suspend fun insertPayment(p: LibraryPayment)

    @Transaction
    suspend fun addMember(m: LibraryMember, pays: List<LibraryPayment>) {
        val id = insertMember(m)
        pays.forEach { insertPayment(it.copy(memberId = id)) }
    }
    @Transaction
    suspend fun pay(m: LibraryMember, p: LibraryPayment) { updateMember(m); insertPayment(p) }
}

@Database(
    entities = [Student::class, Installment::class, Enquiry::class, SeatType::class, LibraryMember::class, LibraryPayment::class],
    version = 2, exportSchema = false,
)
abstract class AppDb : RoomDatabase() {
    abstract fun students(): StudentDao
    abstract fun enquiries(): EnquiryDao
    abstract fun library(): LibraryDao

    companion object {
        @Volatile private var inst: AppDb? = null
        fun get(ctx: android.content.Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, AppDb::class.java, "academy.db")
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        // Starting seat types; fees are set by the owner in Settings.
                        listOf("Normal", "With locker", "Extra leg room").forEach {
                            db.execSQL("INSERT INTO SeatType(name, fee, deposit, regFee) VALUES('$it', 0, 0, 0)")
                        }
                    }
                })
                .fallbackToDestructiveMigration(true) // test builds only; real migrations before handover
                .build().also { inst = it }
        }
        fun dbFileName() = "academy.db"
        fun closeForRestore() { inst?.close(); inst = null }
    }
}
