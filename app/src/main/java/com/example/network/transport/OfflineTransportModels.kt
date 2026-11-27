package com.example.network.transport

import com.example.data.local.TransportKind

data class DiscoveredPeer(
    val id: String,
    val name: String,
    val transportKind: TransportKind,
    val addressLabel: String,
    val isPaired: Boolean = false
)

data class PendingConnectionRequest(
    val endpointId: String,
    val peerName: String,
    val transportKind: TransportKind,
    val authDigits: String = ""
)

data class ConnectedTransportPeer(
    val transportPeerKey: String,
    val endpointId: String,
    val deviceId: String,
    val displayName: String,
    val transportKind: TransportKind,
    val roomCode: String = "",
    val safetyCode: String = "",
    val isAuthenticated: Boolean = false
)

enum class RadioStatus {
    IDLE,
    ADVERTISING,
    DISCOVERING,
    ACTIVE_SERVER,
    CONNECTING,
    ERROR
}

data class TransportStatusSnapshot(
    val kind: TransportKind,
    val isSupported: Boolean,
    val availabilityNote: String,
    val status: RadioStatus = RadioStatus.IDLE,
    val localEndpointInfo: String = "",
    val errorMessage: String? = null
)

enum class TransferStatus {
    IN_PROGRESS,
    COMPLETED,
    INTERRUPTED,
    FAILED
}

data class FileTransferProgress(
    val transferId: String,
    val conversationId: String,
    val fileName: String,
    val totalBytes: Long,
    val transferredBytes: Long,
    val isOutgoing: Boolean,
    val status: TransferStatus,
    val errorMessage: String? = null
)
