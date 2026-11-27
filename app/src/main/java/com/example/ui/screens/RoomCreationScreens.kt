package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.TransportKind
import com.example.ui.components.AppTopBar

/** Screen 8: Create Group */
@Composable
fun CreateGroupScreen(
    defaultTransport: TransportKind,
    onCreateGroup: (title: String, description: String, transport: TransportKind) -> Unit,
    onBack: () -> Unit
) {
    var groupName by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedTransport by remember { mutableStateOf(defaultTransport) }

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.create_group_title),
                onBack = onBack
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier.widthIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                    )
                ) {
                    Text(
                        text = "Host-and-Members Hub: This device acts as the group host, generating an 8-character Room Code and a 6-digit Pairing PIN to authenticate members with ECDH + AES-256-GCM.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(14.dp)
                    )
                }

                OutlinedTextField(
                    value = groupName,
                    onValueChange = { groupName = it },
                    label = { Text(stringResource(R.string.group_name_label)) },
                    placeholder = { Text(stringResource(R.string.group_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("create_group_name_input")
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description / Channel Purpose (Optional)") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = stringResource(R.string.transport_preference),
                    style = MaterialTheme.typography.labelLarge
                )
                TransportSelectorRow(
                    selected = selectedTransport,
                    onSelect = { selectedTransport = it }
                )

                Spacer(Modifier.height(10.dp))

                Button(
                    onClick = {
                        onCreateGroup(
                            groupName.trim().ifBlank { "Offline Group" },
                            description.trim(),
                            selectedTransport
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("create_group_submit_button")
                ) {
                    Text(stringResource(R.string.action_create_group))
                }
            }
        }
    }
}

/** Screen 9: Join Group or Private Room Using Code */
@Composable
fun JoinGroupScreen(
    defaultTransport: TransportKind,
    onJoin: (
        roomCode: String,
        pin: String,
        transport: TransportKind,
        manualHostIp: String,
        isGroup: Boolean
    ) -> Unit,
    onBack: () -> Unit
) {
    var roomCode by remember { mutableStateOf("") }
    var pairingPin by remember { mutableStateOf("") }
    var isGroup by remember { mutableStateOf(true) }
    var selectedTransport by remember { mutableStateOf(defaultTransport) }
    var manualHostIp by remember { mutableStateOf("") }
    var errorHint by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.join_group_title),
                onBack = onBack
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier.widthIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilterChip(
                        selected = isGroup,
                        onClick = { isGroup = true },
                        label = { Text("Offline Group") }
                    )
                    FilterChip(
                        selected = !isGroup,
                        onClick = { isGroup = false },
                        label = { Text("Private Chat") }
                    )
                }

                OutlinedTextField(
                    value = roomCode,
                    onValueChange = { roomCode = it.uppercase().take(12); errorHint = null },
                    label = { Text(stringResource(R.string.enter_room_code)) },
                    placeholder = { Text("e.g. K7M9P4X2") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("join_room_code_input")
                )

                OutlinedTextField(
                    value = pairingPin,
                    onValueChange = { pairingPin = it.filter { ch -> ch.isDigit() }.take(6); errorHint = null },
                    label = { Text(stringResource(R.string.enter_pairing_pin)) },
                    placeholder = { Text("e.g. 482910") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("join_pairing_pin_input")
                )

                Text(
                    text = stringResource(R.string.transport_preference),
                    style = MaterialTheme.typography.labelLarge
                )
                TransportSelectorRow(
                    selected = selectedTransport,
                    onSelect = { selectedTransport = it }
                )

                if (selectedTransport == TransportKind.WIFI_LAN_HOTSPOT) {
                    OutlinedTextField(
                        value = manualHostIp,
                        onValueChange = { manualHostIp = it },
                        label = { Text(stringResource(R.string.host_ip_optional)) },
                        placeholder = { Text("192.168.43.1:48219") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (errorHint != null) {
                    Text(
                        text = errorHint!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Text(
                        text = "Tip: Connect to the host device first in Nearby, Wi-Fi/Hotspot, or Bluetooth — or enter the Host IP above for instant Hotspot pairing.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                Button(
                    onClick = {
                        if (roomCode.trim().length < 4 || pairingPin.trim().length < 4) {
                            errorHint = "Enter both the Room Code and the 6-digit Pairing PIN."
                        } else {
                            onJoin(roomCode.trim(), pairingPin.trim(), selectedTransport, manualHostIp.trim(), isGroup)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("join_room_submit_button")
                ) {
                    Text(stringResource(R.string.join_now))
                }
            }
        }
    }
}

@Composable
fun TransportSelectorRow(
    selected: TransportKind,
    onSelect: (TransportKind) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selected == TransportKind.NEARBY_CONNECTIONS,
            onClick = { onSelect(TransportKind.NEARBY_CONNECTIONS) },
            label = { Text("Nearby") }
        )
        FilterChip(
            selected = selected == TransportKind.WIFI_LAN_HOTSPOT,
            onClick = { onSelect(TransportKind.WIFI_LAN_HOTSPOT) },
            label = { Text("Wi-Fi / Hotspot") }
        )
        FilterChip(
            selected = selected == TransportKind.BLUETOOTH_CLASSIC,
            onClick = { onSelect(TransportKind.BLUETOOTH_CLASSIC) },
            label = { Text("Bluetooth") }
        )
    }
}
