# AGENTS.md

Read `CLAUDE.md` first — it documents the app architecture thoroughly. This file covers what it leaves out.

## Build quirks

- **JAVA_HOME** must be `C:\Program Files\Android\Android Studio\jbr` before any Gradle command
- **ABI filter**: `arm64-v8a` only (line 21 of `app/build.gradle.kts`)
- **MultiDex** enabled
- **Release builds**: `isMinifyEnabled = false` (ProGuard config exists but unused); signing creds hardcoded in build file (storePassword/alias/keyPassword all `insta360`)
- **APK output**: renamed to `insta_sdk_demo_${buildType}_${versionName}.apk`
- **Gradle 8.13**, AGP 8.12.3, Kotlin 2.0.21
- **NDK version**: `25.2.9519653`

## Modules

- **`:app`** — Android application (all UI, camera, player)
- **`:lib`** — pure JVM library (Java 11 + Kotlin, no Android dependencies)

## Testing

- **JUnit 4** only — no mock libraries (Mockito/MockK absent)
- Unit tests: `app/src/test/java/` — 2 real test classes (`PanoramaFovMathTest`, `EquirectangularProjectionTest`)
- Instrumented tests: `app/src/androidTest/java/` — only a placeholder
- Run: `./gradlew testDebugUnitTest`
- Single class: `./gradlew testDebugUnitTest --tests "*ClassName*"`

## Repository & credentials

Insta360 SDK lives on a private Nexus at `http://nexus.arashivision.com:9999/repository/maven-releases/` with `isAllowInsecureProtocol = true` and basic auth `insta360dev` / `50lan123`. SDK version `1.8.1_build_06` via the version catalog (`libs.versions.insta`).

**Media3 ExoPlayer** (`1.5.1`) is declared directly in `app/build.gradle.kts`, not via the version catalog.

## No code quality tooling

No detekt, ktlint, spotless, editorconfig, or typecheck configured. No CI pipeline.

## Manifest details

- **MainActivity** is the launcher, `singleInstance` launch mode
- **CaptureActivity** declared twice in the manifest (both `singleInstance`)
- **ConnectService** is a foreground service (type `connectedDevice`) for camera connection persistence
- **InstaApp** (Application class) initializes `InstaCameraSDK`, `InstaMediaSDK`, and `UsbMgr` on startup

---

# context-mode — MANDATORY routing rules

context-mode MCP tools available. Rules protect context window from flooding. One unrouted command dumps 56 KB into context.

## Think in Code — MANDATORY

Analyze/count/filter/compare/search/parse/transform data: **write code** via `context-mode_ctx_execute(language, code)`, `console.log()` only the answer. Do NOT read raw data into context. PROGRAM the analysis, not COMPUTE it. Pure JavaScript — Node.js built-ins only (`fs`, `path`, `child_process`). `try/catch`, handle `null`/`undefined`. One script replaces ten tool calls.

## BLOCKED — do NOT attempt

### curl / wget — BLOCKED
Shell `curl`/`wget` intercepted and blocked. Do NOT retry.
Use: `context-mode_ctx_fetch_and_index(url, source)` or `context-mode_ctx_execute(language: "javascript", code: "const r = await fetch(...)")`

### Inline HTTP — BLOCKED
`fetch('http`, `requests.get(`, `requests.post(`, `http.get(`, `http.request(` — intercepted. Do NOT retry.
Use: `context-mode_ctx_execute(language, code)` — only stdout enters context

### Direct web fetching — BLOCKED
Use: `context-mode_ctx_fetch_and_index(url, source)` then `context-mode_ctx_search(queries)`

## REDIRECTED — use sandbox

### Shell (output >20 lines)
Shell ONLY for: `git`, `mkdir`, `rm`, `mv`, `cd`, `ls`, `npm install`, `pip install`.
Otherwise: `context-mode_ctx_batch_execute(commands, queries)` or `context-mode_ctx_execute(language: "shell", code: "...")`

### File reading (for analysis)
Reading to **edit** → reading correct. Reading to **analyze/explore/summarize** → `context-mode_ctx_execute_file(path, language, code)`.

### grep / search (large results)
Use `context-mode_ctx_execute(language: "shell", code: "grep ...")` in sandbox.

## Tool selection

0. **MEMORY**: `context-mode_ctx_search(sort: "timeline")` — after resume, check prior context before asking user.
1. **GATHER**: `context-mode_ctx_batch_execute(commands, queries)` — runs all commands, auto-indexes, returns search. ONE call replaces 30+. Each command: `{label: "header", command: "..."}`.
2. **FOLLOW-UP**: `context-mode_ctx_search(queries: ["q1", "q2", ...])` — all questions as array, ONE call (default relevance mode).
3. **PROCESSING**: `context-mode_ctx_execute(language, code)` | `context-mode_ctx_execute_file(path, language, code)` — sandbox, only stdout enters context.
4. **WEB**: `context-mode_ctx_fetch_and_index(url, source)` then `context-mode_ctx_search(queries)` — raw HTML never enters context.
5. **INDEX**: `context-mode_ctx_index(content, source)` — store in FTS5 for later search.

## Parallel I/O batches

For multi-URL fetches or multi-API calls, **always** include `concurrency: N` (1-8):

- `context-mode_ctx_batch_execute(commands: [3+ network commands], concurrency: 5)` — gh, curl, dig, docker inspect, multi-region cloud queries
- `context-mode_ctx_fetch_and_index(requests: [{url, source}, ...], concurrency: 5)` — multi-URL batch fetch

**Use concurrency 4-8** for I/O-bound work (network calls, API queries). **Keep concurrency 1** for CPU-bound (npm test, build, lint) or commands sharing state (ports, lock files, same-repo writes).

GitHub API rate-limit: cap at 4 for `gh` calls.

## Output

Write artifacts to FILES — never inline. Return: file path + 1-line description.
Descriptive source labels for `search(source: "label")`.

## Session Continuity

Skills, roles, and decisions persist for the entire session. Do not abandon them as the conversation grows.

## Memory

Session history is persistent and searchable. On resume, search BEFORE asking the user:

| Need | Command |
|------|---------|
| What did we decide? | `context-mode_ctx_search(queries: ["decision"], source: "decision", sort: "timeline")` |
| What constraints exist? | `context-mode_ctx_search(queries: ["constraint"], source: "constraint")` |

DO NOT ask "what were we working on?" — SEARCH FIRST.
If search returns 0 results, proceed as a fresh session.

## ctx commands

| Command | Action |
|---------|--------|
| `ctx stats` | Call `stats` MCP tool, display full output verbatim |
| `ctx doctor` | Call `doctor` MCP tool, run returned shell command, display as checklist |
| `ctx upgrade` | Call `upgrade` MCP tool, run returned shell command, display as checklist |
| `ctx purge` | Call `purge` MCP tool with confirm: true. Warns before wiping knowledge base. |

After /clear or /compact: knowledge base and session stats preserved. Use `ctx purge` to start fresh.
