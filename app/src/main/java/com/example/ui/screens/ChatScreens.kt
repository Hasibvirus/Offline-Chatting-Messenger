package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Sos
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.R
import com.example.data.local.ConversationEntity
import com.example.data.local.MemberEntity
import com.example.data.local.MessageEntity
import com.example.data.local.MessageType
import com.example.data.local.PeerConnectionStatus
import com.example.network.audio.VoiceRecorderState
import com.example.network.transport.ConnectedTransportPeer
import com.example.ui.components.AppTopBar
import com.example.ui.components.DeliveryStatusIcon
import com.example.ui.components.PermissionHelper
import com.example.ui.components.SafetyVerificationDialog
import com.example.ui.components.formatBytes
import com.example.ui.components.formatDuration
import com.example.ui.components.formatTimeShort
import com.example.ui.theme.EmergencyCrimson
import com.example.ui.theme.JetBrainsMonoFontFamily
import java.io.File

/** Screen 6: Private Chat with Secret Code */
@Composable
fun PrivateChatScreen(
    conversation: ConversationEntity?,
    pairingPin: String,
    messages: List<MessageEntity>,
    connectedPeers: List<ConnectedTransportPeer>,
    voiceState: VoiceRecorderState,
    onSendMessage: (String) -> Unit,
    onShareUri: (android.net.Uri) -> Unit,
    onStartVoiceRecord: () -> Unit,
    onStopVoiceRecord: () -> Unit,
    onToggleVoicePlay: (String) -> Unit,
    onOpenWalkieTalkie: () -> Unit,
    onOpenNearby: () -> Unit,
    onOpenSos: () -> Unit,
    onBack: () -> Unit
) {
    ChatRoomScaffold(
        conversation = conversation,
        pairingPin = pairingPin,
        members = emptyList(),
        messages = messages,
        connectedPeers = connectedPeers,
        voiceState = voiceState,
        isGroup = false,
        onSendMessage = onSendMessage,
        onShareUri = onShareUri,
        onStartVoiceRecord = onStartVoiceRecord,
        onStopVoiceRecord = onStopVoiceRecord,
        onToggleVoicePlay = onToggleVoicePlay,
        onOpenWalkieTalkie = onOpenWalkieTalkie,
        onOpenPolls = {},
        onOpenGroupManagement = {},
        onOpenNearby = onOpenNearby,
        onOpenSos = onOpenSos,
        onBack = onBack
    )
}

/** Screen 7: Offline Group Chat */
@Composable
fun GroupChatScreen(
    conversation: ConversationEntity?,
    pairingPin: String,
    members: List<MemberEntity>,
    messages: List<MessageEntity>,
    connectedPeers: List<ConnectedTransportPeer>,
    voiceState: VoiceRecorderState,
    onSendMessage: (String) -> Unit,
    onShareUri: (android.net.Uri) -> Unit,
    onStartVoiceRecord: () -> Unit,
    onStopVoiceRecord: () -> Unit,
    onToggleVoicePlay: (String) -> Unit,
    onOpenWalkieTalkie: () -> Unit,
    onOpenPolls: () -> Unit,
    onOpenGroupManagement: () -> Unit,
    onOpenNearby: () -> Unit,
    onOpenSos: () -> Unit,
    onBack: () -> Unit
) {
    ChatRoomScaffold(
        conversation = conversation,
        pairingPin = pairingPin,
        members = members,
        messages = messages,
        connectedPeers = connectedPeers,
        voiceState = voiceState,
        isGroup = true,
        onSendMessage = onSendMessage,
        onShareUri = onShareUri,
        onStartVoiceRecord = onStartVoiceRecord,
        onStopVoiceRecord = onStopVoiceRecord,
        onToggleVoicePlay = onToggleVoicePlay,
        onOpenWalkieTalkie = onOpenWalkieTalkie,
        onOpenPolls = onOpenPolls,
        onOpenGroupManagement = onOpenGroupManagement,
        onOpenNearby = onOpenNearby,
        onOpenSos = onOpenSos,
        onBack = onBack
    )
}

@Composable
private fun ChatRoomScaffold(
    conversation: ConversationEntity?,
    pairingPin: String,
    members: List<MemberEntity>,
    messages: List<MessageEntity>,
    connectedPeers: List<ConnectedTransportPeer>,
    voiceState: VoiceRecorderState,
    isGroup: Boolean,
    onSendMessage: (String) -> Unit,
    onShareUri: (android.net.Uri) -> Unit,
    onStartVoiceRecord: () -> Unit,
    onStopVoiceRecord: () -> Unit,
    onToggleVoicePlay: (String) -> Unit,
    onOpenWalkieTalkie: () -> Unit,
    onOpenPolls: () -> Unit,
    onOpenGroupManagement: () -> Unit,
    onOpenNearby: () -> Unit,
    onOpenSos: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var inputText by remember { mutableStateOf("") }
    var showSecurityDialog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let(onShareUri) }

    val docPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(onShareUri) }

    val audioPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) onStartVoiceRecord()
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    val roomPeers = connectedPeers.filter {
        it.roomCode == conversation?.roomCode || it.roomCode.isBlank()
    }
    val onlineMemberCount = if (isGroup) {
        members.count { it.connectionStatus == PeerConnectionStatus.CONNECTED }.coerceAtLeast(roomPeers.size)
    } else {
        roomPeers.size
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = conversation?.title ?: "Encrypted Chat",
                subtitle = if (isGroup) {
                    "Code ${conversation?.roomCode ?: ""} • $onlineMemberCount connected"
                } else {
                    "Code ${conversation?.roomCode ?: ""} • ${if (roomPeers.isNotEmpty()) "Peer Connected" else "Standby"}"
                },
                onBack = onBack,
                actions = {
                    IconButton(onClick = { showSecurityDialog = true }, modifier = Modifier.testTag("chat_security_btn")) {
                        Icon(Icons.Default.VerifiedUser, contentDescription = "Safety Verification")
                    }
                    IconButton(onClick = onOpenWalkieTalkie, modifier = Modifier.testTag("chat_ptt_btn")) {
                        Icon(Icons.Default.RecordVoiceOver, contentDescription = "Walkie-Talkie")
                    }
                    if (isGroup) {
                        IconButton(onClick = onOpenPolls, modifier = Modifier.testTag("chat_polls_btn")) {
                            Icon(Icons.Default.Poll, contentDescription = "Polls")
                        }
                        IconButton(onClick = onOpenGroupManagement, modifier = Modifier.testTag("chat_mgmt_btn")) {
                            Icon(Icons.Default.Group, contentDescription = "Members")
                        }
                    }
                    IconButton(onClick = onOpenSos, modifier = Modifier.testTag("chat_sos_btn")) {
                        Icon(Icons.Default.Sos, contentDescription = "Emergency SOS", tint = EmergencyCrimson)
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 3.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                    if (voiceState.isRecording) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Recording voice… ${formatDuration(voiceState.recordingDurationMs)}",
                                color = EmergencyCrimson,
                                style = MaterialTheme.typography.labelLarge
                            )
                            TextButton(onClick = onStopVoiceRecord) {
                                Icon(Icons.Default.Stop, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Stop & Send")
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = {
                                photoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                )
                            },
                            modifier = Modifier.testTag("chat_pick_photo_btn")
                        ) {
                            Icon(Icons.Default.Image, contentDescription = "Share Photo")
                        }
                        IconButton(
                            onClick = { docPicker.launch(arrayOf("*/*")) },
                            modifier = Modifier.testTag("chat_pick_file_btn")
                        ) {
                            Icon(Icons.Default.AttachFile, contentDescription = "Share Document")
                        }
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            placeholder = { Text(stringResource(R.string.message_input_hint)) },
                            maxLines = 4,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("chat_message_input")
                        )
                        if (inputText.isBlank()) {
                            IconButton(
                                onClick = {
                                    if (voiceState.isRecording) {
                                        onStopVoiceRecord()
                                    } else if (PermissionHelper.hasAudioPermission(context)) {
                                        onStartVoiceRecord()
                                    } else {
                                        audioPermLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                    }
                                },
                                modifier = Modifier.testTag("chat_voice_record_btn")
                            ) {
                                Icon(
                                    imageVector = if (voiceState.isRecording) Icons.Default.Stop else Icons.Default.Mic,
                                    contentDescription = "Voice Message",
                                    tint = if (voiceState.isRecording) EmergencyCrimson else MaterialTheme.colorScheme.primary
                                )
                            }
                        } else {
                            IconButton(
                                onClick = {
                                    onSendMessage(inputText)
                                    inputText = ""
                                },
                                modifier = Modifier.testTag("chat_send_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Send",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Pairing Code Strip
            if (conversation != null) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                            .clickable {
                                clipboard.setText(
                                    AnnotatedString("Room: ${conversation.roomCode} | PIN: $pairingPin")
                                )
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Code: ${conversation.roomCode}  •  PIN: $pairingPin",
                                fontFamily = JetBrainsMonoFontFamily,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                        if (roomPeers.isEmpty()) {
                            TextButton(onClick = onOpenNearby) {
                                Text("Connect Peer")
                            }
                        }
                    }
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    ChatMessageItem(
                        msg = msg,
                        voiceState = voiceState,
                        onToggleVoicePlay = onToggleVoicePlay,
                        onOpenPolls = onOpenPolls,
                        onOpenFile = { path, mime ->
                            runCatching {
                                val file = File(path)
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    file
                                )
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, mime ?: "*/*")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(intent)
                            }
                        }
                    )
                }
            }
        }
    }

    if (showSecurityDialog && conversation != null) {
        SafetyVerificationDialog(
            roomCode = conversation.roomCode,
            pin = pairingPin,
            safetyCode = conversation.safetyCode,
            onDismiss = { showSecurityDialog = false }
        )
    }
}

@Composable
private fun ChatMessageItem(
    msg: MessageEntity,
    voiceState: VoiceRecorderState,
    onToggleVoicePlay: (String) -> Unit,
    onOpenPolls: () -> Unit,
    onOpenFile: (path: String, mime: String?) -> Unit
) {
    if (msg.type == MessageType.SYSTEM) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Surface(
                shape = RoundedCornerShape(99.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
            ) {
                Text(
                    text = msg.content,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                )
            }
        }
        return
    }

    val isOutgoing = !msg.isIncoming
    val alignment = if (isOutgoing) Alignment.End else Alignment.Start
    val bubbleColor = when {
        msg.isEmergency -> EmergencyCrimson.copy(alpha = 0.16f)
        isOutgoing -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surface
    }

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = alignment) {
        Card(
            modifier = Modifier.widthIn(max = 310.dp),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = bubbleColor)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (msg.isIncoming || msg.isEmergency) {
                    Text(
                        text = if (msg.isEmergency) "SOS • ${msg.senderName}" else msg.senderName,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (msg.isEmergency) EmergencyCrimson else MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(4.dp))
                }

                when (msg.type) {
                    MessageType.IMAGE -> {
                        msg.attachmentPath?.let { path ->
                            AsyncImage(
                                model = File(path),
                                contentDescription = msg.attachmentName ?: "Shared photo",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(170.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onOpenFile(path, msg.attachmentMime) },
                                contentScale = ContentScale.Crop
                            )
                            Spacer(Modifier.height(6.dp))
                        }
                        Text(msg.attachmentName ?: "Photo", style = MaterialTheme.typography.bodySmall)
                    }

                    MessageType.FILE -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable {
                                msg.attachmentPath?.let { onOpenFile(it, msg.attachmentMime) }
                            }
                        ) {
                            Icon(Icons.Default.AttachFile, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(msg.attachmentName ?: msg.content, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    formatBytes(msg.attachmentSize),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    MessageType.VOICE -> {
                        val isPlaying = voiceState.playingPath == msg.attachmentPath && !voiceState.isPaused
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { msg.attachmentPath?.let(onToggleVoicePlay) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Play voice message"
                                )
                            }
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Voice Note • ${formatDuration(msg.voiceDurationMs)}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    MessageType.POLL -> {
                        Column(modifier = Modifier.clickable { onOpenPolls() }) {
                            Text("Group Poll: ${msg.content}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(4.dp))
                            Text("Tap to vote & view live results →", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    else -> {
                        Text(text = msg.content, style = MaterialTheme.typography.bodyLarge)
                    }
                }

                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = formatTimeShort(msg.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isOutgoing) {
                        DeliveryStatusIcon(msg.deliveryStatus)
                    }
                }
            }
        }
    }
}
