package com.example.network.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sqrt

enum class PttRadioState { IDLE, TRANSMITTING, RECEIVING }

class WalkieTalkieEngine(private val context: Context) {

    companion object {
        private const val SAMPLE_RATE = 16000
        private const val FRAME_BYTES = 1280 // 40ms @ 16kHz, 16-bit mono
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _state = MutableStateFlow(PttRadioState.IDLE)
    val state: StateFlow<PttRadioState> = _state.asStateFlow()

    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    private val _activeSpeaker = MutableStateFlow<String?>(null)
    val activeSpeaker: StateFlow<String?> = _activeSpeaker.asStateFlow()

    private var recordJob: Job? = null
    private var audioRecord: AudioRecord? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var audioTrack: AudioTrack? = null
    private var focusRequest: AudioFocusRequest? = null
    private var lastRxTimeMs = 0L

    private fun requestFocus(): Boolean {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(attrs)
            .setAcceptsDelayedFocusGain(false)
            .setOnAudioFocusChangeListener { change ->
                if (change == AudioManager.AUDIOFOCUS_LOSS) {
                    stopTransmitting()
                }
            }
            .build()
        focusRequest = req
        return audioManager.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonFocus() {
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        focusRequest = null
    }

    @SuppressLint("MissingPermission")
    fun startTransmitting(onFrameCaptured: (ByteArray) -> Unit): Result<Unit> {
        if (_state.value == PttRadioState.TRANSMITTING) return Result.success(Unit)
        return try {
            val minBuf = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(FRAME_BYTES * 2)

            val recorder = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuf
            )
            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                recorder.release()
                return Result.failure(IllegalStateException("Microphone AudioRecord failed to initialize"))
            }

            requestFocus()
            if (AcousticEchoCanceler.isAvailable()) {
                runCatching {
                    echoCanceler = AcousticEchoCanceler.create(recorder.audioSessionId)?.apply {
                        enabled = true
                    }
                }
            }

            audioRecord = recorder
            recorder.startRecording()
            _state.value = PttRadioState.TRANSMITTING

            recordJob = scope.launch {
                val buffer = ByteArray(FRAME_BYTES)
                while (isActive && _state.value == PttRadioState.TRANSMITTING) {
                    val read = recorder.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        val copy = buffer.copyOf(read)
                        _audioLevel.value = computeRmsNormalized(copy)
                        onFrameCaptured(copy)
                    }
                }
            }
            Result.success(Unit)
        } catch (se: SecurityException) {
            Result.failure(se)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun stopTransmitting() {
        recordJob?.cancel()
        recordJob = null
        runCatching { echoCanceler?.release() }
        echoCanceler = null
        runCatching {
            audioRecord?.stop()
            audioRecord?.release()
        }
        audioRecord = null
        abandonFocus()
        _audioLevel.value = 0f
        if (_state.value == PttRadioState.TRANSMITTING) {
            _state.value = PttRadioState.IDLE
        }
    }

    fun playIncomingFrame(senderName: String, pcmBytes: ByteArray) {
        if (_state.value == PttRadioState.TRANSMITTING) return
        scope.launch {
            try {
                val track = ensureAudioTrack()
                if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                    track.play()
                }
                _state.value = PttRadioState.RECEIVING
                _activeSpeaker.value = senderName
                _audioLevel.value = computeRmsNormalized(pcmBytes)
                lastRxTimeMs = System.currentTimeMillis()
                track.write(pcmBytes, 0, pcmBytes.size)
            } catch (_: Exception) {
                // Ignore transient audio playback failure
            }
        }
    }

    fun clearStaleReceivingState() {
        if (_state.value == PttRadioState.RECEIVING &&
            System.currentTimeMillis() - lastRxTimeMs > 650L
        ) {
            _state.value = PttRadioState.IDLE
            _activeSpeaker.value = null
            _audioLevel.value = 0f
        }
    }

    private fun ensureAudioTrack(): AudioTrack {
        audioTrack?.let { if (it.state == AudioTrack.STATE_INITIALIZED) return it }
        val minBuf = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(FRAME_BYTES * 2)

        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val format = AudioFormat.Builder()
            .setSampleRate(SAMPLE_RATE)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()

        return AudioTrack.Builder()
            .setAudioAttributes(attrs)
            .setAudioFormat(format)
            .setBufferSizeInBytes(minBuf)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
            .also { audioTrack = it }
    }

    private fun computeRmsNormalized(pcm16: ByteArray): Float {
        if (pcm16.size < 2) return 0f
        var sumSq = 0.0
        var count = 0
        var i = 0
        while (i + 1 < pcm16.size) {
            val sample = ((pcm16[i + 1].toInt() shl 8) or (pcm16[i].toInt() and 0xFF)).toShort()
            val norm = sample / 32768.0
            sumSq += norm * norm
            count++
            i += 2
        }
        if (count == 0) return 0f
        return (sqrt(sumSq / count).toFloat() * 3.2f).coerceIn(0f, 1f)
    }

    fun release() {
        stopTransmitting()
        runCatching {
            audioTrack?.stop()
            audioTrack?.release()
        }
        audioTrack = null
    }
}
