---
name: kotlin-standards
description: Apply Kotlin production code standards in the Sticker Maker Android app (domain, imaging, feature and app modules). Anchored on Clean Code and Clean Architecture (Robert C. Martin). Load automatically when the task involves Kotlin, coroutines, Flow, Arrow, SOLID, naming, error handling, or class design. Governs code craft (functions, classes, naming, layering, errors); presentation belongs to compose-ui.
---

> ⚡ kotlin-standards active

---

# SKILL: Kotlin Production Standards (Sticker Maker)

## 🎯 Goal

Produce Kotlin that a reviewer can trust on sight: small honest functions, names that
promise a role, errors that are never swallowed, dependencies that point inward. The
reference books are **Clean Code** and **Clean Architecture** (Robert C. Martin); this
skill translates them into Kotlin idioms for this codebase. Presentation (Compose +
ViewModel) is governed by the sibling `compose-ui` skill.

**Binding on agents.** The repo has no ktlint/detekt/spotless gate (only
`kotlin.code.style=official`), so these rules are enforced by review and discipline, not
tooling. When an agent writes or changes code, it **must** follow them.

---

## 🧠 Core Principles

1. **No `!!`.** Handle absence with `?.`, `?:`, `requireNotNull(x) { "why" }`, or an early
   return.
2. **`val` by default.** `var` only when mutation is the point. Expose **immutable**
   collection types (`List`, `Map`, `Set`); never leak a `MutableList`.
3. **Composition over inheritance.** No `open` in production code. Reuse via **class
   delegation** (`class X(...) : Y by y`). `sealed` hierarchies for *modeling* closed types
   (states, errors) are encouraged; that is not inheritance-for-reuse.
4. **No `lateinit`** except where a framework forces it. Prefer constructor injection.
5. **Domain stays pure.** The `domain` module is plain Kotlin/JVM: no Android, Compose or
   TFLite types. If a domain test needs the Android runtime, a framework type leaked past
   the port; fix the seam, not the test.
6. **Explicit visibility.** Default to `private`/`internal`; make `public` a decision.

---

## 🧱 Layering (Clean Architecture)

Dependencies point inward, toward the domain:

| Layer | Does | Depends on | Must not |
|---|---|---|---|
| **UseCase** | one action (a verb), applies rules, returns a domain result | ports (interfaces) / other use-cases | touch UI, frameworks, raw I/O |
| **Port** | interface for something the app needs from the outside (`StickerClipboard`, `SubjectSegmenter`, `StickerExporter`) | domain types | expose framework types |
| **Adapter** | implements a port against one origin (clipboard + FileProvider, TFLite, Bitmap); maps framework errors to typed errors; is the **concurrency boundary** (`withContext(injectedDispatcher)`) | the framework/SDK | hold product rules |

Real examples to model after: `CopyStickerUseCase` (export, then copy),
`StickerClipboard` (port in `domain/`), `FileStickerClipboard` (adapter in
`feature-editor/`), `DeepLabSubjectSegmenter` (adapter in `imaging/`).

- A **use-case can be thin** (one wrapper over one port call). That is fine: it exposes only
  the one action a caller needs.
- Android entry points (Activity intents, the clipboard) are thin adapters. Put the rules in
  plain Kotlin that is easy to test (`SharedImageIntent`, `ClipboardFiles`).

---

## 🏷️ Naming (Clean Code)

- **Name by role**: `…UseCase` (verb), ports by what they do (`StickerClipboard`,
  `ImageCutter`), `…Mapper`, `…Factory`, plain-noun entities. Implementations are named by
  their origin: `FileStickerClipboard`, `BitmapImageCutter`, `DeepLabSubjectSegmenter`.
- **Forbidden for new code**: `Manager`, `Handler`, `Agent`, `Helper`, `Util`, `Service`.
  They name nothing. Allowed non-layer names: `Mapper`, `Factory`, `Adapter`.
- Functions are **verbs** (`exportSticker`, `onCopyClicked`). Booleans read as questions:
  `isX`, `hasX`, `canX`, `shouldX`.
- No type-encoding (`strName`, `lstItems`), no throwaway abbreviations.
- A name that needs a comment to explain it is the wrong name.

---

## 🔧 Functions (Clean Code)

- **Do one thing**, at one level of abstraction. Extract until the body reads like prose.
- **Command/Query Separation**: a function either changes state *or* returns a value,
  not both.
- **Few arguments** (aim 3 or fewer). More than that: introduce a parameter `data class`
  (e.g. `StickerComposition`).
- **No boolean flag arguments**: `render(isCompact: Boolean)` is two functions. Split, or
  take an enum.
- **No hidden side effects.** A function's effects must be predictable from its name.
- **Early return** over deep nesting. Guard clauses first.

---

## 🧩 Classes and SOLID

- **Single Responsibility.** If you cannot name a class cleanly, its responsibility is
  split; break it up. No god-objects.
- **Dependency Inversion.** Depend on interfaces (ports, use-cases), inject via constructor.
  A use-case never calls Koin `get()` or touches a `Context`.
- **Composition over inheritance** (Core Principle 3): share behavior with `by` delegation.
- **Small, cohesive.** Many small types beat one that knows everything.

---

## 🚨 Error Handling (Arrow)

Failures are modeled with **Arrow `Either`**, not exceptions and not a generic `Result`.
Framework/file/model errors are translated to typed errors **at the adapter boundary**.

- Ports return `Either<FeatureError, T>`, where `FeatureError` is a **sealed interface**
  (e.g. `ClipboardError`, `ExportError`, `CopyStickerError`).
- Compose steps inside `either { ... }` with `.bind()`; convert thrown work with Arrow
  `catch({ ... }) { raise(...) }`. A raw exception must never escape an adapter.
- **Every error is handled.** Consume an `Either` with `fold`, `onLeft`/`onRight`, or an
  exhaustive branch; never let a failure vanish.
- **Never swallow.** `.getOrNull()` that silently drops the `Left`, an empty `catch {}`, a
  `// TODO: handle error`, or a fire-and-forget call are all bugs.
- **No UI feedback for this error yet?** At minimum log it, and leave the question open:
  *does this need a UI error state?* User-perceivable loading/error must reach the `UiState`
  (see `compose-ui`).

```kotlin
// Use case: each step's error is mapped to the use case's own sealed error.
suspend operator fun invoke(...): Either<CopyStickerError, Unit> = either {
    val bytes = exporter.exportSticker(composition, format)
        .mapLeft(CopyStickerError::Export).bind()
    clipboard.copy(EncodedSticker(bytes, format)).mapLeft(CopyStickerError::Clipboard).bind()
}

// ViewModel: both outcomes handled.
copySticker(cutout, items).fold(
    ifLeft = { error ->
        logger.warn(TAG, "copy failed: $error")
        updateText { it.copy(copy = CopyStatus.Failed(failureOf(error))) }
    },
    ifRight = { _events.tryEmit(EditorEvent.Copied) },
)
```

---

## ⚡ Concurrency

- **Structured concurrency only.** No `GlobalScope`. Presentation launches in
  `viewModelScope`.
- **The adapter is the dispatcher boundary**: wrap work in `withContext(injectedDispatcher)`
  (and `flowOn(injectedDispatcher)` for reactive reads), with the dispatcher **injected** so
  it is swappable in tests (see `editorModule`: `Dispatchers.Default` for bitmaps,
  `Dispatchers.IO` for files). Do not inject dispatchers into ViewModels or use-cases.
- Prefer `Flow` for observed data, `suspend` for one-shot actions.
- **`StateFlow` is exposed only by ViewModels.** Lower layers return cold `Flow` or
  `suspend` results.
- Never expose a `MutableStateFlow`/`MutableSharedFlow` publicly; expose the read-only
  `asStateFlow()`/`asSharedFlow()` view.

---

## 🧪 Testing

- Unit tests live in `src/test` of each module (the `domain` module is plain JVM).
  `kotlin.test` + `kotlinx.coroutines.test.runTest`. No Robolectric, no real I/O.
- **Fakes over mocking.** Hand-written fakes (`FakeStickerClipboard`, `FakeStickerExporter`
  in `feature-editor/src/test/.../Fakes.kt`). mockk is not used here.
- Use **Turbine** (`app.cash.turbine`) for `Flow`/`StateFlow` emission assertions.
- Name tests for behavior, backtick style:
  ``@Test fun `an export error is returned and nothing is copied`()``.
- Cover the rules: use-cases, plain-Kotlin adapters' logic, ViewModels.

---

## ❌ Anti-patterns

- `!!` in production; `open` classes / inheritance-for-reuse instead of `by` delegation
- Swallowed errors: `.getOrNull()` dropping a `Left`, empty `catch {}`, `// TODO` error
  branches, fire-and-forget calls
- An adapter throwing instead of returning `Either`
- Boolean flag arguments; functions doing command **and** query
- `GlobalScope`, or dispatcher injection into ViewModels/use-cases
- Exposing `StateFlow`/`MutableSharedFlow` from anything other than a ViewModel
- `Manager` / `Handler` / `Agent` / `Helper` / `Util` / `Service` names in new code
- Domain code importing Android / Compose / TFLite types

---

## 🔚 TL;DR

- No `!!`, `val` first, immutable collections, composition (`by`) not `open`
- Layers point inward: UseCase → Port (interface) ← Adapter (dispatcher boundary)
- Name by role; forbidden `Manager/Handler/Agent/Helper/Util/Service`
- Functions do one thing, 3 args max (else a param `data class`), no flag args, CQS
- Errors: Arrow `Either` + sealed error interfaces mapped at the adapter; **every error handled**; no feedback? log it + ask if UX is owed
- `viewModelScope` in presentation; dispatcher injected at the adapter only; `StateFlow` only from ViewModels
- `src/test` + `kotlin.test` + `runTest` + fakes + Turbine
