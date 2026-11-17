package `in`.vidyadhan.academy

import android.app.Application
import `in`.vidyadhan.academy.notify.Reminders

class AcademyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppSettings.init(this)
        Reminders.createChannel(this)
        Reminders.schedule(this)
    }
}
