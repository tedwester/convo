package tedwester.convo.features.chat.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import tedwester.convo.MainActivity
import tedwester.convo.R

object ChatNotifications {

    const val EXTRA_OPEN_CHAT_ID = "tedwester.convo.OPEN_CHAT_ID"

    private const val CHANNEL_ONGOING = "chat_completions_ongoing"
    private const val CHANNEL_READY = "chat_completions_ready"

    const val ONGOING_NOTIFICATION_ID = 1001
    private const val READY_NOTIFICATION_BASE = 2000

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ONGOING,
                "Generating Response",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Shown while Convo is generating a response in the background"
                setShowBadge(false)
            },
        )

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_READY,
                "Response Completed",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Alerts when a chat finishes generating while you are away"
            },
        )
    }

    fun buildOngoing(context: Context, runningCount: Int): Notification {
        val title = if (runningCount <= 1) {
            context.getString(R.string.notif_generating_title)
        } else {
            context.getString(R.string.notif_generating_title_multi, runningCount)
        }
        return baseBuilder(context, CHANNEL_ONGOING)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.notif_generating_text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(openAppIntent(context))
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    fun notifyReplyReady(context: Context, chatId: String, chatTitle: String) {
        val notification = baseBuilder(context, CHANNEL_READY)
            .setContentTitle(context.getString(R.string.notif_ready_title))
            .setContentText(
                context.getString(R.string.notif_ready_text, chatTitle.ifBlank { "Chat" }),
            )
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(openChatIntent(context, chatId))
            .build()

        runCatching {
            NotificationManagerCompat.from(context)
                .notify(readyNotificationId(chatId), notification)
        }
    }

    fun cancelReplyReady(context: Context, chatId: String) {
        NotificationManagerCompat.from(context).cancel(readyNotificationId(chatId))
    }

    private fun baseBuilder(context: Context, channel: String): NotificationCompat.Builder =
        NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_lucide_astroid)

    private fun openAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun openChatIntent(context: Context, chatId: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_CHAT_ID, chatId)
        }
        return PendingIntent.getActivity(
            context,
            chatId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun readyNotificationId(chatId: String): Int =
        READY_NOTIFICATION_BASE + (chatId.hashCode() and 0x0FFF)
}
