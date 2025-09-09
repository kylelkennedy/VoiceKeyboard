# Voice Controlled Keyboard for Android

A powerful Android application that enables voice control of Android tablets through wireless ADB. Control your tablet using voice commands with AI assistant-style audio feedback.

## Features

- **Voice Recognition**: Wake word activation with "Weight" followed by directional commands
- **Wireless ADB Integration**: Connects to Android tablets over Wi-Fi without USB cables
- **AI Assistant Audio**: Pleasant confirmation tones similar to Google Assistant/Alexa
- **Real-time Debug Logging**: Monitor voice recognition and connection status
- **Dark Theme Support**: Automatically adapts to system theme
- **Toggleable Audio Feedback**: Enable/disable confirmation sounds on demand
- **Background Service**: Continuous listening with foreground notification

## Supported Commands

| Voice Command | Action         | Key Sent   |
|---------------|----------------|------------|
| "Weight Up"   | Navigate up    | Up Arrow   |
| "Weight Down" | Navigate down  | Down Arrow |
| "Weight On"   | Select/confirm | Left Arrow |
| "Weight Off"  | Select/confirm | Left Arrow |

## Technical Requirements

### Phone (Controller)
- Android 11+ (API 30)
- Microphone access
- Wi-Fi connectivity
- 50MB storage space
- Developer options activated
- Wireless debugging enabled

### Tablet (Target Device)
- Android 11+ with wireless debugging enabled
- Same Wi-Fi network as controller phone
- Developer options activated

## Setup Instructions

### 1. Enable Wireless Debugging on Tablet
```bash
Settings → About tablet → Tap "Build number" 7 times
Settings → System → Developer Options → Enable "Wireless debugging"
Note the IP address and port (e.g., 192.168.88.232:5555)
```

### 2. Install and Configure App
1. Download and install the APK on your Android phone
2. Grant microphone permissions when prompted
3. Enter your tablet's IP address and port
4. Tap "Start Voice Keyboard"

### 3. Test Connection
- Say "Weight Up" to test voice recognition
- Check debug log for connection status
- Verify commands are sent to tablet

## Building from Source

### Prerequisites
- Android Studio Arctic Fox or later
- Kotlin 1.8+
- Minimum SDK: API 30 (Android 11)

### Dependencies
```kotlin
implementation("androidx.core:core-ktx:1.12.0")
implementation("androidx.appcompat:appcompat:1.6.1")
implementation("com.google.android.material:material:1.11.0")
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
implementation("androidx.localbroadcastmanager:localbroadcastmanager:1.1.0")
```

### Build Steps
1. Clone the repository
2. Open in Android Studio
3. Sync project with Gradle files
4. Build → Generate Signed Bundle/APK
5. Install on Android device

## Architecture

### Core Components
- **MainActivity**: UI management and user interaction
- **VoiceRecognitionService**: Background voice processing and ADB communication
- **AdbUtils**: Low-level ADB protocol implementation

### Data Flow
```
Voice Input → Speech Recognition → Wake Word Detection → 
Command Processing → ADB Connection → Tablet Key Event
```

## Audio Feedback

The app provides audio confirmation:
- **3-tone sequence**: Ascending pleasant tones for successful commands
- **Volume management**: Automatically mutes system sounds during operation
- **Toggle control**: Enable/disable audio feedback in real-time

## Troubleshooting

### Connection Issues
- Verify both devices are on same Wi-Fi network
- Check tablet's wireless debugging is enabled
- Confirm IP address and port are correct
- Try restarting wireless debugging on tablet

### Voice Recognition Problems
- Ensure microphone permissions are granted
- Speak clearly with "Weight" wake word
- Check debug log for recognition feedback
- Try in quieter environment

### Audio Feedback Issues
- Check notification volume settings
- Verify "Audio: ON" is displayed
- Test with different volume levels

## Permissions Required

| Permission                       | Purpose                                   |
|----------------------------------|-------------------------------------------|
| `RECORD_AUDIO`                   | Voice recognition and wake word detection |
| `INTERNET`                       | Network communication with tablet         |
| `ACCESS_NETWORK_STATE`           | Network connectivity monitoring           |
| `WAKE_LOCK`                      | Maintain service during operation         |
| `FOREGROUND_SERVICE`             | Background voice processing               |
| `FOREGROUND_SERVICE_MICROPHONE`  | Microphone access in background           |

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/improvement`)
3. Commit changes (`git commit -am 'Add new feature'`)
4. Push to branch (`git push origin feature/improvement`)
5. Create Pull Request

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Support

- **Issues**: Report bugs via GitHub Issues
- **Discussions**: Feature requests and general questions
- **Wiki**: Additional documentation and tutorials

## Future Enhancements

- [ ] Custom wake word configuration
- [ ] Additional voice commands
- [ ] Multi-device support
- [ ] Gesture recognition integration
- [ ] Voice command macros
- [ ] Remote desktop control

## Performance

- **Battery Optimized**: Efficient background processing
- **Low Latency**: ~500ms command response time
- **Reliable Connection**: Automatic reconnection handling
- **Memory Efficient**: <50MB RAM usage

---

**Note**: This application requires both devices to be on the same trusted network and proper ADB debugging setup. Ensure you understand the security implications of enabling wireless debugging on your tablet.
