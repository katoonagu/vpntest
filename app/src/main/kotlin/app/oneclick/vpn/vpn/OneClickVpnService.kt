package app.oneclick.vpn.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.oneclick.vpn.BuildConfig
import app.oneclick.vpn.R
import app.oneclick.vpn.data.VpnRepository
import app.oneclick.vpn.ui.MainActivity
import app.oneclick.vpn.ui.OneClickVpnTileService
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class OneClickVpnService : VpnService() {

    private val serviceScope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val repository by lazy { VpnRepository(applicationContext) }
    private val controllerScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val controller: TunnelController =
        if (BuildConfig.DEMO) FakeWgController() else WgController(controllerScope)
    private var stateJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(TunnelState.Disconnected))
        stateJob = serviceScope.launch {
            controller.state.collect { state ->
                updateGlobalState(state)
                when (state) {
                    TunnelState.Disconnected -> {
                        NotificationManagerCompat.from(this@OneClickVpnService)
                            .notify(NOTIFICATION_ID, buildNotification(state))
                        stopForeground(STOP_FOREGROUND_DETACH)
                    }

                    TunnelState.Connecting, is TunnelState.Connected -> {
                        startForeground(NOTIFICATION_ID, buildNotification(state))
                    }

                    is TunnelState.Error -> {
                        NotificationManagerCompat.from(this@OneClickVpnService)
                            .notify(NOTIFICATION_ID, buildNotification(state))
                    }
                }
                OneClickVpnTileService.requestListeningState(applicationContext)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                val asset = intent.getStringExtra(EXTRA_PROFILE)
                    ?: repository.currentProfile()
                connect(asset)
            }

            ACTION_DISCONNECT -> {
                disconnect()
            }

            ACTION_TOGGLE -> {
                val asset = intent.getStringExtra(EXTRA_PROFILE)
                    ?: repository.currentProfile()
                if (stateFlow.value is TunnelState.Connected || stateFlow.value is TunnelState.Connecting) {
                    disconnect()
                } else {
                    connect(asset)
                }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        // Binding is not used; control via startService commands.
        return null
    }

    override fun onDestroy() {
        stateJob?.cancel()
        serviceScope.launch {
            controller.disconnect()
        }
        controllerScope.cancel()
        repository.close()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun connect(assetPath: String) {
        serviceScope.launch {
            repository.setSelectedProfile(assetPath)
            val configText = repository.readAsset(applicationContext, assetPath)
            controller.connect(configText)
        }
    }

    private fun disconnect() {
        serviceScope.launch {
            controller.disconnect()
        }
    }

    private fun buildNotification(state: TunnelState): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val title = when (state) {
            is TunnelState.Connected -> getString(R.string.vpn_status_connected)
            TunnelState.Connecting -> getString(R.string.vpn_status_connecting)
            TunnelState.Disconnected -> getString(R.string.vpn_status_disconnected)
            is TunnelState.Error -> getString(R.string.vpn_status_disconnected)
        }

        val text = when (state) {
            is TunnelState.Connected -> getString(
                R.string.vpn_bytes_template,
                formatBytes(state.rxBytes),
                formatBytes(state.txBytes)
            )

            is TunnelState.Error -> state.message
            else -> repository.currentProfile()
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_vpn_tile)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(state is TunnelState.Connected || state is TunnelState.Connecting)
            .build()
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
        val mb = kb / 1024.0
        return String.format(Locale.US, "%.1f MB", mb)
    }

    private fun createNotificationChannel() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.app_name),
            NotificationManager.IMPORTANCE_LOW
        )
        notificationManager?.createNotificationChannel(channel)
    }

    private fun updateGlobalState(state: TunnelState) {
        stateFlowInternal.value = state
    }

    companion object {
        private const val CHANNEL_ID = "oneclickvpn_guard"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_CONNECT = "app.oneclick.vpn.action.CONNECT"
        const val ACTION_DISCONNECT = "app.oneclick.vpn.action.DISCONNECT"
        const val ACTION_TOGGLE = "app.oneclick.vpn.action.TOGGLE"
        const val EXTRA_PROFILE = "extra_profile"

        private val stateFlowInternal = MutableStateFlow<TunnelState>(TunnelState.Disconnected)
        val stateFlow: StateFlow<TunnelState> = stateFlowInternal

        fun observeState(): StateFlow<TunnelState> = stateFlow

        fun connect(context: Context, assetPath: String) {
            val intent = Intent(context, OneClickVpnService::class.java).apply {
                action = ACTION_CONNECT
                putExtra(EXTRA_PROFILE, assetPath)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun disconnect(context: Context) {
            val intent = Intent(context, OneClickVpnService::class.java).apply {
                action = ACTION_DISCONNECT
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun toggle(context: Context, assetPath: String) {
            val intent = Intent(context, OneClickVpnService::class.java).apply {
                action = ACTION_TOGGLE
                putExtra(EXTRA_PROFILE, assetPath)
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
