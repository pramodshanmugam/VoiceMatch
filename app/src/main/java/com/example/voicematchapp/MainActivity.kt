package com.example.voicematchapp


import android.Manifest
import android.content.pm.PackageManager
import android.content.res.AssetFileDescriptor
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.example.voicematchapp.ui.theme.VoiceMatchAppTheme
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MainActivity : ComponentActivity() {

    private var recording = false
    private lateinit var audioRecorder: AudioRecord
    private val sampleRate = 16000
    private val bufferSize = AudioRecord.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )
    private lateinit var interpreter: Interpreter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request permissions
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                initAudioRecorder()
                loadModel()
            } else {
                Log.e("VoiceMatchApp", "Permission denied")
            }
        }.launch(Manifest.permission.RECORD_AUDIO)

        setContent {
            VoiceMatchAppTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainContent(innerPadding)
                }
            }
        }
    }

    private fun initAudioRecorder() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        audioRecorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )
    }

    private fun loadModel() {
        try {
            val assetFileDescriptor: AssetFileDescriptor = assets.openFd("yamnet.tflite")
            val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
            val model = inputStream.channel.map(
                java.nio.channels.FileChannel.MapMode.READ_ONLY,
                assetFileDescriptor.startOffset,
                assetFileDescriptor.declaredLength
            )
            interpreter = Interpreter(model)
        } catch (e: IOException) {
            Log.e("VoiceMatchApp", "Failed to load TFLite model: $e")
        }
    }

    private fun startRecording() {
        audioRecorder.startRecording()
        recording = true
        val audioBuffer = ByteBuffer.allocateDirect(bufferSize).order(ByteOrder.nativeOrder())

        Thread {
            while (recording) {
                audioRecorder.read(audioBuffer, bufferSize)
                processAudio(audioBuffer)
                audioBuffer.clear()
            }
        }.start()
    }

    private fun stopRecording() {
        recording = false
        audioRecorder.stop()
        Log.d("VoiceMatchApp", "Recording stopped.")
    }

    private fun processAudio(audioBuffer: ByteBuffer) {
        val inputBuffer = ByteBuffer.allocateDirect(audioBuffer.capacity()).order(ByteOrder.nativeOrder())
        inputBuffer.put(audioBuffer)
        inputBuffer.rewind()

        val outputBuffer = Array(1) { FloatArray(1024) }
        interpreter.run(inputBuffer, outputBuffer)

        val embeddings = outputBuffer[0]
        Log.d("VoiceMatchApp", "Extracted embeddings: ${embeddings.joinToString()}")
        compareEmbeddings(embeddings)
    }

    private fun compareEmbeddings(embeddings: FloatArray) {
        val storedEmbeddings = loadStoredEmbeddings()
        val similarity = cosineSimilarity(embeddings, storedEmbeddings)
        if (similarity > 0.85) {
            Log.d("VoiceMatchApp", "Voice matched with similarity: $similarity")
        } else {
            Log.d("VoiceMatchApp", "No match found. Similarity: $similarity")
        }
    }

    private fun loadStoredEmbeddings(): FloatArray {
        return FloatArray(1024) { 0.1f } // Replace with actual stored embeddings
    }

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        val dotProduct = a.zip(b) { x, y -> x * y }.sum()
        val normA = Math.sqrt(a.map { it * it }.sum().toDouble()).toFloat()
        val normB = Math.sqrt(b.map { it * it }.sum().toDouble()).toFloat()
        return dotProduct / (normA * normB)
    }

    @Composable
    fun MainContent(innerPadding: PaddingValues) {
        Column(modifier = Modifier.padding(innerPadding)) {
            Button(
                onClick = {
                    if (!recording) {
                        startRecording()
                    } else {
                        stopRecording()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(text = if (recording) "Stop Recording" else "Start Recording")
            }
        }
    }
}
