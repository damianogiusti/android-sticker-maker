# CLAUDE.md — Sticker Maker (clean rebuild)

Guidance for AI agents in this repository. Android-only app, **Kotlin + Jetpack Compose**,
a quick sticker maker with no ads, tracking or network: share or pick a photo, cut it out, add text
and an outline, **Copy**, paste it in WhatsApp. (Scope since 2026-11-11: see SPEC §0. Packs, the
WhatsApp provider, backup and the keyboard are gone.)

- Product + technical spec: `../docs/SPEC.md` (binding). Reverse-engineering evidence:
  `../docs/re-data-export.md`, `../docs/re-image-pipeline.md`.
- Package root: `com.damianogiusti.stickermaker.*`, applicationId `com.damianogiusti.stickermaker`.

## Stack

Koin (DI), Arrow `Either` (errors), kotlinx.serialization (navigation routes, editor snapshot),
AndroidX `ViewModel` + Compose Navigation, LiteRT/TFLite (on-device segmentation only).
Tests: `kotlin.test`, `kotlinx-coroutines-test`, Turbine, hand-written fakes. **No** Hilt,
Room, SQLDelight, mockk, Robolectric, Firebase, Play-services, ads or analytics SDKs, and **no INTERNET
permission**.

## Modules

```
app/             entry point: MainActivity (share target), NavHost, Koin start, Home, About
core-ui/         theme, SingleEventFlow, shared composables (universal UI base)
domain/          pure Kotlin/JVM: clipboard port (StickerClipboard), StickerImageFormat, limits
imaging/         bitmap pipeline: cutout, segmentation, outline, text, export (WebP/PNG)
feature-editor/  cutting + text/outline screens, Copy use case, clipboard adapter and FileProvider
```

Dependency direction: app → feature-editor → domain + core-ui; imaging → domain.

Flow: Home ("Choose photo", Photo Picker) or an `ACTION_SEND` `image/*` share → editor → Copy.
Copy writes `cacheDir/clipboard/<timestamp>.webp`, sets a content-URI clip (FileProvider
`${applicationId}.clipboard.fileprovider`) and deletes the older files. The format is the
`CLIPBOARD_FORMAT` constant in `editorModule`; switch it to `StickerImageFormat.PNG` if an app
pastes WebP badly.

## Architecture

Clean Architecture, dependencies pointing inward:

```
Presentation (Composables, ViewModels)   → compose-ui skill
Domain       (UseCases, entities)         → kotlin-standards skill
Data         (Repositories, data access)  → kotlin-standards skill
```

## Skills

| Skill | Trigger |
|---|---|
| `kotlin-standards` | Any Kotlin task |
| `compose-ui` | Any Compose screen, `@Composable`, `ViewModel`, `UiState`, navigation |

Rules:
- Load the matching skills before writing code; they are **binding**.
- `SingleEventFlow` lives in `core-ui` (`com.damianogiusti.stickermaker.ui.coroutines`); unit
  tests live in `src/test` of each module.
- Android framework entry points (Activity intents, the clipboard) are thin Adapters; the rules
  live in plain Kotlin (`SharedImageIntent`, `ClipboardFiles`, `CopyStickerUseCase`) and are tested.

## Build and test

```bash
./gradlew assembleDebug            # build
./gradlew testDebugUnitTest :domain:test   # unit tests
```

Do not install on physical devices unless the user asks; if asked, always `adb -s <serial>`.

## Naming quick reference

- `…ViewModel`, ports named by role (`StickerClipboard`, impl by origin: `FileStickerClipboard`),
  `…UseCase` (verb), `…UiState`, Composables `FooScreen` / `FooContent`.
