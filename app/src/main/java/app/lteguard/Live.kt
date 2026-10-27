package app.lteguard

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

data class LiveState(
    val net: String = "-",
    val plain: Boolean? = null,
    val running: Boolean = false,
    val radio: String = "",
    val internet: Boolean? = null,
    val lastAction: String = ""
)

object Live {
    val state = MutableStateFlow(LiveState())
    fun update(f: (LiveState) -> LiveState) {
        state.update(f)
    }
}
