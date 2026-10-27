package app.lteguard

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.telephony.ServiceState
import android.telephony.SubscriptionManager
import android.telephony.TelephonyCallback
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

/**
 * Policy engine. "Plain 4G" = the status-bar network is LTE with no override
 * (override LTE_CA is exactly the 4.5G icon). Anything else for more than 10 s
 * (or 3 flaps in 10 min) triggers a fix, rate limited.
 */
class Guard(private val ctx: Context, private val scope: CoroutineScope) {
    private val mutex = Mutex()

    @Volatile private var netType = TelephonyManager.NETWORK_TYPE_UNKNOWN
    @Volatile private var ovr = TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE
    @Volatile private var inService = false
    @Volatile private var dataConnected = false
    @Volatile private var badSince = 0L
    private var wasBad = false
    private val episodes = ArrayList<Long>()
    private var failStreak = 0
    private var tm: TelephonyManager? = null
    private var cb: Cb? = null
    private var limitLogAt = 0L

    private inner class Cb : TelephonyCallback(),
        TelephonyCallback.DisplayInfoListener,
        TelephonyCallback.ServiceStateListener,
        TelephonyCallback.DataConnectionStateListener {

        override fun onDisplayInfoChanged(telephonyDisplayInfo: TelephonyDisplayInfo) {
            netType = telephonyDisplayInfo.networkType
            ovr = telephonyDisplayInfo.overrideNetworkType
            publish()
            kick("display")
        }

        override fun onServiceStateChanged(serviceState: ServiceState) {
            inService = serviceState.state == ServiceState.STATE_IN_SERVICE
            publish()
            kick("service")
        }

        override fun onDataConnectionStateChanged(state: Int, networkType: Int) {
            dataConnected = state == TelephonyManager.DATA_CONNECTED
            publish()
            kick("data")
        }
    }

    fun start() {
        if (ctx.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            AppLog.log("READ_PHONE_STATE is not granted - open the app and grant it")
            Live.update { it.copy(net = "needs phone permission") }
            return
        }
        try {
            val base = ctx.getSystemService(TelephonyManager::class.java)
            val sub = SubscriptionManager.getDefaultDataSubscriptionId()
            val t = if (sub != SubscriptionManager.INVALID_SUBSCRIPTION_ID) base.createForSubscriptionId(sub) else base
            val c = Cb()
            t.registerTelephonyCallback(ctx.mainExecutor, c)
            tm = t
            cb = c
            AppLog.log("Guard started (data subscription $sub)")
        } catch (e: Exception) {
            AppLog.log("Could not register telephony callback: $e")
        }
    }

    fun stop() {
        try {
            val c = cb
            if (c != null) tm?.unregisterTelephonyCallback(c)
        } catch (_: Exception) {
        }
        cb = null
    }

    private fun netName(t: Int): String = when (t) {
        13 -> "LTE"
        20 -> "5G"
        15 -> "H+"
        1, 2 -> "2G"
        3, 8, 9, 10 -> "3G"
        0 -> "unknown"
        else -> "type $t"
    }

    private fun ovrName(o: Int): String = when (o) {
        0 -> ""
        1 -> " + CA (4.5G)"
        2 -> " + LTE-A Pro"
        3 -> " + 5G NSA"
        4 -> " + 5G mmWave"
        5 -> " + 5G Adv"
        else -> " + $o"
    }

    /** true = plain 4G, false = not plain 4G, null = unknown / no data. */
    private fun plain(): Boolean? {
        if (!inService || !dataConnected || netType == TelephonyManager.NETWORK_TYPE_UNKNOWN) return null
        return netType == TelephonyManager.NETWORK_TYPE_LTE &&
            ovr == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE
    }

    private fun publish() {
        val p = plain()
        Live.update { it.copy(net = netName(netType) + ovrName(ovr), plain = p) }
        val txt = when (p) {
            true -> "Plain 4G"
            false -> "Not plain 4G: " + netName(netType) + ovrName(ovr)
            null -> "Waiting for mobile data"
        }
        if (Prefs.enabled(ctx)) {
            try {
                Notifs.update(ctx, txt)
            } catch (_: Exception) {
            }
        }
    }

    fun kick(reason: String) {
        scope.launch { evaluate(reason) }
    }

    suspend fun tick() {
        Live.update { it.copy(internet = hasCellularInternet()) }
        evaluate("tick")
    }

    @Suppress("DEPRECATION")
    fun hasCellularInternet(): Boolean {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        return cm.allNetworks.any { n ->
            val c = cm.getNetworkCapabilities(n)
            c != null &&
                c.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
                c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }
    }

    suspend fun evaluate(reason: String) {
        if (!Prefs.enabled(ctx)) return
        val p = plain()
        val now = System.currentTimeMillis()
        if (p != false) {
            badSince = 0L
            wasBad = false
            return
        }
        if (!wasBad) {
            wasBad = true
            episodes.add(now)
            episodes.removeAll { now - it > 600_000L }
        }
        if (badSince == 0L) badSince = now
        val flappy = episodes.count { now - it < 600_000L } >= 3
        if (now - badSince < 10_000L && !flappy) return
        if (now - Prefs.lastActionAt(ctx) < 90_000L) return
        if (Prefs.actionsLastHour(ctx) >= 10) {
            if (now - limitLogAt > 600_000L) {
                limitLogAt = now
                AppLog.log("Hourly action limit (10) reached - waiting")
            }
            return
        }
        if (!mutex.tryLock()) return
        try {
            act(reason, flappy)
        } finally {
            mutex.unlock()
        }
    }

    private suspend fun act(reason: String, flappy: Boolean) {
        val s = ShizukuBridge.state(ctx)
        if (s != ShizukuState.READY) {
            AppLog.log("Not plain 4G but Shizuku is $s - cannot fix")
            Notifs.alert(ctx, "Not on plain 4G, but Shizuku is not ready. Open Shizuku and start it.")
            Prefs.touch(ctx)
            return
        }
        val radio = Actions.readRadio()
        val label = radio?.label() ?: "radio info n/a"
        AppLog.log("NOT PLAIN 4G ($reason${if (flappy) ", flapping" else ""}): ${Live.state.value.net}; $label")
        Live.update { it.copy(radio = label) }

        // Longer detach after repeated failures gives the modem a better chance to pick another cell.
        val offSec = when {
            failStreak >= 4 -> 20
            failStreak >= 2 -> 10
            else -> 4
        }
        Actions.cycleData(offSec)
        Prefs.recordAction(ctx)
        delay(35_000)
        if (plain() == false) {
            failStreak++
            AppLog.log("Still not plain 4G after the fix (streak $failStreak)")
            Live.update { it.copy(lastAction = "Fix did not hold") }
        } else {
            failStreak = 0
            AppLog.log("Back on plain 4G")
            Live.update { it.copy(lastAction = "Fixed") }
        }
    }
}
