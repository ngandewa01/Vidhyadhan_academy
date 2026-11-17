package `in`.vidyadhan.academy.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import `in`.vidyadhan.academy.AppSettings
import `in`.vidyadhan.academy.MainActivity
import `in`.vidyadhan.academy.R
import `in`.vidyadhan.academy.data.AppDb
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

object Reminders {
    private const val CH = "daily"

    fun createChannel(ctx: Context) {
        val ch = NotificationChannel(CH, "Daily reminders", NotificationManager.IMPORTANCE_DEFAULT)
        ch.description = "Enquiry follow-ups, fees due and library renewals"
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    /** Schedules one check every day at the time chosen in Settings. */
    fun schedule(ctx: Context) {
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(AppSettings.reminderHour, AppSettings.reminderMinute)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val delay = Duration.between(now, next).toMinutes()
        val req = PeriodicWorkRequestBuilder<DailyWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delay, TimeUnit.MINUTES).build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("daily", ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    fun notify(ctx: Context, text: String) {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(ctx, CH)
            .setSmallIcon(R.drawable.ic_notify).setContentTitle(AppSettings.academyName)
            .setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open).setAutoCancel(true).build()
        NotificationManagerCompat.from(ctx).notify(1, n)
    }
}

class DailyWorker(ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {
    override suspend fun doWork(): Result {
        val db = AppDb.get(applicationContext)
        val today = LocalDate.now().toEpochDay()
        val enq = db.enquiries().dueCount(today)
        val all = `in`.vidyadhan.academy.students.studentsWithFeesNow(db.students())
        val fees = all.count { it.nextDue != null && it.nextDue <= today }
        val now = LocalDate.now()
        val bdays = all.count { `in`.vidyadhan.academy.home.isBirthday(it.dob, now) } +
            db.library().allMembers().count { it.active && `in`.vidyadhan.academy.home.isBirthday(it.dob, now) }
        val lib = db.library().expiringCount(today + 3)
        val parts = buildList {
            if (enq > 0) add("$enq enquiry follow-up(s)")
            if (fees > 0) add("$fees student(s) with fees due")
            if (lib > 0) add("$lib library renewal(s)")
            if (bdays > 0) add("$bdays birthday(s) 🎂")
        }
        if (parts.isNotEmpty()) Reminders.notify(applicationContext, "Today: " + parts.joinToString(", "))
        return Result.success()
    }
}
