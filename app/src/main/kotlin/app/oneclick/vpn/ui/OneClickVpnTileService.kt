package app.oneclick.vpn.ui

import android.content.ComponentName
import android.content.Context
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import app.oneclick.vpn.R
import app.oneclick.vpn.data.VpnRepository
import app.oneclick.vpn.vpn.OneClickVpnService
import app.oneclick.vpn.vpn.TunnelState

class OneClickVpnTileService : TileService() {

    private val repository by lazy { VpnRepository(applicationContext) }

    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
    }

    override fun onClick() {
        super.onClick()
        val currentProfile = repository.currentProfile()
        OneClickVpnService.toggle(applicationContext, currentProfile)
        refreshTile()
    }

    override fun onStopListening() {
        super.onStopListening()
        qsTile?.state = Tile.STATE_INACTIVE
        qsTile?.updateTile()
    }

    override fun onDestroy() {
        repository.close()
        super.onDestroy()
    }

    private fun refreshTile() {
        val tile = qsTile ?: return
        when (OneClickVpnService.observeState().value) {
            is TunnelState.Connected -> {
                tile.state = Tile.STATE_ACTIVE
                tile.subtitle = getString(R.string.vpn_status_connected)
            }

            TunnelState.Connecting -> {
                tile.state = Tile.STATE_UNAVAILABLE
                tile.subtitle = getString(R.string.vpn_status_connecting)
            }

            else -> {
                tile.state = Tile.STATE_INACTIVE
                tile.subtitle = getString(R.string.vpn_status_disconnected)
            }
        }
        tile.label = getString(R.string.app_name)
        tile.updateTile()
    }

    companion object {
        fun requestListeningState(context: Context) {
            requestListeningState(
                context,
                ComponentName(context, OneClickVpnTileService::class.java)
            )
        }
    }
}
