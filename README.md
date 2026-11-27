# Pocket Agent

An Android AI agent app: a cloud AI model (via API) with real tools on the phone.
The model thinks; the app acts — read device state, work with files, run code, and report back.

## Architecture

- **Kotlin** app (Jetpack Compose UI) — native access to Android APIs, permissions and foreground services
- **Agent loop** — send the conversation + tool definitions to the AI API, execute the tool calls the model returns, feed results back, repeat until the task is done
- **Cloud AI API** — any OpenAI-compatible endpoint; base URL + key are configured in-app and are **never** committed to this repo
- **Optional Rust core** — only if a heavy on-device component is added later (e.g. local model inference)

## Planned tools (v1)

- Battery & device info (charge %, charging state, temperature, model, Android version) — no permission needed
- File read/write inside the app workspace (storage access only with the user's grant)
- Web search & page fetch
- Script execution (embedded interpreter) for small coding tasks
- More tools behind their Android runtime permissions, each granted explicitly by the user

## Ground rules

- The agent can only do what Android lets the app do — permissions are the ceiling, always
- No API keys, tokens or personal data in this repository
- Long tasks run in a foreground service so Android does not kill them

## Status

Repository created 2026-11-27. Next step: Android project scaffold (Gradle + Compose + agent loop skeleton).
