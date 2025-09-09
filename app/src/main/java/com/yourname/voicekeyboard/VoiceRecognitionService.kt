package com.yourname.voicekeyboard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlinx.coroutines.*

class VoiceRecognitionService : Service() {
    companion object {
        private const val TAG = "VoiceRecognitionService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "voice_keyboard_channel"
        private const val WAKE_WORD = "weight"

        // Key codes for Android
        const val KEYCODE_DPAD_UP = KeyEvent.KEYCODE_DPAD_UP
        const val KEYCODE_DPAD_DOWN = KeyEvent.KEYCODE_DPAD_DOWN
        const val KEYCODE_DPAD_LEFT = KeyEvent.KEYCODE_DPAD_LEFT

        // Tone constants for AI assistant style
        private const val TONE_DURATION = 80
        private const val PAUSE_DURATION = 40L
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private val adbUtils = AdbUtils()
    private var isConnected = false
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Audio management
    private var audioManager: AudioManager? = null
    private var originalNotificationVolume = 0
    private var audioFeedbackEnabled = true // Default ON
    private var volumeWasMuted = false

    // Tablet connection details
    private var tabletHost = "192.168.88.232"
    private var tabletPort = 5555

    private var isListening = false
    private var reconnectAttempts = 0
    private val maxReconnectAttempts = 3

    private val audioToggleReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val enabled = intent?.getBooleanExtra("audio_enabled", true) ?: true
            audioFeedbackEnabled = enabled
            sendLog("Audio feedback ${if (enabled) "enabled" else "disabled"}")

            // If audio was disabled while muted, restore volume
            if (!enabled && volumeWasMuted) {
                restoreNotificationSounds()
                volumeWasMuted = false
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        sendLog("Service created")

        // Initialize audio manager
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        originalNotificationVolume = audioManager?.getStreamVolume(AudioManager.STREAM_NOTIFICATION) ?: 0

        // Register audio toggle receiver
        LocalBroadcastManager.getInstance(this).registerReceiver(
            audioToggleReceiver,
            IntentFilter("AUDIO_FEEDBACK_TOGGLE")
        )

        initializeSpeechRecognizer()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())

        // Get tablet connection details from intent
        intent?.let {
            tabletHost = it.getStringExtra("tablet_host") ?: tabletHost
            tabletPort = it.getIntExtra("tablet_port", tabletPort)
            audioFeedbackEnabled = it.getBooleanExtra("audio_feedback_enabled", true)
        }

        sendLog("Service started - connecting to $tabletHost:$tabletPort")
        sendLog("Audio feedback: ${if (audioFeedbackEnabled) "enabled" else "disabled"}")
        connectToTablet()

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Voice Keyboard Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Voice controlled keyboard service"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val status = if (isConnected) "Connected" else "Connecting..."
        val audioStatus = if (audioFeedbackEnabled) "🔊" else "🔇"
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Voice Keyboard Active $audioStatus")
            .setContentText("$status - Listening for '$WAKE_WORD' commands")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun muteNotificationSounds() {
        if (audioFeedbackEnabled) {
            audioManager?.setStreamVolume(AudioManager.STREAM_NOTIFICATION, 0, 0)
            volumeWasMuted = true
        }
    }

    private fun restoreNotificationSounds() {
        audioManager?.setStreamVolume(AudioManager.STREAM_NOTIFICATION, originalNotificationVolume, 0)
        volumeWasMuted = false
    }

    private fun playSuccessTone() {
        if (!audioFeedbackEnabled) {
            return // Skip audio if disabled
        }

        serviceScope.launch(Dispatchers.IO) {
            var toneGenerator: ToneGenerator? = null
            try {
                // Temporarily restore volume for our success tone
                restoreNotificationSounds()
                delay(100) // Small delay to ensure volume is restored

                // Create ToneGenerator for AI assistant style sound
                toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70)

                // Play AI assistant style confirmation - ascending pleasant tones
                // Similar to Google Assistant or Alexa confirmation
                toneGenerator.startTone(ToneGenerator.TONE_PROP_ACK, TONE_DURATION)
                delay(TONE_DURATION.toLong() + PAUSE_DURATION)

                toneGenerator.startTone(ToneGenerator.TONE_SUP_CONFIRM, TONE_DURATION)
                delay(TONE_DURATION.toLong() + PAUSE_DURATION)

                // Final higher tone for completion
                toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP2, TONE_DURATION)
                delay(TONE_DURATION.toLong() + 50)

                // Mute again for speech recognition
                muteNotificationSounds()
            } catch (e: Exception) {
                Log.e(TAG, "Error playing AI assistant tone", e)
            } finally {
                // Clean up immediately
                toneGenerator?.release()
            }
        }
    }

    private fun initializeSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            sendLog("ERROR: Speech recognition not available on this device")
            return
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                if (!isListening) {
                    isListening = true
                    muteNotificationSounds() // Mute system sounds if audio feedback enabled
                    sendLog("Voice recognition ready - listening for commands...")
                }
            }

            override fun onBeginningOfSpeech() {
                sendLog("Speech detected...")
            }

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                isListening = false
                sendLog("Speech ended - processing...")
            }

            override fun onError(error: Int) {
                isListening = false
                val errorMsg = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                    SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                    SpeechRecognizer.ERROR_NETWORK -> "Network error"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                    SpeechRecognizer.ERROR_NO_MATCH -> "No speech input matched"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy"
                    SpeechRecognizer.ERROR_SERVER -> "Server error"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
                    else -> "Unknown error ($error)"
                }

                if (error != SpeechRecognizer.ERROR_NO_MATCH && error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                    sendLog("Speech recognition error: $errorMsg")
                }

                // Restart listening after a short delay
                serviceScope.launch {
                    delay(1000)
                    startListening()
                }
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                matches?.let {
                    sendLog("Voice input detected: ${it.joinToString(", ")}")
                    processVoiceResults(it)
                }

                // Continue listening
                serviceScope.launch {
                    delay(500)
                    startListening()
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                matches?.firstOrNull()?.let { partial ->
                    if (partial.lowercase().contains(WAKE_WORD)) {
                        sendLog("Wake word detected in partial: $partial")
                    }
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        startListening()
    }

    private fun startListening() {
        if (speechRecognizer == null) {
            sendLog("ERROR: Speech recognizer not initialized")
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        }

        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            sendLog("ERROR starting speech recognition: ${e.message}")
        }
    }

    private fun processVoiceResults(results: List<String>) {
        for (result in results) {
            val lowerResult = result.lowercase().trim()

            if (lowerResult.contains(WAKE_WORD)) {
                sendLog("WAKE WORD DETECTED: '$result'")
                handleVoiceCommand(lowerResult)
                break
            }
        }
    }

    private fun handleVoiceCommand(command: String) {
        sendLog("Processing command: '$command'")

        val (keyCode, action) = when {
            command.contains("up") -> Pair(KEYCODE_DPAD_UP, "Up Arrow")
            command.contains("down") -> Pair(KEYCODE_DPAD_DOWN, "Down Arrow")
            command.contains("on") -> Pair(KEYCODE_DPAD_LEFT, "Left Arrow (ON)")
            command.contains("off") -> Pair(KEYCODE_DPAD_LEFT, "Left Arrow (OFF)")
            else -> Pair(null, "Unknown")
        }

        if (keyCode != null) {
            sendLog("Sending $action to tablet...")
            serviceScope.launch {
                if (isConnected) {
                    val success = adbUtils.sendKeyEvent(keyCode)
                    if (success) {
                        val audioStatus = if (audioFeedbackEnabled) " " else ""
                        sendLog("✓ Successfully sent $action$audioStatus")
                        playSuccessTone() // AI assistant style confirmation
                        updateNotification("Last: $action")
                    } else {
                        sendLog("✗ Failed to send $action - connection lost")
                        isConnected = false
                        connectToTablet()
                    }
                } else {
                    sendLog("✗ Not connected to tablet - attempting reconnection...")
                    connectToTablet()
                }
            }
        } else {
            sendLog("✗ No matching action for command")
        }
    }

    private fun connectToTablet() {
        serviceScope.launch {
            try {
                sendLog("Connecting to tablet at $tabletHost:$tabletPort...")
                isConnected = adbUtils.connect(tabletHost, tabletPort)

                if (isConnected) {
                    sendLog("✓ Successfully connected to tablet")
                    reconnectAttempts = 0
                    updateNotification("Connected")
                } else {
                    reconnectAttempts++
                    sendLog("✗ Failed to connect to tablet (attempt $reconnectAttempts/$maxReconnectAttempts)")

                    if (reconnectAttempts < maxReconnectAttempts) {
                        sendLog("Retrying connection in 5 seconds...")
                        delay(5000)
                        connectToTablet()
                    } else {
                        sendLog("✗ Max reconnection attempts reached. Check tablet connection.")
                        updateNotification("Connection Failed")
                    }
                }
            } catch (e: Exception) {
                sendLog("✗ Connection error: ${e.message}")
                isConnected = false
                updateNotification("Connection Error")
            }
        }
    }

    private fun updateNotification(status: String) {
        val audioStatus = if (audioFeedbackEnabled) "🔊" else "🔇"
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Voice Keyboard Active $audioStatus")
            .setContentText("$status - Listening for '$WAKE_WORD'")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun sendLog(message: String) {
        Log.d(TAG, message)

        // Send to UI
        val intent = Intent("VOICE_KEYBOARD_LOG").apply {
            putExtra("log_message", message)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        sendLog("Service stopping...")

        // Restore original volume when service stops
        restoreNotificationSounds()

        // Unregister receiver
        LocalBroadcastManager.getInstance(this).unregisterReceiver(audioToggleReceiver)

        speechRecognizer?.destroy()
        adbUtils.disconnect()
        serviceScope.cancel()
    }
}