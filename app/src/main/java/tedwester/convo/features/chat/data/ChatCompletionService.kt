package tedwester.convo.features.chat.data

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.content.ContextCompat

class ChatCompletionService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val count = intent?.getIntExtra(EXTRA_RUNNING_COUNT, 1)?.coerceAtLeast(1) ?: 1
        val notification = ChatNotifications.buildOngoing(this, count)
        startForeground(
            ChatNotifications.ONGOING_NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
        return START_STICKY
    }

    companion object {
        private const val EXTRA_RUNNING_COUNT = "running_count"

        fun update(context: Context, runningCount: Int) {
            if (runningCount <= 0) {
                stop(context)
                return
            }
            val intent = Intent(context, ChatCompletionService::class.java).apply {
                putExtra(EXTRA_RUNNING_COUNT, runningCount)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ChatCompletionService::class.java))
        }
    }
}
