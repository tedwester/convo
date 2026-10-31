<p align="center">
  <img src="assets/convo.png" width="180" alt="Convo logo">
</p>

<h1 align="center">Convo</h1>

<p align="center">
  <a href="https://github.com/tedwester/convo/releases"><img src="https://img.shields.io/github/v/release/tedwester/convo?label=version" alt="Latest release"></a>
  <a href="https://github.com/tedwester/convo/releases"><img src="https://img.shields.io/github/downloads/tedwester/convo/total?label=downloads" alt="Total downloads"></a>
  <a href="LICENSE"><img src="https://img.shields.io/github/license/tedwester/convo" alt="License"></a>
  <img src="https://img.shields.io/github/last-commit/tedwester/convo" alt="Last commit">
</p>

<p align="center">
  Private AI chat for Android, powered by OpenRouter.<br>
  Bring your own API key — conversations stay on your device.
</p>

<p align="center">
  <a href="https://github.com/tedwester/convo/releases"><img src="https://img.shields.io/badge/Get_it_on-GitHub-181717?style=for-the-badge&logo=github&logoColor=white" alt="Get it on GitHub"></a>
  <a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/tedwester/convo"><img src="https://img.shields.io/badge/Get_it_on-Obtainium-6d4bd8?style=for-the-badge&logo=android&logoColor=white" alt="Get it on Obtainium"></a>
</p>

## Screenshots

<!-- Drop phone screenshots in assets/screenshots/ and reference them here, e.g.
<img src="assets/screenshots/chat.png" width="270" alt="Chat"> -->

## Features

- Chat with any model available on OpenRouter
- Streaming replies, reasoning, web search, voice, images, and video
- Voice call mode with live transcription, including on-device transcription
- File attachments the model can actually read, image copy, media gallery
- Variant responses, per-chat model filters, projects, pinned chats
- Optional biometric app lock
- No account required — your key, your data

## Install

- **GitHub:** grab the latest APK from [Releases](https://github.com/tedwester/convo/releases).
- **Obtainium:** tap the button above, or add the source manually — GitHub source, repo URL `https://github.com/tedwester/convo`, APK filter `Convo-.*\.apk`.
- **F-Droid:** in progress — check back soon.

Requires Android 10+ (API 29) and an [OpenRouter API key](https://openrouter.ai/keys).

## Build from source

Requires a JDK 17+ and the Android SDK. Open the project in Android Studio, or build with the command line:

```bash
git clone https://github.com/tedwester/convo.git
cd convo
./gradlew assembleDebug
```

See [CONTRIBUTING](CONTRIBUTING.md) for workflow notes (branching, betas, releases).

## Privacy

- Chats are stored locally in the app's private storage
- Your OpenRouter API key is encrypted on-device and excluded from Android backup
- Network requests go to OpenRouter using your key — review their [privacy policy](https://openrouter.ai/privacy) for API usage

## Third-party assets

- [Inter](https://rsms.me/inter/) — SIL Open Font License 1.1
- [Lucide](https://lucide.dev) icons — ISC License

## License

Apache License 2.0 — see [LICENSE](LICENSE).
