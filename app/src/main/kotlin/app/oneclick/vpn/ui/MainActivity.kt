package app.oneclick.vpn.ui

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.oneclick.vpn.BuildConfig
import app.oneclick.vpn.R
import app.oneclick.vpn.data.VpnRepository
import app.oneclick.vpn.ui.theme.OneClickVpnTheme
import app.oneclick.vpn.vpn.OneClickVpnService
import app.oneclick.vpn.vpn.VpnState
import java.util.Locale
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var repository: VpnRepository
    private var pendingProfile: String? = null
    private lateinit var vpnPermissionLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = VpnRepository(this)

        vpnPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                pendingProfile?.let { profile ->
                    OneClickVpnService.connect(this, profile)
                }
            }
            pendingProfile = null
        }

        setContent {
            OneClickVpnTheme {
                MainScreen()
            }
        }
    }

    override fun onDestroy() {
        repository.close()
        super.onDestroy()
    }

    private fun requestVpnPermission(profile: String) {
        val intent: Intent? = VpnService.prepare(this)
        if (intent != null) {
            pendingProfile = profile
            vpnPermissionLauncher.launch(intent)
        } else {
            OneClickVpnService.connect(this, profile)
        }
    }

    @Composable
    private fun MainScreen() {
        val coroutineScope = rememberCoroutineScope()
        val selectedProfile by repository
            .selectedProfileFlow()
            .collectAsState(initial = repository.currentProfile())
        val vpnState by OneClickVpnService.observeState().collectAsState()
        val showProfileDialog = rememberSaveable { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            if (selectedProfile.isBlank()) {
                repository.setSelectedProfile(BuildConfig.DEFAULT_WG_ASSET)
            }
        }

        Scaffold { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = getString(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold
                )
                StatusCard(
                    state = vpnState,
                    selectedProfile = selectedProfile
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val isActive =
                        vpnState is VpnState.Connected || vpnState is VpnState.Connecting
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (isActive) {
                                OneClickVpnService.disconnect(this@MainActivity)
                            } else {
                                requestVpnPermission(selectedProfile)
                            }
                        }
                    ) {
                        Text(
                            text = if (isActive) {
                                getString(R.string.vpn_action_disconnect)
                            } else {
                                getString(R.string.vpn_action_connect)
                            }
                        )
                    }
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = { showProfileDialog.value = true }
                    ) {
                        Text(text = getString(R.string.vpn_select_profile))
                    }
                }
            }
        }

        if (showProfileDialog.value) {
            ProfileSelectionDialog(
                selectedProfile = selectedProfile,
                onDismiss = { showProfileDialog.value = false },
                onProfileSelected = { profile ->
                    showProfileDialog.value = false
                    coroutineScope.launch {
                        repository.setSelectedProfile(profile)
                    }
                }
            )
        }
    }

    @Composable
    private fun StatusCard(
        state: VpnState,
        selectedProfile: String
    ) {
        val endpoint = (state as? VpnState.Connected)?.endpoint ?: "-"
        val bytesIn = (state as? VpnState.Connected)?.bytesIn ?: 0
        val bytesOut = (state as? VpnState.Connected)?.bytesOut ?: 0

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = when (state) {
                        VpnState.Disconnected -> getString(R.string.vpn_status_disconnected)
                        VpnState.Connecting -> getString(R.string.vpn_status_connecting)
                        is VpnState.Connected -> getString(R.string.vpn_status_connected)
                        is VpnState.Error -> state.message
                    },
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = getString(
                        R.string.vpn_selected_profile,
                        profileLabel(selectedProfile)
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Endpoint: $endpoint",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = getString(
                        R.string.vpn_bytes_template,
                        formatBytes(bytesIn),
                        formatBytes(bytesOut)
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }

    @Composable
    private fun ProfileSelectionDialog(
        selectedProfile: String,
        onDismiss: () -> Unit,
        onProfileSelected: (String) -> Unit
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(text = getString(R.string.vpn_select_profile)) },
            text = {
                val profiles = remember {
                    (1..30).map { index ->
                        "wg/" + String.format(Locale.US, "client%02d.conf", index)
                    }
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(profiles) { profile ->
                        ProfileRow(
                            label = profileLabel(profile),
                            selected = profile == selectedProfile,
                            onClick = { onProfileSelected(profile) }
                        )
                    }
                }
            },
            confirmButton = {}
        )
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun ProfileRow(
        label: String,
        selected: Boolean,
        onClick: () -> Unit
    ) {
        Surface(
            tonalElevation = if (selected) 4.dp else 0.dp,
            onClick = onClick
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge
                )
                if (selected) {
                    Text(
                        text = getString(R.string.vpn_status_connected),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }

    private fun profileLabel(assetPath: String): String {
        val name = assetPath.substringAfterLast('/').substringBefore(".conf")
        val suffix = name.removePrefix("client")
        return "Client $suffix"
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kilobytes = bytes / 1024.0
        if (kilobytes < 1024) {
            return String.format(Locale.US, "%.1f KB", kilobytes)
        }
        val megabytes = kilobytes / 1024.0
        return String.format(Locale.US, "%.1f MB", megabytes)
    }
}
