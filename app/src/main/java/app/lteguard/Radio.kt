package app.lteguard

data class Radio(
    val network: String,
    val ovr: String,
    val tech: String,
    val caFlag: String,
    val chn: Int?,
    val band: Int?,
    val bws: List<Int>
) {
    fun label(): String {
        val b = if (band != null) "B$band" else "band ?"
        val ch = if (chn != null) " ch $chn" else ""
        val cells = if (bws.isEmpty()) "" else " " + bws.size + " cell(s) " +
            bws.joinToString("+") { (it / 1000).toString() + "MHz" }
        return "$network/$ovr $b$ch$cells (CA flag $caFlag)"
    }

    companion object {
        const val CMD = "dumpsys telephony.registry | grep -E " +
            "'Phone Id=|mTelephonyDisplayInfo=|mChannelNumber=|mCellBandwidths=|" +
            "isUsingCarrierAggregation=|getRilDataRadioTechnology=' | head -80"

        fun earfcnToBand(n: Int): Int? = when (n) {
            in 0..599 -> 1
            in 600..1199 -> 2
            in 1200..1949 -> 3
            in 1950..2399 -> 4
            in 2400..2649 -> 5
            in 2750..3449 -> 7
            in 3450..3799 -> 8
            in 6150..6449 -> 20
            in 9210..9659 -> 28
            in 37750..38249 -> 38
            in 38650..39649 -> 40
            in 39650..41589 -> 41
            else -> null
        }

        fun parse(txt: String): Radio {
            val lines = txt.lines()
            val seg = ArrayList<String>()
            var seen = 0
            for (l in lines) {
                if (l.contains("Phone Id=")) {
                    seen++
                    if (seen > 1) break
                    continue
                }
                if (seen >= 1) seg.add(l)
            }
            val all: List<String> = if (seg.isEmpty()) lines else seg

            fun first(re: Regex): MatchResult? {
                for (l in all) {
                    val m = re.find(l)
                    if (m != null) return m
                }
                return null
            }

            val disp = first(Regex("network=([A-Za-z0-9_+]+), overrideNetwork=([A-Za-z0-9_]+)"))
            val tech = first(Regex("getRilDataRadioTechnology=\\d+\\(([A-Z_+]+)\\)"))?.groupValues?.get(1) ?: "?"
            val ca = first(Regex("isUsingCarrierAggregation=(true|false)"))?.groupValues?.get(1) ?: "?"
            val chn = first(Regex("mChannelNumber=(-?\\d+)"))?.groupValues?.get(1)?.toIntOrNull()
            val bwText = first(Regex("mCellBandwidths=\\[([^\\]]*)\\]"))?.groupValues?.get(1) ?: ""
            val bws = bwText.split(",").mapNotNull { it.trim().toIntOrNull() }
            val validChn = if (chn != null && chn in 0..70000) chn else null
            return Radio(
                network = disp?.groupValues?.get(1) ?: tech,
                ovr = disp?.groupValues?.get(2) ?: "?",
                tech = tech,
                caFlag = ca,
                chn = validChn,
                band = if (validChn != null) earfcnToBand(validChn) else null,
                bws = bws
            )
        }
    }
}
