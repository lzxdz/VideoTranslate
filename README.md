# VideoTranslate

Android app for translating English audio into Russian voice-over.

## Main features
- Adaptive UI themes: system, light, dark, Midnight, Ocean and Forest.
- Speech recognition choice:
  - **Whisper tiny.en**: local English speech recognition, can process Android playback capture when the source app permits it.
  - **Google/system SpeechRecognizer**: uses the phone microphone and installed speech recognition services. This mode does not capture another app's internal audio directly.
- Translation choice:
  - **Google ML Kit**: local English-to-Russian translation after downloading the model.
  - **AI API**: separate OpenAI-compatible `/chat/completions` configuration with endpoint, model and user API key.
- Russian Text-to-Speech with separate voice volume and speech-rate settings.
- Floating panel over other apps with start/stop, draggable position and a slider for translated voice volume.
- Whisper audio chunks adjustable to 2, 3 or 5 seconds.

## Setup
1. Install and open the app.
2. In **Settings → Speech recognition**, choose Whisper to capture internal playback audio where supported, or Google/system recognition for microphone input.
3. In **Settings → Text translation**, choose Google ML Kit or AI API.
4. AI API requires an HTTPS base URL, model and your own API key. The app sends recognized text to that provider. API usage may cost money and network latency varies.
5. To enable the floating panel, grant **Display over other apps** and enable the panel from Settings.

## Privacy and limitations
- The AI API key is stored in app SharedPreferences and is not encrypted at rest. Do not commit API keys.
- Google ML Kit translation runs locally after the model is downloaded. Whisper speech recognition runs locally after its model is downloaded.
- Google/system speech recognition uses the microphone; whether it works offline depends on installed speech services and language packs.
- Android playback capture works only on Android 10+ and only when the source app permits capture.
- Android does not provide a public API for changing another app's volume independently. The floating slider scales VideoTranslate's synthesized voice, not the source video's volume.
- Whisper's 2-second chunks may cut phrases; larger chunks can improve context but add delay. Actual latency and recognition quality depend on device and audio quality.
- A normal app cannot silently start MediaProjection capture. Android may require user confirmation whenever capture is started.

## Build
Open in Android Studio or run `gradle :app:assembleDebug` with JDK 17 and Android SDK 35.
