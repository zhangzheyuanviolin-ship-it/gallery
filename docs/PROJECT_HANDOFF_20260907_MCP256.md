# Local Agent Plaza MCP256 handoff — 2026-09-07

## Scope

MCP256 follows the phone failure observed after MCP255. MCP255's Excel argument repair was insufficient for the decisive failure path: textual `excel_workbook` calls were rendered as final assistant text instead of being dispatched as tools.

The MCP255 phone result is treated as authoritative evidence that the previous fix did not solve this end-to-end path.

## Root cause established by phone evidence

The decisive completion path in `AgentChatScreen.kt` only attempted textual tool-call parsing when `resolveAgentToolMode(model) == COMPAT`.

That assumption is invalid in practice. A model/session classified as native can still emit a textual tool-call wire representation such as `<tool_call>{...}</tool_call>`. When that happens, the old completion branch is skipped entirely and the raw protocol survives as ordinary assistant text.

A second structural issue made earlier wire-parser hardening less effective than expected: `CompatToolCallWireAdapter.normalizeFirstToolCall()` supports many model-family dialects, but the decisive completion branch called the narrower `parseCompatToolCall()` directly. Therefore a hardened adapter could exist and pass parser-level unit tests without being used at the final UI dispatch boundary.

The first exact 2026-09-07 phone fixture also revealed a separate wire-corruption class that was not represented in MCP255 tests: the model prematurely closed the `rows` array after the first flattened data row, then continued emitting scalar cells before the next object property. The resulting payload is not valid JSON, so neither the strict parser nor ordinary JSON normalization can reach the Excel compatibility layer.

The second phone fixture is syntactically valid but has no explicit `operation` and adds a blank overflow cell to each data row beyond the four-column header.

## MCP256 implementation

### 1. Mode-agnostic completion fallback

Added `AgentTextToolCallFallback.kt`.

At the completion boundary it:

1. tries the established strict canonical parser;
2. repairs only the structurally provable Excel premature-`rows` closure corruption described below;
3. retries the strict parser and shared family-aware wire adapter on the repaired text;
4. otherwise falls back to `CompatToolCallWireAdapter.normalizeFirstToolCall()` on the original text;
5. reparses the canonical normalized envelope;
6. exposes the existing strong-tool-signal detector for protocol leak protection.

The MCP256 materialization patch changes `AgentChatScreen.kt` so observed textual tool syntax can enter the compatibility execution path regardless of whether the session was classified `NATIVE` or `COMPAT`.

Native structured tool handling remains untouched. The fallback is activated by actual emitted text, not by globally forcing native models into compatibility mode.

### 2. Exact malformed-JSON recovery for the first phone fixture

`AgentTextToolCallFallback` now contains `MCP256_EXCEL_PREMATURE_ROWS_CLOSE_REPAIR`.

The repair is intentionally narrow:

- it runs only for text containing `excel_workbook` and a `"rows"` property;
- it scans the actual array structure while respecting JSON strings and escapes;
- when a candidate `rows` closing bracket is followed by a comma, a valid object continuation must be a quoted property name followed by `:`;
- if the following token is another scalar/array/object value instead, that bracket is structurally impossible as the end of the object property and is removed;
- scanning repeats so the same corruption can be repaired independently at root `rows` and `sheets[0].rows`;
- no arbitrary bracket balancing or free-form JSON guessing is performed.

After those two premature closures are removed from the exact first fixture, the payload becomes valid JSON. The existing MCP255 row normalizer can then reconstruct the 12 flattened scalar cells into two six-column data rows under the six-column header.

### 3. Raw protocol leak guard

If final model text contains a strong tool-call protocol signal but still cannot be parsed safely, MCP256 removes that machine-protocol text from the final assistant message and returns a diagnostic chat info message instead. Internal tool protocol must not survive as an ordinary final reply merely because parsing failed.

### 4. Excel row-width repair extension

MCP256 adds one conservative repair for the second fixture: when a header establishes N columns and a later row has extra cells, overflow cells are trimmed only if every overflow cell in that row is blank/null. Any non-empty overflow value is preserved.

This handles the observed four-column header plus trailing empty fifth-cell pattern without deleting actual spreadsheet data.

### 5. Missing operation remains safely inferred

The second exact fixture has no `operation`. MCP256 does not force an arbitrary create/modify decision. It continues through `AgentOfficeTruthGuard.prepareCompatRequest()` as `office_auto`, allowing the existing workspace-aware inference path to decide safely.

## Exact phone regression fixtures

`Mcp256PhoneToolCallRegressionTest.kt` contains the two phone outputs supplied on 2026-09-07 verbatim.

Required assertions now cover:

- both outputs are recognized as strong textual tool calls;
- both parse to the wire tool name `excel_workbook` through the completion fallback;
- fixture one repairs both premature `rows` closures and reconstructs three six-column rows at root and sheet level;
- fixture two remains safely inferable without an explicit operation;
- fixture two's blank fifth overflow cell is trimmed to the four-column header width at root and sheet level;
- a non-empty overflow fifth cell is preserved;
- an alternate compact wire dialect still passes through the shared adapter fallback.

Important naming boundary: the wire protocol name is `excel_workbook` while the internal Office skill id is `excel-workbook`. The first MCP256 CI attempt incorrectly compared those two namespaces in two test assertions. The runtime dispatcher was already correct; the assertions were fixed to validate the actual wire name.

## Build history for MCP256

### Initial MCP256 release attempt

Run `34087380763` failed in the combined Office/wire regression and release-build step.

That failure produced two new findings:

1. fixture one was genuinely malformed JSON because of premature `rows` closure, requiring the new constrained repair rather than more Excel backend normalization;
2. two test assertions mixed the underscore wire tool name with the hyphenated internal skill id.

The failed attempt was not published as a completed MCP256 release.

### Successful MCP256 release attempt

Final source commit used for the release:

`f2759040bc8d30d290ab254a71d1026cc83443e5`

Successful workflow run:

`34097530069`

Every release gate completed successfully:

- MCP250 protected-boundary verification;
- MCP251 -> MCP256 materialization replay;
- release signing setup;
- existing Office truth/contract/conversion/Excel regressions;
- existing tool-call wire regressions;
- both exact MCP256 phone fixtures;
- release APK assembly;
- protected music/visual/native-runtime boundary verification;
- APK signature verification;
- package/version identity verification;
- all five Office skill assets present in the APK;
- Actions artifact upload;
- permanent GitHub Release publication;
- build-result marker publication.

## Final release

- version name: `1.0.14-mcp.256`
- version code: `356`
- application id: `com.localagent.plaza.mcp`
- release tag: `mcp256-text-tool-dispatch-v1`
- APK name: `local-agent-plaza-1.0.14-mcp.256.apk`
- APK size: `239130801` bytes
- APK SHA-256: `890b4f6897c90a71194f41a8c40b0f8d27a4f03fbd7ff714316c821639c20bc9`
- release source commit: `f2759040bc8d30d290ab254a71d1026cc83443e5`
- successful run id: `34097530069`

Permanent APK URL:

`https://github.com/zhangzheyuanviolin-ship-it/local-agent-plaza/releases/download/mcp256-text-tool-dispatch-v1/local-agent-plaza-1.0.14-mcp.256.apk`

The workflow wrote `docs/mcp256_text_tool_dispatch_result.json` as the machine-readable release record. The resulting bot commit advanced `experimental` to `4fbd43246f70404af7f0f382354d85f31f0b0b54` before this handoff update.

## Protected boundaries

MCP256 does not change music generation, visual creation, protected native libraries, or the native structured-tool transport. The release workflow explicitly verified those boundaries against `golden/mcp-250-product-stable` before publication.

## Project lineage relevant to this fix

The project continues to preserve the MCP250 product-stable boundary. MCP251 introduced Office skills, MCP252 added evidence-based Office truth verification, MCP253 normalized observed Office call dialects, MCP254 added document conversion and wire-path hardening, and MCP255 focused on the Excel evidence captured from phone testing.

MCP255 built and released successfully, but the 2026-09-07 phone test proved that build/test success did not cover the decisive mode-gated completion path. MCP256 therefore moves the regression target one layer upward: from "can a parser or Excel normalizer understand this payload?" to "will the real final-message boundary actually dispatch the emitted text, including the exact malformed wire payload seen on-device?"

## Remaining uncertainty

The code/build side is now green and the two exact supplied phone outputs pass regression tests. Device verification remains the final authority for real model behavior because a model can emit new, previously unseen malformed variants.

One specifically bounded uncertainty remains: streaming may temporarily display partial protocol fragments before `onGenerationDone`. MCP256 guarantees completion-boundary dispatch/leak handling. A separate stream-buffering change should only be made if phone testing shows a persistent visible partial-protocol flash rather than the former final-text leak.

Unknown or genuinely ambiguous non-empty spreadsheet shapes continue to fail diagnostically rather than being destructively guessed.
