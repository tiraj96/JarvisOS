package com.stark.jarvis

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    private lateinit var tts: TextToSpeech
    private lateinit var speechRecognizer: SpeechRecognizer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)

        val prefs = getSharedPreferences("jarvis_prefs", Context.MODE_PRIVATE)

        setContent {
            var apiKey by remember { mutableStateOf(prefs.getString("api_key", "") ?: "") }
            var statusText by remember { mutableStateOf("ONLINE // TAP CORE TO SPEAK") }
            var replyText by remember { mutableStateOf("Systems online, Sir. Tap the core to give a command.") }
            var isListening by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()

            val permLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted ->
                if (!granted) statusText = "MIC PERMISSION REQUIRED"
            }

            LaunchedEffect(Unit) {
                permLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }

            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
            val scale by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = if (isListening) 1.22f else 1.05f,
                animationSpec = infiniteRepeatable(
                    animation = tween(if (isListening) 450 else 1400, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "reactor"
            )

            val speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            }

            DisposableEffect(Unit) {
                speechRecognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        isListening = true
                        statusText = "VOICE CHANNEL OPEN..."
                    }
                    override fun onResults(results: Bundle?) {
                        isListening = false
                        val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: return
                        statusText = "CMD: \"$spoken\""

                        if (apiKey.isBlank()) {
                            val msg = "Heard: $spoken. Please enter your Gemini API key below to activate my neural network, Sir."
                            replyText = msg
                            tts.speak(msg, TextToSpeech.QUEUE_FLUSH, null, null)
                            return
                        }

                        scope.launch {
                            try {
                                val model = GenerativeModel(
                                    modelName = "gemini-2.5-flash",
                                    apiKey = apiKey.trim(),
                                    systemInstruction = content {
                                        text("You are a sophisticated, British-mannered AI assistant. Respond in 1 to 2 crisp, witty sentences and address the user as Sir.")
                                    }
                                )
                                val response = model.generateContent(spoken)
                                val cleanReply = (response.text ?: "No response received, Sir.").replace("*", "")
                                replyText = cleanReply
                                statusText = "STANDBY // TAP CORE TO SPEAK"
                                tts.speak(cleanReply, TextToSpeech.QUEUE_FLUSH, null, null)
                            } catch (e: Exception) {
                                replyText = "Connection error: ${e.localizedMessage}"
                                statusText = "ERROR"
                            }
                        }
                    }
                    override fun onError(error: Int) {
                        isListening = false
                        statusText = "STANDBY // TAP CORE TO RETRY"
                    }
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
                onDispose { speechRecognizer.destroy() }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF040A14))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "J.A.R.V.I.S. SYSTEM MARK VII",
                    color = Color(0xFF00E5FF),
                    fontSize = 16.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(210.dp)
                        .scale(scale)
                        .border(4.dp, Color(0xFF00E5FF), CircleShape)
                        .padding(18.dp)
                        .border(2.dp, Color(0x8800E5FF), CircleShape)
                        .clickable {
                            tts.stop()
                            if (isListening) speechRecognizer.stopListening()
                            else speechRecognizer.startListening(speechIntent)
                        }
                ) {
                    Text(
                        text = if (isListening) "LISTENING" else "TAP CORE",
                        color = Color(0xFF00E5FF),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = replyText,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    Text(
                        text = statusText,
                        color = Color(0xFF00E5FF),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = {
                            apiKey = it
                            prefs.edit().putString("api_key", it).apply()
                        },
                        label = { Text("Gemini API Key (Auto-Saved)", color = Color(0xFF00E5FF), fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.UK
            tts.setPitch(0.9f)
            tts.setSpeechRate(1.05f)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tts.shutdown()
    }
}
