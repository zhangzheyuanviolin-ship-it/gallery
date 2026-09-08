# Local Agent Plaza MCP258 handoff — Excel execution recovery

Date: 2026-09-08
Branch: `experimental`
Protected stable boundary: `golden/mcp-250-product-stable`
Protected SHA: `4a4346a199344ac0912984f1f383abedf3b93819`

## User acceptance criterion

The only meaningful success criterion for this work is device-level Excel creation: when the local model emits a recoverable textual `excel_workbook` tool call, the Android host must parse/repair it, execute the Excel tool, and create an `.xlsx` file in the configured workspace. Merely suppressing protocol text from the final assistant reply is not success.

## Why MCP257 was insufficient

MCP257 fixed a real upstream problem by carrying the complete raw LiteRT-LM completion into Agent tool dispatch instead of depending on the last UI message fragment. That removed a UI fragmentation bypass. However, the latest phone output still did not produce an Excel file.

The latest phone fixture was semantically complete but syntactically truncated by exactly one final outer JSON object brace before `</tool_call>`. In simplified form, the model emitted:

```text
<tool_call>{"tool":"excel_workbook","arguments":{"rows":[...],"sheets":[...]}</tool_call>
```

The arrays, strings, `arguments` payload, tool name, rows and sheet data were all otherwise structurally complete. MCP257 captured the correct full runtime text, but `AgentTextToolCallFallback.parse(...)` returned null because the outer tool object was missing one `}`. MCP256/MCP257 leak protection then hid the protocol instead of executing it.

## MCP258 changes

### 1. Structurally proven truncated-object repair

`AgentTextToolCallFallback.kt` now contains the marker:

`MCP258_TRUNCATED_TOOL_OBJECT_CLOSE_REPAIR`

and helper:

`repairTruncatedToolObjectClosures(...)`

The repair is intentionally narrow. It only adds missing trailing object closures when the scanner can prove that strings and arrays are already closed and that the remaining defect is an unterminated JSON object envelope. It does not attempt free-form JSON guessing. Fixtures with an open array or unterminated string remain unparseable.

The prior MCP256 narrow repair for prematurely closed Excel `rows` arrays remains in place.

### 2. Unambiguous Excel-create promotion

`AgentOfficeTruthGuard.kt` now contains the marker:

`MCP258_UNAMBIGUOUS_EXCEL_CREATE`

For the built-in Excel workbook skill, a payload that:

- has no `input_path` / existing workbook path,
- has no modification operations,
- contains non-empty `rows` or `sheets`,
- has no explicit operation that resolves elsewhere,

is resolved directly to:

`xlsx_create`

with resolution metadata:

`payload_unambiguous_create`

This avoids leaving a clearly new-workbook request in the intermediate `office_auto` state.

Explicit reads and existing-workbook modifications are not reclassified as create.

### 3. Execution chain preserved and asserted

The intended route is now explicitly checked in MCP258 CI:

```text
LiteRT-LM raw completion
  -> AgentTextToolCallFallback
  -> excel_workbook
  -> AgentTools.runOfficeCompatTool
  -> AgentOfficeTruthGuard.prepareCompatRequest
  -> xlsx_create
  -> AgentOfficeTruthGuard.executeVerified
  -> AgentOfficeDocumentSupport.createXlsx
  -> writeBytes
  -> semantic read-back verification
  -> .xlsx in configured workspace
```

MCP257's full-runtime-completion transport remains required and is re-verified by MCP258.

## Tests

`Mcp258ExcelExecutionRecoveryTest.kt` includes the exact 2026-09-08 phone output and verifies that it:

1. is recognized as a strong tool signal,
2. is repaired and parsed,
3. resolves to wire tool `excel_workbook`,
4. resolves to `xlsx_create`,
5. receives `payload_unambiguous_create`,
6. is supported by the Office document backend,
7. normalizes the seven-row comparison table to four columns,
8. preserves the `LLM_Comparison` sheet and its rows.

Negative tests verify that an open array or unterminated string is not guessed/repaired.

Older MCP255/MCP256 tests were updated where they encoded the now-obsolete expectation that no-input rows/sheets remain `office_auto`. Their new expectation is `xlsx_create`. Existing-workbook modify behavior remains `office_auto` where appropriate.

## CI history

First MCP258 run:

- Run ID: `34228217901`
- Conclusion: failure
- Failed step: Office/unit regressions and release build
- Cause: two legacy tests still asserted the pre-MCP258 `office_auto` intermediate semantics.
- No APK was published from this failed run.

Second/final MCP258 run:

- Run ID: `34230228146`
- Job ID: `102074209143`
- Head/release source commit: `3a390b1095977a4877f70accd3f397aa2b6be60b`
- Conclusion: success

All release steps succeeded, including:

- protected MCP250 boundary verification,
- MCP251 through MCP258 materialization,
- raw-runtime completion markers,
- exact phone recovery regression,
- Office regressions,
- Kotlin/JVM tests,
- release APK build,
- protected runtime boundary check,
- APK signature/package/version verification,
- Office skill asset verification,
- DEX marker verification,
- Actions artifact upload,
- permanent GitHub Release,
- machine-readable result marker.

## Release

Version name: `1.0.14-mcp.258`
Version code: `358`
Package: `com.localagent.plaza.mcp`
Release tag: `mcp258-excel-execution-recovery-v1`
APK: `local-agent-plaza-1.0.14-mcp.258.apk`
APK size: `239138597` bytes
SHA-256: `50e2322e70b216ab116103b39f05dd020b94935511e71211698db42ca0653b4e`

Permanent APK URL:

`https://github.com/zhangzheyuanviolin-ship-it/local-agent-plaza/releases/download/mcp258-excel-execution-recovery-v1/local-agent-plaza-1.0.14-mcp.258.apk`

SHA file URL:

`https://github.com/zhangzheyuanviolin-ship-it/local-agent-plaza/releases/download/mcp258-excel-execution-recovery-v1/local-agent-plaza-1.0.14-mcp.258.apk.sha256`

Release page:

`https://github.com/zhangzheyuanviolin-ship-it/local-agent-plaza/releases/tag/mcp258-excel-execution-recovery-v1`

Workflow run:

`https://github.com/zhangzheyuanviolin-ship-it/local-agent-plaza/actions/runs/34230228146`

Machine-readable result:

`docs/mcp258_excel_execution_recovery_result.json`

## Final device verification rule

CI success proves that the supplied phone fixture now reaches an executable `xlsx_create` request and that the shipped APK contains the expected writer/dispatch markers. It does not replace phone verification.

For MCP258 to be considered fully successful, the user must install this APK, use the same local model and Excel-generation request, and confirm that an `.xlsx` file actually appears in the configured workspace with the expected table content.

If MCP258 still fails on device, do not spend another version primarily on protocol hiding. Capture the exact model output plus runtime/tool execution log and locate the first broken edge after `xlsx_create`: dispatch entry, workspace/root resolution, `executeVerified`, `createXlsx`, SAF write, or read-back verification. The next fix should be driven by the first missing execution event and should be named MCP259.
