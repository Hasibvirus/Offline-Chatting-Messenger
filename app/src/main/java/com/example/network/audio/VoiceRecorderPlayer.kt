package com.example.network.audio

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

data class VoiceRecorderState(
    val isRecording: Boolean = false,
    val recordingDurationMs: Long = 0L,
    val amplitudeNormalized: Float = 0f,
    val playingPath: String? = null,
    val isPaused: Boolean = false,
    val playbackPositionMs: Long = 0L,
    val playbackDurationMs: Long = 0L
)

class VoiceRecorderPlayer(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _state = MutableStateFlow(VoiceRecorderState())
    val state: StateFlow<VoiceRecorderState> = _state.asStateFlow()

    private var recorder: MediaRecorder? = null
    private var currentFile: File? = null
    private var recordStartTimeMs = 0L
    private var recordTickerJob: Job? = null

    private var player: MediaPlayer? = null
    private var playTickerJob: Job? = null

    fun startRecording(outputDir: File): Result<File> {
        stopPlayback()
        if (_state.value.isRecording) {
            return Result.failure(IllegalStateException("Already recording"))
        }
        return try {
            if (!outputDir.exists()) outputDir.mkdirs()
            val outFile = File(outputDir, "voice_${UUID.randomUUID().toString().take(8)}.m4a")
            val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            mr.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(24000)
                setAudioEncodingBitRate(48000)
                setOutputFile(outFile.absolutePath)
                prepare()
                start()
            }
            recorder = mr
            currentFile = outFile
            recordStartTimeMs = System.currentTimeMillis()
            _state.value = _state.value.copy(
                isRecording = true,
                recordingDurationMs = 0L,
                amplitudeNormalized = 0f
            )

            recordTickerJob = scope.launch {
                while (isActive && _state.value.isRecording) {
                    val elapsed = System.currentTimeMillis() - recordStartTimeMs
                    val amp = runCatching { (recorder?.maxAmplitude ?: 0) / 32767f }.getOrDefault(0f)
                    _state.value = _state.value.copy(
                        recordingDurationMs = elapsed,
                        amplitudeNormalized = amp.coerceIn(0f, 1f)
                    )
                    delay(80)
                }
            }
            Result.success(outFile)
        } catch (e: Exception) {
            cleanupRecorder()
            Result.failure(e)
        }
    }

    fun stopRecording(): Pair<File, Long>? {
        recordTickerJob?.cancel()
        recordTickerJob = null
        val duration = (System.currentTimeMillis() - recordStartTimeMs).coerceAtLeast(500L)
        val file = currentFile
        runCatching {
            recorder?.stop()
        }
        cleanupRecorder()
        _state.value = _state.value.copy(
            isRecording = false,
            recordingDurationMs = 0L,
            amplitudeNormalized = 0f
        )
        return if (file != null && file.exists() && file.length() > 0L) {
            file to duration
        } else {
            file?.delete()
            null
        }
    }

    fun cancelRecording() {
        recordTickerJob?.cancel()
        recordTickerJob = null
        runCatching { recorder?.stop() }
        cleanupRecorder()
        currentFile?.delete()
        currentFile = null
        _state.value = _state.value.copy(
            isRecording = false,
            recordingDurationMs = 0L,
            amplitudeNormalized = 0f
        )
    }

    fun togglePlayPause(filePath: String) {
        val current = _state.value
        if (current.playingPath == filePath && player != null) {
            if (current.isPaused) {
                player?.start()
                _state.value = current.copy(isPaused = false)
            } else {
                player?.pause()
                _state.value = current.copy(isPaused = true)
            }
            return
        }

        stopPlayback()
        val file = File(filePath)
        if (!file.exists()) return
        runCatching {
            val mp = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                start()
                setOnCompletionListener { stopPlayback() }
            }
            player = mp
            _state.value = _state.value.copy(
                playingPath = filePath,
                isPaused = false,
                playbackPositionMs = 0L,
                playbackDurationMs = mp.duration.toLong()
            )
            playTickerJob = scope.launch {
                while (isActive && player != null) {
                    val pos = runCatching { player?.currentPosition?.toLong() ?: 0L }.getOrDefault(0L)
                    _state.value = _state.value.copy(playbackPositionMs = pos)
                    delay(120)
                }
            }
        }.onFailure {
            stopPlayback()
        }
    }

    fun stopPlayback() {
        playTickerJob?.cancel()
        playTickerJob = null
        runCatching {
            player?.stop()
            player?.release()
        }
        player = null
        _state.value = _state.value.copy(
            playingPath = null,
            isPaused = false,
            playbackPositionMs = 0L,
            playbackDurationMs = 0L
        )
    }

    private fun cleanupRecorder() {
        runCatching { recorder?.release() }
        recorder = null
    }
}
