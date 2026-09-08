# Local Agent Plaza MCP259 handoff — 2026-09-08

## Status

MCP259 is built, signed, regression-tested, independently artifact-checked, and permanently released.

This is a **phone-test candidate**, not a claim that the latency issue is fully solved. Functional XLSX creation was already confirmed on the phone with MCP258. MCP259 specifically targets the post-tool continuation latency/state regression and must still be measured on-device.

## Protected product boundary

- Protected branch: `golden/mcp-250-product-stable`
- Protected SHA: `4a4346a199344ac0912984f1f383abedf3b93819`
- MCP259 workflow re-verified the protected music-generation, visual-creation, and arm64 JNI boundaries before publishing.

## Canonical MCP259 source and CI

- Source commit: `86353404a76cb00824f77f087a31cda193a7538e`
- Commit message: `test: exercise real host-accepted truncated Excel continuation path`
- Workflow: `MCP259 Excel latency continuation build`
- Workflow file: `.github/workflows/mcp259_excel_latency_continuation_build.yaml`
- Run ID: `34245240113`
- Job ID: `102125415356`
- Run URL: `https://github.com/zhangzheyuanviolin-ship-it/local-agent-plaza/actions/runs/34245240113`
- Result: success

The canonical run passed all materialization checks, targeted Office/tool-call regressions, release APK build, protected-boundary verification, APK identity/signature/asset/DEX checks, artifact upload, permanent GitHub Release publication, and result-marker persistence.

## Release identity

- Version name: `1.0.14-mcp.259`
- Version code: `359`
- Package: `com.localagent.plaza.mcp`
- Release tag: `mcp259-excel-latency-continuation-v1`
- Release title: `Local Agent Plaza MCP259 Excel Latency Continuation Fix`
- APK: `local-agent-plaza-1.0.14-mcp.259.apk`
- APK SHA256: `d2e33526ce169a4f26ee7d470b837b484d711d91e9ab584c0d921e5715c17780`
- APK direct URL: `https://github.com/zhangzheyuanviolin-ship-it/local-agent-plaza/releases/download/mcp259-excel-latency-continuation-v1/local-agent-plaza-1.0.14-mcp.259.apk`
- SHA256 URL: `https://github.com/zhangzheyuanviolin-ship-it/local-agent-plaza/releases/download/mcp259-excel-latency-continuation-v1/local-agent-plaza-1.0.14-mcp.259.apk.sha256`
- Release page: `https://github.com/zhangzheyuanviolin-ship-it/local-agent-plaza/releases/tag/mcp259-excel-latency-continuation-v1`

The downloaded Actions artifact was independently unpacked after CI. Recomputed APK SHA256 matched both the artifact checksum file and GitHub Release asset digest exactly.

## Root cause addressed

Phone evidence from MCP258 showed that Excel execution itself was fast and successful, while the continuation was extremely slow:

- request total: `217073.83 ms`
- initial generation: `84621.01 ms`
- initial output chars: `920`
- Excel tool execution: `643.77 ms`
- continuation TTFT: `87560.16 ms`
- `continuation_prepare_ms_total=0.0`
- `last_continuation_prepare_ms=unavailable`
- `last_continuation_reset_ms=unavailable`
- `last_fresh_conversation_reason=top_level_user_turn`

The architectural gap was that host dispatch and runtime coordinator state could disagree for recovered textual tool-call dialects. The host could successfully parse and execute an Excel call while `AgentCompatRuntimeCoordinator` had already classified the model completion as an ordinary final answer, leaving `awaitingToolResult=false`. The subsequent `TOOL_RESULT` then bypassed MCP206's fresh `tool_continuation` conversation path.

## MCP259 implementation

MCP259 closes the state disagreement in two layers.

1. `AgentCompatRuntimeCoordinator.extractToolCallFingerprint()` reuses `AgentTextToolCallFallback.parse(text)`, the same recovered textual-tool parser used by host dispatch. Recoverable textual calls therefore enter tool-awaiting state during generation completion.

2. `AgentChatScreen` calls `AgentCompatRuntimeCoordinator.onHostToolDispatchAccepted(...)` immediately before an accepted parsed tool is actually executed. The host's accepted dispatch is the final state authority. If a future dialect was missed earlier, this method:
   - rolls back any provisional completed-turn record for the current user turn;
   - sets a canonical fingerprint for the accepted tool call;
   - sets `awaitingToolResult=true`;
   - allows the later `TOOL_RESULT` to enter the fresh MCP206 continuation path.

MCP259 also shortens the advertised Excel CREATE schema to prefer root `rows` plus `sheet_name`, explicitly forbidding duplicated identical data under both root `rows` and `sheets`. This is intended to reduce first-pass decode overhead for small local models.

## Regression coverage

`Mcp259ExcelLatencyContinuationRegressionTest.kt` covers:

- recovered truncated Excel textual call;
- alternate Excel wire dialect;
- host-accepted dispatch repairing a coordinator that prematurely completed the turn;
- ordinary final text remaining ordinary when no host dispatch occurs;
- compact Excel CREATE schema without duplicated `rows` + `sheets` payloads.

The final regression path models the real host lifecycle:

`model completion -> AgentTextToolCallFallback -> onGenerationCompleted -> host accepts parsed dispatch -> onHostToolDispatchAccepted -> Excel executes -> TOOL_RESULT -> fresh tool_continuation`

## APK independent DEX evidence

After downloading the successful Actions artifact and unpacking the final APK, the DEX files independently contained the following runtime evidence:

- `AgentCompletionTextResolver`
- `chat.runtime_completion_dispatch`
- `repairTruncatedToolObjectClosures`
- `onHostToolDispatchAccepted`
- `tool_continuation`
- `Omit operation, input_path, and output_path when not needed`
- `Never duplicate identical rows in both root rows and sheets`
- `last_fresh_conversation_reason`
- `continuation_prepare_ms_total`
- `last_continuation_reset_ms`
- `compat_history_step_count`

This verifies that the final release APK contains the MCP257 raw-completion capture, MCP258 structural Excel recovery, and MCP259 host-authoritative continuation-state logic.

## Phone acceptance test

Use the same local model/tool setup that reproduced MCP258.

Functional acceptance:

1. Ask the model to create an Excel workbook again.
2. The workbook must still actually be created and visible in the configured workspace.
3. Raw textual tool protocol must not become the final assistant answer when the host parser can execute it.

Continuation-state acceptance:

The post-request diagnostic should show:

- `last_fresh_conversation_reason=tool_continuation`
- `compat_history_step_count >= 1`
- `continuation_prepare_ms_total > 0`
- `last_continuation_prepare_ms` available and nonzero
- `last_continuation_reset_ms` available and nonzero

Performance acceptance:

Compare against the MCP258 baseline above. The key phone-measured target is a major reduction from `continuation_ttft_ms=87560.16`. Also compare `initial_output_chars=920`; it should usually shrink when the model follows the compact CREATE schema. Exact latency improvement cannot be certified by CI because it depends on the real phone, local model, runtime state, thermal state, and generated token count.

## Result marker

Canonical machine-readable result:

`docs/mcp259_excel_latency_continuation_result.json`

## Next action

Install the MCP259 release APK and repeat the same Excel creation request on the phone. If the XLSX is created, collect the resulting diagnostic log. The next investigation should start by checking `last_fresh_conversation_reason`, continuation prepare/reset metrics, continuation TTFT, initial output chars, and memory at continuation first callback before changing any additional runtime code.
