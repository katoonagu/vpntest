package app.oneclick.vpn.vpn

import kotlinx.coroutines.flow.Flow

sealed class TunnelState {
    object Disconnected : TunnelState()
    object Connecting : TunnelState()
    data class Connected(
        val endpoint: String,
        val rxBytes: Long,
        val txBytes: Long
    ) : TunnelState()

    data class Error(val message: String) : TunnelState()
}

interface TunnelController {
    val state: Flow<TunnelState>
    suspend fun connect(configText: String)
    suspend fun disconnect()
}
