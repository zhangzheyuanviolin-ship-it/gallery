#!/usr/bin/env python3
"""MCP256: mode-agnostic textual tool dispatch plus safe Excel row-width repair.

Run after MCP251-255 materialization. The patch is fail-closed so a source drift cannot silently
produce an APK that claims to contain this fix.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
AGENT = ROOT / "app/src/main/java/com/google/ai/edge/gallery/customtasks/agentchat"
SCREEN = AGENT / "AgentChatScreen.kt"
EXCEL = AGENT / "AgentExcelMcp255Compat.kt"


def require_count(text: str, needle: str, expected: int, label: str) -> None:
    count = text.count(needle)
    if count != expected:
        raise SystemExit(f"MCP256 fail-closed: {label} count={count}, expected={expected}")


def patch_screen(text: str) -> str:
    marker = "MCP256_MODE_AGNOSTIC_TEXT_TOOL_FALLBACK"
    if marker in text:
        return text

    old_entry = """    if (resolveAgentToolMode(model) == ResolvedAgentToolMode.COMPAT && lastAgentText != null) {
      val parsedToolCall = parseCompatToolCall(lastAgentText.content)
"""
    new_entry = """    val textualToolSignal =
      lastAgentText?.let { AgentTextToolCallFallback.hasStrongToolSignal(it.content) } == true
    if (
      lastAgentText != null &&
        (resolveAgentToolMode(model) == ResolvedAgentToolMode.COMPAT || textualToolSignal)
    ) {
      // MCP256_MODE_AGNOSTIC_TEXT_TOOL_FALLBACK
      // Session mode describes the requested generation path; the emitted wire text is authoritative.
      val parsedToolCall = AgentTextToolCallFallback.parse(lastAgentText.content)
"""
    require_count(text, old_entry, 1, "completion parser entry")
    text = text.replace(old_entry, new_entry, 1)

    old_dispatch = """      if (parsedToolCall != null) {
        val originalUserRequest =
"""
    new_dispatch = """      if (parsedToolCall == null && textualToolSignal) {
        // MCP256_PROTOCOL_LEAK_GUARD
        // A strong machine-protocol marker must never survive as an ordinary assistant answer.
        viewModel.removeLastMessage(model = model)
        viewModel.addMessage(
          model = model,
          message =
            ChatMessageInfo(
              content = "检测到工具调用协议，但本轮格式未能安全解析；已阻止内部工具协议作为最终回复显示。"
            ),
        )
        compatToolStepsByModel.remove(model.name)
        updateProgressPanel(viewModel = viewModel, model = model, agentTools = agentTools)
        return@handleGenerationDone
      }
      if (parsedToolCall != null) {
        val originalUserRequest =
"""
    require_count(text, old_dispatch, 1, "tool dispatch branch")
    return text.replace(old_dispatch, new_dispatch, 1)


def patch_excel(text: str) -> str:
    marker = "MCP256_SAFE_TRAILING_BLANK_COLUMN_REPAIR"
    if marker in text:
        return text

    old_all_rows = """    if ((0 until rows.length()).all { rows.opt(it) is JSONArray }) return rows
"""
    new_all_rows = """    if ((0 until rows.length()).all { rows.opt(it) is JSONArray }) {
      return trimTrailingBlankOverflow(rows)
    }
"""
    require_count(text, old_all_rows, 1, "2D row fast path")
    text = text.replace(old_all_rows, new_all_rows, 1)

    anchor = """  private fun actionOf(raw: JSONObject): String =
"""
    helper = """  // MCP256_SAFE_TRAILING_BLANK_COLUMN_REPAIR
  // Small models sometimes append empty placeholder cells beyond the header width. Trimming is
  // allowed only when every overflow cell in that row is blank/null; non-empty overflow is data.
  private fun trimTrailingBlankOverflow(rows: JSONArray): JSONArray {
    if (rows.length() < 2) return rows
    val header = rows.optJSONArray(0) ?: return rows
    val width = header.length()
    if (width <= 0) return rows

    val out = JSONArray().put(JSONArray(header.toString()))
    var changed = false
    for (i in 1 until rows.length()) {
      val row = rows.optJSONArray(i) ?: return rows
      if (row.length() <= width) {
        out.put(JSONArray(row.toString()))
        continue
      }
      val overflowIsBlank = (width until row.length()).all { index -> isBlankCell(row.opt(index)) }
      if (!overflowIsBlank) {
        out.put(JSONArray(row.toString()))
        continue
      }
      val trimmed = JSONArray()
      for (index in 0 until width) trimmed.put(row.opt(index))
      out.put(trimmed)
      changed = true
    }
    return if (changed) out else rows
  }

  private fun isBlankCell(value: Any?): Boolean =
    value == null || value === JSONObject.NULL || (value is String && value.isBlank())

"""
    require_count(text, anchor, 1, "Excel helper insertion anchor")
    return text.replace(anchor, helper + anchor, 1)


for path, patcher, marker in [
    (SCREEN, patch_screen, "MCP256_MODE_AGNOSTIC_TEXT_TOOL_FALLBACK"),
    (EXCEL, patch_excel, "MCP256_SAFE_TRAILING_BLANK_COLUMN_REPAIR"),
]:
    original = path.read_text(encoding="utf-8")
    updated = patcher(original)
    if marker not in updated:
        raise SystemExit(f"MCP256 fail-closed: marker missing in {path.name}")
    path.write_text(updated, encoding="utf-8")
    print(f"MCP256 patched: {path}")

fallback = AGENT / "AgentTextToolCallFallback.kt"
if not fallback.exists():
    raise SystemExit("MCP256 fail-closed: AgentTextToolCallFallback.kt missing")
print("MCP256_TEXT_TOOL_DISPATCH_PATCH_PASS")
