# Pocket Agent — ایجنت جیبی

An Android AI agent app: a cloud AI model (via API) with real tools on the phone.
The model thinks; the app acts — search the web, read pages, generate images, check the battery, work with files, and report back.

## Features (v2.0.0)

- **An agent with an identity** — it has a name (default «جیبی», changeable in Settings), a warm personal-assistant personality, and a **long-term memory**: durable facts about the user are saved via tools, injected into every session, and viewable/deletable in Settings
- **Agent loop** — OpenAI-compatible tool calling: the model plans, calls tools, reads results and continues until the task is done (up to 8 rounds)
- **Tools**
  - `web_search` / `web_fetch` — live web search and page reading via Keenable (keyless public tier)
  - `generate_image` — image generation via Pollinations (keyless), shown right in the chat
  - `get_battery_info` — charge %, charging status, temperature, voltage, health
  - `get_device_info` — model, Android version, RAM, storage
  - `get_current_time`, `write_file` / `read_file` / `list_files` — a private on-device workspace
  - `save_memory` / `read_memory` / `delete_memory` — the long-term memory
- **Provider config is internal** (not shown in the app UI): any OpenAI-compatible endpoint, set in `AiConfig` — the default provider is free and keyless
- **Persian RTL chat UI** (Material 3, dark, gradient avatar, typing indicator, suggestion chips), chat history persisted on the device

## Architecture

- **Kotlin** + Jetpack Compose — native Android API access, no bridge tax
- `AiClient` — minimal Chat Completions client with retry/backoff and ~1 request/second pacing
- `ToolExecutor` — the tool implementations; `AgentViewModel` — the agent loop

## Honest limits

- The agent can only do what Android lets the app do — permissions are the ceiling
- The default free provider may refuse some IPs/prompts; if it does, point Settings to another OpenAI-compatible provider
- Release APKs are signed with a CI-generated keystore: uninstall before reinstalling a newer build

## Build

Open in Android Studio, or push a `v*` tag — GitHub Actions builds the release APK and attaches it to the Release.
