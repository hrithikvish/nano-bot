# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

NanoBot is a single-module Android app (`:app`, package `com.hrithikvish.nanobot`). It is a chat
agent that runs on the device. Gemini Nano (ML Kit GenAI Prompt API) answers user questions through
the AppFunctions of other installed apps. NanoBot hard-codes no target app. It discovers the
functions at run time.

Stack: Kotlin, Compose, Hilt (KSP), `androidx.appfunctions` 1.0.0-alpha12, ML Kit `genai-prompt`
1.0.0-beta4, `genai-schema` and `genai-schema-compiler` 1.0.0-alpha1. minSdk 36, target and compile
SDK 37. Versions are in `gradle/libs.versions.toml`.

## Commands

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:testDebugUnitTest --tests "*GeminiToolConverterTest*"
./run_privileged.sh -b            # build, install, and start with the shell identity
./run_privileged.sh -s SERIAL     # select a device
adb logcat -s NanoBot             # all Gemini Nano prompts, responses, timings, and errors
adb logcat -G 16M                 # larger log buffer, so long prompts stay readable
```

Unit tests run on the JVM. Android's `org.json` is a stub there, so the real `org.json` is a
`testImplementation` dependency. Gemini Nano and AppFunctions work only on a real device.

## Privileged execution (important)

Only a caller with `android.permission.EXECUTE_APP_FUNCTIONS` can execute AppFunctions. Normal apps
cannot get this permission. A launch from the launcher finds the tools, but every execution fails.

- `run_privileged.sh` installs the APK, adds NanoBot to the caller allowlist on API 37 or higher,
  stops other instrumentations, and runs `am instrument … /.ShellIdentityInstrumentation`.
- `ShellIdentityInstrumentation` calls `adoptShellPermissionIdentity` and starts `MainActivity`. It
  blocks on a latch, because the permission stays only while the instrumentation runs. It passes
  errors to the activity with `MainActivity.EXTRA_INSTRUMENTATION_ERROR`, and the status banner
  shows them.
- The shell gives its permissions to only one instrumentation at a time. Another active
  instrumentation, for example the AppFunctions sample agent, causes a `SecurityException`.
- Installing a new APK kills the privileged session. The user must run the script again.

## Architecture: one agent turn

`ChatViewModel` → `AgentOrchestrator` → `GeminiNanoClient` and `AppFunctionManager`.

1. `GetAppFunctionsUseCase` returns all functions (`searchAppFunctions`) and searches again on
   `observeAppFunctions`. `GeminiToolConverter` turns each `AppFunctionMetadata` into an
   `AppFunctionTool`: a short name (the part of the ID after `#`, with `_2` added for duplicates), a
   one-line `summary`, and a JSON `schema` with resolved reference types.
2. `AgentOrchestrator.decide` makes two Gemini Nano calls:
    - `chooseTool` gets only the summaries and returns a tool name.
    - `fillArguments` gets only the chosen tool's schema and returns `argumentsJson`.

   The two steps are necessary. All schemas in one prompt overflowed the context
   ("Input text length exceeds the limit" from `countTokens`), because some system tools have
   descriptions of several thousand characters.
3. The orchestrator validates the result. `wrapSingleObjectParameter` puts the fields inside
   `{"params": {...}}` when the model omits the wrapper. `ConvertInputToAppFunctionDataUseCase`
   builds typed `AppFunctionData` from the metadata, and it throws `IllegalArgumentException` for
   bad input. Every failure becomes `AgentStep.Reply`, not an exception.
4. The UI shows a tool card. Nothing executes until the user taps Run. The rule is one tool call
   per user message, then a summary.
5. `execute` calls `executeAppFunction`. `ConvertAppFunctionDataToJsonUseCase` turns the return
   value into JSON. `explain` makes the third Gemini Nano call for the final answer.

The model sees no chat history. Each message stands alone.

## Gemini Nano conventions

- All decisions use structured output: `AgentDecision` is a `@Generable` class (KSP generates its
  schema), with `generateTypedContentRequest`. `action` is limited by `enumValues` to `CALL_TOOL`,
  `ANSWER_DIRECTLY`, and `ASK_CLARIFICATION`.
- Requests use temperature 0. The instructions go in `promptPrefix` when
  `isCachingFeatureAvailable()` returns true. Otherwise the prefix goes in front of the text.
- `ChatViewModel.prepareModel` must reach `ModelStatus.Ready` (status, download, structured-output
  check, warmup) before the first inference.
- `GeminiNanoClient` and `AgentOrchestrator` are `@ViewModelScoped`. `ChatViewModel.onCleared`
  calls `GeminiNanoClient.close()`.
- Log every Gemini Nano call through `GeminiNanoClient.logged` and `logLong` with the tag
  `NanoBot`. `logLong` splits text into 3,000-character parts, because logcat cuts long lines.

## AppFunctions API notes

- Use the alpha12 API. Older snippets (for example `AppFunctionContext`, removed in alpha12) do not
  compile.
- `AppFunctionManager.getInstance` returns null on unsupported devices. `AppModule` provides it as
  `AppFunctionManager?`, and callers handle null.
- The manifest `<queries>` entry for `android.app.appfunctions.AppFunctionService` is required to
  see other apps' functions.
- The converters resolve `AppFunctionReferenceTypeMetadata` through `components.dataTypes`
  (`resolve()` in `GeminiToolConverter.kt`). allOf, oneOf, bytes, and Parcelable types are not
  supported.

## Known ceilings

Comments that start with `ponytail:` mark deliberate shortcuts and their limits. Examples: the tool
list overflows the context at about 100 tools, and unsupported metadata types are dropped. Read
those comments before you change the related code. Gemini Nano can also convert amounts
incorrectly (it converted "10L" to 10,000,000), so do not trust model arithmetic.
