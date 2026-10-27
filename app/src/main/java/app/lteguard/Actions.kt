package app.lteguard

object Actions {
    suspend fun cycleData(): Boolean {
        AppLog.log("Cycling mobile data (off 4 s, on)")
        val r = ShizukuBridge.exec("svc data disable; sleep 4; svc data enable", 30)
        AppLog.log("Data cycle finished: " + (r?.trim()?.takeLast(40) ?: "no shell"))
        return r != null
    }

    suspend fun readRadio(): Radio? {
        val out = ShizukuBridge.exec(Radio.CMD, 20) ?: return null
        return Radio.parse(out)
    }

    suspend fun applyBandLock(bands: Set<Int>): Boolean = BandLock.set(bands)

    suspend fun restoreBands(): Boolean = BandLock.restore()
}
