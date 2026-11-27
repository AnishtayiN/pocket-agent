# Pocket Agent — ایجنت جیبی

An Android AI agent app: a cloud AI model (via API) with real tools on the phone.
The model thinks; the app acts — search the web, read pages, generate images, check the battery, work with files, and report back.

## Features (v3.0.0)

- **File & image processing on-device** — attach an image or a document right from the chat:
  - Images: OCR (ML Kit, Latin script), dominant colors (Palette), dimensions — the results are fed to the agent, which reacts on its own
  - Files: PDF text extraction (PDFBox), plain-text/code files, CSV structure — attached files land in the workspace, where the agent can re-read them anytime with `process_file`
- **An agent with an identity** — it has a name (default «جیبی», changeable in Settings), a warm personal-assistant personality, and a **long-term memory**: durable facts about the user are saved via tools, injected into every session, and viewable/deletable in Settings
- **Agent loop** — OpenAI-compatible tool calling: the model plans, calls tools, reads results and continues until the task is done (up to 8 rounds)
- **Tools**
  - `web_search` / `web_fetch` — live web search and page reading via Keenable (keyless public tier)
  - `generate_image` — image generation via Pollinations (keyless), shown right in the chat
  - `get_battery_info` — charge %, charging status, temperature, voltage, health
  - `get_device_info` — model, Android version, RAM, storage
  - `get_current_time`, `write_file` / `read_file` / `list_files` / `process_file` — a private on-device workspace
  - `save_memory` / `read_memory` / `delete_memory` — the long-term memory
- **Provider config is internal** (not shown in the app UI): any OpenAI-compatible endpoint, set in `AiConfig` — the default provider is free and keyless
- **Persian RTL chat UI** (Material 3): animated ambient background, rotating-ring avatar, gradient headline, typing indicator, suggestion chips with icons, message timestamps, spring-animated messages

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
