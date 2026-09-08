#!/usr/bin/env python3
"""MCP259: restore COMPAT continuation state parity and compact Excel create calls.

Runs after MCP251-MCP258 materialization. MCP258 made structurally recoverable Excel calls executable,
but the runtime coordinator could still classify a dialect/repaired call as an ordinary final answer.
That leaves awaitingToolResult=false, so the subsequent TOOL_RESULT bypasses MCP206's fresh
conversation continuation path and can incur a very large post-tool TTFT.

MCP259 closes the gap in two ways:
1. Runtime fingerprinting reuses the same shared textual-tool recovery parser as dispatch.
2. The host explicitly confirms every parsed call that is actually about to execute, so coordinator
   state cannot diverge from host execution even if future wire dialects change again.

It also teaches the model the shortest safe Excel-create wire form so it does not duplicate rows at
root and under sheets.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
AGENT = ROOT / "app/src/main/java/com/google/ai/edge/gallery/customtasks/agentchat"
COORD = AGENT / "AgentCompatRuntimeCoordinator.kt"
TOOLING = AGENT / "AgentTooling.kt"
AGENT_SCREEN = AGENT / "AgentChatScreen.kt"


def require_count(text: str, needle: str, expected: int, label: str) -> None:
    count = text.count(needle)
    if count != expected:
        raise SystemExit(f"MCP259 fail-closed: {label} count={count}, expected={expected}")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    require_count(text, old, 1, label)
    return text.replace(old, new, 1)


coord = COORD.read_text(encoding="utf-8")
if "MCP259_RECOVERED_TOOL_CONTINUATION" not in coord:
    old = '''  private fun extractToolCallFingerprint(text: String): String? {\n    val open = text.indexOf(TOOL_CALL_OPEN_TAG_RUNTIME, ignoreCase = true)\n'''
    new = '''  private fun extractToolCallFingerprint(text: String): String? {\n    // MCP259_RECOVERED_TOOL_CONTINUATION\n    // Runtime state and UI dispatch must agree on what is an executable textual tool call.\n    // MCP256-MCP258 intentionally accept multiple wire dialects and narrowly repair proven\n    // structural Excel truncations; if dispatch can execute one of those calls, the coordinator\n    // must mark awaitingToolResult=true so the following TOOL_RESULT takes MCP206's fresh\n    // tool-continuation path instead of reusing a bloated top-level Conversation.\n    val recoveredCall = AgentTextToolCallFallback.parse(text)\n    if (recoveredCall != null) {\n      val recoveredEnvelope =\n        JSONObject()\n          .put("tool", recoveredCall.toolName)\n          .put("arguments", recoveredCall.arguments)\n      return canonicalizeJsonObject(recoveredEnvelope).take(MAX_FINGERPRINT_CHARS)\n    }\n\n    val open = text.indexOf(TOOL_CALL_OPEN_TAG_RUNTIME, ignoreCase = true)\n'''
    coord = replace_once(coord, old, new, "runtime recovered tool fingerprint hook")

if "MCP259_HOST_DISPATCH_STATE_AUTHORITY" not in coord:
    old = '''  @Synchronized\n  internal fun onGenerationFailed(modelName: String) {\n    states[modelName]?.awaitingToolResult = false\n  }\n'''
    new = '''  // MCP259_HOST_DISPATCH_STATE_AUTHORITY\n  // The host's accepted dispatch is the final authority that this turn is not complete yet.\n  // If onGenerationCompleted misclassified a future textual dialect and prematurely committed the\n  // turn, roll that provisional completion back before the tool result arrives. This makes every\n  // actually executed compatibility tool eligible for MCP206's fresh tool-continuation path.\n  @Synchronized\n  internal fun onHostToolDispatchAccepted(\n    modelName: String,\n    toolName: String,\n    arguments: JSONObject,\n  ) {\n    val state = states[modelName] ?: return\n    val wasAwaitingToolResult = state.awaitingToolResult\n    if (state.completed) {\n      sessions[modelName]?.let { session ->\n        val completedIndex =\n          session.completedTurns.indexOfLast { entry -> entry.turnIndex == state.userTurnIndex }\n        if (completedIndex >= 0) session.completedTurns.removeAt(completedIndex)\n      }\n      state.completed = false\n    }\n    val acceptedEnvelope =\n      JSONObject()\n        .put("tool", toolName)\n        .put("arguments", arguments)\n    state.lastToolFingerprint =\n      canonicalizeJsonObject(acceptedEnvelope).take(MAX_FINGERPRINT_CHARS)\n    if (!wasAwaitingToolResult) state.consecutiveRepeatedToolCalls = 0\n    state.awaitingToolResult = true\n  }\n\n  @Synchronized\n  internal fun onGenerationFailed(modelName: String) {\n    states[modelName]?.awaitingToolResult = false\n  }\n'''
    coord = replace_once(coord, old, new, "host dispatch state authority")

COORD.write_text(coord, encoding="utf-8")
print(f"MCP259 patched: {COORD}")


agent_screen = AGENT_SCREEN.read_text(encoding="utf-8")
if "MCP259_HOST_DISPATCH_ACCEPTED" not in agent_screen:
    old = '''        compatToolStepsByModel[model.name] = currentSteps + 1\n        removeCurrentTurnAgentTextMessages(viewModel = viewModel, model = model)\n'''
    new = '''        // MCP259_HOST_DISPATCH_ACCEPTED\n        // A parsed call that is actually about to execute is authoritative runtime state.\n        AgentCompatRuntimeCoordinator.onHostToolDispatchAccepted(\n          modelName = model.name,\n          toolName = parsedToolCall.toolName,\n          arguments = parsedToolCall.arguments,\n        )\n        compatToolStepsByModel[model.name] = currentSteps + 1\n        removeCurrentTurnAgentTextMessages(viewModel = viewModel, model = model)\n'''
    agent_screen = replace_once(
        agent_screen,
        old,
        new,
        "accepted dispatch runtime-state synchronization",
    )
    AGENT_SCREEN.write_text(agent_screen, encoding="utf-8")
    print(f"MCP259 patched: {AGENT_SCREEN}")
else:
    print(f"MCP259 already applied: {AGENT_SCREEN}")


tooling = TOOLING.read_text(encoding="utf-8")
if "MCP259_EXCEL_COMPACT_CREATE_SCHEMA" not in tooling:
    # MCP252-MCP255 rewrite the human-readable suffix of the MCP251 Excel schema line, so anchoring
    # the complete original MCP251 text is intentionally wrong here. Locate the one actual emitted
    # Excel tool-advertisement line after all previous patches, require uniqueness, then replace the
    # whole line. This removes the rows+sheets duplicate example itself, not only its prose.
    lines = tooling.splitlines()
    matches = [
        index
        for index, line in enumerate(lines)
        if 'tools += "- excel_workbook arguments:' in line
    ]
    if len(matches) != 1:
        raise SystemExit(
            f"MCP259 fail-closed: materialized Excel schema line count={len(matches)}, expected=1"
        )
    index = matches[0]
    new_schema_line = '    tools += "- excel_workbook: CREATE prefer the shortest form {\\"rows\\":[[...]],\\"sheet_name\\":\\"Sheet1\\"}. Omit operation, input_path, and output_path when not needed. Never duplicate identical rows in both root rows and sheets. READ/MODIFY use {\\"operation\\":\\"read|modify\\",\\"input_path\\":\\"file/input.xlsx\\",...}. Common small-model wrappers are normalized safely."'
    replacement = [
        "    // MCP259_EXCEL_COMPACT_CREATE_SCHEMA",
        "    // Creating a workbook often requires the model to emit the cell contents itself. Keep",
        "    // the wire envelope minimal so decode time is spent on data, not duplicated JSON.",
        new_schema_line,
    ]
    lines[index:index + 1] = replacement
    tooling = "\n".join(lines) + ("\n" if tooling.endswith("\n") else "")
    TOOLING.write_text(tooling, encoding="utf-8")
    print(f"MCP259 patched: {TOOLING}")
else:
    print(f"MCP259 already applied: {TOOLING}")


final_coord = COORD.read_text(encoding="utf-8")
for required in (
    "MCP259_RECOVERED_TOOL_CONTINUATION",
    "AgentTextToolCallFallback.parse(text)",
    '.put("tool", recoveredCall.toolName)',
    '.put("arguments", recoveredCall.arguments)',
    "MCP259_HOST_DISPATCH_STATE_AUTHORITY",
    "onHostToolDispatchAccepted",
    "state.awaitingToolResult = true",
    "state.completed = false",
    "COMPAT_FRESH_REASON_TOOL_CONTINUATION",
):
    if required not in final_coord:
        raise SystemExit(f"MCP259 fail-closed: coordinator marker missing: {required}")

final_screen = AGENT_SCREEN.read_text(encoding="utf-8")
for required in (
    "MCP259_HOST_DISPATCH_ACCEPTED",
    "AgentCompatRuntimeCoordinator.onHostToolDispatchAccepted(",
    "toolName = parsedToolCall.toolName",
    "arguments = parsedToolCall.arguments",
):
    if required not in final_screen:
        raise SystemExit(f"MCP259 fail-closed: dispatch synchronization marker missing: {required}")

final_tooling = TOOLING.read_text(encoding="utf-8")
for required in (
    "MCP259_EXCEL_COMPACT_CREATE_SCHEMA",
    "excel_workbook: CREATE prefer the shortest form",
    "sheet_name",
    "Omit operation, input_path, and output_path when not needed",
    "Never duplicate identical rows in both root rows and sheets",
):
    if required not in final_tooling:
        raise SystemExit(f"MCP259 fail-closed: compact Excel schema marker missing: {required}")
if 'tools += "- excel_workbook arguments:' in final_tooling:
    raise SystemExit("MCP259 fail-closed: legacy wide Excel schema line still present")

print("MCP259_EXCEL_LATENCY_CONTINUATION_PATCH_PASS")
