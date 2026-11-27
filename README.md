# Pocket Agent — ایجنت جیبی

An Android AI agent app: a cloud AI model (via API) with real tools on the phone.
The model thinks; the app acts — search the web, read pages, generate images, check the battery, work with files, and report back.

## Features (v4.0.0)

- **Writes, runs and debugs real Python on the phone** — ask for code and the agent writes it to a file with the right extension, executes it on-device (Chaquopy, Python 3.11), reads the traceback, fixes the file and re-runs until it works — then **delivers the file in the chat** with open/share actions
- **Light, bright UI** — white surfaces, soft shadows, one cyan→azure accent, animated ambient orbs, rotating-ring avatar, gradient headline, typing indicator, icon suggestion chips, message timestamps, and proper **code-block rendering** (dark card, language label, copy button)
- **File & image processing on-device** — attach an image or a document right from the chat:
  - Images: OCR (ML Kit, Latin script), dominant colors (Palette), dimensions — the results are fed to the agent, which reacts on its own
  - Files: PDF text extraction (PDFBox), source-code and text files (`.py`, `.html`, `.kt`, … — anything that looks like text), CSV structure — attached files land in the workspace, where the agent can re-read them anytime with `process_file`
- **An agent with an identity** — it has a name (default «جیبی», changeable in Settings), a warm personal-assistant personality, and a **long-term memory**: durable facts about the user are saved via tools, injected into every session, and viewable/deletable in Settings
- **Agent loop** — OpenAI-compatible tool calling: the model plans, calls tools, reads results and continues until the task is done (up to 8 rounds)
- **Tools**
  - `run_python` / `run_python_file` — execute Python on-device and debug from real output
  - `web_search` / `web_fetch` — live web search and page reading via Keenable (keyless public tier)
  - `generate_image` — image generation via Pollinations (keyless), shown right in the chat
  - `get_battery_info` — charge %, charging status, temperature, voltage, health
  - `get_device_info` — model, Android version, RAM, storage
  - `get_current_time`, `write_file` / `read_file` / `list_files` / `process_file` — a private on-device workspace
  - `save_memory` / `read_memory` / `delete_memory` — the long-term memory
- **Provider config is internal** (not shown in the app UI): any OpenAI-compatible endpoint, set in `AiConfig` — the default provider is free and keyless
- Builds are arm64-v8a only (the Python runtime ships per-ABI)

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
