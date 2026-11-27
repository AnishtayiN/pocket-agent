# Pocket Agent — ایجنت جیبی

An Android AI agent app: a cloud AI model (via API) with real tools on the phone.
The model thinks; the app acts — research the web, run Python, control the phone, speak and listen, and report back.

## Features (v8.0.0)

- **Thinking toggle** — «فکر عمیق» (deep thinking: native reasoning streamed live, research nudge, answer verification) vs «پاسخ سریع» (fast mode: the plain API answer with minimal reasoning effort, no extra passes). One-tap chip above the composer + a switch in Settings
- **A real agent loop** — the model plans, calls tools, reads results and continues until the task is done (up to 16 rounds). In thinking mode, factual answers are verified by a strict second pass and sent back for completion if they are incomplete
- **Live task plans** — for multi-step work the agent shows a plan checklist in the chat and ticks steps off as it goes (`update_plan`)
- **Two-way voice**
  - Mic button: dictate your message with live on-device transcription (Persian)
  - Answers can be read aloud (TTS): per-message speaker button, an auto-speak switch in Settings, and a `speak` tool the agent can use itself
- **Aurora UI** — a deep-space dark theme and a cool porcelain light theme (light/dark/system + quick toggle in the header), drifting ambient aurora orbs, glassy header, rotating-ring avatar, gradient headline, live streaming with reasoning preview, plan cards, icon suggestion chips, scroll-to-bottom button, haptics, and proper markdown + code-block rendering
- **Writes, runs and debugs real Python on the phone** — Chaquopy (Python 3): the agent writes code to a file, executes it on-device, reads the traceback, fixes and re-runs until it works — then delivers the file in the chat with open/share actions
- **Phone control tools** — alarm & countdown timer, calendar events, flashlight, vibration, clipboard read/write, network status, open links, share text, dialer, pre-filled SMS & email drafts, maps search/navigation
- **Live data, keyless** — weather for any city (wttr.in), currency rates & conversion (open.er-api.com), Wikipedia summaries (fa/en), QR code generation (goqr.me)
- **Chat export** — share the whole conversation as a text file from Settings
- **File & image processing on-device** — attach an image (OCR via ML Kit, dominant colors, dimensions) or a document (PDF text via PDFBox, source-code/text files, CSV structure); attachments land in the workspace where the agent can re-read them anytime
- **An agent with an identity** — a name (default «جیبی», changeable in Settings), a warm personality, and a long-term memory: durable facts about the user are saved via tools, injected into every session, and viewable/deletable in Settings
- **Research tools** — `web_search` / `web_fetch` / `web_research` (searches wide AND reads the top pages) via Keenable's keyless public tier; answers cite real sources as tappable chips
- **Image generation** — `generate_image` via Pollinations (keyless), shown right in the chat
- **Device info** — battery (charge, temperature, voltage, health), model, Android version, RAM, storage, current time
- **Provider config is internal** (not shown in the app UI): any OpenAI-compatible endpoint, set in `AiConfig` — the default provider is free and keyless, with an automatic fallback
- Builds are arm64-v8a only (the Python runtime ships per-ABI)

## Architecture

- **Kotlin** + Jetpack Compose — native Android API access, no bridge tax
- `AiClient` — OpenAI-compatible Chat Completions client with SSE streaming (content + reasoning), retry/backoff, pacing and provider fallback
- `ToolExecutor` — 37 tool implementations; `AgentViewModel` — the agent loop, thinking/fast modes, verification pass and live plan state
- `Voice` — on-device TTS + speech recognition; `Processing` — OCR/PDF/text extraction; `MemoryStore` — long-term memory

## Honest limits

- The agent can only do what Android lets the app do — permissions are the ceiling
- Voice features depend on the device having a TTS engine / recognizer (Google services); otherwise they degrade gracefully
- The default free provider may refuse some IPs/prompts; if it does, point `AiConfig` to another OpenAI-compatible provider
- Release APKs are signed with a CI-generated keystore: uninstall before reinstalling a newer build

## Build

Open in Android Studio, or push a `v*` tag — GitHub Actions builds the release APK and attaches it to the Release.