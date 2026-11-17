package `in`.vidyadhan.academy.students

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import `in`.vidyadhan.academy.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class StudentsViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDb.get(app).students()
    val list = studentsWithFees(dao).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun student(id: Long) = dao.student(id)
    fun installments(id: Long) = dao.installments(id)
    fun payments(id: Long) = dao.payments(id)

    fun save(s: Student, plan: List<Installment>, onSaved: (Long) -> Unit = {}) = viewModelScope.launch {
        if (s.id == 0L) onSaved(dao.create(s, plan)) else { dao.replacePlan(s, plan); onSaved(s.id) }
    }
    fun delete(s: Student) = viewModelScope.launch { dao.delete(s) }
    fun pay(p: StudentPayment, onSaved: (StudentPayment) -> Unit = {}) = viewModelScope.launch {
        val id = dao.insertPayment(p); onSaved(p.copy(id = id))
    }
    fun deletePayment(p: StudentPayment) = viewModelScope.launch { dao.deletePayment(p) }
}
