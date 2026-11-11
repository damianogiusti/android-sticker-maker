# CLAUDE.md — Sticker Maker (clean rebuild)

Guidance for AI agents in this repository. Android-only app, **Kotlin + Jetpack Compose**,
that rebuilds the real features of a sticker-maker app with no ads, tracking or network.

- Product + technical spec: `../docs/SPEC.md` (binding). Reverse-engineering evidence:
  `../docs/re-data-export.md`, `../docs/re-image-pipeline.md`.
- Package root: `com.damianogiusti.stickermaker.*`, applicationId `com.damianogiusti.stickermaker`.

## Stack

Koin (DI), Arrow `Either` (errors), SQLDelight (DB), kotlinx.serialization, AndroidX
`ViewModel` + Compose Navigation, Coil, LiteRT/TFLite (on-device segmentation only).
Tests: `kotlin.test`, `kotlinx-coroutines-test`, Turbine, hand-written fakes. **No** Hilt,
Room, mockk, Robolectric, Firebase, Play-services, ads or analytics SDKs, and **no INTERNET
permission**.

## Modules

```
app/            entry point, NavHost, Koin root, ContentProvider, FileProvider, IME
core-ui/        theme, SingleEventFlow, shared composables (universal UI base)
domain/         pure Kotlin/JVM: models, repository interfaces, use cases, errors, validator
data/           SQLDelight, file storage, repository impls, zip codecs
imaging/        bitmap pipeline: cutout, segmentation, outline, text, export
feature-packs/  feature-editor/  feature-gallery/
```

Dependency direction: app → feature-* → domain + core-ui; data / imaging → domain.

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
- Android framework entry points (ContentProvider, InputMethodService) are thin Adapters
  that delegate to use cases; no business logic in them.

## Build and test

```bash
./gradlew assembleDebug            # build
./gradlew testDebugUnitTest :domain:test   # unit tests
```

Do not install on physical devices unless the user asks; if asked, always `adb -s <serial>`.

## Naming quick reference

- `…ViewModel`, `…Repository` (impl by origin, e.g. `Database…Repository`, `File…Repository`),
  `…UseCase` (verb), `…UiState`, Composables `FooScreen` / `FooContent`.
- SQLDelight schemas in `data/src/main/sqldelight/`.
