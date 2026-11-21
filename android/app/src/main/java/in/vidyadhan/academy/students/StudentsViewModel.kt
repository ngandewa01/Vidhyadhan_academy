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

    private val khata = AppDb.get(app).khata()
    /** Every old khata line, for the balance shown on a linked student. */
    val khataEntries = khata.all().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val lib = AppDb.get(app).library()
    /** Abhyasika members, to show a student's seat, locker and deposit (Combo). */
    val members = lib.members().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun memberFor(id: Long) = lib.memberFor(id)
    fun link(m: LibraryMember, studentId: Long?) = viewModelScope.launch { lib.updateMember(m.copy(studentId = studentId)) }
    fun returnDeposit(m: LibraryMember) = viewModelScope.launch {
        lib.pay(m.copy(depositReturnedOn = today()), LibraryPayment(memberId = m.id, kind = "DEPOSIT_RETURN", amount = m.deposit, paidOn = today(), mode = "Cash"))
    }
    /** Keeps what is paid and divides the rest equally over the installments not yet paid (same due dates). */
    fun resplit(s: Student, plan: List<Installment>, paid: Long) = viewModelScope.launch {
        val st = allocate(plan, paid)
        val done = st.filter { it.fullyPaid }.map { it.inst }
        val partly = st.filter { it.partlyPaid }.map { it.inst.copy(amount = it.covered) }
        val open = st.filter { !it.fullyPaid }
        val rest = s.totalFee - done.sumOf { it.amount } - partly.sumOf { it.amount }
        if (open.isEmpty() || rest <= 0) return@launch
        val n = open.size
        val split = open.mapIndexed { i, o -> o.inst.copy(amount = rest / n + if (i == 0) rest % n else 0, label = "") }
        val all = (done + partly + split).sortedWith(compareBy({ it.dueOn }, { it.number })).mapIndexed { i, x -> x.copy(id = 0, number = i + 1) }
        dao.replacePlan(s, all)
    }
    fun student(id: Long) = dao.student(id)
    fun installments(id: Long) = dao.installments(id)
    fun payments(id: Long) = dao.payments(id)

    fun save(s: Student, plan: List<Installment>, onSaved: (Long) -> Unit = {}) = viewModelScope.launch {
        if (s.id == 0L) onSaved(dao.create(s, plan)) else { dao.replacePlan(s, plan); onSaved(s.id) }
    }
    fun update(s: Student) = viewModelScope.launch { AppDb.get(getApplication()).students().update(s) }
    fun delete(s: Student) = viewModelScope.launch { dao.delete(s) }
    fun pay(p: StudentPayment, onSaved: (StudentPayment) -> Unit = {}) = viewModelScope.launch {
        val id = dao.insertPayment(p); onSaved(p.copy(id = id))
        `in`.vidyadhan.academy.telegram.TelegramBot.studentPaid(getApplication(), p.studentId, p.amount, p.mode, id)
    }
    fun deletePayment(p: StudentPayment) = viewModelScope.launch { dao.deletePayment(p) }
    fun updatePayment(p: StudentPayment) = viewModelScope.launch { dao.updatePayment(p) }
}
