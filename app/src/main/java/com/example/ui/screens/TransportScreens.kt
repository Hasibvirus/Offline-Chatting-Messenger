package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.ConversationEntity
import com.example.data.local.TransportKind
import com.example.network.transport.DiscoveredPeer
import com.example.network.transport.PendingConnectionRequest
import com.example.network.transport.RadioStatus
import com.example.network.transport.TransportStatusSnapshot
import com.example.ui.components.AppTopBar
import com.example.ui.components.PermissionHelper
import com.example.ui.components.PermissionRationaleCard
import com.example.ui.theme.JetBrainsMonoFontFamily
import kotlinx.coroutines.launch

/** Screen 5: Nearby Devices */
@Composable
fun NearbyDevicesScreen(
    status: TransportStatusSnapshot,
    discoveredPeers: List<DiscoveredPeer>,
    pendingRequests: List<PendingConnectionRequest>,
    connectedRaw: Map<String, String>,
    conversations: List<ConversationEntity>,
    onStartAdvertise: () -> Unit,
    onStartDiscover: () -> Unit,
    onStop: () -> Unit,
    onRequestConnect: (String) -> Unit,
    onAccept: (String) -> Unit,
    onReject: (String) -> Unit,
    onDisconnect: (String) -> Unit,
    onPairEndpointWithRoom: suspend (endpointId: String, room: ConversationEntity) -> Unit,
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var hasPerms by remember {
        mutableStateOf(PermissionHelper.hasAll(context, PermissionHelper.nearbyPermissions()))
    }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasPerms = result.values.all { it }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.nearby_title),
                subtitle = status.status.name,
                onBack = onBack
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!hasPerms) {
                item {
                    PermissionRationaleCard(
                        title = "Nearby Radio Permissions",
                        description = stringResource(R.string.permission_rationale_nearby),
                        onGrantClick = { permLauncher.launch(PermissionHelper.nearbyPermissions()) }
                    )
                }
            }

            item {
                TransportOverviewCard(
                    title = "Google Nearby Connections (P2P_STAR)",
                    status = status,
                    primaryControls = {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = onStartAdvertise,
                                enabled = hasPerms && status.isSupported,
                                modifier = Modifier.weight(1f).testTag("nearby_advertise_btn")
                            ) {
                                Icon(Icons.Default.Sensors, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.start_advertising))
                            }
                            OutlinedButton(
                                onClick = onStartDiscover,
                                enabled = hasPerms && status.isSupported,
                                modifier = Modifier.weight(1f).testTag("nearby_discover_btn")
                            ) {
                                Text(stringResource(R.string.start_discovery))
                            }
                        }
                        if (status.status != RadioStatus.IDLE) {
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = onStop) {
                                Icon(Icons.Default.Stop, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.stop_radio))
                            }
                        }
                    }
                )
            }

            if (pendingRequests.isNotEmpty()) {
                item {
                    Text("Incoming Connection Requests", style = MaterialTheme.typography.titleMedium)
                }
                items(pendingRequests, key = { it.endpointId }) { req ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(req.peerName, style = MaterialTheme.typography.titleMedium)
                                if (req.authDigits.isNotBlank()) {
                                    Text(
                                        "Verify token: ${req.authDigits}",
                                        fontFamily = JetBrainsMonoFontFamily,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                            IconButton(onClick = { onAccept(req.endpointId) }) {
                                Icon(Icons.Default.Check, contentDescription = stringResource(R.string.accept))
                            }
                            IconButton(onClick = { onReject(req.endpointId) }) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.reject))
                            }
                        }
                    }
                }
            }

            if (connectedRaw.isNotEmpty()) {
                item {
                    Text("Connected Nearby Endpoints", style = MaterialTheme.typography.titleMedium)
                }
                items(connectedRaw.entries.toList(), key = { it.key }) { (epId, name) ->
                    ConnectedEndpointPairCard(
                        endpointId = epId,
                        peerName = name,
                        conversations = conversations,
                        onPair = onPairEndpointWithRoom,
                        onDisconnect = { onDisconnect(epId) }
                    )
                }
            }

            item {
                Text("Discovered Nearby Devices (${discoveredPeers.size})", style = MaterialTheme.typography.titleMedium)
            }
            if (discoveredPeers.isEmpty()) {
                item {
                    EmptyDiscoveryHint("Tap 'Scan Nearby' on this phone and 'Advertise Device' on the peer phone.")
                }
            } else {
                items(discoveredPeers, key = { it.id }) { peer ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(peer.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    peer.addressLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(onClick = { onRequestConnect(peer.id) }) {
                                Text(stringResource(R.string.connect))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Screen 10: Wi-Fi / Hotspot Connection Guide */
@Composable
fun WifiHotspotScreen(
    status: TransportStatusSnapshot,
    discoveredPeers: List<DiscoveredPeer>,
    connectedRaw: Map<String, String>,
    conversations: List<ConversationEntity>,
    onRefreshIp: () -> Unit,
    onStartServer: () -> Unit,
    onStopServer: () -> Unit,
    onStartNsdScan: () -> Unit,
    onStopNsdScan: () -> Unit,
    onConnectManual: (host: String, port: Int) -> Unit,
    onDisconnect: (String) -> Unit,
    onPairEndpointWithRoom: suspend (endpointId: String, room: ConversationEntity) -> Unit,
    onBack: () -> Unit
) {
    var manualHost by remember { mutableStateOf("192.168.43.1") }
    var manualPort by remember { mutableStateOf("48219") }

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.wifi_hotspot_title),
                subtitle = status.localEndpointInfo,
                onBack = onBack,
                actions = {
                    IconButton(onClick = onRefreshIp) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh IP")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                TransportOverviewCard(
                    title = "Local Wi-Fi & Hotspot TCP Socket",
                    status = status,
                    primaryControls = {
                        Text(
                            text = "This Device Socket: ${status.localEndpointInfo}",
                            fontFamily = JetBrainsMonoFontFamily,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = onStartServer,
                                modifier = Modifier.weight(1f).testTag("wifi_start_server_btn")
                            ) {
                                Icon(Icons.Default.WifiTethering, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Host Server")
                            }
                            OutlinedButton(
                                onClick = onStartNsdScan,
                                modifier = Modifier.weight(1f).testTag("wifi_nsd_scan_btn")
                            ) {
                                Icon(Icons.Default.Wifi, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Scan LAN")
                            }
                        }
                        if (status.status == RadioStatus.ACTIVE_SERVER || status.status == RadioStatus.DISCOVERING) {
                            Spacer(Modifier.height(6.dp))
                            Row {
                                TextButton(onClick = onStopServer) { Text("Stop Server") }
                                TextButton(onClick = onStopNsdScan) { Text("Stop Scan") }
                            }
                        }
                    }
                )
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Manual Connect (Mobile Hotspot Mode)", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "1. Enable Mobile Hotspot on Phone A (Internet/SIM data NOT needed).\n" +
                                "2. Phone A taps 'Host Server' and notes its IP (usually 192.168.43.1 or 192.168.x.1).\n" +
                                "3. Phone B joins Phone A's Wi-Fi Hotspot and connects below.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = manualHost,
                                onValueChange = { manualHost = it },
                                label = { Text("Host IP") },
                                singleLine = true,
                                modifier = Modifier.weight(2f).testTag("wifi_manual_ip_input")
                            )
                            OutlinedTextField(
                                value = manualPort,
                                onValueChange = { manualPort = it },
                                label = { Text("Port") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = { onConnectManual(manualHost, manualPort.toIntOrNull() ?: 48219) },
                            modifier = Modifier.fillMaxWidth().testTag("wifi_manual_connect_btn")
                        ) {
                            Text("Connect to Wi-Fi / Hotspot Host")
                        }
                    }
                }
            }

            if (connectedRaw.isNotEmpty()) {
                item { Text("Connected Wi-Fi Peers", style = MaterialTheme.typography.titleMedium) }
                items(connectedRaw.entries.toList(), key = { it.key }) { (epId, name) ->
                    ConnectedEndpointPairCard(
                        endpointId = epId,
                        peerName = name,
                        conversations = conversations,
                        onPair = onPairEndpointWithRoom,
                        onDisconnect = { onDisconnect(epId) }
                    )
                }
            }

            item { Text("NSD Discovered Peers (${discoveredPeers.size})", style = MaterialTheme.typography.titleMedium) }
            if (discoveredPeers.isEmpty()) {
                item {
                    EmptyDiscoveryHint("Some public Wi-Fi routers block mDNS/NSD ('AP Isolation'). Use Mobile Hotspot or Manual IP above if peers do not appear.")
                }
            } else {
                items(discoveredPeers, key = { it.id }) { peer ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(peer.name, style = MaterialTheme.typography.titleMedium)
                                Text(peer.addressLabel, fontFamily = JetBrainsMonoFontFamily, style = MaterialTheme.typography.bodySmall)
                            }
                            Button(onClick = {
                                val parts = peer.addressLabel.split(":")
                                onConnectManual(parts[0], parts.getOrNull(1)?.toIntOrNull() ?: 48219)
                            }) {
                                Text(stringResource(R.string.connect))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Screen 11: Bluetooth Connection */
@Composable
fun BluetoothScreen(
    status: TransportStatusSnapshot,
    pairedPeers: List<DiscoveredPeer>,
    discoveredPeers: List<DiscoveredPeer>,
    connectedRaw: Map<String, String>,
    conversations: List<ConversationEntity>,
    onRefreshPaired: () -> Unit,
    onStartServer: () -> Unit,
    onStopServer: () -> Unit,
    onStartDiscovery: () -> Unit,
    onStopDiscovery: () -> Unit,
    onConnectDevice: (String) -> Unit,
    onDisconnect: (String) -> Unit,
    onPairEndpointWithRoom: suspend (endpointId: String, room: ConversationEntity) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var hasPerms by remember {
        mutableStateOf(PermissionHelper.hasAll(context, PermissionHelper.bluetoothPermissions()))
    }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasPerms = result.values.all { it }
        if (hasPerms) onRefreshPaired()
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.bluetooth_title),
                subtitle = status.status.name,
                onBack = onBack,
                actions = {
                    if (hasPerms) {
                        IconButton(onClick = onRefreshPaired) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh paired devices")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!hasPerms) {
                item {
                    PermissionRationaleCard(
                        title = "Bluetooth Permissions",
                        description = "Android 12+ requires Nearby Devices (Bluetooth Scan/Connect) permission; Android 8-11 requires Location permission for discovery.",
                        onGrantClick = { permLauncher.launch(PermissionHelper.bluetoothPermissions()) }
                    )
                }
            }

            item {
                TransportOverviewCard(
                    title = "Bluetooth Classic RFCOMM",
                    status = status,
                    primaryControls = {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = onStartServer,
                                enabled = hasPerms && status.isSupported,
                                modifier = Modifier.weight(1f).testTag("bt_server_btn")
                            ) {
                                Icon(Icons.Default.BluetoothConnected, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Listen RFCOMM")
                            }
                            OutlinedButton(
                                onClick = onStartDiscovery,
                                enabled = hasPerms && status.isSupported,
                                modifier = Modifier.weight(1f).testTag("bt_scan_btn")
                            ) {
                                Icon(Icons.Default.BluetoothSearching, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Scan Devices")
                            }
                        }
                        if (status.status != RadioStatus.IDLE) {
                            Spacer(Modifier.height(6.dp))
                            Row {
                                TextButton(onClick = onStopServer) { Text("Stop Server") }
                                TextButton(onClick = onStopDiscovery) { Text("Stop Scan") }
                            }
                        }
                    }
                )
            }

            if (connectedRaw.isNotEmpty()) {
                item { Text("Connected Bluetooth Peers", style = MaterialTheme.typography.titleMedium) }
                items(connectedRaw.entries.toList(), key = { it.key }) { (epId, name) ->
                    ConnectedEndpointPairCard(
                        endpointId = epId,
                        peerName = name,
                        conversations = conversations,
                        onPair = onPairEndpointWithRoom,
                        onDisconnect = { onDisconnect(epId) }
                    )
                }
            }

            item { Text("Paired Bluetooth Devices (${pairedPeers.size})", style = MaterialTheme.typography.titleMedium) }
            items(pairedPeers, key = { "paired_${it.id}" }) { peer ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Bluetooth, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(peer.name, style = MaterialTheme.typography.titleMedium)
                            Text(peer.addressLabel, fontFamily = JetBrainsMonoFontFamily, style = MaterialTheme.typography.bodySmall)
                        }
                        Button(onClick = { onConnectDevice(peer.addressLabel) }, enabled = hasPerms) {
                            Text(stringResource(R.string.connect))
                        }
                    }
                }
            }

            item { Text("Discovered Nearby (${discoveredPeers.size})", style = MaterialTheme.typography.titleMedium) }
            if (discoveredPeers.isEmpty()) {
                item {
                    EmptyDiscoveryHint("Pair phones in system Bluetooth settings first if RFCOMM discovery is blocked by OEM battery policies.")
                }
            } else {
                items(discoveredPeers, key = { "disc_${it.id}" }) { peer ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(peer.name, style = MaterialTheme.typography.titleMedium)
                                Text(peer.addressLabel, fontFamily = JetBrainsMonoFontFamily, style = MaterialTheme.typography.bodySmall)
                            }
                            Button(onClick = { onConnectDevice(peer.addressLabel) }, enabled = hasPerms) {
                                Text(stringResource(R.string.connect))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TransportOverviewCard(
    title: String,
    status: TransportStatusSnapshot,
    primaryControls: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                text = status.availabilityNote,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!status.errorMessage.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = status.errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Spacer(Modifier.height(12.dp))
            primaryControls()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConnectedEndpointPairCard(
    endpointId: String,
    peerName: String,
    conversations: List<ConversationEntity>,
    onPair: suspend (endpointId: String, room: ConversationEntity) -> Unit,
    onDisconnect: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }
    var selectedRoom by remember(conversations) { mutableStateOf(conversations.firstOrNull()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(peerName, style = MaterialTheme.typography.titleMedium)
                    Text(endpointId, fontFamily = JetBrainsMonoFontFamily, style = MaterialTheme.typography.labelSmall)
                }
                TextButton(onClick = onDisconnect) {
                    Text(stringResource(R.string.disconnect))
                }
            }
            if (conversations.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = selectedRoom?.let { "${it.title} (${it.roomCode})" } ?: "Select Room",
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            conversations.forEach { room ->
                                DropdownMenuItem(
                                    text = { Text("${room.title} (${room.roomCode})") },
                                    onClick = {
                                        selectedRoom = room
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                    Button(
                        onClick = {
                            selectedRoom?.let { room ->
                                scope.launch { onPair(endpointId, room) }
                            }
                        }
                    ) {
                        Icon(Icons.Default.Link, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Pair")
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyDiscoveryHint(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp)
        )
    }
}
