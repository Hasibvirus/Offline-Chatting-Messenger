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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Sos
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.ConversationEntity
import com.example.data.local.ConversationType
import com.example.data.local.PollEntity
import com.example.data.local.PollVoteEntity
import com.example.data.local.SosAlertEntity
import com.example.ui.components.AppTopBar
import com.example.ui.components.formatDateTime
import com.example.ui.theme.EmergencyCrimson
import kotlinx.serialization.json.Json

/** Screen 15: Group Polls */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupPollsScreen(
    initialConversationId: String?,
    conversations: List<ConversationEntity>,
    polls: List<PollEntity>,
    votes: List<PollVoteEntity>,
    localDeviceId: String,
    onCreatePoll: (conversationId: String, question: String, options: List<String>, allowMultiple: Boolean) -> Unit,
    onVote: (conversationId: String, pollId: String, optionIndex: Int) -> Unit,
    onClosePoll: (pollId: String) -> Unit,
    onBack: () -> Unit
) {
    val groupRooms = remember(conversations) {
        conversations.filter { it.type == ConversationType.GROUP }.ifEmpty { conversations }
    }
    var selectedRoom by remember(groupRooms, initialConversationId) {
        mutableStateOf(
            groupRooms.firstOrNull { it.id == initialConversationId } ?: groupRooms.firstOrNull()
        )
    }
    var showCreateDialog by remember { mutableStateOf(false) }

    val visiblePolls = remember(polls, selectedRoom) {
        val targetId = selectedRoom?.id
        if (targetId != null) polls.filter { it.conversationId == targetId } else polls
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.polls_title),
                subtitle = selectedRoom?.title ?: "Select a group",
                onBack = onBack
            )
        },
        floatingActionButton = {
            if (selectedRoom != null) {
                FloatingActionButton(
                    onClick = { showCreateDialog = true },
                    modifier = Modifier.testTag("create_poll_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.create_poll))
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (groupRooms.isNotEmpty()) {
                item {
                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                        OutlinedTextField(
                            value = selectedRoom?.let { "${it.title} (${it.roomCode})" } ?: "All Rooms",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Active Group") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            groupRooms.forEach { room ->
                                DropdownMenuItem(
                                    text = { Text("${room.title} (${room.roomCode})") },
                                    onClick = { selectedRoom = room; expanded = false }
                                )
                            }
                        }
                    }
                }
            }

            if (visiblePolls.isEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "No polls yet. Tap + to create an offline group poll synchronized across connected devices.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(18.dp)
                        )
                    }
                }
            } else {
                items(visiblePolls, key = { it.id }) { poll ->
                    val options = remember(poll.optionsJson) {
                        runCatching { Json.decodeFromString<List<String>>(poll.optionsJson) }
                            .getOrDefault(emptyList())
                    }
                    val pollVotes = votes.filter { it.pollId == poll.id }
                    val totalVotes = pollVotes.size
                    val myVotes = pollVotes.filter { it.voterId == localDeviceId }.map { it.optionIndex }.toSet()

                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(poll.question, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                if (poll.isClosed) {
                                    Icon(Icons.Default.Lock, contentDescription = "Closed", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Text(
                                text = "By ${poll.createdByName} • $totalVotes total votes • ${if (poll.allowMultiple) "Multi-choice" else "Single-choice"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            options.forEachIndexed { idx, optionText ->
                                val count = pollVotes.count { it.optionIndex == idx }
                                val fraction = if (totalVotes > 0) count.toFloat() / totalVotes else 0f
                                val isMine = idx in myVotes

                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = if (isMine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("poll_option_${poll.id}_$idx")
                                        .clickable(enabled = !poll.isClosed) {
                                            onVote(poll.conversationId, poll.id, idx)
                                        }
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = if (isMine) "✓ $optionText" else optionText,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isMine) FontWeight.Bold else FontWeight.Normal
                                            )
                                            Text(
                                                text = "$count (${(fraction * 100).toInt()}%)",
                                                style = MaterialTheme.typography.labelMedium
                                            )
                                        }
                                        Spacer(Modifier.height(6.dp))
                                        LinearProgressIndicator(
                                            progress = { fraction },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }

                            if (!poll.isClosed && poll.createdById == localDeviceId) {
                                TextButton(onClick = { onClosePoll(poll.id) }) {
                                    Text("Close Poll")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog && selectedRoom != null) {
        var question by remember { mutableStateOf("") }
        val options = remember { mutableStateListOf("", "") }
        var allowMultiple by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text(stringResource(R.string.create_poll)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = question,
                        onValueChange = { question = it },
                        label = { Text("Poll Question") },
                        modifier = Modifier.fillMaxWidth().testTag("poll_question_input")
                    )
                    options.forEachIndexed { idx, value ->
                        OutlinedTextField(
                            value = value,
                            onValueChange = { options[idx] = it },
                            label = { Text("Option ${idx + 1}") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("poll_option_input_$idx")
                        )
                    }
                    if (options.size < 5) {
                        TextButton(onClick = { options.add("") }) {
                            Text("+ Add Option")
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = allowMultiple, onCheckedChange = { allowMultiple = it })
                        Text("Allow multiple selections", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onCreatePoll(selectedRoom!!.id, question, options.toList(), allowMultiple)
                        showCreateDialog = false
                    },
                    modifier = Modifier.testTag("poll_submit_button")
                ) {
                    Text(stringResource(R.string.create_poll))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

/** Screen 16: Emergency SOS */
@Composable
fun EmergencySosScreen(
    initialConversationId: String?,
    alerts: List<SosAlertEntity>,
    connectedPeerCount: Int,
    onTriggerSos: (conversationId: String?, note: String) -> Unit,
    onAcknowledge: (String) -> Unit,
    onBack: () -> Unit
) {
    var note by remember { mutableStateOf("") }
    var showConfirmDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.sos_title),
                subtitle = "$connectedPeerCount reachable peer(s)",
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
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = EmergencyCrimson.copy(alpha = 0.14f)
                    )
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = EmergencyCrimson
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.sos_disclaimer),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Emergency Note / Location Landmark (Optional)") },
                    placeholder = { Text("e.g. Need medical aid near North Gate") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth().testTag("sos_note_input")
                )
            }

            item {
                Button(
                    onClick = { showConfirmDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = EmergencyCrimson),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .testTag("sos_trigger_button")
                ) {
                    Icon(Icons.Default.Sos, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.send_sos_button),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            item {
                Text("SOS Alert Log (${alerts.size})", style = MaterialTheme.typography.titleMedium)
            }

            items(alerts, key = { it.id }) { alert ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (alert.acknowledged) {
                            MaterialTheme.colorScheme.surface
                        } else {
                            EmergencyCrimson.copy(alpha = 0.1f)
                        }
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "SOS from ${alert.senderName}",
                                style = MaterialTheme.typography.titleSmall,
                                color = EmergencyCrimson,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = formatDateTime(alert.timestamp),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        Text(alert.message, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "Transport: ${alert.transportUsed}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!alert.acknowledged) {
                            OutlinedButton(onClick = { onAcknowledge(alert.id) }) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Acknowledge")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            icon = { Icon(Icons.Default.Sos, contentDescription = null, tint = EmergencyCrimson) },
            title = { Text(stringResource(R.string.sos_confirm_title)) },
            text = { Text(stringResource(R.string.sos_confirm_body)) },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDialog = false
                        onTriggerSos(initialConversationId, note)
                        note = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmergencyCrimson),
                    modifier = Modifier.testTag("sos_confirm_send_button")
                ) {
                    Text(stringResource(R.string.confirm), color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
