# Local Agent Plaza MCP256 handoff — 2026-09-07

## Scope

MCP256 is the follow-up to the phone failure observed after MCP255. MCP255's Excel argument repair was not sufficient: two standard textual `excel_workbook` calls were rendered as final assistant text instead of being dispatched as tools.

This handoff treats the MCP255 phone result as authoritative evidence that the previous fix was ineffective for this failure path.

## New root cause

The decisive completion path in `AgentChatScreen.kt` only attempted textual tool-call parsing when `resolveAgentToolMode(model) == COMPAT`.

That assumption is invalid in practice. A model/session classified as native can still emit a textual tool-call wire representation such as `<tool_call>{...}</tool_call>`. When that happens, the old completion branch is skipped entirely and the raw protocol survives as ordinary assistant text.

A second structural issue made earlier wire-parser hardening less effective than expected: `CompatToolCallWireAdapter.normalizeFirstToolCall()` supports many model-family dialects, but the decisive completion branch called the narrower `parseCompatToolCall()` directly. Therefore a hardened adapter could exist and pass unit tests without being used at the final UI dispatch boundary.

This is distinct from MCP255's Excel schema/argument problem. The two newly supplied phone fixtures are valid enough to identify `excel_workbook`; their observed failure occurred before the Excel compatibility layer could execute.

## MCP256 implementation

### Mode-agnostic completion fallback

Added `AgentTextToolCallFallback.kt`.

At the completion boundary it:

1. tries the established strict canonical parser;
2. falls back to `CompatToolCallWireAdapter.normalizeFirstToolCall()`;
3. reparses the canonical normalized envelope;
4. exposes the existing strong-tool-signal detector for protocol leak protection.

The MCP256 materialization patch changes `AgentChatScreen.kt` so observed textual tool syntax can enter the compatibility execution path regardless of whether the session was classified `NATIVE` or `COMPAT`.

Native structured tool handling remains untouched. The fallback is activated by actual emitted text, not by globally forcing native models into compatibility mode.

### Raw protocol leak guard

If the final model text contains a strong tool-call protocol signal but cannot be parsed safely, MCP256 removes that machine-protocol text from the final assistant message and returns a diagnostic chat info message instead. Internal tool protocol should never be presented as a normal final answer merely because parsing failed.

### Excel row repair extension

MCP255 already repairs the first phone fixture's flattened scalar tail when recovery is unambiguous.

MCP256 adds one conservative repair for the second fixture: when a header establishes N columns and a later row has extra cells, overflow cells are trimmed only if every overflow cell is blank/null. Any non-empty overflow value is preserved.

This handles the observed four-column header plus trailing empty fifth-cell pattern without deleting actual spreadsheet data.

## Exact phone regression fixtures

`Mcp256PhoneToolCallRegressionTest.kt` includes the two phone outputs supplied on 2026-09-07 verbatim.

Required assertions include:

- both outputs are recognized as strong textual tool calls;
- both parse as `excel_workbook` through the completion fallback;
- fixture one reconstructs the flattened project rows into three six-column rows at root and sheet level;
- fixture two remains safely inferable without an explicit operation;
- fixture two's blank fifth overflow cell is trimmed to the four-column header width at root and sheet level;
- a non-empty overflow fifth cell is preserved;
- an alternate compact wire dialect still passes through the shared adapter fallback.

## Release plan

- version name: `1.0.14-mcp.256`
- version code: `356`
- application id: `com.localagent.plaza.mcp`
- release tag: `mcp256-text-tool-dispatch-v1`
- APK name: `local-agent-plaza-1.0.14-mcp.256.apk`

The build workflow must materialize MCP251 -> MCP252 -> MCP253 -> MCP254 -> MCP255 -> MCP256 in that order, run existing Office/wire regressions plus the exact MCP256 phone fixtures, build and sign the release APK, verify package/version identity, and publish the APK plus SHA-256.

## Protected boundaries

MCP256 does not change music generation, visual creation, protected native libraries, or the native structured-tool transport. The change is limited to the agent textual completion fallback and conservative Excel row normalization.

## Project state and lineage relevant to this fix

The project has evolved through the MCP compatibility train while preserving the MCP250 product-stable boundary. MCP251 introduced Office skills, MCP252 added evidence-based Office truth verification, MCP253 normalized observed Office call dialects, MCP254 added document conversion and wire-path hardening, and MCP255 focused on the exact Excel evidence captured from phone testing.

MCP255 built and released successfully, but the 2026-09-07 phone test proved that build/test success did not cover the decisive mode-gated completion path. MCP256 therefore moves the regression target one layer upward: from "can this text/parser or Excel normalizer understand the payload?" to "will the real final-message boundary actually dispatch this emitted text?"

## Remaining uncertainty after MCP256 code changes

The code-level root cause is now directly covered, but device verification remains authoritative for end-to-end behavior. In particular, streaming may temporarily display partial protocol fragments before `onGenerationDone`; MCP256 guarantees completion-boundary dispatch/leak handling, while a separate stream-buffering change should only be made if phone testing shows a persistent visible flash rather than a final-text leak.

The Android storage/backend semantics and Office read-back verification remain those inherited from MCP252-MCP255. Unknown or genuinely ambiguous non-empty spreadsheet shapes continue to fail diagnostically rather than being destructively guessed.
