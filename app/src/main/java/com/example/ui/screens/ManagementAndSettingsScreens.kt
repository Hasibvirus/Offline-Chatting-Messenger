package com.example.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.ConversationEntity
import com.example.data.local.MemberEntity
import com.example.data.local.MemberRole
import com.example.data.local.MessageEntity
import com.example.data.local.MessageType
import com.example.data.local.PeerConnectionStatus
import com.example.data.local.ThemeMode
import com.example.data.local.TransportKind
import com.example.data.local.UserPreferences
import com.example.ui.components.AppTopBar
import com.example.ui.components.AvatarBadge
import com.example.ui.components.formatDateTime
import com.example.ui.theme.EmergencyCrimson
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.StatusOnlineGreen

/** Screen 17: Chat History */
@Composable
fun ChatHistoryScreen(
    searchQuery: String,
    messages: List<MessageEntity>,
    conversations: List<ConversationEntity>,
    onSearchChange: (String) -> Unit,
    onOpenConversation: (ConversationEntity) -> Unit,
    onDeleteMessage: (String) -> Unit,
    onDeleteConversation: (String) -> Unit,
    onBack: (() -> Unit)? = null
) {
    var filterTab by remember { mutableStateOf("ALL") }
    val filtered = remember(messages, filterTab) {
        when (filterTab) {
            "FILES" -> messages.filter { it.type == MessageType.FILE || it.type == MessageType.IMAGE }
            "VOICE" -> messages.filter { it.type == MessageType.VOICE }
            "SOS" -> messages.filter { it.type == MessageType.SOS }
            else -> messages.filter { it.type != MessageType.SYSTEM }
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.history_title),
                subtitle = "${conversations.size} conversations • Room SQLite",
                onBack = onBack
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    placeholder = { Text(stringResource(R.string.search_messages_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("history_search_input")
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("ALL" to "All", "FILES" to "Files", "VOICE" to "Voice", "SOS" to "SOS").forEach { (key, label) ->
                        FilterChip(
                            selected = filterTab == key,
                            onClick = { filterTab = key },
                            label = { Text(label) }
                        )
                    }
                }
            }

            if (searchQuery.isBlank() && conversations.isNotEmpty()) {
                item { Text("Saved Conversations", style = MaterialTheme.typography.titleMedium) }
                items(conversations, key = { "hist_conv_${it.id}" }) { conv ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onOpenConversation(conv) }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${conv.title} (${conv.roomCode})", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    conv.lastMessagePreview,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { onDeleteConversation(conv.id) }) {
                                Icon(Icons.Default.DeleteForever, contentDescription = "Delete Conversation")
                            }
                        }
                    }
                }
            }

            item { Text("Messages (${filtered.size})", style = MaterialTheme.typography.titleMedium) }
            items(filtered, key = { it.id }) { msg ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "${msg.senderName} • ${msg.type.name} • ${formatDateTime(msg.timestamp)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(msg.content, style = MaterialTheme.typography.bodyMedium)
                        }
                        IconButton(onClick = { onDeleteMessage(msg.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
                        }
                    }
                }
            }
        }
    }
}

/** Screen 18: Group and Member Management */
@Composable
fun GroupManagementScreen(
    conversation: ConversationEntity?,
    pairingPin: String,
    members: List<MemberEntity>,
    localDeviceId: String,
    onUpdateDetails: (title: String, description: String) -> Unit,
    onKickMember: (deviceId: String) -> Unit,
    onDeleteGroup: () -> Unit,
    onBack: () -> Unit
) {
    var title by remember(conversation?.title) { mutableStateOf(conversation?.title ?: "") }
    var description by remember(conversation?.description) { mutableStateOf(conversation?.description ?: "") }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val isOwner = conversation?.isLocalHost == true ||
        members.any { it.deviceId == localDeviceId && it.role == MemberRole.OWNER }

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.group_mgmt_title),
                subtitle = conversation?.roomCode,
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
            if (conversation != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Room Code: ${conversation.roomCode}", fontFamily = JetBrainsMonoFontFamily, style = MaterialTheme.typography.titleMedium)
                            Text("Pairing PIN: $pairingPin", fontFamily = JetBrainsMonoFontFamily, style = MaterialTheme.typography.bodyMedium)
                            Text("ECDH Safety Code: ${conversation.safetyCode}", fontFamily = JetBrainsMonoFontFamily, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text(stringResource(R.string.group_name_label)) },
                        enabled = isOwner,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description") },
                        enabled = isOwner,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (isOwner) {
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { onUpdateDetails(title, description) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.save))
                        }
                    }
                }
            }

            item { Text("Group Members (${members.size})", style = MaterialTheme.typography.titleMedium) }
            items(members, key = { it.deviceId }) { member ->
                val isOnline = member.connectionStatus == PeerConnectionStatus.CONNECTED
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AvatarBadge(
                            name = member.displayName,
                            colorIndex = member.deviceId.hashCode(),
                            isOnline = isOnline
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(member.displayName, style = MaterialTheme.typography.titleSmall)
                                Surface(
                                    shape = MaterialTheme.shapes.extraSmall,
                                    color = if (member.role == MemberRole.OWNER) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        member.role.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                "${member.connectionStatus.name} • ${member.deviceId}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isOnline) StatusOnlineGreen else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (isOwner && member.deviceId != localDeviceId) {
                            IconButton(onClick = { onKickMember(member.deviceId) }) {
                                Icon(Icons.Default.PersonRemove, contentDescription = "Remove Member", tint = EmergencyCrimson)
                            }
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = EmergencyCrimson),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isOwner) "Delete Group" else "Leave Group")
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Remove Group?") },
            text = { Text("This will delete this group and its local message history from this device.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDeleteGroup()
                }) {
                    Text(stringResource(R.string.delete), color = EmergencyCrimson)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

/** Screen 19: Settings */
@Composable
fun SettingsScreen(
    preferences: UserPreferences,
    onSaveProfile: (name: String, bio: String, colorIndex: Int, lang: String) -> Unit,
    onSetLanguage: (String) -> Unit,
    onSetThemeMode: (ThemeMode) -> Unit,
    onSetTransport: (TransportKind) -> Unit,
    onClearData: () -> Unit,
    onOpenAbout: () -> Unit,
    onBack: (() -> Unit)? = null
) {
    var displayName by remember(preferences.displayName) { mutableStateOf(preferences.displayName) }
    var bio by remember(preferences.statusBio) { mutableStateOf(preferences.statusBio) }
    var showClearDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.settings_title),
                onBack = onBack
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Profile Identity", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Device ID: ${preferences.deviceId}",
                            fontFamily = JetBrainsMonoFontFamily,
                            style = MaterialTheme.typography.labelMedium
                        )
                        OutlinedTextField(
                            value = displayName,
                            onValueChange = { displayName = it },
                            label = { Text(stringResource(R.string.display_name_label)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = bio,
                            onValueChange = { bio = it },
                            label = { Text(stringResource(R.string.status_bio_label)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = {
                                onSaveProfile(
                                    displayName.trim().ifBlank { preferences.deviceId },
                                    bio.trim(),
                                    preferences.avatarColorIndex,
                                    preferences.languageCode
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.save))
                        }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(R.string.language_label), style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            FilterChip(
                                selected = preferences.languageCode == "en",
                                onClick = { onSetLanguage("en") },
                                label = { Text("English") },
                                modifier = Modifier.testTag("settings_lang_en")
                            )
                            FilterChip(
                                selected = preferences.languageCode == "bn",
                                onClick = { onSetLanguage("bn") },
                                label = { Text("বাংলা") },
                                modifier = Modifier.testTag("settings_lang_bn")
                            )
                        }

                        Text("Theme Mode", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ThemeMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = preferences.themeMode == mode,
                                    onClick = { onSetThemeMode(mode) },
                                    label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) }
                                )
                            }
                        }

                        Text(stringResource(R.string.transport_preference), style = MaterialTheme.typography.titleMedium)
                        TransportSelectorRow(
                            selected = preferences.preferredTransport,
                            onSelect = onSetTransport
                        )
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("Security & Storage", style = MaterialTheme.typography.titleMedium)
                        }
                        Text(
                            "Room keys and pairing secrets are wrapped by AndroidKeyStore (AES-256-GCM) and never stored in plain text.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(onClick = onOpenAbout, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.about_title))
                        }
                        OutlinedButton(
                            onClick = { showClearDialog = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = EmergencyCrimson),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Clear Local Chat History & Files")
                        }
                    }
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear Local Data?") },
            text = { Text("All locally saved messages, rooms, polls, voice notes, and shared files on this device will be permanently erased.") },
            confirmButton = {
                TextButton(onClick = { showClearDialog = false; onClearData() }) {
                    Text(stringResource(R.string.delete), color = EmergencyCrimson)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

/** Screen 20: About Application */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.about_title),
                subtitle = "v1.0 • Native Kotlin & Jetpack Compose",
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
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(
                            "Zero-Internet encrypted peer-to-peer & host-relayed group messenger built for resilient local communication.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Cryptographic Security Model", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "• Ephemeral ECDH (secp256r1) + Room Code + 6-digit PIN bound via HKDF-SHA256\n" +
                                "• Mutual HMAC-SHA256 key confirmation & 6-digit Safety Verification Code\n" +
                                "• AES-256-GCM packet encryption with unique 96-bit random IVs and AAD\n" +
                                "• AndroidKeyStore hardware-backed key wrapping at rest\n" +
                                "• Replay protection, 25 MB file limits, executable blocking & path confinement",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Transport Limitations & Honest Notes", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "• Google Nearby Connections: Best automatic discovery (~10–50m), requires Google Play Services.\n" +
                                "• Wi-Fi / Mobile Hotspot: Highest speed for files & Walkie-Talkie (~20–100m). Some public routers enable AP isolation; Mobile Hotspot works without SIM/Internet.\n" +
                                "• Bluetooth Classic RFCOMM: Universal fallback (~10m), lower throughput for large videos/files.\n" +
                                "• Group Topology: Host-and-Members hub model (no multi-hop internet mesh in v1).",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}
