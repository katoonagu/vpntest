package app.oneclick.vpn.vpn

package app.oneclick.vpn.vpn

import com.wireguard.config.Config
import java.io.ByteArrayInputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class WgController(
    dispatcherScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) : TunnelController {

    private val scope = dispatcherScope
    private val mutex = Mutex()
    private var statsJob: Job? = null
    private val internalState = MutableStateFlow<TunnelState>(TunnelState.Disconnected)

    override val state: Flow<TunnelState> = internalState.asStateFlow()

    override suspend fun connect(configText: String) {
        mutex.withLock {
            internalState.value = TunnelState.Connecting
            statsJob?.cancel()
            statsJob = null
            try {
                val config = Config.parse(ByteArrayInputStream(configText.toByteArray(Charsets.UTF_8)))
                val endpoint = config.peers.firstOrNull()?.endpoint?.toString() ?: "unknown"

                // Simulated tunnel setup delay before marking as connected.
                delay(300)

                internalState.value = TunnelState.Connected(endpoint = endpoint, rxBytes = 0, txBytes = 0)
                statsJob = scope.launch {
                    var download = 0L
                    var upload = 0L
                    while (true) {
                        delay(1000)
                        val current = internalState.value
                        if (current is TunnelState.Connected) {
                            download += 1024
                            upload += 512
                            internalState.value = current.copy(rxBytes = download, txBytes = upload)
                        } else {
                            break
                        }
                    }
                }
            } catch (t: Throwable) {
                internalState.value =
                    TunnelState.Error(t.message ?: "Invalid WireGuard configuration")
            }
        }
    }

    override suspend fun disconnect() {
        mutex.withLock {
            statsJob?.cancel()
            statsJob = null
            internalState.value = TunnelState.Disconnected
        }
    }
}
