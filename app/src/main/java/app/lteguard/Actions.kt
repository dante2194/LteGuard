package app.lteguard

object Actions {
    /** Turns mobile data off and on so the modem re-attaches (often to another cell/band). */
    suspend fun cycleData(offSec: Int = 4): Boolean {
        AppLog.log("Cycling mobile data (off $offSec s, then on)")
        val r = ShizukuBridge.exec("svc data disable; sleep $offSec; svc data enable", offSec + 25)
        AppLog.log("Data cycle finished: " + (r?.trim()?.takeLast(40) ?: "no shell"))
        return r != null
    }

    suspend fun readRadio(): Radio? {
        val out = ShizukuBridge.exec(Radio.CMD, 20) ?: return null
        return Radio.parse(out)
    }
}
