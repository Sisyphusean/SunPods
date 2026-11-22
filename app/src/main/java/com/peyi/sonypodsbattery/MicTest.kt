package com.peyi.sonypodsbattery

import androidx.compose.animation.animateContentSize
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.log10
import kotlin.math.sqrt

/*
 * Call mic test: records a few seconds through the earbuds' microphone exactly the way a call
 * uses it (communication mode, voice processing on), then plays it back through the earbuds.
 * You hear yourself as callers do. Nothing is saved or sent anywhere.
 */
internal object MicTest {
    const val RATE = 16_000
    val LENGTHS = listOf(5, 10, 15, 30)

    data class Analysis(val voiceDb: Double, val noiseDb: Double, val clipped: Double, val device: String)

    private fun audio(context: Context) = context.getSystemService(AudioManager::class.java)

    /** Routes call audio to the earbuds. Returns their name, or null if they can't take a call. */
    @SuppressLint("MissingPermission")
    suspend fun routeToEarbuds(context: Context): String? = withContext(Dispatchers.IO) {
        val am = audio(context) ?: return@withContext null
        am.mode = AudioManager.MODE_IN_COMMUNICATION
        if (Build.VERSION.SDK_INT >= 31) {
            val dev = am.availableCommunicationDevices.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
            } ?: return@withContext null.also { am.mode = AudioManager.MODE_NORMAL }
            if (!am.setCommunicationDevice(dev)) return@withContext null.also { am.mode = AudioManager.MODE_NORMAL }
            // The call link takes a moment to come up.
            for (i in 0 until 20) { if (am.communicationDevice?.id == dev.id) break; delay(100) }
            delay(600)
            dev.productName?.toString()?.ifBlank { null } ?: "Earbuds"
        } else {
            @Suppress("DEPRECATION") run { am.startBluetoothSco(); am.isBluetoothScoOn = true }
            delay(2_000)
            "Earbuds"
        }
    }

    fun release(context: Context) {
        val am = audio(context) ?: return
        if (Build.VERSION.SDK_INT >= 31) runCatching { am.clearCommunicationDevice() }
        else @Suppress("DEPRECATION") runCatching { am.isBluetoothScoOn = false; am.stopBluetoothSco() }
        am.mode = AudioManager.MODE_NORMAL
    }

    /** Records [SECONDS] seconds; [onLevel] gets 0..1 about ten times a second. */
    @SuppressLint("MissingPermission")
    suspend fun record(seconds: Int, onLevel: (Float) -> Unit): ShortArray = withContext(Dispatchers.IO) {
        val min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, RATE))
        val out = ShortArray(RATE * seconds)
        try {
            rec.startRecording()
            var n = 0
            val chunk = ShortArray(RATE / 10)
            while (n < out.size) {
                val r = rec.read(chunk, 0, minOf(chunk.size, out.size - n))
                if (r <= 0) break
                System.arraycopy(chunk, 0, out, n, r)
                n += r
                val rms = sqrt(chunk.take(r).sumOf { it.toDouble() * it } / r)
                onLevel((rms / 8_000).toFloat().coerceIn(0f, 1f))
            }
        } finally {
            runCatching { rec.stop() }; rec.release()
        }
        out
    }

    suspend fun play(samples: ShortArray) = withContext(Dispatchers.IO) {
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(RATE).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(samples.size * 2)
            .build()
        try {
            track.write(samples, 0, samples.size)
            track.play()
            delay(samples.size * 1000L / RATE + 300)
        } finally {
            runCatching { track.stop() }; track.release()
        }
    }

    /** Voice level (loud frames), background level (quiet frames) and clipping, in dBFS. */
    fun analyse(samples: ShortArray, device: String): Analysis {
        val frame = RATE / 20
        val levels = (0 until samples.size / frame).map { f ->
            var sum = 0.0
            for (i in f * frame until (f + 1) * frame) sum += samples[i].toDouble() * samples[i]
            20 * log10(maxOf(sqrt(sum / frame), 1.0) / 32768.0)
        }.sorted()
        val voice = levels[(levels.size * 0.9).toInt().coerceAtMost(levels.size - 1)]
        val noise = levels[(levels.size * 0.1).toInt()]
        val clipped = samples.count { it >= 32_000 || it <= -32_000 }.toDouble() / samples.size
        return Analysis(voice, noise, clipped, device)
    }

    /** Plain-language verdicts, most important first. */
    fun verdicts(a: Analysis): List<String> = buildList {
        if (a.voiceDb < -42) add("Your voice is very quiet. Make sure both earbuds fit snugly, since they also hear you through your ears.")
        else if (a.voiceDb < -32) add("Your voice is a little quiet. A snugger fit or speaking slightly louder can help.")
        if (a.voiceDb - a.noiseDb < 15) add("Background noise is loud compared with your voice. Callers may find it hard to hear you here.")
        if (a.clipped > 0.01) add("Some parts were too loud and sounded distorted.")
        if (isEmpty()) add("Your voice sounds clear. If callers still have trouble hearing you, the Bluetooth connection may be dropping out. Keep your phone on the same side as your earbuds.")
    }
}

private sealed interface MicState {
    data object Idle : MicState
    data object Starting : MicState
    data class Recording(val level: Float, val left: Int) : MicState
    data object Playing : MicState
    data class Done(val analysis: MicTest.Analysis, val samples: ShortArray) : MicState
    data class Failed(val message: String) : MicState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MicTestSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<MicState>(MicState.Idle) }
    var level by remember { mutableFloatStateOf(0f) }
    var seconds by remember { mutableStateOf(5) }

    fun run() {
        state = MicState.Starting
        scope.launch {
            val device = MicTest.routeToEarbuds(context)
            if (device == null) {
                MicTest.release(context)
                state = MicState.Failed("Couldn't use your earbuds' mic. Make sure they're connected and try again.")
                return@launch
            }
            try {
                val start = System.currentTimeMillis()
                val samples = MicTest.record(seconds) { l ->
                    level = l
                    state = MicState.Recording(l, (seconds - (System.currentTimeMillis() - start) / 1000).toInt().coerceAtLeast(0))
                }
                state = MicState.Playing
                MicTest.play(samples)
                state = MicState.Done(MicTest.analyse(samples, device), samples)
            } catch (e: Exception) {
                state = MicState.Failed("The test stopped early. Try again.")
            } finally {
                MicTest.release(context)
            }
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) run() else state = MicState.Failed("SunPods needs microphone access to run the test. It only records during the test.")
    }

    fun start() {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) run()
        else permission.launch(Manifest.permission.RECORD_AUDIO)
    }

    ModalBottomSheet(onDismissRequest = { MicTest.release(context); onDismiss() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().animateContentSize(androidx.compose.animation.core.spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow)).padding(horizontal = 24.dp).padding(bottom = 24.dp).navigationBarsPadding()) {
            Text("Test Your Call Mic", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            val colors = MaterialTheme.colorScheme
            when (val s = state) {
                MicState.Idle -> {
                    Text("Talk normally, then SunPods plays it back so you hear what callers hear. Music pauses during the test.", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    Text("Length", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MicTest.LENGTHS.forEach { n ->
                            androidx.compose.material3.FilterChip(
                                selected = seconds == n, onClick = { seconds = n },
                                label = { Text("$n sec") }
                            )
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = ::start, modifier = Modifier.fillMaxWidth()) { Text("Start Recording") }
                }
                MicState.Starting -> {
                    Text("Getting your mic ready…", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    Spacer(Modifier.height(20.dp)); CircularProgressIndicator()
                }
                is MicState.Recording -> {
                    Text("Recording. Talk like you would on a call.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(16.dp))
                    LinearProgressIndicator(progress = { level }, modifier = Modifier.fillMaxWidth().height(10.dp).clip(CircleShape))
                    Spacer(Modifier.height(8.dp))
                    Text("${s.left} sec left", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
                MicState.Playing -> {
                    Text("Playing it back…", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(20.dp)); CircularProgressIndicator()
                }
                is MicState.Done -> {
                    Text("Recorded with ${s.analysis.device}", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    MicTest.verdicts(s.analysis).forEach { Text(it, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 4.dp)) }
                    Spacer(Modifier.height(20.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = {
                            scope.launch {
                                val samples = s.samples; val analysis = s.analysis
                                state = MicState.Playing
                                if (MicTest.routeToEarbuds(context) != null) runCatching { MicTest.play(samples) }
                                MicTest.release(context)
                                state = MicState.Done(analysis, samples)
                            }
                        }, modifier = Modifier.weight(1f)) { Text("Play Again") }
                        Button(onClick = ::start, modifier = Modifier.weight(1f)) { Text("Test Again") }
                    }
                }
                is MicState.Failed -> {
                    Text(s.message, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = ::start, modifier = Modifier.fillMaxWidth()) { Text("Try Again") }
                }
            }
        }
    }
}
