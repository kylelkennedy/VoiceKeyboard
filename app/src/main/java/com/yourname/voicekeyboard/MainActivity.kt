package com.yourname.voicekeyboard

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var hostEditText: EditText
    private lateinit var portEditText: EditText
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var clearLogButton: Button
    private lateinit var toggleAudioButton: Button
    private lateinit var logTextView: TextView

    private var isServiceRunning = false
    private var audioFeedbackEnabled = true // Default ON
    private val logMessages = mutableListOf<String>()
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    private val logReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val message = intent?.getStringExtra("log_message")
            message?.let { addLogMessage(it) }
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            updateUI()
            showToast("Permissions granted")
        } else {
            showToast("Microphone permission is required for voice recognition")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeViews()
        setupClickListeners()
        requestPermissions()
        updateUI()

        // Register log receiver
        LocalBroadcastManager.getInstance(this).registerReceiver(
            logReceiver,
            IntentFilter("VOICE_KEYBOARD_LOG")
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(logReceiver)
    }

    private fun initializeViews() {
        statusText = findViewById(R.id.statusText)
        hostEditText = findViewById(R.id.hostEditText)
        portEditText = findViewById(R.id.portEditText)
        startButton = findViewById(R.id.startButton)
        stopButton = findViewById(R.id.stopButton)
        clearLogButton = findViewById(R.id.clearLogButton)
        toggleAudioButton = findViewById(R.id.toggleAudioButton)
        logTextView = findViewById(R.id.logTextView)

        // Set default values
        hostEditText.setText("192.168.88.232")
        portEditText.setText("5555")

        // Set initial audio button state
        updateAudioButtonText()
    }

    private fun setupClickListeners() {
        startButton.setOnClickListener {
            if (hasRequiredPermissions()) {
                startVoiceService()
            } else {
                requestPermissions()
            }
        }

        stopButton.setOnClickListener {
            stopVoiceService()
        }

        clearLogButton.setOnClickListener {
            clearLog()
        }

        toggleAudioButton.setOnClickListener {
            toggleAudioFeedback()
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        val permissions = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.RECORD_AUDIO)
        }

        if (permissions.isNotEmpty()) {
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }

    private fun toggleAudioFeedback() {
        audioFeedbackEnabled = !audioFeedbackEnabled
        updateAudioButtonText()

        // Send update to service if it's running
        if (isServiceRunning) {
            val intent = Intent("AUDIO_FEEDBACK_TOGGLE").apply {
                putExtra("audio_enabled", audioFeedbackEnabled)
            }
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent)

            val status = if (audioFeedbackEnabled) "enabled" else "disabled"
            addLogMessage("Audio feedback $status")
        }

        val status = if (audioFeedbackEnabled) "enabled" else "disabled"
        showToast("Audio feedback $status")
    }

    private fun updateAudioButtonText() {
        val status = if (audioFeedbackEnabled) "ON" else "OFF"
        toggleAudioButton.text = "Audio: $status"

        // Update button color based on state
        val colorResId = if (audioFeedbackEnabled) {
            android.R.color.holo_green_dark
        } else {
            android.R.color.holo_red_dark
        }
        toggleAudioButton.setTextColor(ContextCompat.getColor(this, colorResId))
    }

    private fun startVoiceService() {
        if (!hasRequiredPermissions()) {
            showToast("Microphone permission required")
            return
        }

        val host = hostEditText.text.toString().trim()
        val portStr = portEditText.text.toString().trim()

        if (host.isEmpty()) {
            showToast("Please enter tablet IP address")
            return
        }

        val port = portStr.toIntOrNull()
        if (port == null || port < 1 || port > 65535) {
            showToast("Please enter valid port number")
            return
        }

        clearLog()
        addLogMessage("Starting Voice Keyboard service...")
        addLogMessage("Target: $host:$port")
        addLogMessage("Audio feedback: ${if (audioFeedbackEnabled) "enabled" else "disabled"}")

        val serviceIntent = Intent(this, VoiceRecognitionService::class.java).apply {
            putExtra("tablet_host", host)
            putExtra("tablet_port", port)
            putExtra("audio_feedback_enabled", audioFeedbackEnabled)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        isServiceRunning = true
        updateUI()
        showToast("Voice keyboard started")
    }

    private fun stopVoiceService() {
        addLogMessage("Stopping Voice Keyboard service...")

        val serviceIntent = Intent(this, VoiceRecognitionService::class.java)
        stopService(serviceIntent)

        isServiceRunning = false
        updateUI()
        showToast("Voice keyboard stopped")
    }

    private fun updateUI() {
        if (hasRequiredPermissions()) {
            if (isServiceRunning) {
                statusText.text = "Voice Keyboard: ACTIVE\n\nListening for 'Weight' commands:\n• Weight Up → Up Arrow\n• Weight Down → Down Arrow\n• Weight On/Off → Left Arrow"
                statusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
                startButton.isEnabled = false
                stopButton.isEnabled = true
                hostEditText.isEnabled = false
                portEditText.isEnabled = false
                toggleAudioButton.isEnabled = true
            } else {
                statusText.text = "Voice Keyboard: STOPPED\n\nEnter tablet IP and port, then tap Start"
                statusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
                startButton.isEnabled = true
                stopButton.isEnabled = false
                hostEditText.isEnabled = true
                portEditText.isEnabled = true
                toggleAudioButton.isEnabled = false
            }
        } else {
            statusText.text = "Microphone permission required"
            statusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
            startButton.isEnabled = false
            stopButton.isEnabled = false
            toggleAudioButton.isEnabled = false
        }
    }

    private fun addLogMessage(message: String) {
        val timestamp = dateFormat.format(Date())
        val logEntry = "[$timestamp] $message"

        logMessages.add(logEntry)

        // Keep only last 50 messages
        if (logMessages.size > 50) {
            logMessages.removeAt(0)
        }

        runOnUiThread {
            logTextView.text = logMessages.joinToString("\n") + "\n"

            // Auto-scroll to bottom
            logTextView.post {
                val scrollView = logTextView.parent as? android.widget.ScrollView
                scrollView?.fullScroll(android.widget.ScrollView.FOCUS_DOWN)
            }
        }
    }

    private fun clearLog() {
        logMessages.clear()
        logTextView.text = "Log cleared...\n"
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }
}