package app.oneclick.vpn.vpn

import com.wireguard.config.Config
import java.io.ByteArrayInputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class WgController(
    dispatcherScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {

    private val scope = dispatcherScope
    private val state = MutableStateFlow<VpnState>(VpnState.Disconnected)
    private val mutex = Mutex()
    private var statsJob: Job? = null

    fun observeState(): Flow<VpnState> = state.asStateFlow()

    suspend fun connect(configText: String) {
        mutex.withLock {
            state.value = VpnState.Connecting
            try {
                val config = Config.parse(ByteArrayInputStream(configText.toByteArray(Charsets.UTF_8)))
                val endpoint = config.peers.firstOrNull()?.endpoint?.toString()

                // Simulate async tunnel setup before marking as connected.
                delay(300)

                state.value = VpnState.Connected(endpoint = endpoint, bytesIn = 0, bytesOut = 0)
                statsJob?.cancel()
                statsJob = scope.launch {
                    var download = 0L
                    var upload = 0L
                    while (true) {
                        delay(1000)
                        val current = state.value
                        if (current is VpnState.Connected) {
                            download += 1024
                            upload += 512
                            state.value = current.copy(bytesIn = download, bytesOut = upload)
                        } else {
                            break
                        }
                    }
                }
            } catch (t: Throwable) {
                state.value = VpnState.Error(t.message ?: "Invalid WireGuard configuration")
            }
        }
    }

    fun disconnect() {
        statsJob?.cancel()
        statsJob = null
        state.value = VpnState.Disconnected
    }
}
