# NanoBot

NanoBot is an Android chat agent that runs on the device. It uses Gemini Nano (ML Kit GenAI Prompt
API) to answer a question with the [AppFunctions](https://developer.android.com/ai/appfunctions)
of other installed apps. NanoBot knows no app in advance. It finds the functions at run time.

## Requirements

- Android 16 (API 36) or higher, on a device that supports Gemini Nano and AppFunctions (for
  example a Pixel).
- `adb` access to the device. Only a privileged caller can execute AppFunctions (see below).
- At least one installed app that exposes AppFunctions.

## Run

```bash
./run_privileged.sh -b            # build, install, and start NanoBot
./run_privileged.sh -s SERIAL     # select a device when more than one is connected
adb logcat -s NanoBot             # Gemini Nano prompts, responses, and timings
```

Keep the terminal open. Ctrl+C stops the privileged session.

### Why the script is necessary

Executing an AppFunction requires `android.permission.EXECUTE_APP_FUNCTIONS`. Only system apps get
this permission. `run_privileged.sh` gives NanoBot the permission for testing:

1. It installs the debug APK.
2. On Android 17 or higher, it adds NanoBot to the AppFunctions caller allowlist.
3. It stops other instrumentations. The shell gives its permissions to only one instrumentation at
   a time.
4. It starts `ShellIdentityInstrumentation` with `am instrument`. That class takes the shell
   permission and opens the chat screen.

If NanoBot starts from the launcher, it finds the tools but cannot run them.

## How one message works

1. **Discover.** `GetAppFunctionsUseCase` finds all AppFunctions on the device, and finds them again
   when an app changes. `GeminiToolConverter` gives each function a short name, a one-line summary,
   and a JSON schema.
2. **Choose a tool.** Gemini Nano gets the one-line summaries and the user message. It returns a
   tool name, or a direct answer.
3. **Fill the arguments.** Gemini Nano gets the full schema of that tool only. It returns the
   arguments as JSON, or asks for a missing value.
4. **Check.** `AgentOrchestrator` parses the JSON and converts it to typed `AppFunctionData`. Any
   error becomes a chat reply.
5. **Confirm.** The chat shows the call with Run and Cancel. Nothing runs without the user.
6. **Execute.** `AppFunctionManager.executeAppFunction` runs the function in the target app.
7. **Explain.** Gemini Nano writes a short answer from the result JSON.

All Gemini Nano calls use structured output (`AgentDecision`), temperature 0, and prefix caching
when the device supports it. Steps 2 and 3 are separate calls, because the full schemas of all
tools do not fit in the context of Gemini Nano.

## Code

| Path | Contents |
| --- | --- |
| `ShellIdentityInstrumentation.kt`, `run_privileged.sh` | Privileged start |
| `ai/GeminiNanoClient.kt` | Gemini Nano setup, prompts, and logs |
| `ai/model/AgentDecision.kt` | Structured output of the model |
| `agent/AgentOrchestrator.kt` | One agent turn: choose, fill, check, execute, explain |
| `domain/appfunction/` | Discovery, schema conversion, and argument and result conversion |
| `ui/chat/` | Chat screen and view model |

Stack: Kotlin, Jetpack Compose, Hilt, `androidx.appfunctions` 1.0.0-alpha12, ML Kit `genai-prompt`
1.0.0-beta4, `genai-schema` 1.0.0-alpha1.

## Tests

```bash
./gradlew :app:testDebugUnitTest
```

## Known limits

- One tool call for each message. NanoBot sends no chat history to the model.
- The tool list overflows the context of Gemini Nano at about 100 tools.
- Gemini Nano can convert amounts incorrectly. For example, it converted "10L" to 10,000,000.
- The converters do not support allOf, oneOf, bytes, or Parcelable types.
- The `NanoBot` logs include user messages and are also written in release builds.
