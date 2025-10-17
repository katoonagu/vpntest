package app.oneclick.vpn.vpn

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlin.random.Random

class FakeWgController : TunnelController {

    private val _state = MutableStateFlow<TunnelState>(TunnelState.Disconnected)
    override val state = _state.asStateFlow()

    override suspend fun connect(configText: String) = coroutineScope {
        _state.value = TunnelState.Connecting
        delay(800)
        _state.value = TunnelState.Connected("demo.endpoint:51820", 0, 0)
        var rx = 0L
        var tx = 0L
        while (isActive && _state.value is TunnelState.Connected) {
            delay(500)
            rx += Random.nextLong(2_000, 10_000)
            tx += Random.nextLong(1_000, 5_000)
            _state.value = TunnelState.Connected("demo.endpoint:51820", rx, tx)
        }
    }

    override suspend fun disconnect() {
        _state.value = TunnelState.Disconnected
    }
}
