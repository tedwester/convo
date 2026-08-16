# Convo

A local-first Android chat client powered by [OpenRouter](https://openrouter.ai). Bring your own API key — conversations stay on your device.

## Features

- Chat with any model available on OpenRouter
- Streaming replies, reasoning, web search, voice, images, and video
- Projects, pinned chats, and variant responses
- Optional biometric app lock
- No account required — your key, your data

## Requirements

- Android 10+ (API 29)
- An [OpenRouter API key](https://openrouter.ai/keys)

## Install

Download the latest APK from [GitHub Releases](https://github.com/tedwester/convo/releases).

## Build from source

1. Clone the repo:
   ```bash
   git clone https://github.com/tedwester/convo.git
   cd convo
   ```

2. Open the project in Android Studio, or build from the command line:
   ```bash
   ./gradlew assembleDebug
   ```

3. For a signed release APK, create a release keystore and add `keystore.properties` at the project root (see `.gitignore` — this file is never committed):
   ```properties
   storeFile=signing/convo-release.jks
   storePassword=your-store-password
   keyAlias=convo
   keyPassword=your-key-password
   ```
   Then run:
   ```bash
   ./gradlew assembleRelease
   ```

## Privacy

- Chats are stored locally in the app's private storage
- Your OpenRouter API key is encrypted on-device and excluded from Android backup
- Network requests go to OpenRouter using your key — review their [privacy policy](https://openrouter.ai/privacy) for API usage

## Third-party assets

- [Inter](https://rsms.me/inter/) — SIL Open Font License 1.1
- [Lucide](https://lucide.dev) icons — ISC License

## License

Apache License 2.0 — see [LICENSE](LICENSE).
