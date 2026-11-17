package `in`.vidyadhan.academy.students

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import `in`.vidyadhan.academy.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class StudentsViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDb.get(app).students()
    val list = dao.listWithFees().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun student(id: Long) = dao.student(id)
    fun installments(id: Long) = dao.installments(id)

    fun save(s: Student, plan: List<Installment>) = viewModelScope.launch {
        if (s.id == 0L) dao.create(s, plan) else dao.replacePlan(s, plan)
    }
    fun delete(s: Student) = viewModelScope.launch { dao.delete(s) }
    fun markPaid(i: Installment, mode: String) = viewModelScope.launch { dao.updateInstallment(i.copy(paidOn = today(), mode = mode)) }
    fun undoPaid(i: Installment) = viewModelScope.launch { dao.updateInstallment(i.copy(paidOn = null, mode = "")) }
}
