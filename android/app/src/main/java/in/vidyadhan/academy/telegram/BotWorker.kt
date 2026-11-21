package `in`.vidyadhan.academy.telegram

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

/** Every 15 minutes when online: the bot reads new messages (people pressing Start or sharing their number) and replies. */
class BotWorker(ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {
    override suspend fun doWork(): Result {
        TelegramBot.app = applicationContext
        if (TelegramBot.connected) runCatching { TelegramBot.refresh() }
        return Result.success()
    }

    companion object {
        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<BotWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("bot-inbox", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
