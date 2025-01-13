package com.example.voicematchapp

import android.Manifest
import android.content.pm.PackageManager
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
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
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
    private lateinit var classMap: List<String>
    private val client = OkHttpClient() // OkHttpClient instance

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request permissions
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                initAudioRecorder()
                loadModelFromAssets()
                classMap = loadClassMap()
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

    private fun loadModelFromAssets() {
        try {
            val assetFileDescriptor = assets.openFd("1.tflite")
            val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
            val modelBuffer = inputStream.channel.map(
                java.nio.channels.FileChannel.MapMode.READ_ONLY,
                assetFileDescriptor.startOffset,
                assetFileDescriptor.declaredLength
            )
            interpreter = Interpreter(modelBuffer)
            Log.d("VoiceMatchApp", "Model loaded successfully from assets.")
        } catch (e: Exception) {
            Log.e("VoiceMatchApp", "Failed to load model from assets: $e")
        }
    }

    private fun loadClassMap(): List<String> {
        val classMap = mutableListOf<String>()
        try {
            val inputStream = assets.open("yamnet_class_map.csv")
            inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val split = line.split(",")
                    if (split.size >= 2) {
                        classMap.add(split[1]) // Assumes the class name is in the second column
                    }
                }
            }
            Log.d("VoiceMatchApp", "Class map loaded successfully.")
        } catch (e: Exception) {
            Log.e("VoiceMatchApp", "Failed to load class map: $e")
        }
        return classMap
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

    private fun preprocessAudio(audioBuffer: ByteBuffer): FloatArray {
        val audioData = FloatArray(audioBuffer.remaining() / 2) // 16-bit PCM
        audioBuffer.rewind()

        for (i in audioData.indices) {
            val sample = audioBuffer.short.toFloat() / Short.MAX_VALUE
            audioData[i] = sample
        }

        return audioData
    }

    private fun processAudio(audioBuffer: ByteBuffer) {
        val audioData = preprocessAudio(audioBuffer)

        // Prepare input tensor
        val inputTensor = arrayOf(audioData)

        // Define output tensors
        val outputScores = Array(audioData.size / 480) { FloatArray(521) }
        val outputEmbeddings = Array(audioData.size / 480) { FloatArray(1024) }
        val outputSpectrogram = Array(96) { FloatArray(64) } // Updated to match [96, 64]

        try {
            // Run inference
            val outputs = HashMap<Int, Any>()
            outputs[0] = outputScores
            outputs[1] = outputEmbeddings
            outputs[2] = outputSpectrogram

            interpreter.runForMultipleInputsOutputs(inputTensor, outputs)

            // Extract outputs
            val scores = outputs[0] as Array<FloatArray>
            val embeddings = outputs[1] as Array<FloatArray>
            val logMelSpectrogram = outputs[2] as Array<FloatArray>

            // Aggregate scores (e.g., mean across frames)
            val meanScores = FloatArray(521) { 0f }
            for (frameScores in scores) {
                for (i in frameScores.indices) {
                    meanScores[i] += frameScores[i]
                }
            }
            for (i in meanScores.indices) {
                meanScores[i] = meanScores[i] / scores.size
            }

            // Find the highest scoring class
            val maxIndex = meanScores.indices.maxByOrNull { meanScores[it] } ?: -1
            if (maxIndex != -1) {
                Log.d("VoiceMatchApp", "Predicted Class: Index $maxIndex, Score: ${meanScores[maxIndex]}")
                if (maxIndex in classMap.indices) {
                    val predictedClassName = classMap[maxIndex]
                    Log.d("VoiceMatchApp", "Predicted Class Name: $predictedClassName")

                    // Send log data to FastAPI
                    sendLogDataToApi(predictedClassName, meanScores[maxIndex])
                }
            }

            Log.d("VoiceMatchApp", "Embeddings: ${embeddings.joinToString { it.joinToString() }}")
            Log.d("VoiceMatchApp", "Log Mel Spectrogram: ${logMelSpectrogram.joinToString { it.joinToString() }}")
        } catch (e: Exception) {
            Log.e("VoiceMatchApp", "Error during inference: $e")
        }
    }

    private fun sendLogDataToApi(predictedClassName: String, confidenceScore: Float) {
        val url = "http://10.0.2.2:8000/logs/" // Use 10.0.2.2 for emulator or host IP for physical devices
        val jsonBody = """
        {
            "id": 0,
            "timestamp": "${System.currentTimeMillis()}",
            "message": "Predicted Class: $predictedClassName with confidence: $confidenceScore"
        }
    """.trimIndent()
        val body: RequestBody = jsonBody.toRequestBody("application/json".toMediaTypeOrNull())

        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        Thread {
            try {
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    Log.d("VoiceMatchApp", "Log sent successfully: ${response.body?.string()}")
                } else {
                    Log.e("VoiceMatchApp", "Failed to send log: ${response.message}")
                }
            } catch (e: Exception) {
                Log.e("VoiceMatchApp", "Error while sending log: $e")
            }
        }.start()
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
