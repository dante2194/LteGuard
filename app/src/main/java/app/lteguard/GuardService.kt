package app.lteguard

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class GuardService : Service() {
    companion object {
        const val ACTION_STOP = "app.lteguard.STOP"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var guard: Guard? = null
    private var rx: BroadcastReceiver? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Prefs.setEnabled(this, false)
            stopSelf()
            return START_NOT_STICKY
        }
        Notifs.ensureChannels(this)
        ServiceCompat.startForeground(
            this, Notifs.ID_RUN, Notifs.build(this, "Watching the mobile network"),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )
        if (guard == null) {
            val g = Guard(this, scope)
            guard = g
            g.start()
            Live.update { it.copy(running = true) }
            val r = object : BroadcastReceiver() {
                override fun onReceive(c: Context?, i: Intent?) {
                    g.kick("screen")
                }
            }
            rx = r
            val f = IntentFilter()
            f.addAction(Intent.ACTION_USER_PRESENT)
            f.addAction(Intent.ACTION_SCREEN_ON)
            registerReceiver(r, f, Context.RECEIVER_NOT_EXPORTED)
            scope.launch {
                while (isActive) {
                    delay(15_000)
                    try {
                        g.tick()
                    } catch (e: Exception) {
                        AppLog.log("tick error: $e")
                    }
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        try {
            rx?.let { unregisterReceiver(it) }
        } catch (_: Exception) {
        }
        guard?.stop()
        guard = null
        scope.cancel()
        Live.update { it.copy(running = false) }
        super.onDestroy()
    }
}
