# VideoTranslate

Android app for translating English audio from other apps into Russian speech.

## Features
- Adaptive modern UI with system/light/dark themes and Midnight, Ocean and Forest palettes.
- Audio capture on Android 10+ where the source app permits playback capture; microphone fallback otherwise.
- Local English speech recognition using Whisper tiny.en.
- Translation provider selection: Google ML Kit (on-device after model download) or an OpenAI-compatible chat-completions API.
- Russian system TTS with configurable speech rate.
- Optional floating overlay control for opening translation and stopping it while using other apps.
- Adjustable 2, 3 or 5 second audio chunks.

## AI translation provider
Open **Settings → Translation → AI compatible API**, set an HTTPS API base URL, model name and your own API key. The app calls `/chat/completions` and sends recognized English text to that provider. API usage may cost money and network latency varies. The key is stored in app SharedPreferences, not encrypted storage; only use this on a device you trust. Do not commit API keys.

Google ML Kit mode translates locally after the English/Russian model is downloaded. Whisper speech recognition is local after its model is downloaded.

## Floating control
Grant “Display over other apps” permission, then enable the floating button in Settings. Tap it to open the app and start capture (Android may require confirmation); tap it while translation is active to stop. Android security rules do not allow an app to silently reuse a MediaProjection consent grant indefinitely.

## Limitations
- Android does not let a normal third-party app reliably mute another app's original audio. Lower the source app's media volume if you want to hear only the Russian dub.
- Playback capture works only when the source app allows it and its audio usage is eligible.
- Tiny Whisper and short chunks are a latency/accuracy trade-off. Smaller chunks can cut words and reduce recognition quality; performance depends on the phone.
- Cloud AI translation may improve phrasing but can be slower than on-device ML Kit and requires network/API access.
- Russian voice availability depends on the device's installed text-to-speech engine and language data.

## Build
Open with Android Studio or run `gradle :app:assembleDebug` with JDK 17 and Android SDK 35 installed.
