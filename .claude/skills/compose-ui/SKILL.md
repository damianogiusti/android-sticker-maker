---
name: compose-ui
description: Presentation standards for Jetpack Compose and ViewModel in the Sticker Maker Android app. Lean MVVM with an immutable UiState, unidirectional data flow, Koin, and Arrow. Load automatically for any task involving a Compose screen, Composable, ViewModel, UiState, or screen navigation. Reference implementation is feature-editor EditorViewModel and EditorScreen.
---

> ⚡ compose-ui active

---

# SKILL: Compose Presentation (Sticker Maker)

Governs the **presentation layer**. General Kotlin craft, layering, and the Arrow error
model are in `kotlin-standards`; this skill is what those layers get *called into from*.

> **Scope.** All Compose UI in the repo: `app` (Home, About, navigation), `feature-editor`
> (cutting and text screens) and the shared pieces in `core-ui`. Base type is
> `androidx.lifecycle.ViewModel` everywhere. **Reference implementation**:
> `feature-editor` → `EditorViewModel` + `EditorScreen`; look there first when a structural
> question comes up.

---

## 🎯 The pattern: lean MVVM, unidirectional

- **State down, events up.** The ViewModel owns the state; the View renders it and calls
  back typed methods on intent.
- **Intents are public typed methods** on the ViewModel (`onCopyClicked`, `onToolSelected`,
  `onMagicClicked`). No sealed `Action` + single dispatch and no MVI reducer.
- **No `Effect` bus.** One-shot events use `SingleEventFlow` at need (see One-shot events).

---

## 🧱 ViewModel

- Extend **`androidx.lifecycle.ViewModel`**.
- Expose state as a read-only **`StateFlow`** (`_state.asStateFlow()`). For state derived
  from other flows, build it with `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), <initial>)`
  and `combine()`. Avoid an imperative load in `init`.
- **Prefer a single `UiState` per screen.** Multiple `StateFlow`s are acceptable **only when
  the slices are genuinely independent**, not as a way to scatter one screen's state.
- State that must survive process death goes through `SavedStateHandle` (the editor saves an
  `EditorSnapshot` and restores it).
- Launch in **`viewModelScope`**. No dispatcher injection here (that is the adapter's job;
  see `kotlin-standards`).

```kotlin
class EditorViewModel(
    private val copySticker: CopyStickerUseCase,
    // ... other use cases and ports
    private val savedState: SavedStateHandle,
) : ViewModel() {

    private val _state = MutableStateFlow<EditorUiState>(EditorUiState.WaitingForPicker)
    val state: StateFlow<EditorUiState> = _state.asStateFlow()
}
```

---

## 🎭 Modeling UiState: loading, error, empty are explicit

Never implicit (no "empty list means either loading or no data"). Two shapes; pick by the
state's nature:

**Sealed interface: states are mutually exclusive** (the preferred shape):

```kotlin
sealed interface EditorUiState {
    data object WaitingForPicker : EditorUiState
    data object LoadingImage : EditorUiState
    data class Failed(val reason: EditorFailure) : EditorUiState
    data class Cutting(val state: CuttingState) : EditorUiState
    data class Text(val state: TextState) : EditorUiState
}
```

**Data class with derived getters: an editor** where work in progress coexists with
content (`CuttingState` with `busy: CuttingBusy` and `val isBusy get() = ...`). Put computed
display logic in getters, not duplicated stored fields.

Rules:
- Mutually-exclusive screen states → **sealed interface**; content shown *while* working →
  **data class** + a status enum or sealed status (`CopyStatus.Idle / Copying / Failed`).
- `UiState` holds **render-ready** values; never pass a port's raw result straight to a
  `@Composable`.

---

## ⬆️ Intents

Public typed methods; keep them thin: delegate to use-cases, update state. Errors follow
`kotlin-standards` → Error Handling: consume the `Either` (`fold` / `onLeft`/`onRight`),
never `.getOrNull()`-swallow; if there is no UI feedback, log it and flag whether a UX error
state is owed.

```kotlin
fun onCopyClicked() {
    val current = currentText() ?: return
    updateText { it.copy(copy = CopyStatus.Copying) }
    viewModelScope.launch {
        copySticker(current.outlinedCutout, current.items).fold(
            ifLeft = { error ->
                logger.warn(TAG, "copy failed: $error")
                updateText { it.copy(copy = CopyStatus.Failed(failureOf(error))) }
            },
            ifRight = {
                updateText { it.copy(copy = CopyStatus.Idle) }
                _events.tryEmit(EditorEvent.Copied)
            },
        )
    }
}
```

---

## 🔔 One-shot events (navigation / confirmations)

- **Navigation is a callback, not a ViewModel concern.** The ViewModel never navigates; the
  screen composable receives `onClose`, `onNavigate`, … lambdas and the host wires them.
- For genuine fire-once events, use **`SingleEventFlow<T>()`** from `core-ui`
  (`com.damianogiusti.stickermaker.ui.coroutines`), a
  `MutableSharedFlow(replay = 0, extraBufferCapacity = 1, onBufferOverflow = DROP_OLDEST)`.
  Expose the read-only `asSharedFlow()`; collect in `LaunchedEffect`. **Do not use
  `Channel`.**

```kotlin
private val _events = SingleEventFlow<EditorEvent>()
val events: SharedFlow<EditorEvent> = _events.asSharedFlow()

// screen:
LaunchedEffect(viewModel) {
    viewModel.events.collect { event ->
        when (event) {
            EditorEvent.Copied -> /* show confirmation */
            EditorEvent.Cancelled -> onClose()
            EditorEvent.LaunchPicker -> Unit
        }
    }
}
```

---

## 🧩 Composable structure

- **Two-layer split**: a *Screen* composable obtains the ViewModel and collects state; a
  stateless *Content* composable takes `state` + lambdas. State is **hoisted**: Content
  never sees the ViewModel (`EditorScreen` → `CuttingContent` / `TextEditorContent`, with
  the lambdas grouped in `CuttingActions` / `TextEditorActions`).
- Obtain the ViewModel with **`koinViewModel()`**.
- Collect with **`collectAsStateWithLifecycle()`** (do not add new `collectAsState()`).
- **No business logic, no I/O, no use-case calls inside a `@Composable`.** A composable is a
  pure function of state + callbacks. Complex display shaping goes in the `UiState`
  (derived getters) or a mapper.

```kotlin
@Composable
fun EditorScreen(source: EditorSource, onClose: () -> Unit) {
    val viewModel: EditorViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    EditorContent(
        state = state,
        cuttingActions = remember(viewModel) { cuttingActionsOf(viewModel) },
        textActions = remember(viewModel) { textActionsOf(viewModel) },
        onClose = onClose,
    )
}

@Composable
private fun EditorContent(   // stateless, previewable, no ViewModel
    state: EditorUiState,
    cuttingActions: CuttingActions,
    textActions: TextEditorActions,
    onClose: () -> Unit,
) { when (state) { /* exhaustive render */ } }
```

---

## 💉 Dependency Injection (Koin)

- Declare bindings per feature module (`editorModule`). Bind ports to their adapters
  (`single<StickerClipboard> { FileStickerClipboard(...) }`), use cases as `factory`, the
  ViewModel with `viewModel { ... }` (or `viewModelOf(::FooViewModel)` when every argument
  comes from Koin).
- Dispatchers and product constants (e.g. `CLIPBOARD_FORMAT`) are passed explicitly in the
  module, so they are visible in one place.
- The `@Composable` pulls the ViewModel with `koinViewModel()`; never construct it by hand.
- Keep the domain free of Koin: constructor-inject; a use-case never calls `get()`.

```kotlin
val editorModule = module {
    single<StickerClipboard> { FileStickerClipboard(androidContext(), dir, Dispatchers.IO, System::currentTimeMillis) }
    factory { CopyStickerUseCase(exporter = get(), clipboard = get(), format = CLIPBOARD_FORMAT) }
    viewModel { EditorViewModel(/* ... */ copySticker = get(), savedState = get()) }
}
```

---

## 🧪 Testing

- Test the **ViewModel** in `src/test`: `runTest` + hand-written **fakes**
  (`Fakes.kt`) + **Turbine** (`app.cash.turbine`) for state and event sequences. No mockk,
  no Robolectric.
- Set the main dispatcher (`Dispatchers.setMain(UnconfinedTestDispatcher())`) in setup and
  assert the initial state as the first `awaitItem()`.
- **Every new screen ships tests.**

```kotlin
@Test
fun `a clipboard failure shows the storage error and retry copies again`() = runTest {
    clipboard.failure = ClipboardError.Failed("denied")
    val vm = viewModel().openTextWithOneItem()
    vm.onCopyClicked()
    assertEquals(CopyStatus.Failed(CopyFailure.STORAGE), vm.text().copy)

    clipboard.failure = null
    vm.events.test {
        vm.onCopyClicked()
        assertEquals(EditorEvent.Copied, awaitItem())
    }
    assertEquals(1, clipboard.copied.size)
}
```

---

## ❌ Anti-patterns

- Scattering one screen's state across several `StateFlow`s (multiple flows only for
  genuinely independent slices)
- Implicit loading/empty (inferring it from an empty list)
- Business logic, I/O, or use-case calls inside a `@Composable`
- ViewModel that navigates or holds a nav controller
- Passing the ViewModel (or a raw port result) into child composables
- `Channel` for one-shot events (use `SingleEventFlow`)
- `.getOrNull()`-swallowing an `Either` in an intent (handle both sides)
- New `collectAsState()` instead of `collectAsStateWithLifecycle()`

---

## 🔚 TL;DR

- Lean MVVM: prefer one immutable `UiState`, state down / events up, intents as typed methods
- `androidx.lifecycle.ViewModel` + read-only `StateFlow`; `SavedStateHandle` for what must survive process death
- Loading/error/empty explicit: sealed interface (exclusive) or data class + status (editors)
- Navigation = callbacks; one-shot events = `SingleEventFlow` (not `Channel`)
- Screen obtains VM via `koinViewModel()`, collects with `collectAsStateWithLifecycle()`; stateless Content takes state + lambdas; no logic in `@Composable`
- DI via Koin: ports bound to adapters, use cases as `factory`, `viewModel { }`
- `src/test` + `runTest` + fakes + Turbine
