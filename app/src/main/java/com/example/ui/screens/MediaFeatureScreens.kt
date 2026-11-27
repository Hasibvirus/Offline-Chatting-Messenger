package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.R
import com.example.data.local.ConversationEntity
import com.example.data.local.MessageEntity
import com.example.network.audio.PttRadioState
import com.example.network.audio.VoiceRecorderState
import com.example.network.transport.FileTransferProgress
import com.example.network.transport.TransferStatus
import com.example.ui.components.AppTopBar
import com.example.ui.components.PermissionHelper
import com.example.ui.components.PermissionRationaleCard
import com.example.ui.components.formatBytes
import com.example.ui.components.formatDateTime
import com.example.ui.components.formatDuration
import com.example.ui.theme.BrightCyan
import com.example.ui.theme.EmergencyCrimson
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.SignalAmber
import java.io.File

/** Screen 12: Push-to-Talk Walkie-Talkie */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalkieTalkieScreen(
    initialConversationId: String?,
    conversations: List<ConversationEntity>,
    pttState: PttRadioState,
    audioLevel: Float,
    activeSpeaker: String?,
    onStartTransmit: (conversationId: String?) -> Unit,
    onStopTransmit: () -> Unit,
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var hasMicPerm by remember { mutableStateOf(PermissionHelper.hasAudioPermission(context)) }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasMicPerm = granted }

    var selectedRoom by remember(conversations, initialConversationId) {
        mutableStateOf(
            conversations.firstOrNull { it.id == initialConversationId } ?: conversations.firstOrNull()
        )
    }
    var dropdownExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.ptt_title),
                subtitle = "16kHz PCM Low-Latency Stream",
                onBack = onBack
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!hasMicPerm) {
                    PermissionRationaleCard(
                        title = "Microphone Permission",
                        description = stringResource(R.string.permission_rationale_audio),
                        onGrantClick = { permLauncher.launch(Manifest.permission.RECORD_AUDIO) }
                    )
                }

                ExposedDropdownMenuBox(
                    expanded = dropdownExpanded,
                    onExpandedChange = { dropdownExpanded = !dropdownExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedRoom?.let { "${it.title} (${it.roomCode})" } ?: "No active room",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Radio Channel / Room") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(dropdownExpanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false }
                    ) {
                        conversations.forEach { room ->
                            DropdownMenuItem(
                                text = { Text("${room.title} (${room.roomCode})") },
                                onClick = {
                                    selectedRoom = room
                                    dropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // Center Interactive Hold-to-Talk Tactical Button + Live Waveform Rings
            Box(
                modifier = Modifier.size(260.dp),
                contentAlignment = Alignment.Center
            ) {
                val activeColor = when (pttState) {
                    PttRadioState.TRANSMITTING -> EmergencyCrimson
                    PttRadioState.RECEIVING -> BrightCyan
                    PttRadioState.IDLE -> MaterialTheme.colorScheme.primary
                }

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val baseR = size.minDimension * 0.34f
                    val pulseR = baseR + (size.minDimension * 0.14f * audioLevel)
                    drawCircle(
                        color = activeColor.copy(alpha = 0.22f),
                        radius = pulseR,
                        center = center
                    )
                    drawCircle(
                        color = activeColor.copy(alpha = 0.45f),
                        radius = baseR + 12.dp.toPx(),
                        center = center,
                        style = Stroke(width = 3.dp.toPx())
                    )
                }

                Box(
                    modifier = Modifier
                        .size(168.dp)
                        .clip(CircleShape)
                        .background(activeColor)
                        .testTag("ptt_hold_button")
                        .pointerInput(hasMicPerm, selectedRoom) {
                            detectTapGestures(
                                onPress = {
                                    if (!hasMicPerm) {
                                        permLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        return@detectTapGestures
                                    }
                                    onStartTransmit(selectedRoom?.id)
                                    tryAwaitRelease()
                                    onStopTransmit()
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.RecordVoiceOver,
                            contentDescription = stringResource(R.string.ptt_hold_to_talk),
                            tint = Color.White,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = when (pttState) {
                                PttRadioState.TRANSMITTING -> stringResource(R.string.ptt_transmitting)
                                PttRadioState.RECEIVING -> activeSpeaker ?: stringResource(R.string.ptt_receiving)
                                PttRadioState.IDLE -> stringResource(R.string.ptt_hold_to_talk)
                            },
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Text(
                    text = when (pttState) {
                        PttRadioState.TRANSMITTING -> "Live voice streaming via AudioRecord over encrypted peer tunnel. Release to stop."
                        PttRadioState.RECEIVING -> "Incoming audio from ${activeSpeaker ?: "Peer"}…"
                        PttRadioState.IDLE -> stringResource(R.string.ptt_idle)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(14.dp)
                )
            }
        }
    }
}

/** Screen 13: Photo and File Sharing */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileSharingScreen(
    initialConversationId: String?,
    conversations: List<ConversationEntity>,
    transfers: Map<String, FileTransferProgress>,
    fileMessages: List<MessageEntity>,
    onShareUri: (conversationId: String, uri: Uri) -> Unit,
    onDeleteMessage: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var selectedRoom by remember(conversations, initialConversationId) {
        mutableStateOf(
            conversations.firstOrNull { it.id == initialConversationId } ?: conversations.firstOrNull()
        )
    }
    var expanded by remember { mutableStateOf(false) }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        val roomId = selectedRoom?.id ?: return@rememberLauncherForActivityResult
        uri?.let { onShareUri(roomId, it) }
    }

    val docPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        val roomId = selectedRoom?.id ?: return@rememberLauncherForActivityResult
        uri?.let { onShareUri(roomId, it) }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.files_title),
                subtitle = "SAF & Photo Picker • Max 25 MB • SHA-256 Verified",
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
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = selectedRoom?.let { "${it.title} (${it.roomCode})" } ?: "Create or join a room first",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Target Room") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        conversations.forEach { room ->
                            DropdownMenuItem(
                                text = { Text("${room.title} (${room.roomCode})") },
                                onClick = { selectedRoom = room; expanded = false }
                            )
                        }
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            photoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                            )
                        },
                        enabled = selectedRoom != null,
                        modifier = Modifier.weight(1f).testTag("files_pick_media_btn")
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.pick_photo_video))
                    }
                    OutlinedButton(
                        onClick = { docPicker.launch(arrayOf("application/pdf", "text/*", "application/*")) },
                        enabled = selectedRoom != null,
                        modifier = Modifier.weight(1f).testTag("files_pick_doc_btn")
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.pick_document))
                    }
                }
            }

            if (transfers.isNotEmpty()) {
                item { Text("Active & Recent Transfers", style = MaterialTheme.typography.titleMedium) }
                items(transfers.values.toList(), key = { it.transferId }) { tx ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(tx.fileName, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    tx.status.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = when (tx.status) {
                                        TransferStatus.COMPLETED -> MaterialTheme.colorScheme.primary
                                        TransferStatus.INTERRUPTED, TransferStatus.FAILED -> EmergencyCrimson
                                        TransferStatus.IN_PROGRESS -> SignalAmber
                                    }
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            val pct = if (tx.totalBytes > 0) (tx.transferredBytes.toFloat() / tx.totalBytes).coerceIn(0f, 1f) else 0f
                            LinearProgressIndicator(progress = { pct }, modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "${formatBytes(tx.transferredBytes)} / ${formatBytes(tx.totalBytes)}",
                                fontFamily = JetBrainsMonoFontFamily,
                                style = MaterialTheme.typography.labelSmall
                            )
                            if (!tx.errorMessage.isNullOrBlank()) {
                                Text(tx.errorMessage, style = MaterialTheme.typography.bodySmall, color = EmergencyCrimson)
                            }
                        }
                    }
                }
            }

            item { Text("Stored Shared Files (${fileMessages.size})", style = MaterialTheme.typography.titleMedium) }
            items(fileMessages, key = { it.id }) { msg ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(msg.attachmentName ?: msg.content, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "${msg.senderName} • ${formatBytes(msg.attachmentSize)} • ${formatDateTime(msg.timestamp)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(onClick = {
                            msg.attachmentPath?.let { path ->
                                runCatching {
                                    val uri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        File(path)
                                    )
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(uri, msg.attachmentMime ?: "*/*")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(intent)
                                }
                            }
                        }) {
                            Text("Open")
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

/** Screen 14: Voice Messages */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceMessagesScreen(
    initialConversationId: String?,
    conversations: List<ConversationEntity>,
    voiceState: VoiceRecorderState,
    voiceMessages: List<MessageEntity>,
    onStartRecord: () -> Unit,
    onStopAndSend: (String?) -> Unit,
    onCancelRecord: () -> Unit,
    onTogglePlay: (String) -> Unit,
    onDeleteMessage: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var hasMicPerm by remember { mutableStateOf(PermissionHelper.hasAudioPermission(context)) }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasMicPerm = granted }

    var selectedRoom by remember(conversations, initialConversationId) {
        mutableStateOf(
            conversations.firstOrNull { it.id == initialConversationId } ?: conversations.firstOrNull()
        )
    }
    var expanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.voice_title),
                subtitle = "AAC Offline Audio Notes",
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
            if (!hasMicPerm) {
                item {
                    PermissionRationaleCard(
                        title = "Microphone Permission",
                        description = stringResource(R.string.permission_rationale_audio),
                        onGrantClick = { permLauncher.launch(Manifest.permission.RECORD_AUDIO) }
                    )
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ExposedDropdownMenuBox(
                            expanded = expanded,
                            onExpandedChange = { expanded = !expanded }
                        ) {
                            OutlinedTextField(
                                value = selectedRoom?.let { "${it.title} (${it.roomCode})" } ?: "Local Only (No Room)",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Send Recording To") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                            )
                            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                conversations.forEach { room ->
                                    DropdownMenuItem(
                                        text = { Text("${room.title} (${room.roomCode})") },
                                        onClick = { selectedRoom = room; expanded = false }
                                    )
                                }
                            }
                        }

                        if (voiceState.isRecording) {
                            Text(
                                text = "Recording: ${formatDuration(voiceState.recordingDurationMs)}",
                                fontFamily = JetBrainsMonoFontFamily,
                                style = MaterialTheme.typography.headlineSmall,
                                color = EmergencyCrimson
                            )
                            LinearProgressIndicator(
                                progress = { voiceState.amplitudeNormalized },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = { onStopAndSend(selectedRoom?.id) },
                                    modifier = Modifier.weight(1f).testTag("voice_stop_send_btn")
                                ) {
                                    Icon(Icons.Default.Stop, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.stop_recording))
                                }
                                OutlinedButton(onClick = onCancelRecord) {
                                    Text(stringResource(R.string.cancel))
                                }
                            }
                        } else {
                            Button(
                                onClick = {
                                    if (hasMicPerm) onStartRecord()
                                    else permLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                },
                                modifier = Modifier.fillMaxWidth().testTag("voice_start_record_btn")
                            ) {
                                Icon(Icons.Default.Mic, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.record_voice))
                            }
                        }
                    }
                }
            }

            item { Text("Saved Voice Messages (${voiceMessages.size})", style = MaterialTheme.typography.titleMedium) }
            items(voiceMessages, key = { it.id }) { msg ->
                val isCurrent = voiceState.playingPath == msg.attachmentPath
                val isPlaying = isCurrent && !voiceState.isPaused
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { msg.attachmentPath?.let(onTogglePlay) },
                                modifier = Modifier.testTag("voice_play_btn_${msg.id}")
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Play/Pause"
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(msg.senderName, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Duration: ${formatDuration(msg.voiceDurationMs)} • ${formatDateTime(msg.timestamp)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { onDeleteMessage(msg.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
                            }
                        }
                        if (isCurrent && voiceState.playbackDurationMs > 0L) {
                            LinearProgressIndicator(
                                progress = {
                                    (voiceState.playbackPositionMs.toFloat() / voiceState.playbackDurationMs)
                                        .coerceIn(0f, 1f)
                                },
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
