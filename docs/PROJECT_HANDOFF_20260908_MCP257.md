# Local Agent Plaza — MCP257 completed release handoff

Date: 2026-09-08
Branch: `experimental`
Status: MCP257 release build completed successfully; phone validation still required.

## 1. Why MCP257 exists

MCP256 was built to parse textual tool-call output such as `<tool_call>{...}</tool_call>` regardless of whether the model/session resolved to COMPAT or NATIVE mode. Unit and wire regressions passed, and the MCP256 APK contained the expected fallback code.

However, real phone testing still showed the model's Excel call rendered verbatim as the final assistant reply instead of being parsed and executed. The decisive phone fixture was the same Excel call already covered by MCP256 parser-level tests. That proved the failure was above the parser itself.

## 2. Root cause established by MCP257

MCP256 inspected the final UI `ChatMessageText` at completion time. The real LiteRT-LM runtime stream can be segmented by the chat UI lifecycle, especially when progress/status messages are inserted while generation is ongoing. As a result, the UI can audibly/render visually as one continuous completion while the Agent dispatch layer sees only the final text fragment.

Therefore a complete `<tool_call>...</tool_call>` can be present in the runtime output and in the visible conversation while the dispatch parser receives only a suffix that no longer contains the opening protocol marker.

This explains why repeated parser/Excel-normalization fixes in MCP255/MCP256 could pass unit tests and still have zero effect on the phone.

## 3. MCP257 architecture change

MCP257 moves the authoritative tool-dispatch input away from the UI message list.

The release patch:

- accumulates the complete raw LiteRT-LM runtime output inside `LlmChatViewModel` while tokens arrive;
- passes that raw completion through the normal send completion callback;
- passes it through continuation/tool-loop completion paths;
- passes it through the `Run again` path that previously did not carry the same completion callback;
- explicitly shares the Agent ViewModel across the relevant composable paths;
- makes Agent completion dispatch prefer the complete raw runtime completion instead of only the final UI `ChatMessageText`;
- when a textual tool call is dispatched, removes all Agent text fragments belonging to the current turn rather than assuming the final list item is the only model-text message;
- preserves MCP251–MCP256 Office Truth Guard, dialect normalization, Excel repair, document conversion, wire adapters, and protected product/runtime boundaries.

MCP257 deliberately does not rely on another new Excel parser special case. The core repair is runtime-completion plumbing.

## 4. Build-order issue found and fixed while producing MCP257

The first MCP257 CI attempt failed because two completion branches had different source shapes and the patch script used an overly strict shared anchor. The patch was split into precise branch-specific edits.

A later attempt passed MCP257 source-plumbing verification but failed during Gradle configuration because MCP257 had modified a legacy maximum-tool-step block before the older MCP224 Gradle-time patch removed that same block. MCP224 correctly failed closed when its expected anchor was missing.

The final workflow preserves the MCP224 patch order by restoring only the legacy anchor needed for the historical patch stage. That bridge does not survive into the final runtime implementation.

## 5. Final successful build

Workflow: `MCP257 runtime raw completion tool dispatch build`

Run ID: `34223192282`

Source commit used for the release:

`3be525d0cbbce21c04f0737cb3aa4885e662f0d5`

Workflow conclusion: `success`

The successful job completed all of the following stages:

- protected MCP250 boundary verification;
- MCP251 through MCP257 materialization and real-completion-plumbing checks;
- Java 21 and signing setup;
- existing Office regressions;
- MCP255/MCP256 wire and phone fixtures;
- MCP257 runtime-completion regressions;
- release APK assembly;
- protected runtime-boundary verification;
- signed APK package/version/Office-asset/raw-completion-marker verification;
- Actions artifact upload;
- permanent GitHub Release publication;
- repository result-marker commit.

## 6. Release identity

Version name: `1.0.14-mcp.257`

Version code: `357`

Package: `com.localagent.plaza.mcp`

Release tag: `mcp257-runtime-raw-completion-v1`

APK: `local-agent-plaza-1.0.14-mcp.257.apk`

Release APK SHA-256:

`a1d3668e76b1158a4a4d27923f8e98166421cb2bf68be9a09c6fa4e76f4a4b9d`

Permanent APK URL:

`https://github.com/zhangzheyuanviolin-ship-it/local-agent-plaza/releases/download/mcp257-runtime-raw-completion-v1/local-agent-plaza-1.0.14-mcp.257.apk`

Release page:

`https://github.com/zhangzheyuanviolin-ship-it/local-agent-plaza/releases/tag/mcp257-runtime-raw-completion-v1`

Workflow run:

`https://github.com/zhangzheyuanviolin-ship-it/local-agent-plaza/actions/runs/34223192282`

## 7. Repository result marker

The successful workflow committed:

`docs/mcp257_runtime_raw_completion_result.json`

The marker records the run ID, source commit, version, package, release tag, APK name, APK SHA-256, root-cause summary, fix summary, and permanent APK URL.

The workflow result commit moved `experimental` to:

`a6772877240711c7c37316528aed7f6ebe6ef30f`

before this handoff commit was added.

## 8. Exact phone evidence that must be retested

The highest-priority validation remains the Excel request that previously leaked the complete textual protocol into the final assistant reply. The critical behavior is binary:

- expected: the textual tool call is intercepted, parsed, executed by `excel_workbook`, and the user receives the resulting workbook/tool continuation;
- failure: any `<tool_call>`, JSON call envelope, or substantial fragment of that protocol survives as the final assistant message without tool execution.

MCP257 CI coverage is intentionally stronger than MCP256 because it tests runtime output fragmentation/plumbing in addition to parser behavior. It still cannot substitute for the actual Android phone/runtime/model combination.

## 9. What to do next

Install and test MCP257 on the same phone, same local model, and same Excel creation prompt that reproduced MCP256.

If the call executes correctly, treat MCP257 as the first confirmed fix for this dispatch class and continue broader Office tool-call testing.

If the call still leaks as text, do not add another parser-only patch. Capture the exact final text plus diagnostic/runtime logs and trace whether the raw-completion callback reaches `AgentChatScreen.handleGenerationDone`. The next investigation should then focus on callback ownership/session identity or model runtime completion lifecycle rather than Excel argument normalization.

## 10. Protected boundary

Do not regress the protected MCP250 product-stable baseline or unrelated music, visual-generation, media-runtime, and native-runtime behavior while iterating on Agent textual-tool dispatch.

Protected product-stable reference remains:

`golden/mcp-250-product-stable`

`4a4346a199344ac0912984f1f383abedf3b93819`
