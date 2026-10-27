package app.lteguard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon

object Notifs {
    const val CH_RUN = "guard_run"
    const val CH_ALERT = "guard_alert"
    const val ID_RUN = 1
    const val ID_ALERT = 2

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_RUN, "Guard status", NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_ALERT, "Guard alerts", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    fun build(ctx: Context, text: String): Notification {
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            ctx, 1,
            Intent(ctx, GuardService::class.java).setAction(GuardService.ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Builder(ctx, CH_RUN)
            .setSmallIcon(R.drawable.ic_stat_guard)
            .setContentTitle("LTE Guard")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(Icon.createWithResource(ctx, R.drawable.ic_stat_guard), "Stop", stop).build())
            .build()
    }

    fun update(ctx: Context, text: String) {
        ctx.getSystemService(NotificationManager::class.java).notify(ID_RUN, build(ctx, text))
    }

    fun alert(ctx: Context, text: String) {
        ensureChannels(ctx)
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = Notification.Builder(ctx, CH_ALERT)
            .setSmallIcon(R.drawable.ic_stat_guard)
            .setContentTitle("LTE Guard")
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        ctx.getSystemService(NotificationManager::class.java).notify(ID_ALERT, n)
    }
}
