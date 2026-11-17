package `in`.vidyadhan.academy.data

import androidx.room.*
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

@Database(entities = [Student::class, Installment::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun students(): StudentDao

    companion object {
        @Volatile private var inst: AppDb? = null
        fun get(ctx: android.content.Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, AppDb::class.java, "academy.db").build().also { inst = it }
        }
    }
}
