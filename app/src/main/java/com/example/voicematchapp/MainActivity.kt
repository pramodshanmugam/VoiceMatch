package com.example.voicematchapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.*

class MainActivity : ComponentActivity() {

    private var isListening = false
    private var isRecording = false
    private lateinit var speechRecognizer: SpeechRecognizer
    private var audioFile: File? = null
    private var mediaRecorder: MediaRecorder? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request microphone permission
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                initSpeechRecognizer()
                startListeningAndRecording()

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
    private fun startListeningAndRecording() {
        startContinuousRecording() // Start continuous recording
        startListening() // Start speech recognition
    }


    private var recordingHandler: Handler? = null

    private fun startContinuousRecording() {
        recordingHandler = Handler(Looper.getMainLooper())
        recordingHandler?.post(object : Runnable {
            override fun run() {
                if (isRecording) {
                    Log.d("VoiceMatchApp", "Stopping current recording")
                    stopRecording() // Stop the current recording
                }
                Log.d("VoiceMatchApp", "Starting new recording")
                startRecording() // Start a new recording
                recordingHandler?.postDelayed(this, 10000) // Loop every 10 seconds
            }
        })
        Log.d("VoiceMatchApp", "Continuous recording started")
    }


    private fun stopContinuousRecording() {
        recordingHandler?.removeCallbacksAndMessages(null)
        recordingHandler = null
        if (isRecording) {
            stopRecording()
        }
    }


    private val WAKE_WORD = "match" // Replace with your desired wake word


    private fun initSpeechRecognizer() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(object : android.speech.RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.d("VoiceMatchApp", "Ready for speech")
            }

            override fun onBeginningOfSpeech() {
                Log.d("VoiceMatchApp", "Speech started")
            }

            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                Log.d("VoiceMatchApp", "Speech ended")
                restartListening() // Restart after speech ends
            }


            override fun onError(error: Int) {
                val errorMessage = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                    SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                    SpeechRecognizer.ERROR_NETWORK -> "Network error"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                    SpeechRecognizer.ERROR_NO_MATCH -> "No match found"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                        Log.e("VoiceMatchApp", "Recognizer is busy, skipping restart")
                        return // Do not restart listening to prevent overlapping sessions
                    }
                    SpeechRecognizer.ERROR_SERVER -> "Server error"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No input detected"
                    else -> "Unknown error"
                }
                Log.e("VoiceMatchApp", "Speech recognition error: $errorMessage")

                // Restart the listener if it's a recoverable error
                restartListening()
            }



            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)

                if (matches != null) {
                    Log.d("VoiceMatchApp", "Recognized Speech: ${matches.joinToString(", ")}")

                    if (matches.any { it.equals(WAKE_WORD, ignoreCase = true) }) {
                        Log.d("VoiceMatchApp", "Wake word detected: $WAKE_WORD")
                        triggerApi(audioFile) // Trigger your API call
                    }
                }
                restartListening() // Restart for the next recognition cycle
            }



            private fun restartListening() {
                if (isListening) {
                    try {
                        speechRecognizer.stopListening() // Stop the current session
                        isListening = false // Mark as not listening
                    } catch (e: Exception) {
                        Log.e("VoiceMatchApp", "Error stopping recognizer: ${e.message}")
                    }
                }

                // Restart listening after a short delay
                Handler(Looper.getMainLooper()).postDelayed({
                    startListening()
                }, 10000) // Delay of 500ms
            }




            override fun onPartialResults(partialResults: Bundle?) {
                val partialMatches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                partialMatches?.forEach { partialResult ->
                    Log.d("VoiceMatchApp", "Partial Recognized Speech: $partialResult")
                    if (partialResult.equals(WAKE_WORD, ignoreCase = true)) {
                        Log.d("VoiceMatchApp", "Wake word detected in partial results: $WAKE_WORD")
                        triggerApi(audioFile) // Trigger your API call
                    }
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    private fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 7000) // 7 seconds
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 15000) // 15 seconds
        }
        speechRecognizer.startListening(intent)
        isListening = true
        Log.d("VoiceMatchApp", "Started listening")
    }

    private fun stopListening() {
        speechRecognizer.stopListening()
        isListening = false
        Log.d("VoiceMatchApp", "Stopped listening")
    }

    private fun triggerApi(audioFile: File?) {
        if (audioFile == null || !audioFile.exists()) {
            Log.e("VoiceMatchApp", "No audio file available for API trigger")
            return
        }

        // Stop recording if ongoing
        if (isRecording) {
            stopRecording()
            Log.d("VoiceMatchApp", "Recording stopped before triggering API")
        }

        Log.d("VoiceMatchApp", "Triggering API with file: ${audioFile.absolutePath}")

        val client = OkHttpClient()
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                audioFile.name,
                RequestBody.create("audio/mp3".toMediaType(), audioFile)
            )
            .build()

        val request = Request.Builder()
            .url("https://2d4wwhv1-8000.use.devtunnels.ms/trigger/") // Replace with your actual API endpoint
            .post(requestBody)
            .build()

        Thread {
            try {
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    Log.d("VoiceMatchApp", "API Response: $responseBody")
                } else {
                    Log.e("VoiceMatchApp", "API call failed: ${response.code}")
                }
            } catch (e: Exception) {
                Log.e("VoiceMatchApp", "API call error: $e")
            } finally {
                // Restart listening and recording on the main thread
                Handler(Looper.getMainLooper()).post {
                    startListeningAndRecording()
                }
            }
        }.start()
    }






    private fun startRecording() {
        try {
            // Release any previous MediaRecorder instance
            mediaRecorder?.release()
            mediaRecorder = null

            // Create or reuse the audio file
            if (audioFile == null) {
                audioFile = File(filesDir, "recording_temp.mp3") // Single file for overwriting
            }

            // Initialize MediaRecorder
            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(audioFile?.absolutePath)
                prepare() // Prepare MediaRecorder
                start()   // Start recording
            }

            isRecording = true
            Log.d("VoiceMatchApp", "Recording started: ${audioFile?.absolutePath}")
        } catch (e: IOException) {
            Log.e("VoiceMatchApp", "Recording failed: ${e.message}")
            e.printStackTrace()
        } catch (e: IllegalStateException) {
            Log.e("VoiceMatchApp", "MediaRecorder state error: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun stopRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            isRecording = false
            Log.d("VoiceMatchApp", "Recording stopped, file saved at: ${audioFile?.absolutePath}")
        } catch (e: Exception) {
            Log.e("VoiceMatchApp", "Error stopping recording: ${e.message}")
        }
    }


    var responseString: String? = null
    var transcriptionText: String? = null // To store the extracted transcription_text

    private fun uploadAudio() {
        audioFile?.let { file ->
            val client = OkHttpClient()
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    "user1.mp3", // Set the desired filename here
                    RequestBody.create("audio/mp3".toMediaType(), file)
                )
                .build()

            val request = Request.Builder()
                .url("https://2d4wwhv1-8000.use.devtunnels.ms/upload-audio/") // Replace with your FastAPI URL
                .post(requestBody)
                .build()

            Thread {
                try {
                    val response = client.newCall(request).execute()
                    if (response.isSuccessful) {
                        responseString = response.body?.string() ?: "Empty response"
                        Log.d("VoiceMatchApp", "Audio uploaded successfully: $responseString")

                        // Parse the JSON response to extract transcription_text
                        try {
                            val jsonResponse = JSONObject(responseString)
                            transcriptionText = jsonResponse.getString("transcription_text")
                            Log.d("VoiceMatchApp", "Transcription Text: $transcriptionText")
                        } catch (jsonException: Exception) {
                            Log.e("VoiceMatchApp", "Error parsing JSON: $jsonException")
                        }
                    } else {
                        responseString = "Error: ${response.message}"
                        Log.e("VoiceMatchApp", "Audio upload failed: $responseString")
                    }
                } catch (e: Exception) {
                    responseString = "Exception: $e"
                    Log.e("VoiceMatchApp", "Audio upload error: $e")
                }
            }.start()
        } ?: Log.e("VoiceMatchApp", "No audio file to upload")
    }

    @Composable
    fun MainContent(innerPadding: PaddingValues) {
        Column(modifier = Modifier.padding(innerPadding)) {
            Button(
                onClick = {
                    if (!isListening) {
                        startListening()
                    } else {
                        stopListening()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(text = if (isListening) "Stop Listening" else "Start Listening")
            }

            Button(
                onClick = {
                    if (!isRecording) {
                        startRecording()
                    } else {
                        stopRecording()
                        uploadAudio()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(text = if (isRecording) "Stop Recording and Upload" else "Start Recording")
            }
        }
    }
}