package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Sos
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.ConversationEntity
import com.example.data.local.ConversationType
import com.example.data.local.UserPreferences
import com.example.network.transport.ConnectedTransportPeer
import com.example.ui.components.AvatarBadge
import com.example.ui.components.TransportBadge
import com.example.ui.components.formatTimeShort
import com.example.ui.theme.BrightCyan
import com.example.ui.theme.EmergencyCrimson
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.SlateNavy
import com.example.ui.theme.SlateNavyDeep

private data class QuickActionItem(
    val titleRes: Int,
    val icon: ImageVector,
    val accent: Color,
    val tag: String,
    val onClick: () -> Unit
)

/** Screen 4: Home Dashboard */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeDashboardScreen(
    preferences: UserPreferences,
    conversations: List<ConversationEntity>,
    connectedPeers: List<ConnectedTransportPeer>,
    onCreatePrivateChat: (title: String) -> Unit,
    onOpenConversation: (ConversationEntity) -> Unit,
    onOpenCreateGroup: () -> Unit,
    onOpenJoinCode: () -> Unit,
    onOpenNearby: () -> Unit,
    onOpenWifi: () -> Unit,
    onOpenBluetooth: () -> Unit,
    onOpenWalkieTalkie: () -> Unit,
    onOpenFileSharing: () -> Unit,
    onOpenVoiceMessages: () -> Unit,
    onOpenPolls: () -> Unit,
    onOpenSos: () -> Unit,
    onOpenAbout: () -> Unit
) {
    var showNewPrivateDialog by remember { mutableStateOf(false) }
    var privateRoomTitle by remember { mutableStateOf("") }

    val quickActions = listOf(
        QuickActionItem(R.string.action_private_chat, Icons.Default.AddComment, MaterialTheme.colorScheme.primary, "qa_private") {
            showNewPrivateDialog = true
        },
        QuickActionItem(R.string.action_create_group, Icons.Default.GroupAdd, Color(0xFF2563EB), "qa_create_group", onOpenCreateGroup),
        QuickActionItem(R.string.action_join_code, Icons.Default.Password, Color(0xFF7C3AED), "qa_join_code", onOpenJoinCode),
        QuickActionItem(R.string.action_nearby_devices, Icons.Default.Sensors, MaterialTheme.colorScheme.primary, "qa_nearby", onOpenNearby),
        QuickActionItem(R.string.action_wifi_hotspot, Icons.Default.Wifi, Color(0xFF0284C7), "qa_wifi", onOpenWifi),
        QuickActionItem(R.string.action_bluetooth, Icons.Default.Bluetooth, Color(0xFF4F46E5), "qa_bluetooth", onOpenBluetooth),
        QuickActionItem(R.string.action_walkie_talkie, Icons.Default.RecordVoiceOver, Color(0xFFD97706), "qa_walkie", onOpenWalkieTalkie),
        QuickActionItem(R.string.action_file_share, Icons.Default.FolderOpen, Color(0xFF059669), "qa_files", onOpenFileSharing),
        QuickActionItem(R.string.action_voice_notes, Icons.Default.Mic, Color(0xFF0D9488), "qa_voice", onOpenVoiceMessages),
        QuickActionItem(R.string.polls_title, Icons.Default.Poll, Color(0xFF9333EA), "qa_polls", onOpenPolls),
        QuickActionItem(R.string.action_sos, Icons.Default.Sos, EmergencyCrimson, "qa_sos", onOpenSos)
    )

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AvatarBadge(
                        name = preferences.displayName.ifBlank { "OM" },
                        colorIndex = preferences.avatarColorIndex,
                        sizeDp = 36,
                        isOnline = connectedPeers.isNotEmpty()
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = preferences.displayName.ifBlank { preferences.deviceId },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            actions = {
                IconButton(
                    onClick = onOpenSos,
                    modifier = Modifier.testTag("top_sos_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Sos,
                        contentDescription = stringResource(R.string.action_sos),
                        tint = EmergencyCrimson
                    )
                }
                IconButton(
                    onClick = onOpenAbout,
                    modifier = Modifier.testTag("top_about_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = stringResource(R.string.about_title)
                    )
                }
            }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(152.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(SlateNavyDeep)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_hero_radar),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        SlateNavyDeep.copy(alpha = 0.92f),
                                        SlateNavy.copy(alpha = 0.65f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Surface(
                            shape = RoundedCornerShape(99.dp),
                            color = BrightCyan.copy(alpha = 0.18f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = BrightCyan,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.hero_status_active, connectedPeers.size),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = BrightCyan
                                )
                            }
                        }

                        Column {
                            Text(
                                text = stringResource(R.string.hero_status_ready),
                                style = MaterialTheme.typography.headlineSmall,
                                color = Color.White
                            )
                            Text(
                                text = stringResource(R.string.no_internet_required),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    text = stringResource(R.string.quick_actions),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(10.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(quickActions) { item ->
                        Card(
                            modifier = Modifier
                                .width(104.dp)
                                .testTag(item.tag)
                                .clickable { item.onClick() },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(item.accent.copy(alpha = 0.14f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = stringResource(item.titleRes),
                                        tint = item.accent,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(item.titleRes),
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.active_rooms),
                        style = MaterialTheme.typography.titleMedium
                    )
                    TextButton(onClick = onOpenJoinCode) {
                        Text(stringResource(R.string.action_join_code))
                    }
                }
            }

            if (conversations.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.img_empty_chats),
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(150.dp)
                                    .clip(RoundedCornerShape(16.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = stringResource(R.string.empty_chats_title),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = stringResource(R.string.empty_chats_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(conversations, key = { it.id }) { conv ->
                    ConversationRowCard(
                        conversation = conv,
                        connectedCount = connectedPeers.count { it.roomCode == conv.roomCode },
                        onClick = { onOpenConversation(conv) }
                    )
                }
            }
        }
    }

    if (showNewPrivateDialog) {
        AlertDialog(
            onDismissRequest = { showNewPrivateDialog = false },
            title = { Text(stringResource(R.string.private_chat_new)) },
            text = {
                OutlinedTextField(
                    value = privateRoomTitle,
                    onValueChange = { privateRoomTitle = it },
                    label = { Text("Room Label (Optional)") },
                    placeholder = { Text("e.g. Field Alpha Link") },
                    singleLine = true,
                    modifier = Modifier.testTag("new_private_room_input")
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showNewPrivateDialog = false
                        onCreatePrivateChat(privateRoomTitle)
                        privateRoomTitle = ""
                    },
                    modifier = Modifier.testTag("confirm_new_private_button")
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewPrivateDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun ConversationRowCard(
    conversation: ConversationEntity,
    connectedCount: Int,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("conversation_card_${conversation.roomCode}")
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(
                        if (conversation.type == ConversationType.GROUP) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (conversation.type == ConversationType.GROUP) Icons.Default.Groups else Icons.Default.Lock,
                    contentDescription = null,
                    tint = if (conversation.type == ConversationType.GROUP) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    }
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = conversation.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = conversation.roomCode,
                        fontFamily = JetBrainsMonoFontFamily,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = conversation.lastMessagePreview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TransportBadge(conversation.preferredTransport)
                    if (connectedCount > 0) {
                        Text(
                            text = "$connectedCount online",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatTimeShort(conversation.lastMessageAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (conversation.unreadCount > 0) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Badge { Text("${conversation.unreadCount}") }
                }
            }
        }
    }
}
