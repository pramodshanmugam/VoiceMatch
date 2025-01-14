package com.example.voicematchapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
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
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.*

class MainActivity : ComponentActivity() {

    private var isListening = false
    private lateinit var speechRecognizer: SpeechRecognizer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request microphone permission
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                initSpeechRecognizer()
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
            }

            override fun onError(error: Int) {
                val errorMessage = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                    SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                    SpeechRecognizer.ERROR_NETWORK -> "Network error"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                    SpeechRecognizer.ERROR_NO_MATCH -> "No match found"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer is busy"
                    SpeechRecognizer.ERROR_SERVER -> "Server error"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No input detected"
                    else -> "Unknown error"
                }
                Log.e("VoiceMatchApp", "Speech recognition error: $errorMessage")
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)

                if (matches != null && matches.isNotEmpty()) {
                    Log.d("VoiceMatchApp", "Final Recognized Speech: ${matches.joinToString(", ")}")

                    if (matches.any { it.equals("hello", ignoreCase = true) }) {
                        Log.d("VoiceMatchApp", "Matched phrase detected: Hello")
                        triggerApi()
                    } else {
                        Log.d("VoiceMatchApp", "No match found for the desired phrase.")
                    }
                } else {
                    Log.e("VoiceMatchApp", "No results received from speech recognition.")
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val partialMatches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                partialMatches?.forEach { partialResult ->
                    Log.d("VoiceMatchApp", "Partial Recognized Speech: $partialResult")
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

    private fun triggerApi() {
        val client = OkHttpClient()
        val request = Request.Builder()
            .url("https://your-api-endpoint.com/trigger") // Replace with your API URL
            .get()
            .build()

        Thread {
            try {
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    Log.d("VoiceMatchApp", "API triggered successfully")
                } else {
                    Log.e("VoiceMatchApp", "API call failed: ${response.message}")
                }
            } catch (e: Exception) {
                Log.e("VoiceMatchApp", "API call error: $e")
            }
        }.start()
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
        }
    }
}
