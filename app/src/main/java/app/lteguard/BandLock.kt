package app.lteguard

import kotlinx.coroutines.delay

/**
 * Drives Xiaomi's hidden "Set Radio Band Mode" screen (com.android.phone MiuiBandMode)
 * with uiautomator + input tap, as the shell user. The screen applies the band choice
 * through Qualcomm's RIL hook, which has no shell-accessible equivalent.
 */
object BandLock {
    private const val DUMP = "/data/local/tmp/lteguard_band.xml"
    private const val START = "am start -n com.android.phone/com.android.phone.settings.MiuiBandMode"

    data class Row(val text: String, val checked: Boolean, val cx: Int, val cy: Int)

    private val boundsRe = Regex("\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]")
    private val nodeRe = Regex("<node [^>]*>")

    private fun attr(n: String, k: String): String =
        Regex("\\b" + k + "=\"([^\"]*)\"").find(n)?.groupValues?.get(1) ?: ""

    private fun parse(xml: String): Pair<Boolean?, List<Row>> {
        var sw: Boolean? = null
        val rows = ArrayList<Row>()
        for (m in nodeRe.findAll(xml)) {
            val n = m.value
            val cls = attr(n, "class")
            if (cls.endsWith("Switch") && attr(n, "resource-id").endsWith("btn_select")) {
                sw = attr(n, "checked") == "true"
            }
            if (cls.endsWith("CheckedTextView")) {
                val b = boundsRe.find(attr(n, "bounds")) ?: continue
                val g = b.groupValues
                rows.add(
                    Row(
                        attr(n, "text"),
                        attr(n, "checked") == "true",
                        (g[1].toInt() + g[3].toInt()) / 2,
                        (g[2].toInt() + g[4].toInt()) / 2
                    )
                )
            }
        }
        return Pair(sw, rows)
    }

    private fun describe(rows: List<Row>): String =
        rows.filter { it.checked }.joinToString(" ") { it.text.removeSuffix(" Preferred") }

    private fun bandOf(label: String): Int? =
        Regex("LTE B(\\d+)").find(label)?.groupValues?.get(1)?.toIntOrNull()

    private suspend fun dump(): String? {
        for (i in 1..3) {
            val out = ShizukuBridge.exec("uiautomator dump $DUMP >/dev/null 2>&1; cat $DUMP", 25)
            if (out != null && out.contains("<hierarchy")) return out
            delay(800)
        }
        return null
    }

    private suspend fun open(): String? {
        ShizukuBridge.exec(START, 15)
        delay(1600)
        return dump()
    }

    private suspend fun back() {
        ShizukuBridge.exec("input keyevent KEYCODE_BACK", 10)
    }

    private suspend fun ensure(label: String, wantChecked: Boolean): Boolean {
        val xml = dump() ?: return false
        val row = parse(xml).second.find { it.text == label }
        if (row == null) {
            AppLog.log("Row not found on band screen: $label")
            return false
        }
        if (row.checked == wantChecked) return true
        ShizukuBridge.exec("input tap ${row.cx} ${row.cy}", 10)
        delay(700)
        val after = dump() ?: return false
        val r2 = parse(after).second.find { it.text == label }
        val ok = r2?.checked == wantChecked
        if (!ok) AppLog.log("Tapping '$label' did not change its state")
        return ok
    }

    suspend fun set(want: Set<Int>): Boolean {
        val c = App.ctx
        val target = want.sorted().joinToString("+") { "B$it" }
        AppLog.log("Band lock: opening band screen, target $target")
        val xml = open()
        if (xml == null) {
            AppLog.log("Band lock failed: could not read the band screen (screen off or locked?)")
            back()
            return false
        }
        val (sw, rows) = parse(xml)
        if (rows.none { it.text.startsWith("LTE B") }) {
            AppLog.log("Band lock failed: band list not found on screen")
            back()
            return false
        }
        AppLog.log("Band screen before: switch=$sw checked=[${describe(rows)}]")
        if (Prefs.origBands(c) == null) {
            Prefs.setOrigBands(c, rows.filter { it.checked }.joinToString(",") { it.text })
        }
        var ok = true
        for (r in rows) {
            val b = bandOf(r.text) ?: continue
            val wantChecked = b in want
            if (r.checked != wantChecked) {
                if (!ensure(r.text, wantChecked)) ok = false
            }
        }
        val fin = dump()
        if (fin != null) {
            val (sw2, rows2) = parse(fin)
            AppLog.log("Band screen after: switch=$sw2 checked=[${describe(rows2)}]")
            for (b in want) {
                val row = rows2.find { bandOf(it.text) == b }
                if (row == null || !row.checked) ok = false
            }
        } else {
            ok = false
        }
        back()
        AppLog.log(if (ok) "Band lock applied: $target" else "Band lock NOT confirmed")
        return ok
    }

    suspend fun restore(): Boolean {
        val c = App.ctx
        val orig = (Prefs.origBands(c) ?: "").split(",").filter { it.isNotBlank() }.toSet()
        AppLog.log("Restoring band screen to its original state")
        val xml = open()
        if (xml == null) {
            AppLog.log("Restore failed: could not read the band screen")
            back()
            return false
        }
        val rows = parse(xml).second
        var ok = true
        for (r in rows) {
            val shouldBe = r.text in orig
            if (r.checked != shouldBe) {
                if (!ensure(r.text, shouldBe)) ok = false
            }
        }
        val fin = dump()
        if (fin != null) AppLog.log("Band screen now: checked=[${describe(parse(fin).second)}]")
        back()
        AppLog.log(if (ok) "Band screen restored" else "Restore incomplete")
        return ok
    }
}
