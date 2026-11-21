package `in`.vidyadhan.academy

import android.app.Application
import androidx.work.Configuration
import `in`.vidyadhan.academy.notify.Reminders

class AcademyApp : Application(), Configuration.Provider {
    // WorkManager (daily reminders) is set up here explicitly so it never depends on automatic start-up.
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()

    override fun onCreate() {
        super.onCreate()
        AppSettings.init(this)
        runCatching { `in`.vidyadhan.academy.cloud.Cloud.start(this) } // cloud sync must never stop the app opening
        runCatching { Reminders.createChannel(this) }
        runCatching { Reminders.schedule(this) } // a reminder problem must never stop the app opening
        runCatching { `in`.vidyadhan.academy.ai.News.load(this); `in`.vidyadhan.academy.ai.News.schedule(this) }
        `in`.vidyadhan.academy.telegram.TelegramBot.app = this
        runCatching { `in`.vidyadhan.academy.telegram.BotWorker.schedule(this) }
    }
}
