#!/usr/bin/env python3
"""MCP259: restore COMPAT continuation state parity and compact Excel create calls.

Runs after MCP251-MCP258 materialization. MCP258 made structurally recoverable Excel calls executable,
but the runtime coordinator could still classify a dialect/repaired call as an ordinary final answer.
That leaves awaitingToolResult=false, so the subsequent TOOL_RESULT bypasses MCP206's fresh
conversation continuation path and can incur a very large post-tool TTFT. MCP259 makes the runtime
state machine use the same shared fallback parser as dispatch, and teaches the model the shortest
safe Excel-create wire form so it does not duplicate rows at root and under sheets.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
AGENT = ROOT / "app/src/main/java/com/google/ai/edge/gallery/customtasks/agentchat"
COORD = AGENT / "AgentCompatRuntimeCoordinator.kt"
TOOLING = AGENT / "AgentTooling.kt"


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
    COORD.write_text(coord, encoding="utf-8")
    print(f"MCP259 patched: {COORD}")
else:
    print(f"MCP259 already applied: {COORD}")


tooling = TOOLING.read_text(encoding="utf-8")
if "MCP259_EXCEL_COMPACT_CREATE_SCHEMA" not in tooling:
    old = '''    tools += "- excel_workbook arguments: {\\\"operation\\\":\\\"create|read|modify\\\",\\\"input_path\\\":\\\"file/input.xlsx\\\",\\\"output_path\\\":\\\"file/output.xlsx\\\",\\\"rows\\\":[[...]],\\\"sheets\\\":[...],\\\"operations\\\":[...]}. Creates, reads, or edits XLSX workbooks."\n'''
    new = '''    // MCP259_EXCEL_COMPACT_CREATE_SCHEMA\n    // Creating a workbook often requires the model to emit the cell contents itself. Keep the wire\n    // envelope minimal so small on-device models spend decode time on data, not duplicate JSON.\n    tools += "- excel_workbook: for CREATE prefer the shortest form {\\\"rows\\\":[[...]],\\\"sheet_name\\\":\\\"Sheet1\\\"}. Omit operation, input_path, and output_path when not needed. Never duplicate identical rows in both root rows and sheets. For READ/MODIFY use {\\\"operation\\\":\\\"read|modify\\\",\\\"input_path\\\":\\\"file/input.xlsx\\\",...}."\n'''
    tooling = replace_once(tooling, old, new, "Excel compact COMPAT schema")
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
    "COMPAT_FRESH_REASON_TOOL_CONTINUATION",
):
    if required not in final_coord:
        raise SystemExit(f"MCP259 fail-closed: coordinator marker missing: {required}")

final_tooling = TOOLING.read_text(encoding="utf-8")
for required in (
    "MCP259_EXCEL_COMPACT_CREATE_SCHEMA",
    "sheet_name",
    "Never duplicate identical rows",
):
    if required not in final_tooling:
        raise SystemExit(f"MCP259 fail-closed: compact Excel schema marker missing: {required}")

print("MCP259_EXCEL_LATENCY_CONTINUATION_PATCH_PASS")
