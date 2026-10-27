package app.lteguard

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private fun sp(c: Context): SharedPreferences =
        c.getSharedPreferences("lteguard", Context.MODE_PRIVATE)

    fun enabled(c: Context) = sp(c).getBoolean("enabled", false)
    fun setEnabled(c: Context, v: Boolean) = sp(c).edit().putBoolean("enabled", v).apply()

    fun useBandLock(c: Context) = sp(c).getBoolean("band_lock", true)
    fun setUseBandLock(c: Context, v: Boolean) = sp(c).edit().putBoolean("band_lock", v).apply()

    fun useDataCycle(c: Context) = sp(c).getBoolean("data_cycle", true)
    fun setUseDataCycle(c: Context, v: Boolean) = sp(c).edit().putBoolean("data_cycle", v).apply()

    fun bands(c: Context): Set<Int> =
        (sp(c).getString("bands", "1,3,7") ?: "")
            .split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()

    fun setBands(c: Context, s: Set<Int>) =
        sp(c).edit().putString("bands", s.sorted().joinToString(",")).apply()

    fun lastActionAt(c: Context) = sp(c).getLong("last_action", 0L)
    fun touch(c: Context) = sp(c).edit().putLong("last_action", System.currentTimeMillis()).apply()

    fun recordAction(c: Context) {
        val now = System.currentTimeMillis()
        val old = (sp(c).getString("actions", "") ?: "")
            .split(",").mapNotNull { it.toLongOrNull() }.filter { now - it < 3_600_000L }
        sp(c).edit()
            .putString("actions", (old + now).joinToString(","))
            .putLong("last_action", now)
            .apply()
    }

    fun actionsLastHour(c: Context): Int {
        val now = System.currentTimeMillis()
        return (sp(c).getString("actions", "") ?: "")
            .split(",").mapNotNull { it.toLongOrNull() }.count { now - it < 3_600_000L }
    }

    fun lockVerifiedAt(c: Context) = sp(c).getLong("lock_verified", 0L)
    fun setLockVerifiedAt(c: Context, t: Long) = sp(c).edit().putLong("lock_verified", t).apply()

    fun origBands(c: Context): String? = sp(c).getString("orig_bands", null)
    fun setOrigBands(c: Context, s: String) = sp(c).edit().putString("orig_bands", s).apply()
}
