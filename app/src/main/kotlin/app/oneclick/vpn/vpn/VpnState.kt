package app.oneclick.vpn.vpn

sealed interface VpnState {
    data object Disconnected : VpnState
    data object Connecting : VpnState
    data class Connected(
        val endpoint: String?,
        val bytesIn: Long,
        val bytesOut: Long
    ) : VpnState

    data class Error(val message: String) : VpnState
}
